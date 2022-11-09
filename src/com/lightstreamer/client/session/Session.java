/*
 *  Copyright (c) Lightstreamer Srl
 *  
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *      https://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */


package com.lightstreamer.client.session;

import java.util.ArrayList;
import java.util.Date;

import com.lightstreamer.client.ClientListener;
import com.lightstreamer.client.ConnectionDetails;
import com.lightstreamer.client.ConnectionOptions;
import com.lightstreamer.client.Constants;
import com.lightstreamer.client.mpn.MpnRegisterRequest;
import com.lightstreamer.client.mpn.MpnRegisterTutor;
import com.lightstreamer.client.mpn.MpnResetBadgeRequest;
import com.lightstreamer.client.mpn.MpnResetBadgeTutor;
import com.lightstreamer.client.mpn.MpnSubscribeRequest;
import com.lightstreamer.client.mpn.MpnSubscribeTutor;
import com.lightstreamer.client.mpn.MpnUnsubscribeFilterRequest;
import com.lightstreamer.client.mpn.MpnUnsubscribeFilterTutor;
import com.lightstreamer.client.mpn.MpnUnsubscribeRequest;
import com.lightstreamer.client.mpn.MpnUnsubscribeTutor;
import com.lightstreamer.client.platform_data.offline.OfflineStatus;
import com.lightstreamer.client.platform_data.offline.OfflineStatus.NetworkStatusListener;
import com.lightstreamer.client.protocol.Protocol;
import com.lightstreamer.client.protocol.ProtocolListener;
import com.lightstreamer.client.requests.BindSessionRequest;
import com.lightstreamer.client.requests.ChangeSubscriptionRequest;
import com.lightstreamer.client.requests.ConstrainRequest;
import com.lightstreamer.client.requests.CreateSessionRequest;
import com.lightstreamer.client.requests.DestroyRequest;
import com.lightstreamer.client.requests.ForceRebindRequest;
import com.lightstreamer.client.requests.MessageRequest;
import com.lightstreamer.client.requests.RecoverSessionRequest;
import com.lightstreamer.client.requests.RequestTutor;
import com.lightstreamer.client.requests.ReverseHeartbeatRequest;
import com.lightstreamer.client.requests.SubscribeRequest;
import com.lightstreamer.client.requests.UnsubscribeRequest;
import com.lightstreamer.client.requests.VoidTutor;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;
import com.lightstreamer.util.Assertions;
import com.lightstreamer.util.ListenableFuture;
import com.lightstreamer.util.threads.PendingTask;

/**
 * Calls to this class are all performed through the Session Thread 
 */
public abstract class Session {
  
  
  protected static final String OFF = "OFF";
  /**
   * Expecting create_session/recovery response
   * (next: CREATED)
   */
  protected static final String CREATING = "CREATING";
  /**
   * Expecting LOOP after having received CONOK as create_session/recovery response
   * (previous: CREATING, next: FIRST_PAUSE)
   */
  protected static final String CREATED = "CREATED";
  /**
   * Expecting the expiration of a small pause after create_session/recovery response 
   * and before sending bind_session 
   * (previous: CREATED, next: FIRST_BINDING)
   */
  protected static final String FIRST_PAUSE = "FIRST_PAUSE";
  /**
   * Expecting bind_session response 
   * (previous: FIRST_PAUSE, next: RECEIVING)
   */
  protected static final String FIRST_BINDING = "FIRST_BINDING";
  protected static final String PAUSE = "PAUSE";
  /**
   * Expecting bind_session response (transport is polling)
   */
  protected static final String BINDING = "BINDING";
  /**
   * Reading item updates
   * (previous: FIRST_BINDING)
   */
  protected static final String RECEIVING = "RECEIVING";
  protected static final String STALLING = "STALLING";
  protected static final String STALLED = "STALLED";
  /**
   * Expecting the expiration of a timeout after an error 
   * and before creating a new session or recovering the current
   * (next: CREATING)
   */
  protected static final String SLEEP = "SLEEP";
  
  protected static final boolean GO_TO_SLEEP = true;
  protected static final boolean GO_TO_OFF = false;
  
  protected static final boolean CLOSED_ON_SERVER = true;
  protected static final boolean OPEN_ON_SERVER = false;
  protected static final boolean NO_RECOVERY_SCHEDULED = true;
  protected static final boolean RECOVERY_SCHEDULED = false;
  
  private static final int PERMISSION_TO_FAIL = 1;
    
  protected final Logger log = LogManager.getLogger(Constants.SESSION_LOG);
  
  /**
   * Address of the server for the current session.
   * It can be the control-link (carried by CONOK message), 
   * or {@link ConnectionDetails#getServerAddress()} if the control-link is not set.
   * It can also be null before receiving the first CONOK message.
   */
  protected String sessionServerAddress = null;
  /**
   * Copy of {@link ConnectionDetails#getServerAddress()}.
   */
  protected String serverAddressCache = null;
  /**
   * Copy of {@link ConnectionOptions#isServerInstanceAddressIgnored()}.
   */
  protected boolean ignoreServerAddressCache = false;
  
  protected boolean isPolling;
  protected boolean isForced;
  private String sessionId;
  protected int bindCount = 0;
  private long dataNotificationCount = 0;
  
  boolean switchRequired = false;
  private boolean slowRequired = false;
  private boolean switchForced = false;
  private String switchCause = "";
  /**
   * WebSocket support has been enabled again because the client IP has changed. 
   * Next bind_session must try WebSocket as transport.
   */
  private boolean switchToWebSocket = false;
  
  private boolean cachedRequiredBW;
  
  private int workedBefore = 0;
  private long sentTime = 0;
  private PendingTask lastKATask = null;
  private long reconnectTimeout = 0;
  
  protected String phase = OFF;
  protected int phaseCount = 0;
  
  protected SessionListener handler;
  protected int handlerPhase;
  
  protected InternalConnectionDetails details;
  protected InternalConnectionOptions options;
  private SlowingHandler slowing;
  private SubscriptionsListener subscriptions;
  private MessagesListener messages;
  
  private SessionThread thread;
  protected final Protocol protocol;
  private final boolean retryAgainIfStreamFails;
  private final OfflineCheck offlineCheck;
  
  /**
   * Recovery status bean.
   */
  protected final RecoveryBean recoveryBean;
  
  protected final int objectId;
  
  /**
   * When true, the special create request must be emitted which handles CONERR,5
   */
  protected boolean serverBusy = false;
  /**
   * Recovery can be temporarily disabled when the client discovers that the server counter
   * (as expressed by PROG) is different from the client counter.
   * The current session is not invalidated but in case of error a new session will be created.
   */
  protected boolean isRecoveryDisabled = false;
  
  protected final NetworkStatusListener networkStatusListener;
  
