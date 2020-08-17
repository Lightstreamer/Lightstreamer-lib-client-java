package com.lightstreamer.client.transport.providers.netty;

import com.lightstreamer.client.session.SessionThread;
import com.lightstreamer.client.transport.providers.TransportFactory;
import com.lightstreamer.client.transport.providers.WebSocketProvider;

public class WebSocketProviderFactory extends TransportFactory<WebSocketProvider> {

    @Override
    public WebSocketProvider getInstance(SessionThread thread) {
        return new NettyWebSocketProvider();
    }

    @Override
    public boolean isResponseBuffered() {
        return false;
    }
}
