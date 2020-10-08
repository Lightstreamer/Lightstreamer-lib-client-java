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
package com.lightstreamer.client.protocol;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.lightstreamer.client.Constants;
import com.lightstreamer.client.mpn.MpnRegisterRequest;
import com.lightstreamer.client.mpn.MpnRegisterTutor;
import com.lightstreamer.client.mpn.MpnRequest;
import com.lightstreamer.client.mpn.MpnResetBadgeRequest;
import com.lightstreamer.client.mpn.MpnResetBadgeTutor;
import com.lightstreamer.client.mpn.MpnSubscribeRequest;
import com.lightstreamer.client.mpn.MpnSubscribeTutor;
import com.lightstreamer.client.mpn.MpnTutor;
import com.lightstreamer.client.mpn.MpnUnsubscribeFilterRequest;
import com.lightstreamer.client.mpn.MpnUnsubscribeFilterTutor;
import com.lightstreamer.client.mpn.MpnUnsubscribeRequest;
import com.lightstreamer.client.mpn.MpnUnsubscribeTutor;
import com.lightstreamer.client.protocol.ControlResponseParser.ERRORParser;
import com.lightstreamer.client.protocol.ControlResponseParser.ParsingException;
import com.lightstreamer.client.protocol.ControlResponseParser.REQERRParser;
import com.lightstreamer.client.protocol.ControlResponseParser.REQOKParser;
import com.lightstreamer.client.requests.BindSessionRequest;
import com.lightstreamer.client.requests.ChangeSubscriptionRequest;
import com.lightstreamer.client.requests.ConstrainRequest;
import com.lightstreamer.client.requests.CreateSessionRequest;
import com.lightstreamer.client.requests.DestroyRequest;
import com.lightstreamer.client.requests.ForceRebindRequest;
import com.lightstreamer.client.requests.LightstreamerRequest;
import com.lightstreamer.client.requests.MessageRequest;
import com.lightstreamer.client.requests.RecoverSessionRequest;
import com.lightstreamer.client.requests.RequestTutor;
import com.lightstreamer.client.requests.ReverseHeartbeatRequest;
import com.lightstreamer.client.requests.SubscribeRequest;
import com.lightstreamer.client.requests.UnsubscribeRequest;
import com.lightstreamer.client.session.InternalConnectionOptions;
import com.lightstreamer.client.session.Session.ConstrainTutor;
import com.lightstreamer.client.session.SessionThread;
import com.lightstreamer.client.transport.Http;
import com.lightstreamer.client.transport.RequestHandle;
import com.lightstreamer.client.transport.RequestListener;
import com.lightstreamer.client.transport.SessionRequestListener;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;
import com.lightstreamer.util.EncodingUtils;
import com.lightstreamer.util.ListenableFuture;

public abstract class TextProtocol implements Protocol {
    
  protected final Logger log = LogManager.getLogger(Constants.PROTOCOL_LOG);
  
  static enum StreamStatus {
      /**
       * Before create_session command or after LOOP message.
       */
      NO_STREAM,
      /**
       * After create_session or bind_session command but before CONOK/CONERR message.
       */
      OPENING_STREAM,
      /**
       * After CONOK message.
       */
      READING_STREAM,
      /**
       * After a fatal error or END message.
       */
      STREAM_CLOSED}
  
  protected final SessionThread sessionThread;
  protected final HttpRequestManager httpRequestManager;
  private ProtocolListener session;
  private StreamListener activeListener;

  private StreamStatus status = StreamStatus.NO_STREAM;
  private Long currentProg = null;

  private RequestHandle activeConnection;

  protected final InternalConnectionOptions options;
  
  /**
   * The maximum time between two heartbeats.
   * It is the value of the parameter LS_inactivity_millis sent with a bind_session request.
   * It doesn't change during the life of a session.
   */
  protected final ReverseHeartbeatTimer reverseHeartbeatTimer;
  
  protected final int objectId;
  protected final Http httpTransport;
  
  /**
   * If the server sends PROGs, the client captures messages between two consecutive PROGs
   * to ease debugging in case of mismatch between the server and the client counters.
   */
  private boolean messageCaptureEnabled;
  private ArrayList<String> capturedMessages;
  
  public TextProtocol(int objectId, SessionThread thread, InternalConnectionOptions options, Http httpTransport) {
    this.httpTransport = httpTransport;
    this.objectId = objectId;
    if (log.isDebugEnabled()) {
        log.debug("New protocol oid=" + this.objectId);
    }
    this.sessionThread = thread;
    this.options = options;
    this.httpRequestManager = new HttpRequestManager(thread, this, this.httpTransport, options, new HttpRequestManager.FatalErrorListener() {
        @Override
        public void onError(int errorCode, String errorMessage) {
            log.error("The server has generated an error. The session will be closed");
            forwardControlResponseError(errorCode, errorMessage, null);
        }
    });
    this.reverseHeartbeatTimer = new ReverseHeartbeatTimer(thread, options);
    /* */
    this.messageCaptureEnabled = false;
    this.capturedMessages = new ArrayList<>();
  }
  
  protected void setStatus(StreamStatus status) {
      setStatus(status, false);
  }
  
  protected void setStatus(StreamStatus status,boolean forceConnectionClose) {
    this.status = status;
    if (statusIs(StreamStatus.STREAM_CLOSED) || statusIs(StreamStatus.NO_STREAM)) {
      //we now expect the onClose event, but we're not interested in it 
      this.stopActive(forceConnectionClose);
    }
  }
  
  private boolean statusIs(StreamStatus what) {
    return status.equals(what);
  }
  
  /**
   * Returns the {@code InternalConnectionOptions}.
   * 
   * @deprecated This method is meant to be used ONLY as a workaround for iOS implementation, as
   *             it requires to send a non Unified API and platform specific event through the
   *             {@code ClientListener} interface, whose instances can be accessed through the
   *             {@code EventDispatcher} reference inside the {@code InternalConnectionOptions}.
   *             embedded in the
   * 
   * @return the {@code InternalConnectionOptions}
   */
  public InternalConnectionOptions getOptions() {
      return options;
  }

  @Override
  public void stopActive(boolean forceConnectionClose) {
    if (this.activeListener != null) {
      this.activeListener.disable();
    }
    if (this.activeConnection != null) {
      this.activeConnection.close(forceConnectionClose);
    }
  }

