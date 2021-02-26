/*
 * Copyright (c) 2004-2015 Weswit s.r.l., Via Campanini, 6 - 20124 Milano, Italy.
 * All rights reserved.
 * www.lightstreamer.com
 *
 * This software is the confidential and proprietary information of
 * Weswit s.r.l.
 * You shall not disclose such Confidential Information and shall use it
 * only in accordance with the terms of the license agreement you entered
 * into with Weswit s.r.l.
 */
package com.lightstreamer.client.session;

import com.lightstreamer.client.Constants;
import com.lightstreamer.client.LightstreamerClient;
import com.lightstreamer.client.mpn.MpnManager.MpnEventManager;
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
import com.lightstreamer.client.requests.ChangeSubscriptionRequest;
import com.lightstreamer.client.requests.ReverseHeartbeatRequest;
import com.lightstreamer.client.requests.MessageRequest;
import com.lightstreamer.client.requests.RecoverSessionRequest;
import com.lightstreamer.client.requests.RequestTutor;
import com.lightstreamer.client.requests.SubscribeRequest;
import com.lightstreamer.client.requests.UnsubscribeRequest;
import com.lightstreamer.client.transport.WebSocket;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;

public class SessionManager implements SessionListener {
  
  private static SessionFactory sessionFactory = new SessionFactory();
  public static void setCustomFactory(SessionFactory customFactory) { //just for testing
    sessionFactory = customFactory;
  }
  
  private static enum Status {
      OFF,
      STREAMING_WS,
      SWITCHING_STREAMING_WS,
      POLLING_WS,
      SWITCHING_POLLING_WS,
      STREAMING_HTTP,
      SWITCHING_STREAMING_HTTP,
      POLLING_HTTP,
      SWITCHING_POLLING_HTTP,
      END
  }
  
  private static final boolean STREAMING_SESSION = false;
  private static final boolean POLLING_SESSION = true;
  private static final boolean WS_SESSION = false;
  private static final boolean HTTP_SESSION = true;
  
  private static final boolean AVOID_SWITCH = true;
  //private static final boolean NO_RECOVERY = true;
  private static final boolean YES_RECOVERY = false;
  
  private static String statusToString(Status type) {
    if (type == null) {
      return null;
    }
    
    switch(type) {
      case OFF:
        return "No session";
      case STREAMING_WS:
        return "WS Streaming";
      case SWITCHING_STREAMING_WS:
        return "prepare WS Streaming";
      case POLLING_WS:
        return "WS Polling";
      case SWITCHING_POLLING_WS:
        return "prepare WS Polling";
      case STREAMING_HTTP:
        return "HTTP Streaming";
      case SWITCHING_STREAMING_HTTP:
        return "prepare HTTP Streaming";
      case POLLING_HTTP:
        return "HTTP Polling";
      case SWITCHING_POLLING_HTTP:
        return "prepare HTTP Polling";
      case END:
        return "Shutting down";
      default: 
        return type.name();
    }
  }
  
  
  
  protected final Logger log = LogManager.getLogger(Constants.SESSION_LOG);
  private Status status = Status.OFF;
  private int statusPhase = 0;
  protected Session session = null;
  protected ServerSession serverSession = null;
  private boolean isFrozen = false;
  private String clientIP = null;
  protected InternalConnectionOptions options;
  protected InternalConnectionDetails details;
  protected SubscriptionsListener subscriptions;
  protected MessagesListener messages;
  protected SessionsListener listener;
  private final SessionThread thread;
  private volatile MpnEventManager mpnEventManager;
  
  /**
   * Counts the bind_session requests following the corresponding create_session.
   */
  private int nBindAfterCreate = 0;
  
  public SessionManager(InternalConnectionOptions options, InternalConnectionDetails details, SessionThread thread) {
      this.options = options;
      this.details = details;
      this.thread = thread;
  }

  // only for test
  public SessionManager(InternalConnectionOptions options, InternalConnectionDetails details,
      SessionsListener listener, SubscriptionsListener subscriptions, MessagesListener messages, SessionThread thread) {
      this(options, details, thread);
    this.subscriptions = subscriptions;
    this.messages = messages;
    this.listener = listener;
  }
  
  public void setSessionsListener(SessionsListener listener) {
    this.listener = listener;
  }
  
  public void setSubscriptionsListener(SubscriptionsListener listener) {
    this.subscriptions = listener;
  }
  
  public void setMessagesListener(MessagesListener listener) {
    this.messages = listener;
  }

