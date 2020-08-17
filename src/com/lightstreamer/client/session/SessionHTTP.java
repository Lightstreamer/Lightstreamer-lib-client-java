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