  @Override
  public void copyPendingRequests(Protocol protocol) {
    getRequestManager().copyTo(protocol.getRequestManager());
    if (protocol instanceof TextProtocol) {
        // ((TextProtocol) protocol).currentProg = this.currentProg;
        // optionally can be enabled, for testing purpose
    }
  }
  
  @Override
  public void setListener(ProtocolListener listener) {
    this.session = listener;
  }

  /**
   * Dispatches a control request to the transport layer (HTTP or WebSocket).
   * <br>
   * NB All control/message requests which don't depend on the transport implementation must call this method.
   */
  abstract public void sendControlRequest(LightstreamerRequest request, RequestTutor tutor, RequestListener reqListener);

  @Override
  public void handleReverseHeartbeat() {
      reverseHeartbeatTimer.onChangeInterval();
  }

  @Override
  public void sendForceRebind(ForceRebindRequest request, RequestTutor tutor) {
    RequestListener reqListener = new ControlRequestListener<RequestTutor>(tutor) {
        @Override public void onOK() {}
        @Override public void onError(int code, String message) {
            tutor.discard();
            log.error("force_rebind request caused the error: " + code + " " + message + " - The error will be silently ignored.");
        }
    };
    // force_rebind is always sent via HTTP
    httpRequestManager.addRequest(request, tutor, reqListener);
  }

  @Override
  public void sendDestroy(DestroyRequest request, RequestTutor tutor) {
    RequestListener reqListener = new ControlRequestListener<RequestTutor>(null) {
        @Override public void onOK() {}
        @Override public void onError(int code, String message) {
            log.error("destroy request caused the error: " + code + " " + message + " - The error will be silently ignored.");
        }
    };
    forwardDestroyRequest(request, tutor, reqListener);
  }
  
  protected abstract void forwardDestroyRequest(DestroyRequest request, RequestTutor tutor, RequestListener reqListener);
  
  @Override
  public void sendMessageRequest(final MessageRequest request, final RequestTutor tutor) {
    
    RequestListener reqListener = new ControlRequestListener<RequestTutor>(tutor) {

      @Override
      public void onOK() {
        if (request.needsAck()) {
          session.onMessageAck(request.getSequence(), request.getMessageNumber(), ProtocolConstants.SYNC_RESPONSE);
        } else {
          // unneeded acks are possible, for instance with HTTP transport
        }
      }
      
      @Override 
      public void onError(int code, String message) {
        session.onMessageError(request.getSequence(), code, message, request.getMessageNumber(), ProtocolConstants.SYNC_RESPONSE);
      }
    };
    
    sendControlRequest(request, tutor, reqListener);
  }
  
  @Override
  public void sendMpnRegisterRequest(MpnRegisterRequest request, MpnRegisterTutor tutor) {
      MpnRequestListener<MpnRegisterRequest, MpnRegisterTutor> reqListener = new MpnRequestListener<MpnRegisterRequest, MpnRegisterTutor>(request, tutor) {
          @Override
          public void onError(int code, String message) {
              super.onError(code, message);
              session.onMpnRegisterError(code, message);
          }
      };
      sendControlRequest(request, tutor, reqListener);
  }
  
  @Override
  public void sendMpnSubscribeRequest(MpnSubscribeRequest request, MpnSubscribeTutor tutor) {
      MpnRequestListener<MpnSubscribeRequest, MpnSubscribeTutor> reqListener = new MpnRequestListener<MpnSubscribeRequest, MpnSubscribeTutor>(request, tutor) {
          public void onError(int code, String message) {
              super.onError(code, message);
              session.onMpnSubscribeError(request.getSubscriptionId(), code, message);
          }
      };
      sendControlRequest(request, tutor, reqListener);
  }
  
  @Override
  public void sendMpnUnsubscribeRequest(MpnUnsubscribeRequest request, MpnUnsubscribeTutor tutor) {
      MpnRequestListener<MpnUnsubscribeRequest, MpnUnsubscribeTutor> reqListener = new MpnRequestListener<MpnUnsubscribeRequest, MpnUnsubscribeTutor>(request, tutor) {
          public void onError(int code, String message) {
              super.onError(code, message);
              session.onMpnUnsubscribeError(request.getSubscriptionId(), code, message);
          }
      };
      sendControlRequest(request, tutor, reqListener);
  }
  
  @Override
  public void sendMpnUnsubscribeRequest(MpnUnsubscribeFilterRequest request, MpnUnsubscribeFilterTutor tutor) {
      MpnRequestListener<MpnUnsubscribeFilterRequest, MpnUnsubscribeFilterTutor> reqListener = new MpnRequestListener<MpnUnsubscribeFilterRequest, MpnUnsubscribeFilterTutor>(request, tutor);
      sendControlRequest(request, tutor, reqListener);
  }
  
  @Override
  public void sendMpnResetBadgeRequest(MpnResetBadgeRequest request, MpnResetBadgeTutor tutor) {
      MpnRequestListener<MpnResetBadgeRequest, MpnResetBadgeTutor> reqListener = new MpnRequestListener<MpnResetBadgeRequest, MpnResetBadgeTutor>(request, tutor) {
          @Override
          public void onError(int code, String message) {
              super.onError(code, message);
              session.onMpnBadgeResetError(code, message);
          }
      };
      sendControlRequest(request, tutor, reqListener);
  }

  @Override
  public void sendSubscriptionRequest(final SubscribeRequest request, RequestTutor tutor) {
    log.info("Sending subscription request");
    if (log.isDebugEnabled()) {
        log.debug("subscription parameters: " + request.getTransportUnawareQueryString());
        // TODO we log the full version, as we have no transport information here 
    }
    RequestListener reqListener = new ControlRequestListener<RequestTutor>(tutor) {

      @Override
      public void onOK() {
          session.onSubscriptionAck(request.getSubscriptionId());
      }
      
      @Override 
      public void onError(int code, String message) {
        session.onSubscriptionError(request.getSubscriptionId(), code, message, ProtocolConstants.SYNC_RESPONSE);
      }
    };
    
    sendControlRequest(request, tutor, reqListener);
  }

  @Override
  public void sendConfigurationRequest(final ChangeSubscriptionRequest request, RequestTutor tutor) {

    RequestListener reqListener = new ControlRequestListener<RequestTutor>(tutor) {

      @Override
      public void onOK() {
        session.onSubscriptionReconf(request.getSubscriptionId(), request.getReconfId(), ProtocolConstants.SYNC_RESPONSE);
      }
      
      @Override 
      public void onError(int code, String message) {
        tutor.discard();
        log.error("configuration request [" + request.getTransportUnawareQueryString() + "] caused the error: " + code + " " + message);
        // TODO we log the full version, as we have no transport information here 
      }
    };
    
    sendControlRequest(request, tutor, reqListener);
  }
  
