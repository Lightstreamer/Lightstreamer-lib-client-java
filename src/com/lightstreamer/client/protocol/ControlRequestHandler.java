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


package com.lightstreamer.client.protocol;

import com.lightstreamer.client.requests.LightstreamerRequest;
import com.lightstreamer.client.requests.RequestTutor;
import com.lightstreamer.client.transport.RequestListener;

public interface ControlRequestHandler {

  /**
   * Adds a control/message request.
   */
  void addRequest(LightstreamerRequest request, RequestTutor tutor, RequestListener reqListener);
  
  void setRequestLimit(long limitNum);
  
  void copyTo(ControlRequestHandler newHandler);

  void close(boolean waitPending);
}
