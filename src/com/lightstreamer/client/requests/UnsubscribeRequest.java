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
package com.lightstreamer.client.requests;

/**
 * 
 */
public class UnsubscribeRequest extends ControlRequest {

  private final int subscriptionId;

  public UnsubscribeRequest(int subId) {
    this.addParameter("LS_op", "delete");
    this.addParameter("LS_subId", subId);
    
    this.subscriptionId = subId;
    
  }

  public int getSubscriptionId() {
    return this.subscriptionId;
  }

}