  protected Session(int objectId, boolean isPolling, boolean forced, 
      SessionListener handler, SubscriptionsListener subscriptions, MessagesListener messages,
      Session originalSession, SessionThread thread, Protocol protocol,
      InternalConnectionDetails details, final InternalConnectionOptions options, int callerPhase,
      boolean retryAgainIfStreamFails, boolean sessionRecovery) {
      
      this.objectId = objectId;
      if (log.isDebugEnabled()) {
          log.debug("New session oid=" + this.objectId);
      }
    
    this.isPolling = isPolling;
    this.isForced = forced;
    
    this.handler = handler;
    this.handlerPhase = callerPhase;
    
    this.details = details;
    this.options = options;
    
    this.slowing = new SlowingHandler(this.options);
    
    this.subscriptions = subscriptions;
    this.messages = messages;
    
    this.thread = thread;
    
    this.protocol = protocol;
    this.protocol.setListener(new TextProtocolListener());
    
    this.retryAgainIfStreamFails = retryAgainIfStreamFails;
    this.offlineCheck = new OfflineCheck(thread);
    
    if (originalSession != null) {
      
      setSessionId(originalSession.sessionId);
      this.sessionServerAddress  = originalSession.sessionServerAddress;
      this.bindCount = originalSession.bindCount;
      this.dataNotificationCount = originalSession.dataNotificationCount;
      
      assert originalSession.serverAddressCache != null;
      this.serverAddressCache =  originalSession.serverAddressCache;
      this.ignoreServerAddressCache = originalSession.ignoreServerAddressCache;
      
      this.slowing.setMeanElaborationDelay(originalSession.slowing.getMeanElaborationDelay());
      
      originalSession.protocol.copyPendingRequests(this.protocol);
      
      this.recoveryBean = new RecoveryBean(sessionRecovery, originalSession.recoveryBean);
      
    } else {
        assert ! sessionRecovery;
        this.recoveryBean = new RecoveryBean();
    }
    
    this.networkStatusListener = new OfflineStatus.NetworkStatusListener() {
        @Override
        public void onOnline() {
            long timeLeftMs = recoveryBean.timeLeftMs(options.getSessionRecoveryTimeout());
            boolean startRecovery = timeLeftMs > 0 && ! isRecoveryDisabled;
            if (timeLeftMs <= 0) {
                // close the current session and prepare to create a new one
                closeSession("recovery.timeout.elapsed", CLOSED_ON_SERVER, RECOVERY_SCHEDULED);
                //now is SLEEPing
            }
            launchTimeout("online.again", 0, null, startRecovery);            
        }
        @Override
        public void onOffline() {
            // not used
        }
    };
    // during initialization getPushServerAddress() can be null, but getServerAddress() is always set
    // if getPushServerAddress() is different from getServerAddress(), the online checker may not be accurate
    // but online event detection is only a best-effort service to speed-up the reconnection
    offlineCheck.addStatusListener(networkStatusListener, details.getServerAddress());
  }


  private void reset() {
    setSessionId(null);
    this.sessionServerAddress = null;
    this.bindCount = 0;
    this.dataNotificationCount = 0;
    
    this.serverAddressCache = null;
    this.ignoreServerAddressCache = false; 
    
    this.switchRequired = false;
    this.switchForced = false;
    this.slowRequired = false;
    this.switchCause = "";
    
    this.cachedRequiredBW = false; //this is set only if a changeBW request is received while in CREATING status (too late to send it via create_session, too early to issue a control)
    //note that when the session number is received the control handler is reset, so that put it there is not an option
  }
  
  
///////////////////////////////////phase handling
  
  protected boolean is(String phaseToCheck) {
    return this.phase.equals(phaseToCheck);
  }
  
  protected boolean isNot(String phaseToCheck) {
    return !this.is(phaseToCheck);
  }
  
  protected boolean changePhaseType(String newType, boolean startRecovery) {
    String oldType = this.phase;
    int ph = this.phaseCount;
    
    if (isNot(newType)) {
        this.phase = newType;
        this.phaseCount++;
        ph = this.phaseCount;
        if (log.isDebugEnabled()) {
            log.debug("Session state change (" + objectId + "): " + oldType + " -> " + newType);
        }
        this.handler.sessionStatusChanged(this.handlerPhase, this.phase, startRecovery);
    }
    
    //XXX this check should be useless, this.handler.statusChanged should never change our status: verify and adapt
    return ph == this.phaseCount;
  }
  
  protected boolean changePhaseType(String newType) {
      return changePhaseType(newType, false);
  }
  
  String getHighLevelStatus(boolean startRecovery) {
    if (is(OFF)) {
      return Constants.DISCONNECTED;
      
    } else if (is(SLEEP)) {
      if (startRecovery) {
          return Constants.TRYING_RECOVERY;
      } else {
          return Constants.WILL_RETRY;
      }
      
    } else if (is(CREATING)) {
        if (recoveryBean.isRecovery()) {
            return Constants.TRYING_RECOVERY;
        } else {
            return Constants.CONNECTING;
        }
      
    } else if (is(CREATED) || is(FIRST_PAUSE) || is(FIRST_BINDING)) {
      return Constants.CONNECTED + this.getFirstConnectedStatus();
      
    } else if (is(STALLED)) {
      return Constants.STALLED;
      
    /*} else if (is(RECEIVING) && (this.switchRequired || this.slowRequired)) {
      return Constants.CONNECTED + Constants.SENSE;
      
      this would avoid the SENSE->STREAMING->POLLING case but introduces the
      STREAMING->STALLED->SENSE->POLLING one (problem is the client would be receiving data while in SENSE status)
      
      */
      
    } else { //BINDING RECEIVING STALLING PAUSE
      return Constants.CONNECTED + this.getConnectedHighLevelStatus();
    }        
  }
  
  protected abstract String getConnectedHighLevelStatus();
  protected abstract String getFirstConnectedStatus();
  
  protected void handleReverseHeartbeat(boolean force) {
      this.protocol.handleReverseHeartbeat();
  }
  
  protected abstract boolean shouldAskContentLength();
  
  boolean isOpen() { 
    return isNot(OFF) && isNot(CREATING) && isNot(SLEEP);
  }
  
  boolean isStreamingSession() {
    return !this.isPolling;
  }
  
  String getPushServerAddress() {
      // use the control-link address if available, otherwise use the address configured at startup 
      return sessionServerAddress == null ? serverAddressCache : sessionServerAddress;
  }
  
  public String getSessionId() {
    return sessionId == null ? "" : sessionId;
  }
    
//////////////////////////////////external calls
  
  protected void createSession(String oldSessionId, String reconnectionCause, boolean serverBusy) {
      this.serverBusy = serverBusy;
      createSession(oldSessionId, reconnectionCause);
  }
  
  protected void createSession(String oldSessionId,String reconnectionCause) {
    boolean openOnServer = isNot(OFF) && isNot(SLEEP) ? OPEN_ON_SERVER : CLOSED_ON_SERVER;
    
    //JS client here tests the mad timeouts, returns false if it fails, here we always return true
    
    if (openOnServer == OPEN_ON_SERVER) {
      reconnectionCause = reconnectionCause != null ? reconnectionCause : "";
      this.closeSession("new."+reconnectionCause,OPEN_ON_SERVER,RECOVERY_SCHEDULED);
    }

    this.reset();
    
    this.details.setSessionId(null);
    this.details.setServerSocketName(null);
    this.details.setClientIp(null);
    this.details.setServerInstanceAddress(null);
    
    this.serverAddressCache =  this.details.getServerAddress();
    this.ignoreServerAddressCache =  this.options.isServerInstanceAddressIgnored();
    
    this.options.setInternalRealMaxBandwidth(null);

    //HTML CLIENT ONLY (prevents late forever-frame events to pass through
    //this.incPushPhase();
    
    log.info("Opening new session");
    
    boolean sent = this.createSessionExecution(this.phaseCount,oldSessionId, reconnectionCause);
    if (sent) {
      this.createSent();
    } //else we're offline and we set a timeout to try again in OFFLINE_TIMEOUT millis
  }
  
