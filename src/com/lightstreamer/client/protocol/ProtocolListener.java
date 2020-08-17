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

import com.lightstreamer.client.requests.RequestTutor;

public interface ProtocolListener {

  void onConstrainResponse(RequestTutor tutor);

  void onServerSentBandwidth(String maxBandwidth);
  
  void onTakeover(int specificCode); 
  
  void onExpiry();
  
  void onKeepalive();
  
  void onOKReceived(String newSession, String controlLink, long requestLimitLength, 
      long keepaliveIntervalDefault);
  
  void onLoopReceived(long serverSentPause);
  
  void onSyncError(boolean async);
  
  void onUpdateReceived(int subscriptionId, int item, ArrayList<String> values); 
  
  void onEndOfSnapshotEvent(int subscriptionId, int item);
  
  void onClearSnapshotEvent(int subscriptionId, int item);
  
  void onLostUpdatesEvent(int subscriptionId, int item, int lost);
  
  void onMessageAck(String sequence, int messageNumber, boolean async);
  
  void onMessageOk(String sequence, int messageNumber);
  
  void onMessageDeny(String sequence, int denyCode, String denyMessage, int messageNumber, boolean async);  //<= 0 messaggio rifiutato dal Metadata Adapter
  
  void onMessageDiscarded(String sequence, int messageNumber, boolean async); //38 messaggio non giunto in tempo (e forse mai giunto)
  
  void onMessageError(String sequence, int errorCode , String errorMessage, int messageNumber, boolean async);
  
  void onSubscriptionError(int subscriptionId, int errorCode , String errorMessage, boolean async);
  
  void onServerError(int errorCode , String errorMessage); //AKA onEnd
  
  void onUnsubscription(int subscriptionId);
  
  void onSubscription(int subscriptionId, int totalItems, int totalFields, int keyPosition, int commandPosition);

  void onSubscriptionReconf(int subscriptionId, long reconfId, boolean async);
  
  void onSyncMessage(long seconds);

  void onInterrupted(boolean wsError, boolean unableToOpen);

  void onConfigurationEvent(int subscriptionId, String frequency);

  void onServerName(String serverName);

  void onClientIp(String clientIp);

  void onSubscriptionAck(int subscriptionId);

  void onUnsubscriptionAck(int subscriptionId);

  /**
   * Forward the MPN event to the Session Manager.
   */
  void onMpnRegisterOK(String deviceId, String adapterName);

  /**
   * Forward the MPN event to the Session Manager.
   */
  void onMpnRegisterError(int code, String message);

  /**
   * Forward the MPN event to the Session Manager.
   */
  void onMpnSubscribeOK(String lsSubId, String pnSubId);

  /**
   * Forward the MPN event to the Session Manager.
   */
  void onMpnSubscribeError(String subId, int code, String message);
  
  /**
   * Forward the MPN event to the Session Manager.
   */
  void onMpnUnsubscribeError(String subId, int code, String message);

  /**
   * Forward the MPN event to the Session Manager.
   */
  void onMpnUnsubscribeOK(String subId);

  /**
   * Forward the MPN event to the Session Manager.
   */
  void onMpnResetBadgeOK(String deviceId);

  /**
   * Forward the MPN event to the Session Manager.
   */
  void onMpnBadgeResetError(int code, String message);
  
  long getDataNotificationProg();

  void onDataNotification();

  void onRecoveryError();

  void onServerBusy();

  void onPROGCounterMismatch();
}