  @Override
  public void sendUnsubscriptionRequest(final UnsubscribeRequest request,
      RequestTutor tutor) {
    
    RequestListener reqListener = new ControlRequestListener<RequestTutor>(tutor) {

      @Override
      public void onOK() {
          session.onUnsubscriptionAck(request.getSubscriptionId());
      }
      
      @Override 
      public void onError(int code, String message) {
          tutor.discard();
          log.error("unsubscription request [" + request.getTransportUnawareQueryString() + "] caused the error: " + code + " " + message);
          // TODO we log the full version, as we have no transport information here 
      }
    };
    
    sendControlRequest(request, tutor, reqListener);
  }

  @Override
  public void sendConstrainRequest(final ConstrainRequest request, final RequestTutor tutor) {
      RequestListener reqListener = new ControlRequestListener<RequestTutor>(tutor) {
          
          @Override
          public void onOK() {
              session.onConstrainResponse((ConstrainTutor) tutor);
          }

          @Override 
          public void onError(int code, String message) {
              log.error("constrain request [" + request.getTransportUnawareQueryString() + "] caused the error: " + code + " " + message);
              // bandwidth requests should not generate REQERR/ERROR
              // anyway we stop retransmissions
              session.onConstrainResponse((ConstrainTutor) tutor);
          }
      };
      sendControlRequest(request, tutor, reqListener);
  }

  @Override
  public void sendReverseHeartbeat(final ReverseHeartbeatRequest request, RequestTutor tutor) {
      sendControlRequest(request, tutor, new BaseControlRequestListener<RequestTutor>(tutor) {

          {
              /*
               * NB
               * as a little optimization to avoid unnecessary rescheduling of heartbeat messages
               * don't wait for onOpen() to call setLastSentTime() as in the other control request listeners
               * (see ControlRequestListener) but call it immediately during the initialization
               */
              reverseHeartbeatTimer.onControlRequest();
          }

          @Override
          public void onOK() {
              /* heartbeat doesn't care for REQOK */
          }

          @Override
          public void onError(int code, String message) {
              /* heartbeat doesn't care for REQERR */
          }
      });
  }

  @Override
  public void sendCreateRequest(CreateSessionRequest request) {
    assert activeListener == null && statusIs(StreamStatus.NO_STREAM) : status;    
    this.activeListener = new OpenSessionListener();
    
    long connectDelay = request.getDelay();
    long readDelay = request.getDelay();
    if (request.isPolling()) {
      readDelay+=this.options.getIdleTimeout();
      connectDelay+=this.options.getPollingInterval();
          
    }
    
    // create_session is always sent over HTTP
    this.activeConnection = httpRequestManager.createSession(request, activeListener, options.getTCPConnectTimeout() + connectDelay, 
            options.getTCPReadTimeout() + readDelay);
    
    this.setStatus(StreamStatus.OPENING_STREAM);
  }
  
  @Override
  public ListenableFuture sendBindRequest(BindSessionRequest request) {
    assert statusIs(StreamStatus.NO_STREAM) : status;    
    this.activeListener = new BindSessionListener();
    
    long connectDelay = request.getDelay();
    long readDelay = request.getDelay();
    if (request.isPolling()) {
      readDelay+=this.options.getIdleTimeout();
      connectDelay+=this.options.getPollingInterval();
          
    }
    
    ListenableFuture bindFuture = new ListenableFuture();
    this.activeConnection = getRequestManager().bindSession(request, activeListener, 
            options.getTCPConnectTimeout() + connectDelay, options.getTCPReadTimeout() + readDelay, bindFuture);
    
    this.setStatus(StreamStatus.OPENING_STREAM);
    return bindFuture;
  }
  
  @Override
  public void sendRecoveryRequest(RecoverSessionRequest request) {
      assert activeListener == null && statusIs(StreamStatus.NO_STREAM) : status;
      this.activeListener = new OpenSessionListener();
      
      long connectDelay = request.getDelay();
      long readDelay = request.getDelay();
      if (request.isPolling()) {
        readDelay+=this.options.getIdleTimeout();
        connectDelay+=this.options.getPollingInterval();
      }
      
      // recovery is always sent over HTTP
      this.activeConnection = httpRequestManager.recoverSession(
              request, 
              activeListener, 
              options.getTCPConnectTimeout() + connectDelay, 
              options.getTCPReadTimeout() + readDelay);
      
      this.setStatus(StreamStatus.OPENING_STREAM);
  }
  
  /**
   * Pattern of a subscription message.
   * This message has the following form
   * {@code SUBOK,<table>,<total items>,<total fields>}. 
   */
  public static final Pattern SUBOK_REGEX = Pattern.compile("SUBOK,(\\d+),(\\d+),(\\d+)");
  /**
   * Pattern of a command-mode subscription message.
   * This message has the following form
   * {@code SUBCMD,<table>,<total items>,<total fields>,<key field>,<command field>}.
   */
  public static final Pattern SUBCMD_REGEX = Pattern.compile("SUBCMD,(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)");
  /**
   * Pattern of an unsubscription message.
   * This message has the form {@code UNSUB,<table>}.
   */
  public static final Pattern UNSUBSCRIBE_REGEX = Pattern.compile("UNSUB,(\\d+)");
  /**
   * Pattern of a message to change the server bandwidth.
   * This message has the form {@code CONS,<bandwidth>}. 
   */
  public static final Pattern CONSTRAIN_REGEX = Pattern.compile("CONS,(unmanaged|unlimited|(\\d+(?:\\.\\d+)?))");
  /**
   * Pattern of a synchronization message.
   * This message has the form {@code SYNC,<seconds>}.
   */
  public static final Pattern SYNC_REGEX = Pattern.compile("SYNC,(\\d+)");
  /**
   * Pattern of a clear-snapshot message.
   * This message has the form {@code CS,<table>,<item>}.
   */
  public static final Pattern CLEAR_SNAPSHOT_REGEX = Pattern.compile("CS,(\\d+),(\\d+)");
  /**
   * Pattern of a end-of-snapshot message.
   * This message has the form {@code EOS,<table>,<item>}.
   */
  public static final Pattern END_OF_SNAPSHOT_REGEX = Pattern.compile("EOS,(\\d+),(\\d+)");
  /**
   * Pattern of an overflow message.
   * This message has the form {@code OV,<table>,<item>,<lost updates>}.
   */
  public static final Pattern OVERFLOW_REGEX = Pattern.compile("OV,(\\d+),(\\d+),(\\d+)");
  /**
   * Pattern of a configuration message.
   * This message has the form {@code CONF,<table>,<frequency>,("filtered"|"unfiltered")}.
   */
  public static final Pattern CONFIGURATION_REGEX = Pattern.compile("CONF,(\\d+),(unlimited|(\\d+(?:\\.\\d+)?)),(filtered|unfiltered)");
  /**
   * Pattern of a server-name message.
   * This message has the form {@code SERVNAMR,<server name>}.
   */
  public static final Pattern SERVNAME_REGEX = Pattern.compile("SERVNAME,(.+)");
  /**
   * Pattern of a client-ip message.
   * This message has the form {@code CLIENTIP,<client ip>}.
   */
  public static final Pattern CLIENTIP_REGEX = Pattern.compile("CLIENTIP,(.+)");
  /**
   * Pattern of a current-progressive message.
   * This message has the form {@code PROG,<number>}.
   */
  public static final Pattern PROG_REGEX = Pattern.compile("PROG,(\\d+)");
  