  protected boolean createSessionExecution(final int ph, final String oldSessionId, String cause) {
    if (ph != this.phaseCount) {
      return false;
    }
    
    
    String server = this.getPushServerAddress();
    
    if (this.offlineCheck.shouldDelay(server)) {
      log.info("Client is offline, delaying connection to server");
      
      this.thread.schedule(new Runnable() {
        @Override
        public void run() {
          createSessionExecution(ph,oldSessionId,"offline");
        }
      }, this.offlineCheck.getDelay());
      
      return false;
    }
    

    CreateSessionRequest request = new CreateSessionRequest(server,
        this.isPolling,cause,this.options,this.details, this.slowing.getDelay(), 
        this.details.getPassword(), oldSessionId, serverBusy);
    
    
    this.protocol.sendCreateRequest(request);
    return true;
  }
  
  
  protected void bindSession(String bindCause) {
    //JS client here tests the mad timeouts, returns false if it fails, here we always return true
    
    this.bindCount++;
  
    if (isNot(PAUSE) && isNot(FIRST_PAUSE) && isNot(OFF)) {
      //OFF is valid if we bind to someone else's phase
      log.error("Unexpected phase during binding of session");
      this.shutdown(GO_TO_OFF);
      return;
    }
    
    if (is(OFF)) {
      //bind someonelse's session
      if (!this.changePhaseType(FIRST_PAUSE)) { 
        return;
      }
      
    }
    
    if (this.isPolling) {
      log.debug("Binding session");
    } else {
      log.info("Binding session");
    }
    
    ListenableFuture bindFuture = this.bindSessionExecution(bindCause);
    bindFuture.onFulfilled(new Runnable() {
        @Override
        public void run() {
            assert Assertions.isSessionThread();
            bindSent();
        }
    });
  }
  
  protected ListenableFuture bindSessionExecution(String bindCause) {
    /*if (ph != this.phaseCount) {
      return false; //on the JS client there was the possibility to get this called asynchronously
      //we don't have that case here
    }*/

    BindSessionRequest request = new BindSessionRequest(this.getPushServerAddress(),this.getSessionId(),
        this.isPolling,bindCause,this.options, this.slowing.getDelay(), this.shouldAskContentLength(),
        protocol.getMaxReverseHeartbeatIntervalMs());
    
    return this.protocol.sendBindRequest(request);
  }
  
  protected void recoverSession() {
      RecoverSessionRequest request = new RecoverSessionRequest(
              getPushServerAddress(),
              getSessionId(),
              "network.error",
              options,
              slowing.getDelay(),
              dataNotificationCount);
      protocol.sendRecoveryRequest(request);
      /*
       * Start a timeout. If the server doesn't answer to the recovery request,
       * the recovery request is sent again.
       */
      createSent();
  }
  
  boolean isActive() {
      return is(CREATED) 
          || is(FIRST_BINDING) 
          || is(BINDING)
          || is(RECEIVING) 
          || is(STALLING )
          || is(STALLED);
  }
  
  protected void requestSwitch(int newHPhase, String switchCause, boolean forced, boolean startRecovery) {
    this.handlerPhase = newHPhase;
    
    if (this.switchRequired) {
      //switch already requested!
      return;
    }
    
    log.debug("Switch requested phase=" + phase + " cause=" + switchCause);
    //in case we were waiting a slow-switch we have to override that command
    this.slowRequired = false;
    
    if (is(CREATING) || is(SLEEP) || is(OFF)) { 
        //Session Machine: during these statuses we do not have a session id,
        //we're switch ready but the switch is not possible
        /*
         * WARNING
         * I suppose that this condition does not happen because a session creation should never
         * superimpose another one, but I am not sure.
         */
        log.error("Unexpected creation of a session while another one is still creating");
        this.handler.streamSense(this.handlerPhase,switchCause,forced);
      
    } else if (is(PAUSE) || is(FIRST_PAUSE)) {
      this.handler.switchReady(this.handlerPhase,switchCause,forced,startRecovery);
      
    } else  { 
      //Session Machine: during these statuses a control to ask for an immediate loop is sent if switch 
      //or slow are requested
      // CREATED, FIRST_BINDING, BINDING, RECEIVING, STALLING, STALLED
      this.switchRequired = true;
      this.switchForced = forced;
      this.switchCause = switchCause;
      
      this.sendForceRebind(switchCause);
    } 

  }
  
  protected void requestSlow(int newHPhase) {
    this.handlerPhase = newHPhase;
    
    if (this.slowRequired) {
      //slow already requested
      return;
    }
    
    log.debug("Slow requested");
    
    if (is(CREATING) || is(SLEEP) || is(OFF)
            /* I have inverted the condition, because the old one seems wrong: isNot(CREATING) && isNot(SLEEP) && isNot(OFF)*/) {
      log.error("Unexpected phase during slow handling");
      this.shutdown(GO_TO_OFF);
      return;
    }

    if (is(PAUSE) || is(FIRST_PAUSE)) {
      this.handler.slowReady(this.handlerPhase);
      
    } else {
      this.slowRequired = true;
      this.sendForceRebind("slow");
    }
  }
  
  protected void closeSession(String closeReason,boolean alreadyClosedOnServer,boolean noRecoveryScheduled) {
      closeSession(closeReason, alreadyClosedOnServer, noRecoveryScheduled, false);
  }
  
  protected void closeSession(String closeReason,boolean alreadyClosedOnServer,boolean noRecoveryScheduled,boolean forceConnectionClose) {
    log.info("Closing session");
    if (this.isOpen()) {
      //control link is now obsolete
      
      if (!alreadyClosedOnServer) {
        this.sendDestroySession(closeReason);
      }  
   
      this.subscriptions.onSessionClose();
      this.messages.onSessionClose();
      this.handlerPhase = this.handler.onSessionClose(this.handlerPhase,noRecoveryScheduled);
      
      this.details.setSessionId(null);
      this.details.setServerSocketName(null);
      this.details.setClientIp(null);
      this.details.setServerInstanceAddress(null);
      this.options.setInternalRealMaxBandwidth(null);
      
    } else {
        this.subscriptions.onSessionClose();
        this.messages.onSessionClose();
        this.handlerPhase = this.handler.onSessionClose(this.handlerPhase,noRecoveryScheduled);
    }
    this.shutdown(!noRecoveryScheduled, forceConnectionClose);
  }
  
  void resetTimers() {
      this.options.resetRetryDelay();
      this.options.resetConnectTimeout();
  }
  
  protected void shutdown(boolean goToSleep) {
      shutdown(goToSleep, false);
  }
  
  /**
   * can be used from the outside to stop this Session without killing the (server) session
   */
  protected void shutdown(boolean goToSleep,boolean forceConnectionClose) {
    this.reset();
    this.changePhaseType(goToSleep?SLEEP:OFF);
    if (is(OFF)) {
        offlineCheck.removeStatusListener(networkStatusListener);
    }
    this.protocol.stop(goToSleep, forceConnectionClose); //if we sleep we will still be interested in pending control response 
    log.debug("Session shutdown");
  }
  
/////////////////////////////////////////SESSION MACHINE EVENTS 
  
