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

import com.lightstreamer.client.protocol.TextProtocol.StreamListener;
import com.lightstreamer.client.requests.BindSessionRequest;
import com.lightstreamer.client.transport.RequestHandle;
import com.lightstreamer.util.ListenableFuture;

/**
 * Encapsulates the transport (HTTP or WebSocket) and provides services such as batching, serialization, 
 * buffering if the transport is not ready,...
 * 
 * 
 * @since October 2016
 */
public interface RequestManager extends ControlRequestHandler {

    /**
     * Binds a session.
     * @param bindFuture a future which is fulfilled when the bind_session request is sent by the transport.
     */
    RequestHandle bindSession(BindSessionRequest request, StreamListener reqListener, long tcpConnectTimeout, long tcpReadTimeout, ListenableFuture bindFuture);
}
