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

import com.lightstreamer.client.protocol.ControlResponseParser.ERRORParser;
import com.lightstreamer.client.protocol.ControlResponseParser.ParsingException;
import com.lightstreamer.client.protocol.ControlResponseParser.REQERRParser;
import com.lightstreamer.client.protocol.ControlResponseParser.REQOKParser;
import com.lightstreamer.client.requests.DestroyRequest;
import com.lightstreamer.client.requests.LightstreamerRequest;
import com.lightstreamer.client.requests.RequestTutor;
import com.lightstreamer.client.session.InternalConnectionDetails;
import com.lightstreamer.client.session.InternalConnectionOptions;
import com.lightstreamer.client.session.SessionThread;
import com.lightstreamer.client.transport.Http;
import com.lightstreamer.client.transport.RequestListener;
import com.lightstreamer.util.Assertions;
import com.lightstreamer.util.ListenableFuture;

/**
 * 
 */
public class TextProtocolWS extends TextProtocol {
    
    private final WebSocketRequestManager wsRequestManager;

    public TextProtocolWS(int objectId, SessionThread thread, InternalConnectionOptions options, InternalConnectionDetails details, Http httpTransport) {
        super(objectId, thread, options, httpTransport);
        wsRequestManager = new WebSocketRequestManager(thread, this, options);
    }
    
    @Override
    public RequestManager getRequestManager() {
        return this.wsRequestManager;
    }
    
    @Override
    public ListenableFuture openWebSocketConnection(String serverAddress) {
        return wsRequestManager.openWS(this, serverAddress, new BindSessionListener());
    }

    @Override
    public void sendControlRequest(LightstreamerRequest request, RequestTutor tutor, final RequestListener reqListener) {
        wsRequestManager.addRequest(request, tutor, reqListener);
    }

    @Override
    public void processREQOK(final String message) {
        assert Assertions.isSessionThread();
        try {
            REQOKParser parser = new REQOKParser(message);
            RequestListener reqListener = wsRequestManager.getAndRemoveRequestListener(parser.getRequestId());
            if (reqListener == null) {
                /* discard the response of a request made outside of the current session */
                log.warn("Acknowledgement discarded: " + message);
            } else {
                // notify the request listener (NB we are on SessionThread)
                reqListener.onMessage(message);
                reqListener.onClosed();            
            }
            
        } catch (ParsingException e) {
            onIllegalMessage(e.getMessage());
        }
    }
    
    @Override
    public void processREQERR(final String message) {
        assert Assertions.isSessionThread();
        try {
            REQERRParser parser = new REQERRParser(message);
            RequestListener reqListener = wsRequestManager.getAndRemoveRequestListener(parser.requestId);
            if (reqListener == null) {
                /* discard the response of a request made outside of the current session */
                log.warn("Acknowledgement discarded: " + message);
            } else {
                // notify the request listener (NB we are on SessionThread)
                reqListener.onMessage(message);
                reqListener.onClosed();            
            }
            
        } catch (ParsingException e) {
            onIllegalMessage(e.getMessage());
        }
    }

    @Override
    public void processERROR(final String message) {
        // the error is a serious one and we cannot identify the related request;
        // we can't but close the whole session
        log.error("Closing the session because of unexpected error: " + message);
        try {
            ERRORParser parser = new ERRORParser(message);
            forwardERROR(parser.errorCode, parser.errorMsg);
            
        } catch (ParsingException e) {
            onIllegalMessage(e.getMessage());
        }
    }

    @Override
    public void stop(boolean waitPendingControlRequests,boolean forceConnectionClose) {
      super.stop(waitPendingControlRequests, forceConnectionClose);
      httpRequestManager.close(waitPendingControlRequests);
      wsRequestManager.close(waitPendingControlRequests);
    }
    
    @Override
    protected void onBindSessionForTheSakeOfReverseHeartbeat() {
        reverseHeartbeatTimer.onBindSession(true);
    }
    
    @Override
    public void setDefaultSessionId(String sessionId) {
        wsRequestManager.setDefaultSessionId(sessionId);
    }

    @Override
    protected void forwardDestroyRequest(DestroyRequest request, RequestTutor tutor, RequestListener reqListener) {
        wsRequestManager.addRequest(request, tutor, reqListener);
    }
}