  public String getSessionId() {
      return session == null ? "" : session.getSessionId();
  }

  private void changeStatus(Status newStatus) {
    if (log.isDebugEnabled()) {
        log.debug("SessionManager state change: " + status + " -> " + newStatus);
    }
    this.status = newStatus;
    this.statusPhase++;
  }
  
  private boolean is(Status check) {
    return this.status.equals(check);
  }
  
  private boolean isNot(Status check) {
    return !is(check);
  }
  
  private boolean isAlive() {
    return isNot(Status.OFF) && isNot(Status.END);
  }
  
  private Status getNextSensePhase() {
    switch(this.status) {
      case STREAMING_WS:
        if (this.isFrozen) {
          return Status.SWITCHING_STREAMING_WS;
        } else {
          return Status.SWITCHING_STREAMING_HTTP;
        }
        
      case STREAMING_HTTP:
        return Status.SWITCHING_POLLING_HTTP;
        
      case POLLING_WS:
        return Status.SWITCHING_STREAMING_WS;
  
      case POLLING_HTTP:
        if (this.isFrozen) {
          return Status.SWITCHING_POLLING_HTTP;
        } else {
          return Status.SWITCHING_STREAMING_WS;
        }
        
      default: //already switching
        return this.status;
    
    }
  }
  
  private Status getNextSlowPhase() {
    switch(this.status) {
      case STREAMING_WS:
        return Status.SWITCHING_POLLING_WS;
        
      case STREAMING_HTTP:
      case SWITCHING_STREAMING_HTTP:
      case SWITCHING_POLLING_HTTP:
        //a slow command can be issued while we're switching (while we wait for the loop command)
        return Status.SWITCHING_POLLING_HTTP;
        
        //it's still not possible during SWITCHING_STREAMING_WS (unless we manually ask to switch) 
        //and during SWITCHING_POLLING_WS (unless we manually ask to switch we only switch to polling ws because
        //of a slowing so that a second call is not possible because of the isSlowRequired check.)
      default: //already switching
        return null;
    }
  }

  
/////////////////////////////////////////////API Calls
  
  /**
   * A non-recoverable error causing the closing of the session.
   */
  public void onFatalError(Throwable e) {
      if (session != null) {
        session.onFatalError(e);
      }
  }
  
  public void changeBandwidth() {
    if (this.session != null) {
      this.session.sendConstrain(0, null);
    }
  }
  
  public void handleReverseHeartbeat(boolean force) {
    if (this.session != null) {
      this.session.handleReverseHeartbeat(force);
    }
  }
  
  public void closeSession(boolean fromAPI, String reason, boolean noRecoveryScheduled) {
    if (noRecoveryScheduled) {
        /*
         * Since, when noRecoveryScheduled is true, this method is called by Lightstreamer.disconnect();
         * then we are sure that the server session can be safely discarded.
         */
        if (serverSession != null) {
            serverSession.close();
        }
    }
    if (this.is(Status.OFF) || this.is(Status.END)) {
      return;
    }

    if (this.session != null) {
      this.session.closeSession(fromAPI?"api":reason,false,noRecoveryScheduled);
    }
  }
  
  public void changeTransport(boolean fromAPI, boolean isTransportForced, boolean isComboForced, boolean isPolling, boolean isHTTP) {
      if (this.session != null && this.session.isActive()) {
          /*
           * the session is active: send a force_rebind
           */
          this.createOrSwitchSession(fromAPI, isTransportForced, isComboForced, isPolling, isHTTP, null, false, false, false);
      } else {
          /*
           * the session is not active (i.e. TRYING-RECOVERY or WILL-RETRY):
           * await that the client is connected and then bind the session with the new transport
           */
          Status nextPH = isPolling ? (isHTTP ? Status.SWITCHING_POLLING_HTTP : Status.SWITCHING_POLLING_WS) : (isHTTP ? Status.SWITCHING_STREAMING_HTTP : Status.SWITCHING_STREAMING_WS);
          this.status = nextPH;
          this.session.switchRequired = true;
      }
  }
  
  public void createOrSwitchSession(boolean fromAPI, boolean isTransportForced, boolean isComboForced,
      boolean isPolling, boolean isHTTP, String reason, boolean avoidSwitch, boolean retryAgainIfStreamFails, boolean startRecovery) {
    if (!avoidSwitch && this.isAlive()) {
        switchSession(fromAPI, isTransportForced, isComboForced, isPolling, isHTTP, reason, startRecovery);
      
    } else {
        boolean serverBusy = false;
        createSession(
                fromAPI, isTransportForced, isComboForced, 
                isPolling, isHTTP, reason, retryAgainIfStreamFails,
                serverBusy);
    }
    
  }
  
