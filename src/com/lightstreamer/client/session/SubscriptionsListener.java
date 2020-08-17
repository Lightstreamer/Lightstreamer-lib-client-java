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

import java.util.ArrayList;

/**
 * 
 */
public interface SubscriptionsListener {
  
  void onSessionStart();
  void onSessionClose();
  
  void onUpdateReceived(int subscriptionId, int item, ArrayList<String> args);

  void onEndOfSnapshotEvent(int subscriptionId, int item);

  void onClearSnapshotEvent(int subscriptionId, int item);

  void onLostUpdatesEvent(int subscriptionId, int item, int lost);
  
  void onUnsubscription(int subscriptionId);

  void onSubscription(int subscriptionId, int totalItems, int totalFields, int keyPosition, int commandPosition);

  void onSubscription(int subscriptionId, long reconfId);
  
  void onSubscriptionError(int subscriptionId, int errorCode , String errorMessage);
  
  void onConfigurationEvent(int subscriptionId, String frequency);
  
  void onSubscriptionAck(int subscriptionId);
  
  void onUnsubscriptionAck(int subscriptionId);
}
