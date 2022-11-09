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


package com.lightstreamer.client.transport.providers.netty;

import java.io.IOException;

import com.lightstreamer.client.transport.providers.netty.pool.WebSocketPoolManager;
import com.lightstreamer.util.GlobalProperties;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;

/**
 * Utilities managing the pipeline of channels living in a pool where channel created to send HTTP request can be
 * converted to send WebSocket messages after the channel upgrade 
 * (see {@link HttpPoolManager} and {@link WebSocketPoolManager}).
 * <p>
 * The typical life-cycle of a channel is the following:
 * <ul>
 * <li>the channel is created to send a HTTP request and a HTTP user-defined handler is added to the pipeline 
 * (see {@link #populateHttpPipeline(Channel, NettyFullAddress, ChannelHandler)})</li>
 * <li>the channel is used and then released to its HTTP pool</li>
 * <li>the channel is acquired from the pool and upgraded to WebSocket (see {@link #populateWSPipelineForHandshake(Channel, ChannelHandler)})</li>
 * <li>when the handshake is complete, the WebSocket user-defined handler is added to the pipeline (see {@link #populateWSPipeline(Channel, ChannelHandler)})</li>
 * <li>the channel is used and then released to its WebSocket pool</li>
 * </ul>
 * 
 * 
 * @since September 2017
 */
public class PipelineUtils {
    
    /**
     * Name of the channel handler in a pipeline reading TLCP incoming messages.
     */
    private static final String READER_KEY = "reader";

    /**
     * Gets the channel handler reading TLCP incoming messages.
     */
    public static ChannelHandler getChannelHandler(Channel ch) {
        return ch.pipeline().get(READER_KEY);
    }

    /**
     * Populates the channel pipeline in order to read data from a HTTP connection. 
     */
    public static void populateHttpPipeline(Channel ch, NettyFullAddress remoteAddress, ChannelHandler httpChHandler) throws IOException {
        ProxyHandler proxy = remoteAddress.getProxy();
        if (proxy != null) {
            ch.pipeline().addLast("proxy", proxy);
        }

        SslContext sslCtx = null;
        if (remoteAddress.isSecure()) {
            SslContextBuilder builder = SslContextBuilder.forClient();
            builder.sslProvider(SslProvider.JDK);
            if (GlobalProperties.INSTANCE.getTrustManagerFactory() != null) {
                builder.trustManager(GlobalProperties.INSTANCE.getTrustManagerFactory());
            }
            sslCtx = builder.build();

            ch.pipeline().addLast("ssl", sslCtx.newHandler(ch.alloc(),
                remoteAddress.getHost(), remoteAddress.getPort()));
        }

        ch.pipeline().addLast("http", new HttpClientCodec());
        ch.pipeline().addLast(READER_KEY, httpChHandler);
    }
    
    /**
     * Populates the channel pipeline in order to upgrade a connection to WebSocket.
     */
    public static void populateWSPipelineForHandshake(Channel ch, ChannelHandler wsHandshakeHandler) {
        ChannelPipeline p = ch.pipeline();
        /*
         * NB since the channel pipeline was filled by populateHttpPipeline(), 
         * we must remove the HTTP user-defined handler before of upgrading the channel
         */
        p.remove(READER_KEY);
        p.addLast(new HttpObjectAggregator(8192));
        p.addLast(WebSocketClientCompressionHandler.INSTANCE);
        p.addLast(wsHandshakeHandler);
    }
    
    /**
     * Populates the channel pipeline in order to read data from a WebSocket connection. 
     */
    public static void populateWSPipeline(Channel ch, ChannelHandler wsChHandler) {
        ChannelPipeline chPipeline = ch.pipeline();
        ChannelHandler reader = chPipeline.get(READER_KEY);
        if (reader == null) {
            /*
             * there is no reader when the WebSocket channel is fresh, 
             * i.e the channel was filled by populateWSPipelineForHandshake()
             */
            chPipeline.addLast(READER_KEY, wsChHandler);
        } else {
            /*
             * the old reader is the WebSocket channel handler used before the channel was released to the pool,
             * i.e the channel was already filled by populateWSPipeline()
             */
            chPipeline.replace(READER_KEY, READER_KEY, wsChHandler);
        }
    }
}