  void switchSession(
          boolean fromAPI, boolean isTransportForced, boolean isComboForced,
          boolean isPolling, boolean isHTTP, String reason, boolean startRecovery) {
      
      reason = fromAPI?"api":reason;
      this.isFrozen = isTransportForced;
        
      Status nextPH = isPolling ? (isHTTP ? Status.SWITCHING_POLLING_HTTP : Status.SWITCHING_POLLING_WS) : (isHTTP ? Status.SWITCHING_STREAMING_HTTP : Status.SWITCHING_STREAMING_WS);
      this.changeStatus(nextPH);
      this.startSwitchTimeout(reason,0);
      this.session.requestSwitch(this.statusPhase,reason,isComboForced,startRecovery);
  }
  
  void createSession(
          boolean fromAPI, boolean isTransportForced, boolean isComboForced,
          boolean isPolling, boolean isHTTP, String reason, boolean retryAgainIfStreamFails,
          boolean serverBusy) {
      
      reason = fromAPI?"api":reason;
      this.isFrozen = isTransportForced;
      
      //there is no session active or we want to start from scratch

      String currSessionId = this.session != null ? this.session.getSessionId() : null;

      reason = "new."+reason;
      this.closeSession(false,reason,YES_RECOVERY);

      Status nextPH = isPolling ? (isHTTP ? Status.POLLING_HTTP : Status.POLLING_WS) : (isHTTP ? Status.STREAMING_HTTP : Status.STREAMING_WS);
      this.changeStatus(nextPH);

      if (this.session != null) {
          this.session.shutdown(false);
      }
      this.prepareNewSessionInstance(isPolling,isComboForced,isHTTP, null, retryAgainIfStreamFails, false);

      this.session.createSession(currSessionId,reason,serverBusy);
  }
  
  private void prepareNewSessionInstance(boolean isPolling,
      boolean isComboForced, boolean isHTTP, Session prevSession,
      boolean retryAgainIfStreamFails, boolean sessionRecovery) {
    
    this.session = sessionFactory.createNewSession(isPolling, isComboForced, isHTTP, prevSession, 
        this, subscriptions, messages, thread, details, options, this.statusPhase, retryAgainIfStreamFails, sessionRecovery);
    
    if (prevSession == null) {
        // create_session
        if (serverSession != null) {
            serverSession.close();
        }
        serverSession = new ServerSession(session);
        
    } else {
        // bind_session
        serverSession.setNewStreamConnection(session);
    }
    
    if (prevSession != null) {
      prevSession.shutdown(false);//close it without killing the session, the new session is taking its place
    }
  }

  private void bindSession(boolean isForced, boolean isPolling, boolean isHTTP, String switchCause, boolean startRecovery) {
    Status nextPH = isPolling ? (isHTTP ? Status.POLLING_HTTP : Status.POLLING_WS) : (isHTTP ? Status.STREAMING_HTTP : Status.STREAMING_WS);
    this.changeStatus(nextPH);
    
    this.prepareNewSessionInstance(isPolling,isForced,isHTTP,this.session, false, startRecovery);
    
    this.session.bindSession(switchCause);
  }
  

  private void startSwitchTimeout(final String reason, long delay) {
    long timeout = this.options.getSwitchCheckTimeout() + delay; //we could add the delay from the slowing, but that will probably make things worse
                                                                 //we might take into account how much the connect timeout has been increased
    
    final int ph = this.statusPhase;
    
    thread.schedule(new Runnable() {
      public void run() {
        switchTimeout(ph, reason);
      }
    }, timeout);
  }
  
  @Override
  public void retry(
          int handlerPhase, String retryCause, 
          boolean forced, boolean retryAgainIfStreamFails, boolean serverBusy) {
    if (handlerPhase != this.statusPhase) {
      return;
    }
    
    boolean strOrPoll = this.is(Status.STREAMING_WS) || this.is(Status.STREAMING_HTTP) ? STREAMING_SESSION : POLLING_SESSION;
    boolean wsOrHttp = this.is(Status.STREAMING_WS) || this.is(Status.POLLING_WS) ? WS_SESSION : HTTP_SESSION;
    
    
    createSession(
            false, this.isFrozen, forced, 
            strOrPoll, wsOrHttp, retryCause, retryAgainIfStreamFails,
            serverBusy);
  }
  
