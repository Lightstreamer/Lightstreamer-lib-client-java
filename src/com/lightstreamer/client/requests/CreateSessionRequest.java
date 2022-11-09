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

import com.lightstreamer.client.session.InternalConnectionDetails;
import com.lightstreamer.client.session.InternalConnectionOptions;

public class CreateSessionRequest extends SessionRequest {


    public CreateSessionRequest(String targetServer, boolean polling, 
            String cause, InternalConnectionOptions options, InternalConnectionDetails details, long delay,
            String password, String oldSession, boolean serverBusy) {
        this(targetServer, polling, cause, options, details, delay, password, oldSession);
        if (serverBusy) {
            this.addParameter("LS_ttl_millis", "unlimited");
        }
    }
    
  public CreateSessionRequest(String targetServer, boolean polling, 
      String cause, InternalConnectionOptions options, InternalConnectionDetails details, long delay,
      String password, String oldSession) {
    
    super(polling,delay);
    
    this.setServer(targetServer);
    
    this.addParameter("LS_polling", "true");
    
    if (cause != null) {
      this.addParameter("LS_cause", cause);
    }
    
    long requestedPollingInterval = 0;
    long requestedIdleTimeout = 0;
    if (polling) {
      // we ask this polling interval to the server, but the server might
      // refuse it and specify a smaller value
      // NOTE the client might even wait less than specified by the server: 
      // we don't currently do that though
      requestedPollingInterval = options.getPollingInterval() + delay;
    }
    
    this.addParameter("LS_polling_millis", requestedPollingInterval);
    this.addParameter("LS_idle_millis", requestedIdleTimeout);
    
    this.addParameter("LS_cid", "mgQkwtwdysogQz2BJ4Ji%20kOj2Bg");
    
    if (options.getInternalMaxBandwidth() == 0) {
      // unlimited: just omit the parameter
    } else if (options.getInternalMaxBandwidth() > 0) {
      this.addParameter("LS_requested_max_bandwidth", options.getInternalMaxBandwidth());
    }
    
    if (details.getAdapterSet() != null) {
      this.addParameter("LS_adapter_set", details.getAdapterSet());
    }
    
    if (details.getUser() != null) {
      this.addParameter("LS_user", details.getUser());
    }
    
    if (password != null) {
      this.addParameter("LS_password", password);
    }
    
    if (oldSession != null) {
      this.addParameter("LS_old_session", oldSession);
    }
  }
  
  @Override
  public String getRequestName() {
    return "create_session";
  }

  @Override
  public boolean isSessionRequest() {
    return true;
  }
  
}
