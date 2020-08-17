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

public class ConstrainRequest extends ControlRequest {

  private final double maxBandwidth;
  /**
   * This field was added to distinguish between requests made by the client (where requestId equals clientId) 
   * and requests made by retransmission algorithm (where requestId is different from clientId).
   */
  private final long clientRequestId;

  /**
   * Change-bandwidth request.
   * 
   * @param maxBandwidth
   * @param parent if this is a retransmission, {@code parent} must be the original client request. 
   * If this is a client request, {@code parent} must be null.
   */
  public ConstrainRequest(double maxBandwidth, ConstrainRequest parent) {
      this.addParameter("LS_op", "constrain");
      
      if (maxBandwidth == 0) {
          this.addParameter("LS_requested_max_bandwidth", "unlimited");
      } else if (maxBandwidth > 0) {
          this.addParameter("LS_requested_max_bandwidth", maxBandwidth);
      }
      
      this.maxBandwidth = maxBandwidth;
      this.clientRequestId = (parent == null ? requestId : parent.getClientRequestId());
  }
  
  public double getMaxBandwidth() {
    return this.maxBandwidth;
  }

  /**
   * The requestId of the original request made by the client. 
   * It may be different from the {@link #getRequestId()} if this is a retransmission request.
   */
  public long getClientRequestId() {
      return clientRequestId;
  }
}