  protected void onTimeout(String timeoutType, int phaseCount, long usedTimeout, String coreCause, boolean startRecovery) {
    if (phaseCount != this.phaseCount) {
      return;
    }
    
    if (log.isDebugEnabled()) {        
        log.debug("Timeout event [" + timeoutType + "] while " + this.phase 
                + " cause=" + coreCause 
                + " startRecovery=" + startRecovery);
    }
    
    //in case of sleep we lose information in the LS_cause
    String tCause = "timeout."+this.phase+"."+this.bindCount;
    if (is(SLEEP) && coreCause != null) {
      tCause = coreCause;
    }
    
    if (is(CREATING)) {
      long timeLeftMs = recoveryBean.timeLeftMs(options.getSessionRecoveryTimeout());
      if (recoveryBean.isRecovery() && timeLeftMs > 0) {
          /*
           * POINT OF RECOVERY (1/2):
           * a previous recovery request has received no response within the established timeout, 
           * so we send another request in loop.
           */
          if (log.isDebugEnabled()) {
              log.debug("Start session recovery. Cause: no response timeLeft=" + timeLeftMs);
          }          
          this.options.increaseConnectTimeout();
          handler.recoverSession(this.handlerPhase, tCause, this.isForced, this.workedBefore>0);
    
      } else {
          log.debug("Start new session. Cause: no response");
          String sleepCause = "create.timeout";
          //send to SLEEP
          this.closeSession(sleepCause,CLOSED_ON_SERVER,RECOVERY_SCHEDULED,true/*forceConnectionClose*/);
          assert is(SLEEP);

          this.options.increaseConnectTimeout();
          this.launchTimeout("zeroDelay", 0, sleepCause, false);
      }
      
    } else if (is(CREATED) || is(BINDING) || is(STALLED) || is(SLEEP)) {
      
      if (this.slowRequired || this.switchRequired) {
        log.debug("Timeout: switch transport");
        this.handler.streamSense(this.handlerPhase,tCause+".switch",this.switchForced);
      } else if (!this.isPolling || this.isForced) {
        //this.createSession(this.sessionId,tCause); //THIS is bad, because it forces us to reuse stuff
        if (startRecovery) {
            /*
             * POINT OF RECOVERY (2/2):
             * 
             * This point is reached 
             * 1) after the method onErrorEvent has detected a socket failure,
             *    set the phase to SLEEP and scheduled the onTimeout task; or
             * 2) when the session is STALLED because the client doesn't receive any data from the server
             *    (see method timeoutForReconnect)
             */
            log.debug("Timeout: recover session");
            this.handler.recoverSession(this.handlerPhase,tCause,this.isForced,this.workedBefore>0);
        } else {
            
            log.debug("Timeout: new session");
            this.handler.retry(this.handlerPhase,tCause,this.isForced,this.workedBefore>0,this.serverBusy);
        }
      } else {
          /*
           * Branch reserved for polling.
           * 
           * NOTE 
           * In the past, when an error occurred during polling, the new session was created not in polling
           * but in streaming (probably because polling was seen as sub-optimal transport).
           * With the introduction of the recovery, we are faced with 3 options:
           * 1) recovering the session in polling
           * 2) recovering the session in streaming
           * 3) creating a new session in streaming.
           * The second option is probably the best one, but, since the client falls-back rarely to polling,
           * in order to ease the implementation, I have decided to follow the third path.
           */
          log.debug(startRecovery ? "Timeout: switch transport from polling (ignore recovery)" : "Timeout: switch transport from polling");
          this.handler.streamSense(this.handlerPhase,tCause,false);
      }
      
    } else if (is(FIRST_BINDING)) {

      if (this.slowRequired || this.switchRequired) {
        this.handler.streamSense(this.handlerPhase,tCause+".switch",this.switchForced);
      } else if (this.workedBefore > 0 || this.isForced || this.retryAgainIfStreamFails) {
        this.handler.retry(this.handlerPhase,tCause,this.isForced,this.workedBefore>0,this.serverBusy);
        //this.createSession(this.sessionId,tCause); //THIS is bad, because it forces us to reuse stuff
      } else if (this.createNewOnFirstBindTimeout()) {
        this.handler.streamSense(this.handlerPhase,tCause+".switch",this.switchForced);
      } else { 
        //NOTE: the JS implementation is different because it has onSessionGivesUp
        //calls based on not-working connections (i.e.: browser does not support WebSockets)
        this.handler.streamSenseSwitch(this.handlerPhase, tCause, phase, recoveryBean.isRecovery());
      }
      
    } else if (is(PAUSE)) {
      if (this.isPolling) {
        this.slowing.testPollSync(usedTimeout,new Date().getTime());
      }
      this.bindSession("loop");
      
    } else if(is(FIRST_PAUSE)) {
      if (switchToWebSocket) {
          /*
           * We must bind the session returned by
           * create_session command, but firstly
           * we must change the transport from
           * HTTP to WebSocket.
           */
          handler.switchToWebSocket(recoveryBean.isRecovery());
          switchToWebSocket = false; // reset the flag
      } else {  
          this.bindSession("loop1");
      }
      
    } else if (is(RECEIVING)) {
      this.timeoutForStalled();
      
    } else if (is(STALLING)) {
      this.timeoutForReconnect();
      
    } else { //_OFF
      log.error("Unexpected timeout event while session is OFF");
      this.shutdown(GO_TO_OFF);
    }
  }
  
  private boolean createNewOnFirstBindTimeout() {
    return this.isPolling;
  }

 ////////////////////////////////////////////////////////actions
   
  protected void createSent() {
    this.sentTime = new Date().getTime();
  
    if (isNot(OFF) && isNot(SLEEP)) {
      log.error("Unexpected phase after create request sent: " + this.phase);
      this.shutdown(GO_TO_OFF);
      return;
    }
    if (!this.changePhaseType(CREATING)) {
      return;
    }
    
    //will be executed if create does not return, no need to specify the cause
    this.launchTimeout("currentConnectTimeout", this.options.getCurrentConnectTimeout(), null, false);
  }
  
  protected void bindSent() {
    this.sentTime = System.currentTimeMillis();

    if (isNot(PAUSE) && isNot(FIRST_PAUSE)) {
      log.error("Unexpected phase after bind request sent: " + this.phase);
      this.shutdown(GO_TO_OFF);
      return;
    }
  
    if (!this.changePhaseType(is(PAUSE) ? BINDING : FIRST_BINDING)) {
      return;
    }
    
    this.serverBusy = false;
    
    this.launchTimeout("bindTimeout", this.getBindTimeout(), null, false); //will be executed if the bind does not return no need to specify the cause
    
  }
  
//////////////////////timeouts   
  
  PendingTask launchTimeout(final String timeoutType, final long pauseToUse, final String cause, final boolean startRecovery) {
    final int pc = this.phaseCount;
    
    if (log.isDebugEnabled()) {        
        log.debug("Status timeout in " + pauseToUse + " [" + timeoutType + "]");
    }
    
    return this.thread.schedule(new Runnable() {

      @Override
      public void run() {
        //XXX instead of checking the phase we might cancel pending tasks when phaseCount changes
        if (pc != phaseCount) { 
          return;
        }
        
        onTimeout(timeoutType,pc,pauseToUse, cause, startRecovery);
      }
      
    }, pauseToUse);
  }
  
  private void timeoutForStalling() {
    if (this.options.getKeepaliveInterval() > 0) {
      //there is probably a timeout already scheduled that has no need to execute, 
      //we cancel it
      if (this.lastKATask != null && !this.lastKATask.isCancelled()) {
        this.lastKATask.cancel();
      }
      
      //we won't reconnect if this executes (we go to STALLING), so no need to add a cause
      this.lastKATask = this.launchTimeout("keepaliveInterval", this.options.getKeepaliveInterval(), null, false);
      
    }
  }
  
  private void timeoutForStalled() {
    if (!this.changePhaseType(STALLING)) {
      return;
    }
    //we won't reconnect if this executes (we go to STALLED), so no need to add a cause
    this.launchTimeout("stalledTimeout", this.options.getStalledTimeout(), null, false); 
  }
  
  private void timeoutForReconnect() {
    if (!this.changePhaseType(STALLED)) {
      return;
    }
    long timeLeftMs = recoveryBean.timeLeftMs(options.getSessionRecoveryTimeout());
    boolean startRecovery = timeLeftMs > 0 && ! isRecoveryDisabled;
    //the onTimeout already knows the cause for this because we're STALLED
    this.launchTimeout("reconnectTimeout", this.options.getReconnectTimeout(), null, startRecovery); 
  }
  
  private void timeoutForExecution() {
    //we won't reconnect if this executes, so no need to add a cause
    this.launchTimeout("executionTimeout", this.options.getStalledTimeout(), null, false);  
    
  }
  
  private long getBindTimeout() {
    if (this.isPolling) {
      return this.options.getCurrentConnectTimeout() + this.options.getIdleTimeout();
    } else {
      return this.workedBefore > 0 && this.reconnectTimeout > 0 ? this.reconnectTimeout : this.options.getCurrentConnectTimeout();
    }
  }
  
  private long getRealPollingInterval() {
    if (is(FIRST_PAUSE)) {
      return this.options.getPollingInterval();
      
    } else {
      
      long spent = System.currentTimeMillis() - this.sentTime;
      return spent > this.options.getPollingInterval() ? 0 : this.options.getPollingInterval()-spent;
    }
  }


