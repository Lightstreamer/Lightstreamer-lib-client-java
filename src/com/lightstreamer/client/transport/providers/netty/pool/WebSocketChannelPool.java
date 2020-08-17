package com.lightstreamer.client.transport.providers.netty.pool;

import com.lightstreamer.client.transport.providers.netty.HttpPoolManager.HttpChannelPool;

import io.netty.channel.Channel;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.util.concurrent.Future;

/**
 * A pool of WebSocket connections having the same {@link ExtendedNettyFullAddress} remote address.
 * 
 * 
 * @since August 2017
 */
public class WebSocketChannelPool extends ChildChannelPool {

    private final ExtendedNettyFullAddress address;

    public WebSocketChannelPool(HttpChannelPool parentPool, ExtendedNettyFullAddress address, ChannelPoolHandler handler) {
        super(parentPool.getBootstrap(), parentPool, handler);
        this.address = address;
        if (log.isDebugEnabled()) {
            log.debug("New WS channel pool created. Remote address: " + address.getAddress().getAddress());
        }
    }

    @Override
    protected ChannelUpgradeFuture connectChannel(Future<Channel> fc) {
        return new WebSocketChannelUpgradeFuture(fc, address);
    }
}