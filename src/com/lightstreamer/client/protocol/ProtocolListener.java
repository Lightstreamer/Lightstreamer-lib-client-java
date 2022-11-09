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