  private long calculateRetryDelay() {
    long spent = new Date().getTime() - this.sentTime;
    long currentRetryDelay = options.getCurrentRetryDelay();
    return spent > currentRetryDelay ? 0 : currentRetryDelay - spent;
  }
  
//////////////////////////Requests to protocol  
  
  private void sendForceRebind(String rebindCause) {
    log.info("Sending request to the server to force a rebind on the current connection during " + this.phase);
    
    ForceRebindRequest request = new ForceRebindRequest(this.getPushServerAddress(),this.sessionId,rebindCause,this.slowing.getDelay());
    ForceRebindTutor tutor = new ForceRebindTutor(this.phaseCount,rebindCause);
    
    this.protocol.sendForceRebind(request,tutor);
  }
  
  private void sendDestroySession(String closeReason) {
    log.info("Sending request to the server to destroy the current session during " + this.phase);
    
    DestroyRequest request = new DestroyRequest(this.getPushServerAddress(),this.sessionId,closeReason);
    this.protocol.sendDestroy(request, new VoidTutor(thread, options));
    
    //we do not retry destroy requests: just fire and forget    
  }
  
  void sendMessage(MessageRequest request, RequestTutor tutor) {
    request.setServer(this.getPushServerAddress());
    request.setSession(this.sessionId);
    this.protocol.sendMessageRequest(request,tutor);
  }
  
  /**
   * Forward the MPN request to the protocol layer.
   */
  public void sendMpnRegistration(MpnRegisterRequest request, MpnRegisterTutor tutor) {
      request.setServer(getPushServerAddress());
      request.setSession(sessionId);
      protocol.sendMpnRegisterRequest(request, tutor);
  }
  
  /**
   * Forward the MPN request to the protocol layer.
   */
  public void sendMpnSubscription(MpnSubscribeRequest request, MpnSubscribeTutor tutor) {
      request.setServer(getPushServerAddress());
      request.setSession(sessionId);
      protocol.sendMpnSubscribeRequest(request, tutor);
  }
  
  /**
   * Forward the MPN request to the protocol layer.
   */
  public void sendMpnUnsubscription(MpnUnsubscribeRequest request, MpnUnsubscribeTutor tutor) {
      request.setServer(getPushServerAddress());
      request.setSession(sessionId);
      protocol.sendMpnUnsubscribeRequest(request, tutor);
  }
  
  /**
   * Forward the MPN request to the protocol layer.
   */
  public void sendMpnUnsubscription(MpnUnsubscribeFilterRequest request, MpnUnsubscribeFilterTutor tutor) {
      request.setServer(getPushServerAddress());
      request.setSession(sessionId);
      protocol.sendMpnUnsubscribeRequest(request, tutor);
  }
  
  /**
   * Forward the MPN request to the protocol layer.
   */
  public void sendMpnResetBadge(MpnResetBadgeRequest request, MpnResetBadgeTutor tutor) {
      request.setServer(getPushServerAddress());
      request.setSession(sessionId);
      protocol.sendMpnResetBadgeRequest(request, tutor);
  }
  
  public void sendSubscription(SubscribeRequest request, RequestTutor tutor) {
    request.setServer(this.getPushServerAddress());
    request.setSession(this.sessionId);
    this.protocol.sendSubscriptionRequest(request,tutor);
  }
  
  public void sendUnsubscription(UnsubscribeRequest request, RequestTutor tutor) {
    request.setServer(this.getPushServerAddress());
    request.setSession(this.sessionId);
    this.protocol.sendUnsubscriptionRequest(request,tutor);
  }
  
  public void sendSubscriptionChange(ChangeSubscriptionRequest request, RequestTutor tutor) {
    request.setServer(this.getPushServerAddress());
    request.setSession(this.sessionId);
    this.protocol.sendConfigurationRequest(request,tutor);
  }
  
  public void sendReverseHeartbeat(ReverseHeartbeatRequest request, RequestTutor tutor) {
    request.setServer(this.getPushServerAddress());
    request.setSession(this.sessionId);
    this.protocol.sendReverseHeartbeat(request,tutor);
  }
    
  /**
   * Send a bandwidth request to the transport layer.
   * 
   * @param timeoutMs
   * @param clientRequest If the request is a retransmission, {@code clientRequest} is the original
   * client request. If the request is a client request, {@code clientRequest} is null.
   */
  void sendConstrain(long timeoutMs, ConstrainRequest clientRequest) {
    if (is(OFF) || is(SLEEP)) {
      return;
    } else if (options.isBandwidthUnmanaged()) {
      // if the bandwidth is unmanaged, it is useless to try to change it
      return;
    } else if (is(CREATING)) {
      //too late to send it via create_session
      //too early to send it via control
      this.cachedRequiredBW = true;
      return;
    }
    
    ConstrainRequest request = new ConstrainRequest(this.options.getInternalMaxBandwidth(), clientRequest);
    request.setSession(this.sessionId);
    ConstrainTutor tutor = new ConstrainTutor(timeoutMs, request, thread, options);
    request.setServer(this.getPushServerAddress());
    if (bwRetransmissionMonitor.canSend(request)) {
        this.protocol.sendConstrainRequest(request,tutor);
    }
  }
  
  /**
   * Closes the session and notifies the error to {@link ClientListener}.
   */
  public void notifyServerError(int errorCode, String errorMessage) {
      closeSession("end", CLOSED_ON_SERVER, NO_RECOVERY_SCHEDULED);
      handler.onServerError(errorCode, errorMessage);
  }

  private final BandwidthRetransmissionMonitor bwRetransmissionMonitor = new BandwidthRetransmissionMonitor();
  
  /**
   * The monitor handles, together with {@link ConstrainTutor}, the bandwidth requests made by the client 
   * and the retransmissions due to expiration of timeouts.
   * <p>
   * Retransmissions are regulated by these rules:
   * <ol>
   * <li>it is forbidden to retransmit a request when a more recent request has been (re)transmitted</li>
   * <li>it is forbidden to retransmit a request when a response to a more recent request has been received</li>
   * </ol>
   * NB We say that a request is more recent than another if it was issued later by the client
   * (i.e. it has a greater value of {@link ConstrainRequest#getClientRequestId()}).
   * <p>
   * The rules above ensures that when two bandwidth requests are successively issued, it is not possible
   * that the older one "overrides" and cancels out the effect of the newer one.
   */
  static class BandwidthRetransmissionMonitor {
      /**
       * ClientRequestId of the more recent request which received a response.
       */
      private long lastReceivedRequestId = -1;
      /**
       * ClientRequestId of the more recent request which was (re)transmitted.
       */
      private long lastPendingRequestId = -1;
      
      /**
       * Must be checked before sending a request to the transportation layer.
       * It ensures that when two bandwidth requests are successively issued, it is not possible
       * that the older one "overrides" and cancels out the effect of the newer one.
       */
      synchronized boolean canSend(ConstrainRequest request) {
          long clientId = request.getClientRequestId();
          boolean forbidden = (
                  /* Rule 1: a more recent request is pending */ 
                  clientId < lastPendingRequestId 
                  /* Rule 2: a more recent request has been answered 
                   * NB It is not forbidden to retransmit a request when a previous retransmission is still pending
                   * (if the timeout is expired), but it is forbidden to retransmit a request when a response has arrived.
                   * This is the reason why we use < in the first rule, but we use <= in the second rule. */
                  || clientId <= lastReceivedRequestId);
          if (! forbidden) {
              lastPendingRequestId = clientId;
          }
          return ! forbidden;
      }

