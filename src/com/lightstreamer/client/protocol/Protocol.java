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


package com.lightstreamer.client.protocol;

import com.lightstreamer.client.ClientListener;
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
import com.lightstreamer.util.ListenableFuture;

/**
 * 
 */
public interface Protocol {
  
  void setListener(ProtocolListener listener);
 
  void sendForceRebind(ForceRebindRequest request, RequestTutor tutor);

  void sendDestroy(DestroyRequest request, RequestTutor tutor);

  void sendConstrainRequest(ConstrainRequest request, RequestTutor tutor);

  void sendCreateRequest(CreateSessionRequest request);

  ListenableFuture sendBindRequest(BindSessionRequest request);

  /**
   * Closes the stream connection. 
   */
  void stop(boolean waitPendingControlRequests, boolean forceConnectionClose);

  void sendMessageRequest(MessageRequest request, RequestTutor tutor);

  void sendSubscriptionRequest(SubscribeRequest request, RequestTutor tutor);

  void sendUnsubscriptionRequest(UnsubscribeRequest request, RequestTutor tutor);

  void sendConfigurationRequest(ChangeSubscriptionRequest request,
      RequestTutor tutor);

  void sendReverseHeartbeat(ReverseHeartbeatRequest request, RequestTutor tutor);

  void copyPendingRequests(Protocol protocol);

  RequestManager getRequestManager();

  void handleReverseHeartbeat();

  /**
   * A non-recoverable error causing the closing of the session
   * and the notification of the error 61 to the method {@link ClientListener#onServerError(int, String)}.
   */
  void onFatalError(Throwable cause);
  
  /**
   * Opens a WebSocket connection. If a connection is already open (this can happen when the flag isEarlyWSOpenEnabled is set),
   * the connection is closed and a new connection is opened.
   */
  public ListenableFuture openWebSocketConnection(String serverAddress);

  /**
   * Forward the MPN request to the transport layer.
   */
  void sendMpnRegisterRequest(MpnRegisterRequest request, MpnRegisterTutor tutor);

  /**
   * Forward the MPN request to the transport layer.
   */
  void sendMpnSubscribeRequest(MpnSubscribeRequest request, MpnSubscribeTutor tutor);

  /**
   * Forward the MPN request to the transport layer.
   */
  void sendMpnUnsubscribeRequest(MpnUnsubscribeRequest request, MpnUnsubscribeTutor tutor);
  
  /**
   * Forward the MPN request to the transport layer.
   */
  void sendMpnUnsubscribeRequest(MpnUnsubscribeFilterRequest request, MpnUnsubscribeFilterTutor tutor);
  
  /**
   * Forward the MPN request to the transport layer.
   */
  void sendMpnResetBadgeRequest(MpnResetBadgeRequest request, MpnResetBadgeTutor tutor);

  /**
   * Forward the session recovery request to the transport layer.
   */
  void sendRecoveryRequest(RecoverSessionRequest request);
  
  /**
   * Set the default sessionId so the protocol can omit parameter LS_session from requests.
   */
  void setDefaultSessionId(String sessionId);
  
  /**
   * The maximum time between two heartbeats.
   * It is the value of the parameter LS_inactivity_millis sent with a bind_session request.
   * It doesn't change during the life of a session.
   */
  long getMaxReverseHeartbeatIntervalMs();

  void stopActive(boolean forceConnectionClose);
}
