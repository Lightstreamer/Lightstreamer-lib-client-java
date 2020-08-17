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
public class DestroyRequest extends ControlRequest {

  private String session;

  /**
   * @param sessionId
   * @param closeReason
   */
  public DestroyRequest(String targetServer, String sessionID, String closeReason) {
    this.setServer(targetServer);
    
    this.addParameter("LS_op", "destroy");
    
    this.session = sessionID;
    
    this.addParameter("LS_session", sessionID);
    
    if (closeReason != null) {
      this.addParameter("LS_cause", closeReason);
    }
  }
  
  public String getSession() {
    return this.session;
  }

}
