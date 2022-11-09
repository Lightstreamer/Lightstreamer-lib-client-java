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
    
    //this.addParameter("LS_session", sessionID);
    
    if (closeReason != null) {
      this.addParameter("LS_cause", closeReason);
    }
  }
  
  public String getSession() {
    return this.session;
  }

}
