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

import java.util.concurrent.atomic.AtomicInteger;

import com.lightstreamer.client.protocol.Protocol;
import com.lightstreamer.client.protocol.TextProtocolHttp;
import com.lightstreamer.client.protocol.TextProtocolWS;
import com.lightstreamer.client.transport.Http;
import com.lightstreamer.client.transport.providers.HttpProvider;
import com.lightstreamer.client.transport.providers.TransportFactory;

/**
 * 
 */
public class SessionFactory {
    
  private static final AtomicInteger objectIdGenerator = new AtomicInteger();

  protected Session createNewSession(boolean isPolling,
      boolean isComboForced, boolean isHTTP, Session prevSession,
      SessionListener listener, SubscriptionsListener subscriptions, MessagesListener messages,
      SessionThread sessionThread, 
      InternalConnectionDetails details, InternalConnectionOptions options, int handlerPhase,
      boolean retryAgainIfStreamFails, boolean sessionRecovery) {
    
    int objectId = objectIdGenerator.incrementAndGet();
    HttpProvider httpProvider = TransportFactory.getDefaultHttpFactory().getInstance(sessionThread);
    Http httpTransport = new Http(sessionThread, httpProvider);
      
    if (isHTTP) {
      Protocol txt = new TextProtocolHttp(objectId, sessionThread, options, httpTransport);
      SessionHTTP sessionHTTP = new SessionHTTP(objectId, isPolling, isComboForced,
          listener, subscriptions, messages,
          prevSession, sessionThread, txt,
          details, options, handlerPhase,
          retryAgainIfStreamFails, sessionRecovery);
      return sessionHTTP;
      
    } else {
        
      Protocol ws = new TextProtocolWS(objectId, sessionThread, options, details, httpTransport);
      return new SessionWS(objectId, isPolling, isComboForced,
          listener, subscriptions, messages,
          prevSession, sessionThread, ws,
          details, options, handlerPhase,
          retryAgainIfStreamFails, sessionRecovery);
    }
  }
  
  
}
