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
package com.lightstreamer.client;

import com.lightstreamer.client.events.EventsThread;
import com.lightstreamer.client.session.InternalConnectionOptions;
import com.lightstreamer.client.session.SessionManager;
import com.lightstreamer.client.session.SessionThread;
import com.lightstreamer.client.session.SessionsListener;
import com.lightstreamer.client.transport.WebSocket;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;

/**
 * this class moves calls from the LightstreamerClient (from the EventsThread) to the SessionHandler (to the SessionThread)
 */
class LightstreamerEngine {
  
  private static final boolean FROM_API = true;
  private static final boolean NO_TRANSPORT_FORCED = false;
  private static final boolean NO_COMBO_FORCED = false;
  private static final boolean NO_POLLING = false;
  private static final boolean CAN_SWITCH = false;
  private static final boolean NO_RECOVERY = true;
  
    
  protected final Logger log = LogManager.getLogger(Constants.SESSION_LOG);
  
  
  private final SessionManager sessionManager;
  private final InternalConnectionOptions connectionOptions;
  private final SessionThread sessionThread;
  private final EventsThread eventsThread;
  private final ClientListener clientListener;
  
  private boolean connectionRequested = false;
  
  public LightstreamerEngine(InternalConnectionOptions options, SessionThread sessionThread, 
      EventsThread eventsThread, ClientListener listener,SessionManager manager) {
    
    this.connectionOptions = options;
    this.sessionThread = sessionThread;
    this.clientListener = listener;
    this.eventsThread = eventsThread;
    
    this.sessionManager = manager;
    manager.setSessionsListener(new SessionsListenerImpl());
    
  }

  
  //////////////////Client --> Session
 
  //from EventsThread
  public void connect() {
    this.connect(false);
  }
  
  void doChangeTransport() {
      String ft = connectionOptions.getForcedTransport(); 
      log.info("Transport change requested " + ft);
      if (ft == null) {
          boolean fromAPI = true;
          boolean isTransportForced = false;
          boolean isComboForced = false;
          boolean isPolling = false;
          boolean isHTTP = false;
          this.sessionManager.changeTransport(fromAPI, isTransportForced, isComboForced, isPolling, isHTTP);
      } else {
          boolean fromAPI = true;
          boolean isTransportForced = ft == Constants.WS_ALL || ft == Constants.HTTP_ALL;
          boolean isComboForced = !isTransportForced;
          boolean isPolling = ft == Constants.WS_POLLING || ft == Constants.HTTP_POLLING;
          boolean isHTTP = ft == Constants.HTTP_POLLING || ft == Constants.HTTP_STREAMING || ft == Constants.HTTP_ALL;
          this.sessionManager.changeTransport(fromAPI, isTransportForced, isComboForced, isPolling, isHTTP);
      }
  }
  
  private void connect(final boolean forced) {
    this.connectionRequested = true;
    
    sessionThread.queue(new Runnable() {
      public void run() {
         /*
         //different thread need atomic boolean
         if (!this.connectionRequested) {
           return;
         }
          
         */
        
        
        String currentStatus = sessionManager.getHighLevelStatus(false);
        if (!forced && !currentStatus.equals(Constants.DISCONNECTED)) {
          // note: this case includes DISCONNECTED:WILL-RETRY and DISCONNECTED:TRYING-RECOVERY.
          log.debug("Already reaching for a connection, skip connect call");
          return;
        }
        
        log.debug("Opening a new session and starting automatic reconnections");
        
        String ft = connectionOptions.getForcedTransport(); 
        
        if (ft == null) {
          /* 
           * If the WebSocket support is disabled, we must use HTTP (isHttp = true).
           * On the other hand if the WebSocket support is available, we must use it (isHttp = false).
           */
          boolean isHttp = WebSocket.isDisabled();
          sessionManager.createOrSwitchSession(FROM_API,NO_TRANSPORT_FORCED,NO_COMBO_FORCED,NO_POLLING,isHttp,null,CAN_SWITCH,false,false);
        } else {
          boolean isPolling = ft.equals(Constants.WS_POLLING) || ft.equals(Constants.HTTP_POLLING);
          boolean isHTTP = ft.equals(Constants.HTTP_POLLING) || ft.equals(Constants.HTTP_STREAMING) || ft.equals(Constants.HTTP_ALL);
          boolean isTransportForced = ft.equals(Constants.WS_ALL) || ft.equals(Constants.HTTP_ALL);
          boolean isComboForced = !isTransportForced;
          
          sessionManager.createOrSwitchSession(FROM_API,isTransportForced,isComboForced,isPolling,isHTTP,null,CAN_SWITCH,false,false);
        }
      }
    });
  }
  
  //from EventsThread
  public void disconnect() {
    
    this.connectionRequested = false;
    
    sessionThread.queue(new Runnable() {
      public void run() {
        /*
        //different thread need atomic boolean
        if (!this.connectionRequested) {
          return;
        }
         
        */
        log.debug("Closing a new session and stopping automatic reconnections");
        sessionManager.closeSession(FROM_API,"api",NO_RECOVERY);
      }
    });
  }
  
  //from EventsThread
  public void onRequestedMaxBandwidthChanged() {
    sessionThread.queue(new Runnable() {
      public void run() {
       sessionManager.changeBandwidth(); 
      }
    });
  }

  //from EventsThread
  public void onReverseHeartbeatIntervalChanged() {
    sessionThread.queue(new Runnable() {
      public void run() {
       sessionManager.handleReverseHeartbeat(false);
      }
    });
  }

  //from EventsThread
  public void onForcedTransportChanged() {
    if (this.connectionRequested) {
      this.doChangeTransport();
    }
  }
  
  
//////////////////Session -> Client
  
  private class SessionsListenerImpl implements SessionsListener {

    @Override
    //from SessionThread
    public void onStatusChanged(final String status) {
      eventsThread.queue(new Runnable() {
        public void run() {
          clientListener.onStatusChange(status);
        }
      });
    }

    @Override
    //from SessionThread
    public void onServerError(final int errorCode, final String errorMessage) {
      eventsThread.queue(new Runnable() {
        public void run() {
          clientListener.onServerError(errorCode,errorMessage);
        }
      });
    }
   
  }
  
  
}
