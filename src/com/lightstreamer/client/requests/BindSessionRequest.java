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

import com.lightstreamer.client.session.InternalConnectionOptions;

public class BindSessionRequest extends SessionRequest {

  public BindSessionRequest(String targetServer, String session, boolean polling, 
      String cause, InternalConnectionOptions options, long delay, boolean addContentLength, 
      long maxReverseHeartbeatIntervalMs) {
    
    super(polling,delay); 

    this.setServer(targetServer);
    
    setSession(session);
        // the session ID can still be omitted from the request, depending on the transport
    
    if(polling) {
      this.addParameter("LS_polling", "true");
      this.addParameter("LS_polling_millis", options.getPollingInterval() + delay);
      this.addParameter("LS_idle_millis", options.getIdleTimeout());
    } else {
      if (options.getKeepaliveInterval() > 0) {
        this.addParameter("LS_keepalive_millis", options.getKeepaliveInterval());
      }
      
      if (maxReverseHeartbeatIntervalMs > 0) {
          this.addParameter("LS_inactivity_millis", maxReverseHeartbeatIntervalMs);
      }
      
      if (addContentLength) {
        this.addParameter("LS_content_length", options.getContentLength());
      }
    }
    
    if (cause != null) {
      this.addParameter("LS_cause", cause);
    }
  }
    
  @Override
  public String getRequestName() {
    return "bind_session";
  }
  
  @Override
  public boolean isSessionRequest() {
    return true;
  }
}
