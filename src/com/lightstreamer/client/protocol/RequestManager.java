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
