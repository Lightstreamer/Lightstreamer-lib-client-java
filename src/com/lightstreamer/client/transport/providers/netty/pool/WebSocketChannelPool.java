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