  /**
   * CONOK message has the form {@literal CONOK,<session id>,<request limit>,<keep alive>,<control link>}.
   */
  public static final Pattern CONOK_REGEX = Pattern.compile("CONOK,([^,]+),(\\d+),(\\d+),([^,]+)");
  /**
   * CONERR message has the form {@literal CONERR,<error code>,<error message>}.
   */
  public static final Pattern CONERR_REGEX = Pattern.compile("CONERR,([-]?\\d+),(.*)");
  /**
   * END message has the form {@literal END,<error code>,<error message>}.
   */
  public static final Pattern END_REGEX = Pattern.compile("END,([-]?\\d+),(.*)");
  /**
   * LOOP message has the form {@literal LOOP,<holding time>}.
   */
  public static final Pattern LOOP_REGEX = Pattern.compile("LOOP,(\\d+)");
  
  void onProtocolMessage(String message) {
      if (log.isDebugEnabled()) {
          log.debug("New message (" + objectId + "): " + message);
      }
      if (messageCaptureEnabled) {
          capturedMessages.add(message);
      }

      switch (status) {
      case READING_STREAM:
          if (message.startsWith(ProtocolConstants.reqokMarker)) {
              processREQOK(message);

          } else if (message.startsWith(ProtocolConstants.reqerrMarker)) {
              processREQERR(message);

          } else if (message.startsWith(ProtocolConstants.errorMarker)) {
              processERROR(message);

          } else if (message.startsWith(ProtocolConstants.updateMarker)) {
              processUpdate(message);

          } else if (message.startsWith(ProtocolConstants.msgMarker)) {
              processUserMessage(message);

          } else if (message.startsWith(ProtocolConstants.probeCommand)) {
              session.onKeepalive();

          } else if (message.startsWith(ProtocolConstants.loopCommand)) {
              setStatus(StreamStatus.NO_STREAM); // NB status must be changed before processLOOP is called
              processLOOP(message);

          } else if (message.startsWith(ProtocolConstants.endCommand)) {
              processEND(message);
              setStatus(StreamStatus.STREAM_CLOSED);

          } else if (message.startsWith(ProtocolConstants.subscribeMarker)) {
              processSUBOK(message);

          } else if (message.startsWith(ProtocolConstants.unsubscribeMarker)) {
              processUNSUB(message);

          } else if (message.startsWith(ProtocolConstants.constrainMarker)) {
              processCONS(message);

          } else if (message.startsWith(ProtocolConstants.syncMarker)) {
              processSYNC(message);

          } else if (message.startsWith(ProtocolConstants.clearSnapshotMarker)) {
              processCS(message);

          } else if (message.startsWith(ProtocolConstants.endOfSnapshotMarker)) {
              processEOS(message);

          } else if (message.startsWith(ProtocolConstants.overflowMarker)) {
              processOV(message);

          } else if (message.startsWith(ProtocolConstants.configurationMarker)) {
              processCONF(message);

          } else if (message.startsWith(ProtocolConstants.serverNameMarker)) {
              processSERVNAME(message);

          } else if (message.startsWith(ProtocolConstants.clientIpMarker)) {
              processCLIENTIP(message);

          } else if (message.startsWith(ProtocolConstants.mpnRegisterMarker)) {
              processMPNREG(message);

          } else if (message.startsWith(ProtocolConstants.mpnSubscribeMarker)) {
              processMPNOK(message);

          } else if (message.startsWith(ProtocolConstants.mpnUnsubscribeMarker)) {
              processMPNDEL(message);

          }  else if (message.startsWith(ProtocolConstants.mpnResetBadgeMarker)) {
              processMPNZERO(message);

          } else if (message.startsWith(ProtocolConstants.progMarker)) {
              processPROG(message);

          } else if (message.startsWith(ProtocolConstants.noopMarker)) {
              // skip
              
          } else {
              onIllegalMessage("Unexpected message in state " + status + ": " + message);
          }
          break;

      case OPENING_STREAM:
          if (message.startsWith(ProtocolConstants.reqokMarker)) {
              processREQOK(message);

          } else if (message.startsWith(ProtocolConstants.reqerrMarker)) {
              processREQERR(message);

          } else if (message.startsWith(ProtocolConstants.errorMarker)) {
              processERROR(message);

          } else if (message.startsWith(ProtocolConstants.conokCommand)) {
              processCONOK(message);
              setStatus(StreamStatus.READING_STREAM);

          } else if(message.startsWith(ProtocolConstants.conerrCommand)) {
              processCONERR(message);
              setStatus(StreamStatus.STREAM_CLOSED);

          } else if (message.startsWith(ProtocolConstants.endCommand)) {
              processEND(message);
              setStatus(StreamStatus.STREAM_CLOSED);
              
          } else {
              onIllegalMessage("Unexpected message in state " + status + ": " + message);
          }
          break;

      default:
          assert status.equals(StreamStatus.STREAM_CLOSED);
//          onIllegalMessage("Unexpected message in state " + status + ": " + message);
      }
  }
  
  protected final Matcher matchLine(Pattern pattern, String message) throws IllegalArgumentException {
      Matcher matcher = pattern.matcher(message);
      boolean found = matcher.matches();
      if (! found) {
          onIllegalMessage("Malformed message received: " + message);
      }
      return matcher;
  }
  