      /**
       * Must be checked after receiving a REQOK/REQERR/ERROR from a bandwidth request.
       * It ensures that when two bandwidth requests are successively issued, it is not possible
       * that the older one "overrides" and cancels out the effect of the newer one.
       */
      synchronized void onReceivedResponse(ConstrainRequest request) {
          long clientId = request.getClientRequestId();
          if (clientId > lastReceivedRequestId) {
              lastReceivedRequestId = clientId;
          }
      }
  }
  
  public class TextProtocolListener implements ProtocolListener {
      
      @Override
      public void onInterrupted(boolean wsError,boolean unableToOpen) {
          /*
           * An interruption triggers an attempt to recover the session.
           * The sequence of actions is roughly as follow:
           * - StreamListener.onClosed
           * - Session.onInterrupted
           * - Session.onErrorEvent
           * - Session.launchTimeout
           * - Session.onTimeout
           * - SessionManager.recoverSession (creates a new session object inheriting from this session)
           * - Session.recoverSession
           * - TextProtocol.protocol.sendRecoveryRequest
           */
          onErrorEvent("network.error", false/*closedOnServer*/, unableToOpen, true/*tryRecovery*/, wsError, false);
      }
      
      @Override
      public void onConstrainResponse(RequestTutor tutor) {
          bwRetransmissionMonitor.onReceivedResponse(((ConstrainTutor) tutor).getRequest());
      }

      @Override
      public void onServerSentBandwidth(String maxBandwidth) {
          onEvent();

          if (maxBandwidth.equalsIgnoreCase("unmanaged")) {
              options.setBandwidthUnmanaged(true);
              maxBandwidth = "unlimited";
          }
          options.setInternalRealMaxBandwidth(maxBandwidth);
      }

      @Override
      public void onTakeover(int specificCode) {
          if (specificCode == 41) {
              //this is a nasty android browser bug, that causes a POST to be lost and another one to be reissued 
              //under certain circumstances (https is fundamental for the issue to appear).
              //seen on Android: see mail thread with PartyGaming around the 13/12/2010 for further details. 

              //this is also a Sony-Bravia TV Opera bug, that causes frame-made requests to be reissued when the frame changes again
              //sometimes the correct answer reach the client, others the wrong one carrying error 41 does
              //seen on Opera on bravia TV: see mail thread with Cell-Data around 15/10/2011 for further details.  

              //NOTE at the time of coding this has never happened on the unified Java client
          }
          onErrorEvent("error" + specificCode, CLOSED_ON_SERVER, false, false, false, false);
      }

      @Override
      public void onKeepalive() {
          onEvent();
      }

      @Override
      public void onOKReceived(String newSession, String controlLink, long requestLimitLength, long keepaliveIntervalDefault) {
          log.debug("OK event while " + phase);

          if (isNot(CREATING) && isNot(FIRST_BINDING) && isNot(BINDING)) {
              log.error("Unexpected OK event while session is in status: " + phase);
              shutdown(GO_TO_OFF);
              return;
          }

          String lastUsedAddress = getPushServerAddress();
          String addressToUse = lastUsedAddress;
          if (controlLink != null && !ignoreServerAddressCache) {
              controlLink = RequestsHelper.completeControlLink(addressToUse, controlLink);
              addressToUse = controlLink;
          }
          sessionServerAddress = addressToUse;
          
          if (! lastUsedAddress.equals(sessionServerAddress)) {
              if (is(CREATING)) {
                  /*
                   * Close the WebSocket open because of wsEarlyOpen flag and 
                   * open a new WebSocket using the given control-link.
                   * 
                   * NB This operation affects only create_session requests.
                   * Bind_session requests ignore the control-link.
                   */
                  if (log.isDebugEnabled()) {                    
                      log.debug("Control-Link has changed: " + lastUsedAddress + " -> " + sessionServerAddress);
                  }
                  changeControlLink(sessionServerAddress);
              }
          }

          if (keepaliveIntervalDefault > 0) {
              if (isPolling) {
                  //on polling sessions the longest inactivity permitted is sent instead of the keepalive setting
                  options.setIdleTimeout(keepaliveIntervalDefault);
              } else {
                  options.setKeepaliveInterval(keepaliveIntervalDefault);
              }
          }

          if (is(CREATING)) {
              //New session!
              if (sessionId != null && ! (sessionId.equals(newSession))) {
                  // nothing can be trusted here
                  log.debug("Unexpected session " + sessionId + " found while initializing " + newSession);
                  reset();
              }
              setSessionId(newSession);

          } else {
              if (!sessionId.equals(newSession)) {
                  log.error("Bound unexpected session: " +newSession + " (was waiting for " + sessionId + ")");
                  shutdown(GO_TO_OFF);
                  return;
              }
              /* calculate reconnect timeout, i.e. the actual time we spent to send the request and receive the reponse (the roundtirp) */ 
              long spentTime = new Date().getTime() - sentTime;
              //we add to our connectTimeout the spent roundtrip and we'll use that time as next connectCheckTimeout
              //ok, we wanna give enough time to the client to connect if necessary, but we should not exaggerate :)
              //[obviously if spentTime can't be > this.policyBean.connectTimeout after the first connection, 
              //but it may grow connection after connection if we give him too much time]
              long ct = options.getCurrentConnectTimeout();
              reconnectTimeout = (spentTime > ct ? ct : spentTime) + ct;
          }

          slowing.startSync(isPolling, isForced, new Date().getTime());
          
          // NB 
          // onEvent() changes the connection status, 
          // so the connection details/options must be set before notifying the user
          details.setSessionId(newSession);
          details.setServerInstanceAddress(sessionServerAddress);
          
          onEvent();

          if (is(CREATED)) {
              if (recoveryBean.isRecovery()) {                  
                  /* 
                   * branch reserved for recovery responses 
                   * (i.e. bind_session requests with LS_recovery_from parameter)
                   */
                  recoveryBean.restoreTimeLeft();
                  
              } else {
                  /* 
                   * branch reserved for create_session responses 
                   */
                  handler.onSessionStart();
                  subscriptions.onSessionStart();
                  messages.onSessionStart();
                  if (cachedRequiredBW) {
                      sendConstrain(0, null);
                      cachedRequiredBW = false;
                  }
              }

          } else {
              /* 
               * branch reserved for bind_session responses (recovery responses excluded) 
               */              
              handler.onSessionBound();
              this.onSessionBound();
              
              protocol.setDefaultSessionId(newSession);
          }
      }
      
      void onSessionBound() {
          resetTimers();
      }

      @Override
      public void onLoopReceived(long serverSentPause) {
          log.debug("Loop event while " + phase);

          if (is(RECEIVING) || is(STALLING) || is(STALLED) || is(CREATED)) {
              if (switchRequired) {
                  handler.switchReady(handlerPhase, switchCause, switchForced, false);
              } else if (slowRequired) {
                  handler.slowReady(handlerPhase);
              } else {
                  doPause(serverSentPause);
              }
          } else {
              log.error("Unexpected loop event while session is an non-active status: " + phase);
              shutdown(GO_TO_OFF);
          }          
      }

      @Override
      public void onSyncError(boolean async) {
          String cause = async ? "syncerror" : "control.syncerror";
          onErrorEvent(cause, true, false, false, false, false);
      }
      
      @Override
      public void onRecoveryError() {
          // adapted from method onSyncError
          onErrorEvent("recovery.error", true, false, false, false, false);
      }

      @Override
      public void onServerBusy() {
          serverBusy = true;
          onErrorEvent("server.busy", true, false, false, false, true);
      }
      
      @Override
      public void onExpiry() {
          onErrorEvent("expired", true, false, false, false, false);
      }

      @Override
      public void onUpdateReceived(int subscriptionId, int item, ArrayList<String> args) {
          onEvent();
          subscriptions.onUpdateReceived(subscriptionId, item, args);
      }

      @Override
      public void onEndOfSnapshotEvent(int subscriptionId, int item) {
          onEvent();
          subscriptions.onEndOfSnapshotEvent(subscriptionId, item);
      }

