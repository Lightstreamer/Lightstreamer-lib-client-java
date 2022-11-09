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