  protected final int myParseInt(String field, String description, String orig) throws IllegalArgumentException {
      try {
          return Integer.parseInt(field);
      } catch (NumberFormatException e) {
          onIllegalMessage("Malformed " + description + " in message: " + orig);
          return 0; // but onIllegalMessage only throws
      }
  }

  protected final long myParseLong(String field, String description, String orig) throws IllegalArgumentException {
      try {
          return Long.parseLong(field);
      } catch (NumberFormatException e) {
          onIllegalMessage("Malformed " + description + " in message: " + orig);
          return 0; // but onIllegalMessage only throws
      }
  }

  protected final double myParseDouble(String field, String description, String orig) throws IllegalArgumentException {
      try {
          return Double.parseDouble(field);
      } catch (NumberFormatException e) {
          onIllegalMessage("Malformed " + description + " in message: " + orig);
          return 0; // but onIllegalMessage only throws
      }
  }

  /**
   * Processes a REQOK message received on the stream connection.
   * It only matters for WebSocket transport, because in HTTP this message is sent over a control connection.
   */
  abstract public void processREQOK(String message);

  /**
   * Processes a REQERR message received on the stream connection.
   * It only matters for WebSocket transport, because in HTTP this message is sent over a control connection.
   */
  abstract public void processREQERR(String message);

  /**
   * Processes a ERROR message received on the stream connection.
   * It only matters for WebSocket transport, because in HTTP this message is sent over a control connection.
   */
  abstract public void processERROR(String message);

  private void processCLIENTIP(String message) {
      Matcher matcher = matchLine(CLIENTIP_REGEX, message);
      String clientIp = matcher.group(1);
      session.onClientIp(clientIp);
  }

  private void processSERVNAME(String message) {
      Matcher matcher = matchLine(SERVNAME_REGEX, message);
      String serverName = EncodingUtils.unquote(matcher.group(1));
      session.onServerName(serverName);
  }

  private void processPROG(String message) {
      Matcher matcher = matchLine(PROG_REGEX, message);
      long prog = myParseLong(matcher.group(1), "prog", message);
      long sessionProg = session.getDataNotificationProg();
      if (currentProg == null) {
          currentProg = prog;
          if (currentProg > sessionProg) {
              log.error("Received event prog higher than expected: " + prog + " " + sessionProg + " " + capturedMessages);
              session.onPROGCounterMismatch();
          }
      } else {
          // not allowed by the protocol, but we handle the case for testing scenarios;
          // these extra invocations of PROG can be enabled on the Server
          // through the <PROG_NOTIFICATION_GAP> private flag
          if (currentProg != prog) {
              log.error("Received event prog different than expected: " + prog + " " + currentProg + " " + capturedMessages);
              session.onPROGCounterMismatch();
          }
          else if (prog != sessionProg) {
              log.error("Received event prog different than actual: " + prog + " " + sessionProg + " " + capturedMessages);
              session.onPROGCounterMismatch();
          }
      }
      /* */
      messageCaptureEnabled = true;
      capturedMessages.clear();
  }

  private void processCONF(String message) {
      Matcher matcher = matchLine(CONFIGURATION_REGEX, message);
      if (! processCountableNotification()) {
          return;
      }
      int table = myParseInt(matcher.group(1), "subscription", message);
      if (matcher.group(3) != null) {
          String frequency = matcher.group(3);
          myParseDouble(frequency, "frequency", message); // preliminary check
          session.onConfigurationEvent(table, frequency);
      } else {
          assert matcher.group(2).equalsIgnoreCase("unlimited"); // ensured by the regexp check
          session.onConfigurationEvent(table, "unlimited");
      }
      // assert matcher.group(4) corresponds to the filtered/unfiltered flag of the table
  }

  private void processEND(String message) {
      Matcher matcher = matchLine(END_REGEX, message);
      int errorCode = myParseInt(matcher.group(1), "error code", message);
      String errorMessage = EncodingUtils.unquote(matcher.group(2));
      forwardError(errorCode, errorMessage);
  }

  private void processLOOP(String message) {
      Matcher matcher = matchLine(LOOP_REGEX, message);
      int millis = myParseInt(matcher.group(1), "holding time", message);
      session.onLoopReceived(millis);
  }

  private void processOV(String message) {
      Matcher matcher = matchLine(OVERFLOW_REGEX, message);
      if (! processCountableNotification()) {
          return;
      }
      int table = myParseInt(matcher.group(1), "subscription", message);
      int item = myParseInt(matcher.group(2), "item", message);
      int overflow = myParseInt(matcher.group(3), "count", message);
      session.onLostUpdatesEvent(table, item, overflow);
  }

  private void processEOS(String message) {
      Matcher matcher = matchLine(END_OF_SNAPSHOT_REGEX, message);
      if (! processCountableNotification()) {
          return;
      }
      int table = myParseInt(matcher.group(1), "subscription", message);
      int item = myParseInt(matcher.group(2), "item", message);
      session.onEndOfSnapshotEvent(table, item);
  }

  private void processCS(String message) {
      Matcher matcher = matchLine(CLEAR_SNAPSHOT_REGEX, message);
      if (! processCountableNotification()) {
          return;
      }
      int table = myParseInt(matcher.group(1), "subscription", message);
      int item = myParseInt(matcher.group(2), "item", message);
      session.onClearSnapshotEvent(table, item);
  }

  private void processSYNC(String message) {
      Matcher matcher = matchLine(SYNC_REGEX, message);
      long seconds = myParseLong(matcher.group(1), "prog", message);
      session.onSyncMessage(seconds);
  }

  private void processCONS(String message) {
      Matcher matcher = matchLine(CONSTRAIN_REGEX, message);
      if (matcher.group(2) != null) {
          String bandwidth = matcher.group(2);
          myParseDouble(bandwidth, "bandwidth", message); // preliminary check
          session.onServerSentBandwidth(bandwidth);
      } else {
          String bwType = matcher.group(1);
          assert bwType.equalsIgnoreCase("unmanaged") || bwType.equalsIgnoreCase("unlimited"); // ensured by the regexp check
          session.onServerSentBandwidth(bwType);
      }
  }

  private void processUNSUB(String message) {
      Matcher matcher = matchLine(UNSUBSCRIBE_REGEX, message);
      if (! processCountableNotification()) {
          return;
      }
      int table = myParseInt(matcher.group(1), "subscription", message);
      session.onUnsubscription(table);
  }

