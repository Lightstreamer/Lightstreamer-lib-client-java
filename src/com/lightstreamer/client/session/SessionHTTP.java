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


package com.lightstreamer.client.session;

import com.lightstreamer.client.Constants;
import com.lightstreamer.client.protocol.Protocol;
import com.lightstreamer.client.requests.ReverseHeartbeatRequest;
import com.lightstreamer.client.requests.RequestTutor;


public class SessionHTTP extends Session {

  public SessionHTTP(int objectId, boolean isPolling, boolean forced, SessionListener handler,
      SubscriptionsListener subscriptions, MessagesListener messages,
      Session originalSession, SessionThread thread, Protocol protocol,
      InternalConnectionDetails details, InternalConnectionOptions options,
      int callerPhase, boolean retryAgainIfStreamFails, boolean sessionRecovery) {
    super(objectId, isPolling, forced, handler, subscriptions, messages, originalSession,
        thread, protocol, details, options, callerPhase, retryAgainIfStreamFails, sessionRecovery);
  }

  @Override
  protected String getConnectedHighLevelStatus() {
    return this.isPolling?Constants.HTTP_POLLING:Constants.HTTP_STREAMING;
  }

  @Override
  protected String getFirstConnectedStatus() {
    return this.isPolling?Constants.HTTP_POLLING:Constants.SENSE;
  }

  @Override
  protected boolean shouldAskContentLength() {
    return !this.isPolling;
  }

  @Override
  public void sendReverseHeartbeat(ReverseHeartbeatRequest request, RequestTutor tutor) {
      request.addUnique();
      super.sendReverseHeartbeat(request, tutor);
  }
}
