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
public class SessionRequest extends LightstreamerRequest {

  private boolean polling;
  private long delay;


  public SessionRequest(boolean polling,long delay) {
    this.polling = polling;
    this.delay = delay;
  }
  
  public boolean isPolling() {
    return polling;
  }
  public long getDelay() {
    return delay;
  }
  
  
  @Override
  public String getRequestName() {
    return null;
  }

}