  /**
   * The method is similar to {@code bindSession} but there are the following differences:
   * <ul>
   * <li>{@code prepareNewSessionInstance} is called with the argument {@code sessionRecovery} set to true</li>
   * <li>a special bind_session request is sent (see {@link RecoverSessionRequest})</li>
   * </ul>
   */
  @Override
  public void recoverSession(int handlerPhase, String retryCause, boolean forced, boolean retryAgainIfStreamFails) {
      if (handlerPhase != this.statusPhase) {
          return;
      }

      boolean isPolling = this.is(Status.STREAMING_WS) || this.is(Status.STREAMING_HTTP) ? STREAMING_SESSION : POLLING_SESSION;
      boolean isHTTP = this.is(Status.STREAMING_WS) || this.is(Status.POLLING_WS) ? WS_SESSION : HTTP_SESSION;
      Status nextPH = isPolling ? (isHTTP ? Status.POLLING_HTTP : Status.POLLING_WS) : (isHTTP ? Status.STREAMING_HTTP : Status.STREAMING_WS);
      this.changeStatus(nextPH);

      this.prepareNewSessionInstance(isPolling, forced, isHTTP, this.session, retryAgainIfStreamFails, true);
      this.session.recoverSession();
  }
  
  @Override
  public void streamSense(int handlerPhase, String switchCause, boolean forced) {
    if (handlerPhase != this.statusPhase) {
      return;
    }
    
    Status switchType = this.getNextSensePhase();
    log.info("Setting up new session type " + statusToString(this.status) + "->" + statusToString(switchType));
    
    if (switchType.equals(Status.OFF) || switchType.equals(Status.END)) {
      log.warn("Unexpected fallback type switching with new session");
      return;
    }
    
    boolean strOrPoll = switchType.equals(Status.SWITCHING_STREAMING_WS) || switchType.equals(Status.SWITCHING_STREAMING_HTTP) ? STREAMING_SESSION : POLLING_SESSION;
    boolean wsOrHttp = switchType.equals(Status.SWITCHING_STREAMING_WS) || switchType.equals(Status.SWITCHING_POLLING_WS) ? WS_SESSION : HTTP_SESSION;
     
    //if we enter the createMachine the status of the session is unknown, so we can't recover by switching so 
    //we ask to AVOID_SWITCH
    this.createOrSwitchSession(false,this.isFrozen,forced,strOrPoll,wsOrHttp,switchCause,AVOID_SWITCH,false,false);
  }

  @Override
  public void switchReady(int handlerPhase, String switchCause, boolean forced, boolean startRecovery) {
    if (handlerPhase != this.statusPhase) {
      return;
    }
    
    Status switchType = this.status;
    log.info("Switching current session type " + statusToString(this.status));
    
    if (isNot(Status.SWITCHING_STREAMING_WS) && isNot(Status.SWITCHING_STREAMING_HTTP) && isNot(Status.SWITCHING_POLLING_HTTP) && isNot(Status.SWITCHING_POLLING_WS)) {
      log.error("Unexpected fallback type switching with a force rebind");
      return;
    }
    
  
    boolean strOrPoll = switchType.equals(Status.SWITCHING_STREAMING_WS) || switchType.equals(Status.SWITCHING_STREAMING_HTTP) ? STREAMING_SESSION : POLLING_SESSION;
    boolean wsOrHttp = switchType.equals(Status.SWITCHING_STREAMING_WS) || switchType.equals(Status.SWITCHING_POLLING_WS) ? WS_SESSION : HTTP_SESSION;
   
    this.bindSession(forced,strOrPoll,wsOrHttp,switchCause,startRecovery);
  }

  @Override
  public void slowReady(int handlerPhase) {
    if (handlerPhase != this.statusPhase) {
      return;
    }
    log.info("Slow session switching");
    this.switchReady(handlerPhase,"slow",false,false);
  }
  