      @Override
      public void onClearSnapshotEvent(int subscriptionId, int item) {
          onEvent();
          subscriptions.onClearSnapshotEvent(subscriptionId, item);
      }

      @Override
      public void onLostUpdatesEvent(int subscriptionId, int item, int lost) {
          onEvent();
          subscriptions.onLostUpdatesEvent(subscriptionId, item, lost);
      }

      @Override
      public void onConfigurationEvent(int subscriptionId, String frequency) {
          onEvent();
          subscriptions.onConfigurationEvent(subscriptionId, frequency);
      }

      @Override
      public void onMessageAck(String sequence, int number, boolean async) {
          if (async) {
              onEvent();
          }
          messages.onMessageAck(sequence, number);
      }

      @Override
      public void onMessageOk(String sequence, int number) {
          onEvent();
          messages.onMessageOk(sequence, number);
      }

      @Override
      public void onMessageDeny(String sequence, int denyCode, String denyMessage, int number, boolean async) {
          if (async) {
              onEvent();
          }
          messages.onMessageDeny(sequence, denyCode, denyMessage, number);
      }

      @Override
      public void onMessageDiscarded(String sequence, int number, boolean async) {
          if (async) {
              onEvent();
          }
          messages.onMessageDiscarded(sequence, number);
      }

      @Override
      public void onMessageError(String sequence, int errorCode, String errorMessage, int number, boolean async) {
          if (async) {
              onEvent();
          }
          messages.onMessageError(sequence, errorCode, errorMessage, number);
      }

      @Override
      public void onSubscriptionError(int subscriptionId, int errorCode, String errorMessage, boolean async) {
          if (async) {
              onEvent();
          }
          subscriptions.onSubscriptionError(subscriptionId, errorCode, errorMessage);
      }

      @Override
      public void onServerError(int errorCode, String errorMessage) {
          notifyServerError(errorCode, errorMessage);
      }
      
      @Override
      public void onPROGCounterMismatch() {
          isRecoveryDisabled = true;
      }
      
      @Override
      public void onUnsubscriptionAck(int subscriptionId) {
          onEvent();
          subscriptions.onUnsubscriptionAck(subscriptionId);
      }

      @Override
      public void onUnsubscription(int subscriptionId) {
          onEvent();
          subscriptions.onUnsubscription(subscriptionId);
      }
      
      @Override
      public void onSubscriptionAck(int subscriptionId) {
          subscriptions.onSubscriptionAck(subscriptionId);
      }

      @Override
      public void onSubscription(int subscriptionId, int totalItems, int totalFields, int keyPosition, int commandPosition) {
          onEvent();
          subscriptions.onSubscription(subscriptionId, totalItems, totalFields, keyPosition, commandPosition);
      }

      @Override
      public void onSubscriptionReconf(int subscriptionId, long reconfId, boolean async) {
          if (async) {
              onEvent();
          }
          subscriptions.onSubscription(subscriptionId,reconfId);
      }

      @Override
      public void onSyncMessage(long seconds) {
          onEvent();
          
          log.debug("Sync event while " + phase);
          boolean syncOk = slowing.syncCheck(seconds, !isPolling, new Date().getTime());
          if (syncOk) {
              if (is(RECEIVING)) {
                  //with XHRStreamingConnection we've seen cases (e.g.: Chrome on Android 2.2 / Opera 10.64 on Kubuntu)
                  //where the first part of the connection is sent as expected while its continuation is not sent at all (Opera)
                  //or is sent in blocks (Chrome) so we wait for the sync method before remembering that the streaming actually works
                  workedBefore = PERMISSION_TO_FAIL; //XXX this will only correclty work with future servers
              }
          } else {
              //we're late, let's fix the issue
              //if already slowing or switching I should not ask to slow again.
              if (switchRequired || slowRequired) {
                  //this Session is already changing, we do not act
                  return;
              }

              handler.onSlowRequired(handlerPhase, slowing.getDelay());
          }      
      }

      @Override
      public void onServerName(String serverName) {
          details.setServerSocketName(serverName);
      }

      @Override
      public void onClientIp(String clientIp) {
          details.setClientIp(clientIp);
          handler.onIPReceived(clientIp);
      }

      private void onEvent() {
          if (log.isDebugEnabled()) {
              log.debug("Data event while " + phase);
          }

          if (is(CREATING)) {
              if (!changePhaseType(CREATED)) { 
                  return;
              } 
              timeoutForExecution();

          } else if (is(CREATED)) {
              //stay created

          } else if (is(FIRST_BINDING)) {
              if (!changePhaseType(RECEIVING)) { 
                  return;
              } 
              offlineCheck.resetMaybeOnline();
              timeoutForStalling();

          } else if (is(BINDING) || is(STALLING) || is(STALLED) || is(RECEIVING)) {
              if (!changePhaseType(RECEIVING)) { 
                  return;
              } 
              timeoutForStalling();

          } else { //FIRST_PAUSE PAUSE SLEEP _OFF 
              log.error("Unexpected push event while session is an non-active status: " + phase);
              shutdown(GO_TO_OFF);
          }
      }

      /**
       * @param reason
       * @param closedOnServer true when receiving CONERR
       * @param unableToOpen
       * @param tryRecovery the flag is true when the method is called from onInterrupted
       * @param wsError unable to open WS
       * @param serverBusyError true when receiving CONERR,5
       */
      private void onErrorEvent(String reason, 
              boolean closedOnServer, boolean unableToOpen, 
              boolean tryRecovery, boolean wsError, 
              boolean serverBusyError) {
          long timeLeftMs = recoveryBean.timeLeftMs(options.getSessionRecoveryTimeout());
          log.error("Error event while " + phase + " reason: " + reason + " tryRecovery=" + tryRecovery + " timeLeft=" + timeLeftMs
                  + " isRecoveryDisabled=" + isRecoveryDisabled
                  + " closedOnServer=" + closedOnServer + " unableToOpen=" + unableToOpen + " wsError=" + wsError
                  + " serverBusyError=" + serverBusyError);
          boolean startRecovery = tryRecovery && timeLeftMs > 0;
          
          doOnErrorEvent(reason, closedOnServer, unableToOpen, startRecovery, timeLeftMs, wsError, serverBusyError);
      }
      
      private void doPause(long serverSentPause) {
          if (!changePhaseType(is(CREATED) ? FIRST_PAUSE : PAUSE)) {
              return;
          }

          long pauseToUse = serverSentPause;
          if (isPolling && isNot(FIRST_PAUSE)) {
              /* 
               * Pausing after a poll cycle.
               * 
               * The check on the state is needed to distinguish create_session requests
               * (characterized by having FIRST_PAUSE as state) which must ignore polling interval
               * and bind_session requests (with state different from FIRST_PAUSE) 
               * which use polling interval.
               */

              if (serverSentPause >= options.getPollingInterval()) { 
                  // we're likely delaying because of the slowing algorithm
                  // nothing to do
              } else {
                  //the server didn't like our request, let's adapt 
                  options.setPollingInterval(serverSentPause);
              }

              pauseToUse = getRealPollingInterval();

          }

          if (isNot(FIRST_PAUSE) && pauseToUse > 0) {
              log.debug("Make pause before next bind");
              launchTimeout("pause", pauseToUse, null, false);
          } else {
              onTimeout("noPause", phaseCount, 0, null, false);
          }
      }

      @Override
      public void onMpnRegisterOK(String deviceId, String adapterName) {
          onEvent();
          handler.onMpnRegisterOK(deviceId, adapterName);
      }
      
      @Override
      public void onMpnRegisterError(int code, String message) {
          handler.onMpnRegisterError(code, message);
      }
      
      @Override
      public void onMpnSubscribeOK(String lsSubId, String pnSubId) {
          handler.onMpnSubscribeOK(lsSubId, pnSubId);
      }
      
