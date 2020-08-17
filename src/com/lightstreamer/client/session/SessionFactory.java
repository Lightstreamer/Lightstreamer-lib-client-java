/*
 * Copyright (c) 2004-2015 Weswit s.r.l., Via Campanini, 6 - 20124 Milano, Italy.
 * All rights reserved.
 * www.lightstreamer.com
 *
 * This software is the confidential and proprietary information of
 * Weswit s.r.l.
 * You shall not disclose such Confidential Information and shall use it
 * only in accordance with the terms of the license agreement you entered
 * into with Weswit s.r.l.
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