  private void switchTimeout(int ph, String reason) {
    if (ph != this.statusPhase) {
      return;
    }
    
    log.info("Failed to switch session type. Starting new session " + statusToString(this.status));
    
    Status switchType = this.status;
    
    if (isNot(Status.SWITCHING_STREAMING_WS) && isNot(Status.SWITCHING_STREAMING_HTTP) && isNot(Status.SWITCHING_POLLING_HTTP) && isNot(Status.SWITCHING_POLLING_WS)) {
      log.error("Unexpected fallback type switching because of a failed force rebind");
      return;
    }
    reason = "switch.timeout."+reason;
    
    boolean strOrPoll = switchType.equals(Status.SWITCHING_STREAMING_WS) || switchType.equals(Status.SWITCHING_STREAMING_HTTP) ? STREAMING_SESSION : POLLING_SESSION;
    boolean wsOrHttp = switchType.equals(Status.SWITCHING_STREAMING_WS) || switchType.equals(Status.SWITCHING_POLLING_WS) ? WS_SESSION : HTTP_SESSION;
   
    this.createOrSwitchSession(false,this.isFrozen,false,strOrPoll,wsOrHttp,reason,AVOID_SWITCH,false,false);
    

  }
  
  @Override
  public void streamSenseSwitch(int handlerPhase, String reason, String sessionPhase, boolean startRecovery) {
    if (handlerPhase != this.statusPhase) {
      return;
    }
    
    Status switchType = getNextSensePhase();
    
    if (switchType.equals(Status.OFF) || switchType.equals(Status.END)) {
      log.warn("Unexpected fallback type switching with new session");
      return;
    }
    
    log.info("Unable to establish session of the current type. Switching session type " +  statusToString(this.status) +"->"+statusToString(switchType));
    
    /*
     * Since WebSocket transport is not working, we disable it for this session and the next ones.
     * WebSocket will be enabled again, if the client IP changes.
     */
    if (sessionPhase.equals("FIRST_BINDING") 
            && status.equals(Status.STREAMING_WS)
            && switchType.equals(Status.SWITCHING_STREAMING_HTTP)) {
        log.debug("WebSocket support has been disabled.");
        WebSocket.disable();
    }
    
    this.changeStatus(switchType);
    
    this.startSwitchTimeout(reason,0);
    this.session.requestSwitch(this.statusPhase,reason,false,startRecovery);
  }

  @Override
  public void onSlowRequired(int handlerPhase,long delay) {
    if (handlerPhase != this.statusPhase) {
      return;
    }
    
    Status switchType = this.getNextSlowPhase();
    
    log.info("Slow session detected. Switching session type " + statusToString(this.status)+"->"+statusToString(switchType));
    
    if(switchType == null) {
      log.error("Unexpected fallback type; switching because of a slow connection was detected" + statusToString(this.status) +", "+this.session);
      return;
    }
    this.changeStatus(switchType);
    
    this.startSwitchTimeout("slow",delay);
    this.session.requestSlow(this.statusPhase);
    
  }
  
////////////////////////////PUSH EVENTS  
  
  @Override
  public void sessionStatusChanged(int handlerPhase, String phase, boolean sessionRecovery) {
    if (handlerPhase != this.statusPhase) {
      return;
    }
    this.listener.onStatusChanged(this.getHighLevelStatus(sessionRecovery));
  }

  @Override
  public void onIPReceived(String newIP) {
    if (this.clientIP != null && !newIP.equals(this.clientIP) && WebSocket.isDisabled()) {
      WebSocket.restore();
      session.restoreWebSocket();
    }
    this.clientIP = newIP;
  }

  @Override
  public void onSessionBound() {
      if (nBindAfterCreate == 0) {
          /*
           * The check is needed to distinguish a true change of session (i.e. a new session id)
           * from a change of transport preserving the session.
           * We are only interested in true change of session.
           */
          mpnEventManager.onSessionStart();
      }
      nBindAfterCreate++;
  }

  @Override
  public void onSessionStart() {
      nBindAfterCreate = 0;
  }

  @Override
  public void onServerError(int errorCode, String errorMessage) {
    this.listener.onServerError(errorCode,errorMessage);
  }
  
  @Override
  public int onSessionClose(int handlerPhase, boolean noRecoveryScheduled) {
    if (handlerPhase != this.statusPhase) {
      return 0;
    }
    
    log.debug("Session closed: " + getSessionId());
    
    if(noRecoveryScheduled) {
      this.changeStatus(Status.OFF);
    } else {
      this.changeStatus(this.status);//so that the statusPhase changes
    }
    
    mpnEventManager.onSessionClose(! noRecoveryScheduled);
    
    return this.statusPhase;
  }


/////////////////////////////////////////////////GETTERS
  
  public String getHighLevelStatus(boolean sessionRecovery) {
    String hlStatus = this.session == null ? Constants.DISCONNECTED : this.session.getHighLevelStatus(sessionRecovery);
    return hlStatus;
  }
  
  public Session getSession() {
      return session; // may be null
  }
  
