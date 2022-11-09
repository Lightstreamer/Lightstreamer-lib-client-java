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
