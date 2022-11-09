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

import com.lightstreamer.util.Descriptor;


/**
 * 
 */
public class SubscribeRequest extends ControlRequest {

  
  private int id;

  public SubscribeRequest(int subId, String mode, Descriptor items, Descriptor fields,
      String dataAdapter, String selector, String requiredSnapshot, 
      double requestedMaxFrequency, int requestedBufferSize) {
    this.id = subId;
    
    this.addParameter("LS_op", "add");
    this.addParameter("LS_subId", subId);

    this.addParameter("LS_mode", mode);
    this.addParameter("LS_group", items.getComposedString());
    this.addParameter("LS_schema", fields.getComposedString());
    
    if (dataAdapter != null) {
      this.addParameter("LS_data_adapter", dataAdapter);
    }
    
    if (selector != null) {
      this.addParameter("LS_selector", selector);
    }
    
    if (requiredSnapshot != null) {
      if (requiredSnapshot.equals("yes")) {
        this.addParameter("LS_snapshot", "true");
      } else if (requiredSnapshot.equals("no")) {
        this.addParameter("LS_snapshot", "false");
      } else {
        this.addParameter("LS_snapshot", requiredSnapshot);
      }
    }
    
    if (requestedMaxFrequency == -2)  {
      // server default: just omit the parameter
    } else if (requestedMaxFrequency == -1)  {
      this.addParameter("LS_requested_max_frequency", "unfiltered");
    } else if (requestedMaxFrequency == 0) {
      this.addParameter("LS_requested_max_frequency", "unlimited");
    } else if (requestedMaxFrequency > 0) {
      this.addParameter("LS_requested_max_frequency", requestedMaxFrequency);
    }
    
    if (requestedBufferSize == -1) {
      // server default: just omit the parameter
    } else if (requestedBufferSize == 0) {
      this.addParameter("LS_requested_buffer_size", "unlimited");
    } else if (requestedBufferSize > 0) {
      this.addParameter("LS_requested_buffer_size", requestedBufferSize);
    }
    
    //LS_start & LS_end are obsolete, removed from APIs
  }

  public int getSubscriptionId() {
    return this.id;
  }
  
}
