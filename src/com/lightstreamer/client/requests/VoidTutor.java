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
import com.lightstreamer.client.session.SessionThread;

/**
 * 
 */
public class VoidTutor extends RequestTutor {

  public VoidTutor(SessionThread thread,
      InternalConnectionOptions connectionOptions) {
    super(thread, connectionOptions);
  }

  @Override
  public boolean shouldBeSent() {
    return true;
  }

  @Override
  protected boolean verifySuccess() {
    return true;
  }

  @Override
  protected void doRecovery() {
  }

  @Override
  public void notifyAbort() {
  }

  @Override
  protected boolean isTimeoutFixed() {
    return false;
  }

  @Override
  protected long getFixedTimeout() {
    return 0;
  }
  
  @Override
  protected void startTimeout() {
      /* 
       * doesn't schedule a task on session thread since the void tutor doesn't need retransmissions
       */
  }

}
