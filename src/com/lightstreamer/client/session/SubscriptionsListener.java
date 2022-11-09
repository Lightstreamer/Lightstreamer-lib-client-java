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
