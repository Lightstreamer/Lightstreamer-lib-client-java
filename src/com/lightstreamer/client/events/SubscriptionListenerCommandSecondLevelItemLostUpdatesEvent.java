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
package com.lightstreamer.client.events;

import com.lightstreamer.client.SubscriptionListener;

public class SubscriptionListenerCommandSecondLevelItemLostUpdatesEvent implements Event<SubscriptionListener> {
  
  private final String key;
  private final int lostUpdates;

  public SubscriptionListenerCommandSecondLevelItemLostUpdatesEvent(int lostUpdates,String key) {
    this.key = key;
    this.lostUpdates = lostUpdates;
  }

  @Override
  public void applyTo(SubscriptionListener listener) {
    listener.onCommandSecondLevelItemLostUpdates(lostUpdates, key);
  }
}
