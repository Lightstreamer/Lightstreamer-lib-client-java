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

import java.lang.ref.WeakReference;

import com.lightstreamer.client.Constants;
import com.lightstreamer.client.protocol.Protocol;
import com.lightstreamer.client.requests.ReverseHeartbeatRequest;
import com.lightstreamer.client.requests.RequestTutor;
import com.lightstreamer.client.transport.WebSocket;
import com.lightstreamer.util.ListenableFuture;

public class SessionWS extends Session {
    
  private enum WsState {
      WS_NOT_CONNECTED, WS_CONNECTING, WS_CONNECTED, WS_BROKEN
  }
  
  private class StateMachine {
      private WsState state = WsState.WS_NOT_CONNECTED;
      private String controlLink;
      private ListenableFuture openWsFuture;
      
      void createSent() {
          switch (state) {
          case WS_NOT_CONNECTED:
              if (earlyOpen) {
                  next(WsState.WS_CONNECTING, "createSent");
                  assert controlLink == null;
                  openWS();
              }
              break;

          default:
              assert false;
          }
      }
      
      ListenableFuture sendBind(String bindCause) {
          switch (state) {
          case WS_NOT_CONNECTED:
              next(WsState.WS_CONNECTING, "sendBind");
              openWS();
              return SessionWS.super.bindSessionExecution(bindCause);
              
          case WS_BROKEN:
              next(WsState.WS_BROKEN, "sendBind");
              handler.streamSenseSwitch(handlerPhase, "ws.error", phase, recoveryBean.isRecovery());
              return ListenableFuture.rejected();
              
          default:
              assert state == WsState.WS_CONNECTED || state == WsState.WS_CONNECTING;
              next(state, "sendBind");
              return SessionWS.super.bindSessionExecution(bindCause);
          }
      }
      
      void changeControlLink(String newControlLink) {
          switch (state) {
          case WS_NOT_CONNECTED:
              assert ! earlyOpen;
              next(WsState.WS_NOT_CONNECTED, "clink");
              controlLink = newControlLink;
              break;
              
          case WS_CONNECTING:
          case WS_CONNECTED:
          case WS_BROKEN:
              assert openWsFuture != null;
              next(WsState.WS_CONNECTING, "clink");
              controlLink = newControlLink;
              openWsFuture.abort();
              openWS();
              break;
          }
      }
      
      void connectionOK() {
          assert state == WsState.WS_CONNECTING;
          next(WsState.WS_CONNECTED, "ok");
      }
      
      void connectionError() {
          assert state == WsState.WS_CONNECTING;
          next(WsState.WS_BROKEN, "error");
          if (is(OFF) || is(CREATING) || is(STALLED) || is(CREATED)) {
              //this is an error on a early open, we can't act now as we must wait for the loop from the create
              //otherwise we would waste the entire session
              //NOPPING!
          } else {              
              launchTimeout("zeroDelay", 0, "ws.broken.wait", false);
          }
      }
      
      private void openWS() {
          String cLink = (controlLink == null ? getPushServerAddress() : controlLink);
          assert openWsFuture == null || openWsFuture.getState() == ListenableFuture.State.ABORTED;
          openWsFuture = 
                  protocol
                  .openWebSocketConnection(cLink)
                  .onFulfilled(new MyRunnableConnectOK(this))
                  .onRejected(new MyRunnableError(this));
      }
      
      private void next(WsState nextState, String event) {
          if (log.isDebugEnabled()) {
              log.debug("SessionWS state change (" + objectId + ") (" + event + "): " + state.name() + (state != nextState ? " -> " + nextState.name() : ""));
          }
          state = nextState;
      }
  }
  
  /**
   * iOS hack to avoid strong back references between Runnable and StateMachine.
   */
  private static class MyRunnableConnectOK implements Runnable {
      final WeakReference<StateMachine> ref;

      MyRunnableConnectOK(StateMachine sm) {
          ref = new WeakReference<StateMachine>(sm); 
      }

      @Override
      public void run() {
          StateMachine sm = ref.get();
          if (sm != null) {
              sm.connectionOK();
          }
      }
  }
  
  /**
   * iOS hack to avoid strong back references between Runnable and StateMachine.
   */
  private static class MyRunnableError implements Runnable {
      final WeakReference<StateMachine> ref;

      MyRunnableError(StateMachine sm) {
          ref = new WeakReference<StateMachine>(sm); 
      }

      @Override
      public void run() {
          StateMachine sm = ref.get();
          if (sm != null) {
              sm.connectionError();
          }
      }
  }
  
  private final boolean earlyOpen;
  private final StateMachine wsMachine = new StateMachine();

  public SessionWS(int objectId, boolean isPolling, boolean forced, SessionListener handler,
      SubscriptionsListener subscriptions, MessagesListener messages,
      Session originalSession, SessionThread thread, Protocol protocol,
      InternalConnectionDetails details, InternalConnectionOptions options,
      int callerPhase, boolean retryAgainIfStreamFails, boolean sessionRecovery) {
    super(objectId, isPolling, forced, handler, subscriptions, messages, originalSession,
        thread, protocol, details, options, callerPhase, retryAgainIfStreamFails, sessionRecovery);
    this.earlyOpen = options.isEarlyWSOpenEnabled() && ! WebSocket.isDisabled();
  }

  @Override
  protected void createSent() {
      // create_session request has been sent
      super.createSent();
      wsMachine.createSent();
  }

  @Override
  protected String getConnectedHighLevelStatus() {
    return this.isPolling?Constants.WS_POLLING:Constants.WS_STREAMING;
  }

  @Override
  protected String getFirstConnectedStatus() {
    return Constants.SENSE;
  }

  @Override
  protected boolean shouldAskContentLength() {
    return false;
  }

  @Override
  public void sendReverseHeartbeat(ReverseHeartbeatRequest request, RequestTutor tutor) {
      super.sendReverseHeartbeat(request, tutor);
  }
  
  @Override
  protected ListenableFuture bindSessionExecution(String bindCause) {
      // LOOP received from create_session response
      return wsMachine.sendBind(bindCause);
  }
  
  @Override
  protected void changeControlLink(String controlLink) {
      // CONOK received from create_session response and control link changed
      wsMachine.changeControlLink(controlLink);
  }
  
  @Override
  protected void doOnErrorEvent(String reason, 
          boolean closedOnServer, boolean unableToOpen, 
          boolean startRecovery, long timeLeftMs, 
          boolean wsError,
          boolean serverBusyError) {
      if (wsError) {
          if (is(OFF) || is(CREATING) || is(CREATED)) {
              log.info("WebSocket was broken before it was used");
              //this is an error on a early open, we can't act now as we must wait for the loop from the create
              //otherwise we would waste the entire session
              //NOPPING!
              
          } else if (is(FIRST_PAUSE)) {
              log.info("WebSocket was broken while we were waiting the first bind");
              //as the bind was not yet sent (otherwise we would be in the FIRST_BINDING phase) we can recover
              //binding via HTTP
              handler.streamSenseSwitch(handlerPhase, reason, phase, recoveryBean.isRecovery());
              
          } else {
              super.doOnErrorEvent(reason, closedOnServer, unableToOpen, startRecovery, timeLeftMs, wsError, serverBusyError);
          }
          
      } else {
          super.doOnErrorEvent(reason, closedOnServer, unableToOpen, startRecovery, timeLeftMs, wsError, serverBusyError);
      }
  }
}
