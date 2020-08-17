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

public class ChangeSubscriptionRequest extends ControlRequest {

  private int reconfId;
  private int subscriptionId;

  public ChangeSubscriptionRequest(int subscriptionId, double requestedMaxFrequency, int reconfId) {
    this.reconfId = reconfId;
    this.subscriptionId = subscriptionId;
        
    this.addParameter("LS_op", "reconf");
    this.addParameter("LS_subId", subscriptionId);
    
    assert requestedMaxFrequency != -2;
    assert requestedMaxFrequency != -1;
    if (requestedMaxFrequency == 0) {
      this.addParameter("LS_requested_max_frequency", "unlimited");
    } else if (requestedMaxFrequency > 0) {
      this.addParameter("LS_requested_max_frequency", requestedMaxFrequency);
    }
   
  }
  
  public int getReconfId() {
    return this.reconfId;
  }
  
  public int getSubscriptionId() {
    return this.subscriptionId;
  }

}
