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


/**
 * 
 */
public interface SessionListener {

  void sessionStatusChanged(int handlerPhase, String phase, boolean sessionRecovery);

  void streamSense(int handlerPhase, String switchCause, boolean forced);

  void switchReady(int handlerPhase, String switchCause, boolean forced, boolean startRecovery);

  void slowReady(int handlerPhase);

  int onSessionClose(int handlerPhase, boolean noRecoveryScheduled);

  void streamSenseSwitch(int handlerPhase, String reason, String sessionPhase, boolean startRecovery); //we want to stream-sense but we have a pending connection, try to force the switch

  void onIPReceived(String clientIP);

  void onSessionBound();

  void onSessionStart();

  void onServerError(int errorCode, String errorMessage);

  void onSlowRequired(int handlerPhase, long delay);

  void retry(int handlerPhase, String retryCause, boolean forced, boolean retryAgainIfStreamFails, boolean serverBusy);
  
  /**
   * Since the client IP has changed and WebSocket support
   * was enabled again, we ask the {@link SessionManager}
   * to use a WebSocket transport for this session.
   */
  void switchToWebSocket(boolean startRecovery);

  /**
   * Forward the MPN event to the MPN manager.
   */
  void onMpnRegisterOK(String deviceId, String adapterName);

  /**
   * Forward the MPN event to the MPN manager.
   */
  void onMpnRegisterError(int code, String message);

  /**
   * Forward the MPN event to the MPN manager.
   */
  void onMpnSubscribeOK(String lsSubId, String pnSubId);

  /**
   * Forward the MPN event to the MPN manager.
   */
  void onMpnSubscribeError(String subId, int code, String message);
  
  /**
   * Forward the MPN event to the MPN manager.
   */
  void onMpnUnsubscribeError(String subId, int code, String message);

  /**
   * Forward the MPN event to the MPN manager.
   */
  void onMpnUnsubscribeOK(String subId);

  /**
   * Forward the MPN event to the MPN manager.
   */
  void onMpnResetBadgeOK(String deviceId);

  /**
   * Forward the MPN event to the MPN manager.
   */
  void onMpnBadgeResetError(int code, String message);

  void recoverSession(int handlerPhase, String retryCause, boolean forced, boolean retryAgainIfStreamFails);
}
