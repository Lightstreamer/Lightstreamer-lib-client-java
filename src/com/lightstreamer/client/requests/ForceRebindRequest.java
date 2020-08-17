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

public class ForceRebindRequest extends ControlRequest {


  public ForceRebindRequest(String targetServer, String sessionID, String rebindCause, double delay) {
    this.setServer(targetServer);
    
    this.addParameter("LS_op", "force_rebind");
    
    this.addParameter("LS_session", sessionID);
 
    if (rebindCause != null) {
      this.addParameter("LS_cause", rebindCause);
    }
    
    if(delay > 0) {
      this.addParameter("LS_polling_millis", delay);
    }
    
  }
  
}