  public ServerSession getServerSession() {
      return serverSession;
  }

 //////////////////////////////////////////////MESSAGE API CALLS
  
  public void sendMessage(MessageRequest request, RequestTutor tutor) {
    if (this.session != null) {
      this.session.sendMessage(request,tutor);
    }
  }

/////////////////////////////////////////////////SUBSCRIPTIONS API CALLS  
  
  public void sendSubscription(SubscribeRequest request, RequestTutor tutor) {
    if (this.session != null) {
      this.session.sendSubscription(request,tutor);
    }
  }

  public void sendUnsubscription(UnsubscribeRequest request,
      RequestTutor tutor) {
    if (this.session != null) {
      this.session.sendUnsubscription(request,tutor);
    }
  }

  public void sendSubscriptionChange(ChangeSubscriptionRequest request,
      RequestTutor tutor) {
    if (this.session != null) {
      this.session.sendSubscriptionChange(request,tutor);
    }
  }
  
  public void sendReverseHeartbeat(ReverseHeartbeatRequest request,
      RequestTutor tutor) {
    if (this.session != null) {
      this.session.sendReverseHeartbeat(request,tutor);
    }
  }
      
  //////////////////////

  @Override
  public void switchToWebSocket(boolean startRecovery) {
      createOrSwitchSession(false,this.isFrozen,false,false,false,"ip",false,false,startRecovery);
  }
  
  /**
   * Sets the MPN manager. 
   * <p>
   * <b>NB</b> There is a circular dependency between the SessionManager and the MpnManger
   * and further this method is called by the user thread (see the implementation of {@link LightstreamerClient#LightstreamerClient(String, String)})
   * but it is used by the session thread, so the attribute {@code mpnEventManager} must be volatile.
   */
  public void setMpnEventManager(MpnEventManager manager) {
      mpnEventManager = manager;
  }
  
  /**
   * Forward the MPN request to the session layer.
   */
  public void sendMpnRegistration(MpnRegisterRequest request, MpnRegisterTutor tutor) {
      if (session != null) {
          session.sendMpnRegistration(request, tutor);
      }
  }

  /**
   * Forward the MPN request to the session layer.
   */
  public void sendMpnSubscription(MpnSubscribeRequest request, MpnSubscribeTutor tutor) {
      if (session != null) {
        session.sendMpnSubscription(request, tutor);
      }
  }
  
  /**
   * Forward the MPN request to the session layer.
   */
  public void sendMpnUnsubscription(MpnUnsubscribeRequest request, MpnUnsubscribeTutor tutor) {
      if (session != null) {
          session.sendMpnUnsubscription(request, tutor);
      }
  }
  
  /**
   * Forward the MPN request to the session layer.
   */
  public void sendMpnFilteredUnsubscription(MpnUnsubscribeFilterRequest request, MpnUnsubscribeFilterTutor tutor) {
      if (session != null) {
          session.sendMpnUnsubscription(request, tutor);
      }
  }
  
  /**
   * Forward the MPN request to the session layer.
   */
  public void sendMpnResetBadge(MpnResetBadgeRequest request, MpnResetBadgeTutor tutor) {
      if (session != null) {
          session.sendMpnResetBadge(request, tutor);
      }
  }

  @Override
  public void onMpnRegisterOK(String deviceId, String adapterName) {
      mpnEventManager.onRegisterOK(deviceId, adapterName);
  }
  
  @Override
  public void onMpnRegisterError(int code, String message) {
      mpnEventManager.onRegisterError(code, message);
  }
  
  @Override
  public void onMpnSubscribeOK(String lsSubId, String pnSubId) {
      mpnEventManager.onSubscribeOK(lsSubId, pnSubId);
  }
  
  @Override
  public void onMpnSubscribeError(String subId, int code, String message) {
      mpnEventManager.onSubscribeError(subId, code, message);
  }
  
  @Override
  public void onMpnUnsubscribeError(String subId, int code, String message) {
      mpnEventManager.onUnsubscribeError(subId, code, message);
  }
  
  @Override
  public void onMpnUnsubscribeOK(String subId) {
      mpnEventManager.onUnsubscribeOK(subId);
  }
  
  @Override
  public void onMpnResetBadgeOK(String deviceId) {
      mpnEventManager.onResetBadgeOK(deviceId);
  }
  
  @Override
  public void onMpnBadgeResetError(int code, String message) {
      mpnEventManager.onMpnBadgeResetError(code, message);
  }
}
