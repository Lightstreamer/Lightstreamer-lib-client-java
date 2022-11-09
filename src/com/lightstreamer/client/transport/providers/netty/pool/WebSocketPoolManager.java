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

import java.io.Closeable;

import com.lightstreamer.client.Constants;
import com.lightstreamer.client.transport.providers.netty.BaseChannelPoolHandler;
import com.lightstreamer.client.transport.providers.netty.HttpPoolManager;
import com.lightstreamer.client.transport.providers.netty.HttpPoolManager.HttpChannelPool;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;

import io.netty.channel.Channel;
import io.netty.channel.pool.AbstractChannelPoolMap;
import io.netty.channel.pool.ChannelPool;
import io.netty.channel.pool.ChannelPoolHandler;

/**
 * A channel pool sharing WebSocket connections.
 * 
 * 
 * @since August 2017
 */
public class WebSocketPoolManager implements Closeable {
    
    private static final Logger log = LogManager.getLogger(Constants.NETTY_POOL_LOG);

    private final AbstractChannelPoolMap<ExtendedNettyFullAddress, WebSocketChannelPool> poolMap;
    
    public WebSocketPoolManager(final HttpPoolManager httpPoolMap) {
        log.debug("Creating new WS channel pool map");
        this.poolMap = new AbstractChannelPoolMap<ExtendedNettyFullAddress, WebSocketChannelPool>() {

            @Override
            protected WebSocketChannelPool newPool(ExtendedNettyFullAddress key) {
                HttpChannelPool httpPool = httpPoolMap.getChannelPool(key.getAddress());
                ChannelPoolHandler wsPoolHandler = decorateChannelPoolHandler(new WebSocketChannelPoolHandler());
                WebSocketChannelPool wsPool = new WebSocketChannelPool(httpPool, key, wsPoolHandler);
                return wsPool;
            }
        };
    }
    
    /**
     * Gets a channel from the pool.
     */
    public ChannelPool get(ExtendedNettyFullAddress addr) {
        return poolMap.get(addr);
    }
    
    @Override
    public void close() {
        poolMap.close();
    }
    
    // TEST ONLY
    protected ChannelPoolHandler decorateChannelPoolHandler(ChannelPoolHandler handler) {
        return handler;
    }
    
    /**
     * Handler which is called by the pool manager when a channel is acquired or released.
     */
    private static class WebSocketChannelPoolHandler extends BaseChannelPoolHandler {

        @Override
        public void channelReleased(Channel ch) throws Exception {
            super.channelReleased(ch);
            if (log.isDebugEnabled()) {                                    
                log.debug("WS channel released [" + ch.id() + "]");
            }
        }

        @Override
        public void channelAcquired(Channel ch) throws Exception {
            super.channelAcquired(ch);
            if (log.isDebugEnabled()) {                                    
                log.debug("WS channel acquired [" + ch.id() + "]");
            }
        }

        @Override
        public void channelCreated(Channel ch) throws Exception {
            super.channelCreated(ch);
            if (log.isDebugEnabled()) {                                    
                log.debug("WS channel created [" + ch.id() + "]");
            }
        }
    } // end WebSocketChannelPoolHandler
}
