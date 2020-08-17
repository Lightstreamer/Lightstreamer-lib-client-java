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