      @Override
      public void onMpnSubscribeError(String subId, int code, String message) {
          handler.onMpnSubscribeError(subId, code, message);
      }
      
      @Override
      public void onMpnUnsubscribeError(String subId, int code, String message) {
          handler.onMpnUnsubscribeError(subId, code, message);
      }
      
      @Override
      public void onMpnUnsubscribeOK(String subId) {
          handler.onMpnUnsubscribeOK(subId);
      }
      
      @Override
      public void onMpnResetBadgeOK(String deviceId) {
          handler.onMpnResetBadgeOK(deviceId);
      }
      
      @Override
      public void onMpnBadgeResetError(int code, String message) {
          handler.onMpnBadgeResetError(code, message);
      }

      public long getDataNotificationProg() {
          return dataNotificationCount;
      }

      public void onDataNotification() {
          dataNotificationCount++;
      }
  }
  
  public class ForceRebindTutor extends RequestTutor {

    private final int currentPhase;
    private final String cause;

    ForceRebindTutor(int currentPhase,String cause) {
      super(thread,options);
      
      this.currentPhase = currentPhase;
      this.cause = cause;
    }
    
    @Override
    protected boolean verifySuccess() {
      return this.currentPhase != phaseCount;
    }

    @Override
    protected void doRecovery() {
      sendForceRebind(this.cause);
    }

    @Override
    public void notifyAbort() {
      //nothing to do
    }

    @Override
    protected boolean isTimeoutFixed() {
      return true;
    }

    @Override
    protected long getFixedTimeout() {
      return this.connectionOptions.getForceBindTimeout();
    }

    @Override
    public boolean shouldBeSent() {
      return this.currentPhase == phaseCount;
    }
    
  }
  
  public static class ConstrainTutor extends RequestTutor {
      
    private final ConstrainRequest request;

    public ConstrainTutor(long timeoutMs, ConstrainRequest request, SessionThread sessionThread, InternalConnectionOptions options) {
      super(timeoutMs, sessionThread, options);
      this.request = request;
    }

    @Override
    protected boolean verifySuccess() {
        return false; // NB the real check is made inside the method Session.changeBandwidth
    }

    @Override
    protected void doRecovery() {
        Session session = sessionThread.getSessionManager().getSession();
        if (session != null) {
            session.sendConstrain(this.timeoutMs, request);
        }
    }

    @Override
    public void notifyAbort() {
        // nothing to do
    }

    @Override
    protected boolean isTimeoutFixed() {
      return false;
    }

    @Override
    protected long getFixedTimeout() {
      return 0;
    }

    @Override
    public boolean shouldBeSent() {
      return true;
    }

    public ConstrainRequest getRequest() {
        return request;
    }
    
  }
  
  private void setSessionId(String sessionId) {
      this.sessionId = sessionId;
  }

  /**
   * This method is called by {@link SessionManager} to notify the session that WebSocket support has been enabled again
   * because the client IP has changed. So next bind_session must try WebSocket as transport 
   * (except in the case of forced transport).
   */
  public void restoreWebSocket() {
      assert switchToWebSocket == false;
      assert phase.equals(CREATED);
      assert this instanceof SessionHTTP;
      if (options.getForcedTransport() == null) {
          switchToWebSocket = true;
      } else {
          /*
           * If the transport is forced, it is either HTTP or WebSocket.
           * If it is HTTP, we must not switch to WebSocket. So the flag must remain false.
           * If it is WebSocket, the switch is useless. So the flag must remain false.
           * In either case, we don't need to change it.
           */
      }
  }
  
  public void onFatalError(Throwable e) {
      log.error("A fatal error has occurred. The session will be closed. Cause: " + e);
      protocol.onFatalError(e);
  }

  /**
   * @param reason
   * @param closedOnServer true when receiving CONERR
   * @param unableToOpen
   * @param startRecovery the flag is true when the method is called from onInterrupted
   * @param timeLeftMs
   * @param wsError unable to open WS
   * @param serverBusyError true when receiving CONERR,5
   */
  protected void doOnErrorEvent(String reason, 
          boolean closedOnServer, boolean unableToOpen, 
          boolean startRecovery, long timeLeftMs, 
          boolean wsError,
          boolean serverBusyError) {
      if (is(RECEIVING) || is(STALLED) || is(STALLING) || is(BINDING) || is(PAUSE)) {
          if (startRecovery && ! isRecoveryDisabled) {
              /*
               * POINT OF RECOVERY (1/2):
               * the socket failure has happened while we were receiving data.
               * 
               * To recover the session after a socket failure, set the phase to SLEEP 
               * and schedule the onTimeout task (see launchTimeout below), 
               * where the recovery will be performed.
               */
              log.debug("Start session recovery. Cause: socket failure while receiving");
              changePhaseType(SLEEP, startRecovery);
              
              //we used to retry immediately here, now we wait a random time <= firstRetryMaxDelay ms
              long pause = Math.round(Math.random() * options.getFirstRetryMaxDelay());
              launchTimeout("firstRetryMaxDelay", pause, reason, true/*startRecovery*/);

          } else {
              log.debug("Start new session. Cause: socket failure while receiving");
              closeSession(reason,closedOnServer,RECOVERY_SCHEDULED);
              assert is(SLEEP);
              
              //we used to retry immediately here, now we wait a random time <= firstRetryMaxDelay ms
              long pause = Math.round(Math.random() * options.getFirstRetryMaxDelay());
              launchTimeout("firstRetryMaxDelay", pause, reason, false/*startRecovery*/);
          }

      } else if (is(CREATING) || is(CREATED) || is(FIRST_BINDING)) {
          if (recoveryBean.isRecovery() && timeLeftMs > 0 && ! closedOnServer) {
              /*
               * POINT OF RECOVERY (2/2):
               * the socket failure has happened while we were trying to do a recovery.
               * 
               * When a recovery request fails we send another one in loop until a recovery succeeds or
               * the server replies with a sync error.
               */
              log.debug("Start session recovery. Cause: socket failure while recovering");
              changePhaseType(SLEEP, true);              
              launchTimeout("currentRetryDelay", calculateRetryDelay(), reason, startRecovery);
              this.options.increaseRetryDelay();

          } else if (switchRequired && !isForced) {
              log.debug("Transport switch");
              //connection is broken but we already requested a change in session type, so move on
              handler.streamSense(handlerPhase, switchCause+".error", switchForced);

          } else {
              String cause = (closedOnServer ? "closed on server" /*i.e. CONERR*/ : "socket error");
              log.debug("Start new session. Cause: " + cause);
              closeSession(reason, closedOnServer, RECOVERY_SCHEDULED);
              assert is(SLEEP);
              
              if (serverBusyError) {
                  launchTimeout("zeroDelay", 0, reason, false);
                  this.options.increaseConnectTimeoutToMax();
              } else if (closedOnServer || (recoveryBean.isRecovery() && timeLeftMs <= 0)) {
                  /*
                   * If the client is trying to create a new session/recovering the current
                   * When it receives a CONERR 
                   * or it detects a connection error but it can't do a recovery because the sessionRecoveryTimeout is expired
                   * Then it creates a new session without waiting for retryDelay
                   */
                  launchTimeout("zeroDelay", 0, reason, false);
              } else {
                  launchTimeout("currentRetryDelay", calculateRetryDelay(), reason, false);
                  this.options.increaseRetryDelay();
              }
          }

      } else { //FIRST_PAUSE || OFF || SLEEP
          /*
           * 19/11/2018
           * I think that it is logically possible that errors can occur during non-active phase, 
           * so I commented out the shutdown.
           */
          //log.error("(" +reason+ ") Unexpected error event while session is an non-active status: " + phase);
          //shutdown(GO_TO_OFF);
      }
  }
  
  protected void changeControlLink(String controlLink) {
      // do nothing in HTTP session
  }
}