  private void processSUBOK(String message) {
      if (! processCountableNotification()) {
          return;
      }
      if (message.startsWith("SUBOK")) {
          Matcher matcher = matchLine(SUBOK_REGEX, message);
          int table = myParseInt(matcher.group(1), "subscription", message);
          int totalItems = myParseInt(matcher.group(2), "item count", message);
          int totalFields = myParseInt(matcher.group(3), "field count", message);
          session.onSubscription(table, totalItems, totalFields, -1, -1);
          
      } else if (message.startsWith("SUBCMD")) {
          Matcher matcher = matchLine(SUBCMD_REGEX, message);
          int table = myParseInt(matcher.group(1), "subscription", message);
          int totalItems = myParseInt(matcher.group(2), "item count", message);
          int totalFields = myParseInt(matcher.group(3), "field count", message);
          int key = myParseInt(matcher.group(4), "key position", message);
          int command = myParseInt(matcher.group(5), "command position", message);
          session.onSubscription(table, totalItems, totalFields, key, command);
          
      } else {
          onIllegalMessage("Malformed message received: " + message);
      }
  }
  
  private void processUserMessage(String message) {
      // a message notification can have the following forms:
      // 1) MSGDONE,<sequence>,<prog>
      // 2) MSGFAIL,<sequence>,<prog>,<error-code>,<error-message>

      String[] splitted = message.split(",", 5);  

      if (splitted.length == 3) {
          if (! splitted[0].equals("MSGDONE")) {
              onIllegalMessage("MSGDONE expected: " + message);
          }
          if (! processCountableNotification()) {
              return;
          }
          String sequence = splitted[1];
          if (sequence.equals("*")) {
              sequence = Constants.UNORDERED_MESSAGES;
          }
          int messageNumber = myParseInt(splitted[2], "prog", message);
          session.onMessageOk(sequence, messageNumber);
          
      } else if (splitted.length == 5) {
          if (! splitted[0].equals("MSGFAIL")) {
              onIllegalMessage("MSGFAIL expected: " + message);
          }
          if (! processCountableNotification()) {
              return;
          }
          String sequence = splitted[1];
          if (sequence.equals("*")) {
              sequence = Constants.UNORDERED_MESSAGES;
          }
          int messageNumber = myParseInt(splitted[2], "prog", message);
          int errorCode = myParseInt(splitted[3], "error code", message);
          String errorMessage = EncodingUtils.unquote(splitted[4]);
          onMsgErrorMessage(sequence, messageNumber, errorCode, errorMessage, message);
      } else {
          onIllegalMessage("Wrong number of fields in message: " + message);
      }
  }

  private void processUpdate(String message) {
      // update message has the form U,<table>,<item>|<field1>|...|<fieldN>
      // or U,<table>,<item>,<field1>|^<number of unchanged fields>|...|<fieldN>
      
      /* parse table and item */
      int tableIndex = message.indexOf(',') + 1;
      assert tableIndex == 2; // tested by the caller
      int itemIndex = message.indexOf(',', tableIndex) + 1;
      if (itemIndex <= 0) {
          onIllegalMessage("Missing subscription field in message: " + message);
      }
      int fieldsIndex = message.indexOf(',', itemIndex) + 1;
      if (fieldsIndex <= 0) {
          onIllegalMessage("Missing item field in message: " + message);
      }
      assert message.substring(0, tableIndex).equals("U,"); // tested by the caller
      int table = myParseInt(message.substring(tableIndex, itemIndex - 1), "subscription", message);
      int item = myParseInt(message.substring(itemIndex, fieldsIndex - 1), "item", message);
      
      if (! processCountableNotification()) {
          return;
      }

      /* parse fields */
      ArrayList<String> values = new ArrayList<String>();
      int fieldStart = fieldsIndex - 1; // index of the separator introducing the next field
      assert message.charAt(fieldStart) == ','; // tested above
      while (fieldStart < message.length()) {
          
          int fieldEnd = message.indexOf('|', fieldStart + 1);
          if (fieldEnd == -1) {
              fieldEnd = message.length();
          }
          /*
            Decoding algorithm:
                1) Set a pointer to the first field of the schema.
                2) Look for the next pipe “|” from left to right and take the substring to it, or to the end of the line if no pipe is there.
                3) Evaluate the substring:
                       A) If its value is empty, the pointed field should be left unchanged and the pointer moved to the next field.
                       B) Otherwise, if its value corresponds to a single “#” (UTF-8 code 0x23), the pointed field should be set to a null value and the pointer moved to the next field.
                       C) Otherwise, If its value corresponds to a single “$” (UTF-8 code 0x24), the pointed field should be set to an empty value (“”) and the pointer moved to the next field.
                       D) Otherwise, if its value begins with a caret “^” (UTF-8 code 0x5E):
                               - take the substring following the caret and convert it to an integer number;
                               - for the corresponding count, leave the fields unchanged and move the pointer forward;
                               - e.g. if the value is “^3”, leave unchanged the pointed field and the following two fields, and move the pointer 3 fields forward;
                       E) Otherwise, the value is an actual content: decode any percent-encoding and set the pointed field to the decoded value, then move the pointer to the next field.
                          Note: “#”, “$” and “^” characters are percent-encoded if occurring at the beginning of an actual content.
                4) Return to the second step, unless there are no more fields in the schema.
           */
            String value = message.substring(fieldStart + 1, fieldEnd);
            if (value.isEmpty()) { // step A
                values.add(ProtocolConstants.UNCHANGED);

            } else if (value.charAt(0) == '#') { // step B
                if (value.length() != 1) {
                    onIllegalMessage("Wrong field quoting in message: " + message);
                } // a # followed by other text should have been quoted
                values.add(null);

            } else if (value.charAt(0) == '$') { // step C
                if (value.length() != 1) {
                    onIllegalMessage("Wrong field quoting in message: " + message);
                } // a $ followed by other text should have been quoted
                values.add("");

            } else if (value.charAt(0) == '^') { // step D
                int count = myParseInt(value.substring(1), "compression", message);
                while (count-- > 0) {
                    values.add(ProtocolConstants.UNCHANGED);
                }

            } else { // step E
                String unquoted = EncodingUtils.unquote(value);
                values.add(unquoted);
            }
            fieldStart = fieldEnd;
      }
      
      /* notify listener */
      session.onUpdateReceived(table, item, values);
  }

  private void processCONERR(String message) {
      Matcher matcher = matchLine(CONERR_REGEX, message);
      int errorCode = myParseInt(matcher.group(1), "error code", message);
      String errorMessage = EncodingUtils.unquote(matcher.group(2));
      this.forwardError(errorCode, errorMessage);
  }

