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

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;

import com.lightstreamer.client.Constants;
import com.lightstreamer.client.transport.providers.CookieHelper;
import com.lightstreamer.client.transport.providers.netty.NettyFullAddress;
import com.lightstreamer.client.transport.providers.netty.PipelineUtils;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;
import com.lightstreamer.util.LsUtils;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

/**
 * Result of an asynchronous operation of upgrading a channel to WebSocket.
 * 
 * 
 * @since November 2017
 */
public class WebSocketChannelUpgradeFuture implements ChannelUpgradeFuture {
    
    private static final Logger log = LogManager.getLogger(Constants.NETTY_POOL_LOG);
    
    private final StateMachine machine = new StateMachine();
    
    /**
     * Upgrades the given channel.
     * 
     * @param channelFuture the channel to upgrade
     * @param address the address to which the channel connects 
     */
    public WebSocketChannelUpgradeFuture(final Future<Channel> channelFuture, final ExtendedNettyFullAddress address) {
        /* 
         * wait for the channel connection
         */
        channelFuture.addListener(new FutureListener<Channel>() {
            
            @Override
            public void operationComplete(Future<Channel> channelFutureResp) throws Exception {
                assert channelFuture == channelFutureResp;
                if (channelFuture.isSuccess()) {
                    /* 
                     * channel is connected 
                     */
                    final Channel ch = channelFuture.getNow();
                    machine.setChannel(ch, Phase.CONNECTION_OK);
                    final ChannelFuture upgradeFuture = upgrade(ch, address);
                    /* 
                     * wait for the upgrade of the channel
                     */
                    upgradeFuture.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture upgradeFutureResp) throws Exception {
                            assert upgradeFutureResp == upgradeFuture;
                            if (upgradeFuture.isSuccess()) {
                                /* 
                                 * channel is upgraded 
                                 */
                                assert upgradeFuture.channel() == ch;
                                machine.next(Phase.UPGRADE_OK);

                            } else {
                                /*
                                 * channel upgrade error
                                 */
                                machine.setErrorCause(upgradeFuture.cause(), Phase.UPGRADE_FAILURE);
                            }
                        } // operationComplete of channel upgrade
                    });

                } else {
                    /*
                     * channel connection error
                     */
                    machine.setErrorCause(channelFuture.cause(), Phase.CONNECTION_FAILURE);
                }
            } // operationComplete of channel connection
        });
    }
    
    /**
     * Upgrade the channel to WebSocket.
     */
    private ChannelFuture upgrade(final Channel ch, final ExtendedNettyFullAddress address) {
        /*
         * ========================================= NB ===================================================
         * Operations on the channel must happen in the thread associated with the channel.
         * Otherwise subtle bugs can appear. 
         * For example the method WebSocketClientHandshaker.finishHandshake can return before
         * the method WebSocketClientHandshaker.handshake returns leaving the channel pipeline in a mess.
         * ================================================================================================
         */
        final ChannelPromise handshakeFuture = ch.newPromise();
        ch.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                /*
                 * If the eventLoop is overloaded, when this task is executed the channel can be broken
                 * (for example because the server has closed it). 
                 * So the first thing to do is to check if the channel is healthy.   
                 */
                if (ch.isActive()) {
                    /* set cookies and extra headers */
                    String cookies = address.getCookies();
                    Map<String, String> extraHeaders = address.getExtraHeaders();
                    DefaultHttpHeaders customHeaders = new DefaultHttpHeaders();
                    if (extraHeaders != null) {
                        for (Entry<String, String> entry : extraHeaders.entrySet()) {
                            customHeaders.add(entry.getKey(), entry.getValue());
                        }
                    }
                    if (cookies != null && ! cookies.isEmpty()) {
                        customHeaders.set(HttpHeaderNames.COOKIE, cookies);
                    }
                    /* build url */
                    NettyFullAddress remoteAddress = address.getAddress();
                    String scheme = remoteAddress.isSecure() ? "wss" : "ws";
                    String host = remoteAddress.getHost();
                    int port = remoteAddress.getPort();
                    String url = scheme + "://" + host + ":" + port + "/lightstreamer";
                    URI uri = LsUtils.uri(url);
                    String subprotocol = Constants.TLCP_VERSION + ".lightstreamer.com";
                    /* build pipeline */
                    WebSocketHandshakeHandler wsHandshakeHandler = new WebSocketHandshakeHandler(uri, subprotocol, customHeaders);
                    PipelineUtils.populateWSPipelineForHandshake(ch, wsHandshakeHandler);
                    /* WS handshake */
                    wsHandshakeHandler.handshake(ch, handshakeFuture);
                    
                } else {
                    handshakeFuture.tryFailure(new IOException("Channel " + ch.id() + " is broken"));
                }
            }
        });
        return handshakeFuture;
    }

    @Override
    public boolean isDone() {
        return machine.isDone();
    }

    @Override
    public boolean isSuccess() {
        return machine.isSuccess();
    }

    @Override
    public void addListener(ChannelUpgradeFutureListener fl) {
        machine.addListener(fl);
    }

    @Override
    public Channel channel() {
        return machine.getChannel();
    }

    @Override
    public Throwable cause() {
        return machine.getCause();
    }
    
    /*
     * ===============
     * Support classes
     * ===============
     */
    
    @Immutable
    private enum Phase {
        
        CONNECTING(false, false),
        CONNECTION_OK(false, false),
        CONNECTION_FAILURE(true, false),
        UPGRADE_OK(true, true),
        UPGRADE_FAILURE(true, false);

        final boolean isDone;
        final boolean isSuccess;

        /**
         * A phase in the process of upgrading a channel.
         * 
         * @param isDone true if and only if {@link ChannelUpgradeFuture#isDone()} is true
         * @param isSuccess true if and only if {@link ChannelUpgradeFuture#isSuccess()} is true
         */
        Phase(boolean isDone, boolean isSuccess) {
            this.isDone = isDone;
            this.isSuccess = isSuccess;
        }

        @Override
        public String toString() {
            return name();
        }
    } // Phase
    
    @ThreadSafe
    private class StateMachine {
        
        private Phase phase = Phase.CONNECTING;
        private ChannelUpgradeFutureListener listener;
        private Channel channel;
        private Throwable cause;
        
        /**
         * Changes the future state. 
         * Fire the listener if the phase is final.
         */
        synchronized void next(Phase target) {
            if (log.isDebugEnabled()) {
                Object chId = channel != null ? channel.id() : "";
                log.debug("ChannelUpgradeFuture state change [" + chId + "]: " + phase + " -> " + target);
            }
            phase = target;
            if (target.isDone && listener != null) {
                listener.operationComplete(WebSocketChannelUpgradeFuture.this);
            }
        }
        
        synchronized boolean isDone() {
            return phase.isDone;
        }

        synchronized boolean isSuccess() {
            return phase.isSuccess;
        }

        synchronized void addListener(ChannelUpgradeFutureListener fl) {
            assert listener == null;
            listener = fl;
            if (phase.isDone) {
                listener.operationComplete(WebSocketChannelUpgradeFuture.this);
            }
        }

        synchronized Channel getChannel() {
            assert phase.isDone && phase.isSuccess;
            return channel;
        }

        synchronized void setChannel(Channel ch, Phase target) {
            channel = ch;
            next(target);
        }
        
        synchronized Throwable getCause() {
            assert phase.isDone && !phase.isSuccess;
            return cause;
        }

        synchronized void setErrorCause(Throwable ex, Phase target) {
            cause = ex;
            next(target);
        }
    } // StateMachine
    
    /**
     * Upgrades a HTTP request to WebSocket.<br>
     * The code was adapted from <a href="http://netty.io/4.1/xref/io/netty/example/http/websocketx/client/package-summary.html">this Netty example</a>. 
     */
    private static class WebSocketHandshakeHandler extends SimpleChannelInboundHandler<Object> {
        
        private static final Logger logStream = LogManager.getLogger(Constants.TRANSPORT_LOG);
        
        private final WebSocketClientHandshaker handshaker;
        private ChannelPromise handshakeFuture;

        public WebSocketHandshakeHandler(URI uri, String subprotocol, HttpHeaders headers) {
            this.handshaker = WebSocketClientHandshakerFactory.newHandshaker(uri, WebSocketVersion.V13, subprotocol, true, headers);
        }

        /**
         * Starts the handshake protocol.
         */
        public ChannelFuture handshake(final Channel ch, ChannelPromise promise) {
            if (log.isDebugEnabled()) {
                log.debug("WS channel handshake [" + ch.id() + "]");
            }
            handshakeFuture = promise;
            handshaker.handshake(ch).addListener(new ChannelFutureListener() {
                
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (! future.isSuccess()) {
                        log.error("WS channel handshake failed [" + ch.id() + "]", future.cause());
                        handshakeFuture.tryFailure(future.cause());
                    }
                }
            });
            return handshakeFuture;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            if (log.isDebugEnabled()) {
                Channel ch = ctx.channel();
                log.debug("WS handshaker active [" + ch.id() + "]");
            }
            ctx.fireChannelActive();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (! handshakeFuture.isDone()) {
                IOException cause = new IOException("WS handshake failed [" + ctx.channel().id() + "]");
                handshakeFuture.tryFailure(cause);
            }
            ctx.close();
            if (log.isDebugEnabled()) {
                log.debug("WS channel inactive [" + ctx.channel().id() + "]");
            }
            ctx.fireChannelInactive();
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            Channel ch = ctx.channel();
            if (!handshaker.isHandshakeComplete()) {
                FullHttpResponse resp = (FullHttpResponse) msg;
                handshaker.finishHandshake(ch, resp);
                handshakeFuture.setSuccess();
                /* save cookies */
                for (String cookie : resp.headers().getAll(HttpHeaderNames.SET_COOKIE)) {
                    CookieHelper.saveCookies(handshaker.uri(), cookie);
                }
                return;
            }

            if (msg instanceof FullHttpResponse) {
                FullHttpResponse response = (FullHttpResponse) msg;
                throw new IllegalStateException(
                        "Unexpected FullHttpResponse (getStatus=" + response.status() + ", content=" + response.content().toString(CharsetUtil.UTF_8) + ")");
            }

            WebSocketFrame frame = (WebSocketFrame) msg;
            if (frame instanceof TextWebSocketFrame) {
                TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
                if (logStream.isDebugEnabled()) {
                    logStream.debug("WS transport receiving [" + ch.id() + "]:\n" + textFrame.text());
                }
                ctx.fireChannelRead(textFrame.content().retain());
                
            } else if (frame instanceof ContinuationWebSocketFrame) {
                final ContinuationWebSocketFrame textFrame = (ContinuationWebSocketFrame) frame;
                if (logStream.isDebugEnabled()) {
                    logStream.debug("WS transport receiving [" + ch.id() + "]:\n" + textFrame.text());
                }
                ctx.fireChannelRead(textFrame.content().retain());
                
            } else if (frame instanceof PongWebSocketFrame) {
                log.debug("WS received pong");
                
            } else if (frame instanceof CloseWebSocketFrame) {
                log.debug("WS received close [" + ch.id() + "]");
                ch.close();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (! handshakeFuture.isDone()) {
                handshakeFuture.tryFailure(cause);
            }
            ctx.close();
            log.debug("WS closed [" + ctx.channel().id() + "]");
        }
    } // WebSocketHandshakeHandler
}
