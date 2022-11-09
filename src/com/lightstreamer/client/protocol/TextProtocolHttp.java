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

import com.lightstreamer.client.requests.DestroyRequest;
import com.lightstreamer.client.requests.LightstreamerRequest;
import com.lightstreamer.client.requests.RequestTutor;
import com.lightstreamer.client.session.InternalConnectionOptions;
import com.lightstreamer.client.session.SessionThread;
import com.lightstreamer.client.transport.Http;
import com.lightstreamer.client.transport.RequestListener;
import com.lightstreamer.util.ListenableFuture;

public class TextProtocolHttp extends TextProtocol {
    
    public TextProtocolHttp(int objectId, SessionThread thread, InternalConnectionOptions options, Http httpTransport) {
        super(objectId, thread, options, httpTransport);
    }
    
    @Override
    public RequestManager getRequestManager() {
        return this.httpRequestManager;
    }
    
    @Override
    public void sendControlRequest(LightstreamerRequest request, RequestTutor tutor, RequestListener reqListener) {
        httpRequestManager.addRequest(request, tutor, reqListener);
    }

    @Override
    public void processREQOK(String message) {
        assert false;
    }
    
    @Override
    public void processREQERR(String message) {
        assert false;
    }

    @Override
    public void processERROR(String message) {
        assert false;
    }
    
    @Override
    public void stop(boolean waitPendingControlRequests,boolean forceConnectionClose) {
        super.stop(waitPendingControlRequests, forceConnectionClose);
        this.httpRequestManager.close(waitPendingControlRequests);
    }
    
    @Override
    public ListenableFuture openWebSocketConnection(String serverAddress) {
        // should never be called (the actual implementation is in TextProtocolWS)
        assert false;
        return ListenableFuture.rejected();
    }
    
    @Override
    protected void onBindSessionForTheSakeOfReverseHeartbeat() {
        reverseHeartbeatTimer.onBindSession(false);
    }
    
    @Override
    public void setDefaultSessionId(String sessionId) {
        // http connections don't have a default session id
    }

    @Override
    protected void forwardDestroyRequest(DestroyRequest request, RequestTutor tutor, RequestListener reqListener) {
        // don't send destroy request when transport is http
    }
}