  private void processCONOK(String message) {
      Matcher matcher = matchLine(CONOK_REGEX, message);
      // process session id
      String sessionId = matcher.group(1);
      // process request limit
      long requestLimitLength = myParseLong(matcher.group(2), "request limit", message);
      getRequestManager().setRequestLimit(requestLimitLength);
      // process keep alive
      long keepaliveIntervalDefault = myParseLong(matcher.group(3), "keepalive time", message);
      String controlLink;
      // process control link (when unknown, server sends *)
      if (matcher.group(4).equals("*")) {
          controlLink = null;
      } else {
          controlLink = EncodingUtils.unquote(matcher.group(4));
      }
      // notify listeners
      session.onOKReceived(sessionId, controlLink, requestLimitLength, keepaliveIntervalDefault);
  }
  
  private void processMPNREG(String message) {
      if (! processCountableNotification()) {
          return;
      }
      // MPNREG,<device-id>,<mpn-adapter-name>
      int firstComma = message.indexOf(',');
      if (firstComma == -1) onIllegalMessage(message);
      int secondComma = message.indexOf(',', firstComma + 1);
      if (secondComma == -1) onIllegalMessage(message);
      String deviceId = message.substring(firstComma + 1, secondComma);
      if (deviceId.isEmpty()) onIllegalMessage(message);
      String adapterName = message.substring(secondComma + 1);
      if (adapterName.isEmpty()) onIllegalMessage(message);
      session.onMpnRegisterOK(deviceId, adapterName);
  }
  
  private void processMPNOK(String message) {
      if (! processCountableNotification()) {
          return;
      }
      // MPNOK,<subscription-id>,<pn-subscription-id>
      int firstComma = message.indexOf(',');
      if (firstComma == -1) onIllegalMessage(message);
      int secondComma = message.indexOf(',', firstComma + 1);
      if (secondComma == -1) onIllegalMessage(message);
      String lsSubId = message.substring(firstComma + 1, secondComma);
      if (lsSubId.isEmpty()) onIllegalMessage(message);
      String pnSubId = message.substring(secondComma + 1);
      if (pnSubId.isEmpty()) onIllegalMessage(message);
      session.onMpnSubscribeOK(lsSubId, pnSubId);
  }
  
  private void processMPNDEL(String message) {
      if (! processCountableNotification()) {
          return;
      }
      // MPNDEL,<subscription-id>
      int firstComma = message.indexOf(',');
      if (firstComma == -1) onIllegalMessage(message);
      String subId = message.substring(firstComma + 1);
      if (subId.isEmpty()) onIllegalMessage(message);
      session.onMpnUnsubscribeOK(subId);
  }
  
  private void processMPNZERO(String message) {
      if (! processCountableNotification()) {
          return;
      }
      // MPNZERO,<device-id>
      int firstComma = message.indexOf(',');
      if (firstComma == -1) onIllegalMessage(message);
      String deviceId = message.substring(firstComma + 1);
      if (deviceId.isEmpty()) onIllegalMessage(message);
      session.onMpnResetBadgeOK(deviceId);
  }

  void onMsgErrorMessage(String sequence, int messageNumber, 
      int errorCode, String errorMessage, String orig) {
  
    if (errorCode == 39) {  // code 39: list of discarded messages, the message is actually a counter
      int count = myParseInt(errorMessage, "number of messages", orig);
      for (int i=messageNumber - count + 1; i<=messageNumber; i++) {
        session.onMessageDiscarded(sequence, i, ProtocolConstants.ASYNC_RESPONSE);
      }
      
    } else if (errorCode == 38) {
      //just discarded
      session.onMessageDiscarded(sequence, messageNumber, ProtocolConstants.ASYNC_RESPONSE);
    } else if (errorCode <= 0) {
      // Metadata Adapter has refused the message
      session.onMessageDeny(sequence, errorCode, errorMessage, messageNumber, ProtocolConstants.ASYNC_RESPONSE);
    } else {
      // 32 / 33 The specified progressive number is too low
      // 34 NotificationException from metadata 
      // 35 unexpected processing error
      // 68 Internal server error
      session.onMessageError(sequence, errorCode, errorMessage, messageNumber, ProtocolConstants.ASYNC_RESPONSE);
      
    }
  }
  
  /**
   * Checks if a data notification can be forwarded to session.
   * In fact, in case of recovery, the initial notifications may be redundant.
   */
  private boolean processCountableNotification() {
      if (currentProg != null) {
          long sessionProg = session.getDataNotificationProg();
          assert (currentProg <= sessionProg); // ensured since processPROG
          currentProg++;
          if (currentProg <= sessionProg) {
              // already seen: to be skipped
              return false;
          } else {
              session.onDataNotification();
              sessionProg = session.getDataNotificationProg();
              assert (currentProg == sessionProg);
              return true;
          }
      } else {
          session.onDataNotification();
          return true;
      }
  }

  /**
   * Manages CONERR errors.
   */
  protected void forwardError(int code, String message) {
    if (code == 41) {
      session.onTakeover(code);
    } else if (code == 40) {
      // manual or spurious rebind: let's recover
      session.onTakeover(code);
    } else if (code == 48) {
      session.onExpiry();
    } else if (code == 20) {
      // Answer sent by the Server to signal that a control or rebind
      // request has been refused because the indicated session has not
      // been found.
      session.onSyncError(ProtocolConstants.ASYNC_RESPONSE);
    } else if (code == 4) {
      session.onRecoveryError();
    } else if (Constants.handleError5 && code == 5) {
        session.onServerBusy();
    } else {
        /*
         * fall-back case handles fatal errors: 
         * close current session, don't create a new session, notify client listeners
         */
      session.onServerError(code, message);
    }
  }
  
  /**
   * Manages REQERR/ERROR errors.
   */
  protected void forwardControlResponseError(int code, String message, BaseControlRequestListener<?> listener) {
      if (code == 20) {
          session.onSyncError(ProtocolConstants.SYNC_RESPONSE);
          //Actually we're already END because
          //onSyncError will call errorEvent->closeSession->shutdown 
          //and finally stop on the protocol, thus this call is superfluous
          setStatus(StreamStatus.STREAM_CLOSED);
      } else if (code == 11) {
          // error 11 is managed as CONERR 21
          session.onServerError(21, message);
      } else if (listener != null && code != 65 /*65 is a fatal error*/) {
          /*
           * since there is a listener (because it is a REQERR message), 
           * don't fall-back to fatal error case
           */
          listener.onError(code, message);
      } else {
          /*
           * fall-back case handles fatal errors, i.e. ERROR messages: 
           * close current session, don't create a new session, notify client listeners
           */
          this.session.onServerError(code, message);
          this.setStatus(StreamStatus.STREAM_CLOSED);
      }
  }
  
  protected final void onIllegalMessage(String description) {
      forwardControlResponseError(61, description, null);
  }
  
  @Override
  public void onFatalError(Throwable e) {
      this.session.onServerError(61, "Internal error");
      this.setStatus(StreamStatus.STREAM_CLOSED);
  }
  
  @Override
  public void stop(boolean waitPendingControlRequests,boolean forceConnectionClose) {
      log.info("Protocol dismissed");
      this.setStatus(StreamStatus.STREAM_CLOSED, forceConnectionClose);
      reverseHeartbeatTimer.onClose();
  }
  
  @Override
  public long getMaxReverseHeartbeatIntervalMs() {
      return reverseHeartbeatTimer.getMaxIntervalMs();
  }

  /**
   * calls from Transport are made using the SessionThread
   */
  public abstract class StreamListener implements SessionRequestListener {

    boolean disabled = false;
    boolean isOpen = false;
    boolean isInterrupted = false;
    
    public void disable() {
      disabled = true;
    }
    
    @Override
    public final void onMessage(String message) {
        if (disabled) {
            if (log.isDebugEnabled()) {            
                log.warn("Message discarded oid=" + objectId + ": " + message);
            }
            return;
        }
        doMessage(message);
    }
    
    protected void doMessage(String message) {
        onProtocolMessage(message);        
    }

    @Override
    public final void onOpen() {
        if (disabled) {
            return;
        }
        doOpen();
    }
    
    protected void doOpen() {        
        this.isOpen = true;
    }

    @Override
    public final void onClosed() {
        if (disabled) {
            return;
        }
        doClosed();
    }
    
    protected void doClosed() {        
        interruptSession(false);
    }

    @Override
    public final void onBroken() {
        if (disabled) {
            return;
        }
        doBroken(false);
    }
    
    public final void onBrokenWS() {
        if (disabled) {
            return;
        }
        doBroken(true);
    }
    
    protected void doBroken(boolean wsError) {        
        interruptSession(wsError);
    }
    
    /**
     * Interrupts the current session if an error occurs or the session is closed unexpectedly.
     * @param wsError unable to open WS
     */
    protected void interruptSession(boolean wsError) {
        if (! isInterrupted) {
            session.onInterrupted(wsError, !this.isOpen);
            isInterrupted = true;
        }
    }
  } // StreamListener
  
  /**
   * Stream listener for create_session and recovery requests. 
   */
  public class OpenSessionListener extends StreamListener {}
  
  /**
   * Stream listener for bind_session requests supporting reverse heartbeats.
   */
  public class BindSessionListener extends StreamListener {
      
      @Override
      protected void doOpen() {
          super.doOpen();
          onBindSessionForTheSakeOfReverseHeartbeat();
      }
  }
  
  /**
   * Allows to customize the behavior of {@link ReverseHeartbeatTimer#onBindSession(boolean)}
   * with respect to the transport: if the transport is HTTP, bind requests don't matter in the measuring
   * of heartbeat distance; but if the transport is WebSocket bind requests matter. 
   */
  protected abstract void onBindSessionForTheSakeOfReverseHeartbeat();
  
  public abstract class BaseControlRequestListener<T extends RequestTutor> implements RequestListener {

      private boolean opened = false;
      private boolean completed = false;
      protected final T tutor;
      private final StringBuffer response = new StringBuffer();

      public BaseControlRequestListener(T tutor) {
          this.tutor = tutor;
      }

      abstract public void onOK();      

      abstract public void onError(int code,String message);

      public void onOpen() {
          if (tutor != null) {
              opened  = true;
              tutor.notifySender(false);
          }
      }

      public void onMessage(String message) {
          response.append(message);
      }

      public void onClosed() {
          if (completed) {
              return;
          }
          completed = true;
          if (!opened) {
              if (tutor != null) {
                  tutor.notifySender(true);
              }
          } else {
              this.onComplete(response.toString());
          }
      }

      public void onComplete(String message) {
          if (message == null || message.isEmpty()) {
              // an empty message means that the sever has probably closed the socket.
              // ignore it and await the request timeout expires and the request is transmitted again.
              return;
          }
          
          try {
            ControlResponseParser parser = ControlResponseParser.parseControlResponse(message);
            if (parser instanceof REQOKParser) {
                this.onOK();
                
            } else if (parser instanceof REQERRParser) {
                REQERRParser request = (REQERRParser) parser;
                forwardControlResponseError(request.errorCode, request.errorMsg, this);
                
            } else if (parser instanceof ERRORParser) {
                ERRORParser request = (ERRORParser) parser;
                forwardControlResponseError(request.errorCode, request.errorMsg, this);
                
            } else {
                // should not happen
                onIllegalMessage("Unexpected response to control request: " + message);
            }
            
        } catch (ParsingException e) {
            onIllegalMessage(e.getMessage());
        }
      }

      public void onBroken() {
          if (completed) {
              return;
          }
          completed = true;
          if (!opened && tutor != null) {
              tutor.notifySender(true);
          }
      }

  }
  
  /**
   * Control request listener supporting reverse heartbeats.
   */
  public abstract class ControlRequestListener<T extends RequestTutor> extends BaseControlRequestListener<T> {

      public ControlRequestListener(T tutor) {
          super(tutor);
      }

      @Override
      public void onOpen() {
          super.onOpen();
          reverseHeartbeatTimer.onControlRequest();
      }
  }
  
  /**
   * Superclass of all the MPN request listeners.
   * <p>
   * <b>NB</b> To stop retransmissions, it is important that the tutor is notified whenever a response is received
   * (see implementations of {@link #onOK()} and {@link #onError(int, String)}). 
   */
  public class MpnRequestListener<T extends MpnRequest, Q extends MpnTutor> extends ControlRequestListener<Q> {

      final T request;

      public MpnRequestListener(T request, Q tutor) {
          super(tutor);
          this.request = request;
      }

      @Override
      public void onOK() {
          tutor.onResponse();
      }

      @Override
      public void onError(int code, String message) {
          tutor.onResponse();
      }
  }
}
