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

import java.net.URI;
import java.util.Map;
import java.util.regex.Matcher;

import com.lightstreamer.client.Constants;
import com.lightstreamer.client.Proxy;
import com.lightstreamer.client.protocol.TextProtocol;
import com.lightstreamer.client.transport.RequestListener;
import com.lightstreamer.client.transport.SessionRequestListener;
import com.lightstreamer.client.transport.providers.WebSocketProvider;
import com.lightstreamer.client.transport.providers.netty.pool.ExtendedNettyFullAddress;
import com.lightstreamer.client.transport.providers.netty.pool.WebSocketChannelPool;
import com.lightstreamer.client.transport.providers.netty.pool.WebSocketPoolManager;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;
import com.lightstreamer.util.LsUtils;
import com.lightstreamer.util.threads.ThreadShutdownHook;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.pool.ChannelPool;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

/**
 * WebSocket client based on Netty.
 * The implementation is modeled after <a href="https://github.com/netty/netty/tree/4.1/example/src/main/java/io/netty/example/http/websocketx/client">
 * this example</a>.
 * <br>
 * This class notifies a {@link SessionRequestListener} when the following events happen:
 * <ul>
 * <li>onOpen: fires when the connection is established and the WebSocket handshake is complete</li>
 * <li>onMessage: fires when a new text frame is received</li>
 * <li>onClosed: fires when the connection is closed</li>
 * <li>onBroken: fires when there is an error.</li>
 * </ul>
 * <p>
 * <b>NB1</b>
 * The current implementation allows the sending of cookies in the handshake request but doesn't
 * support the setting of cookies in the handshake response. A contrived solution is explained
 * <a href="http://stackoverflow.com/questions/38306203/netty-set-cookie-in-websocket-handshake">here</a>. 
 * <p>
 * <b>NB2</b>
 * The actual implementation limits to 64Kb the maximum frame size. 
 * This is not a problem because the Lightstreamer server sends frames whose size is at most 8Kb. 
 * The limit can be modified specifying a different size at the creation of the {@link WebSocketClientHandshaker}
 * (see the method {@link WebSocketClientHandshakerFactory#newHandshaker(URI, WebSocketVersion, String, boolean, io.netty.handler.codec.http.HttpHeaders, int)}
 * in the inner class {@code WebSocketHandshakeHandler} of {@link WebSocketChannelPool}).
 * 
 * 
 * @since August 2017
 */
public class NettyWebSocketProvider implements WebSocketProvider {
    
    private static final Logger log = LogManager.getLogger(Constants.NETTY_LOG);
    private static final Logger logStream = LogManager.getLogger(Constants.TRANSPORT_LOG);
    private static final Logger logPool = LogManager.getLogger(Constants.NETTY_POOL_LOG);
    
    private final WebSocketPoolManager wsPoolManager;

    private volatile MyChannel channel;
   
    public NettyWebSocketProvider() {
        this.wsPoolManager = SingletonFactory.instance.getWsPool();
    }
    
    // TEST ONLY
    public NettyWebSocketProvider(WebSocketPoolManager channelPool) {
        this.wsPoolManager = channelPool;
    }

    @Override
    public void connect(final String address, final SessionRequestListener networkListener, Map<String, String> extraHeaders, String cookies, Proxy proxy) {
        URI uri = LsUtils.uri(address);
        String host = uri.getHost();
        int port = LsUtils.port(uri);
        boolean secure = LsUtils.isSSL(uri);
        NettyFullAddress remoteAddress = new NettyFullAddress(secure, host, port, proxy);
        ExtendedNettyFullAddress extendedRemoteAddress = new ExtendedNettyFullAddress(remoteAddress, extraHeaders, cookies);
        
        final ChannelPool wsPool = wsPoolManager.get(extendedRemoteAddress);
        Future<Channel> futureCh = wsPool.acquire();
        futureCh.addListener(new FutureListener<Channel>() {

            @Override
            public void operationComplete(Future<Channel> futureChResp) throws Exception {
                if (futureChResp.isSuccess()) {
                    Channel ch = futureChResp.getNow();
                    assert ch != null;
                    channel = new MyChannel(ch, wsPool, networkListener);
                    WebSocketChannelHandler chHandler = new WebSocketChannelHandler(networkListener, channel);
                    PipelineUtils.populateWSPipeline(ch, chHandler);
                    
                } else {
                    if (futureChResp.getNow() != null) {
                        futureChResp.getNow().close();
                    }
                    log.error("WebSocket handshake error", futureChResp.cause());
                    networkListener.onBroken();
                }
            }
        });
    }

    @Override
    public void send(String message, RequestListener listener) {
        if (logStream.isDebugEnabled()) {
            logStream.debug("WS transport sending [" + channel + "]: " + message);
        }
        channel.write(message, listener);
    }

    @Override
    public void disconnect() {
        if (logPool.isDebugEnabled()) {
            logPool.debug("WS disconnect [" + channel + "]");
        }
        if (channel != null) {
            channel.close();
            channel = null;
        }
    }

    @Override
    public ThreadShutdownHook getThreadShutdownHook() {
        return null; // nothing to do
    }
    
    /**
     * Netty channel wrapper.
     * <p>
     * <b>NB</b> The class is synchronized because its methods are called from both session thread and Netty thread.
     */
    private static class MyChannel {
        
        private final Channel ch;
        private final ChannelPool pool;
        private final SessionRequestListener networkListener;
        private boolean closed = false;
        private boolean released = false;

        public MyChannel(Channel ch, ChannelPool pool, SessionRequestListener networkListener) {
            this.ch = ch;
            this.pool = pool;
            this.networkListener = networkListener;
        }
        
        public synchronized void write(final String message, final RequestListener listener) {
            if (closed || released) {
                log.warn("Message discarded because the channel [" + ch.id() + "] is closed: " + message);
                return;
            }
            if (listener != null) {
                /*
                 * NB 
                 * I moved the onOpen call outside of operationComplete write callback 
                 * because sometimes the write callback of a request was fired after
                 * the read callback of the response.
                 * The effect of calling onOpen after the method onMessage/onClosed of a RequestListener
                 * was the retransmission of the request.
                 * Probably this behavior was caused by the tests running on localhost, but who knows?
                 */
                listener.onOpen();
            }
            ChannelFuture chf = ch.writeAndFlush(new TextWebSocketFrame(message));
            chf.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (! future.isSuccess()) {
                        onBroken(message, future.cause());                        
                    }
                }
            });
        }
        
        /**
         * Releases the channel to its pool.
         */
        public synchronized void release() {
            if (! closed) {                
                if (! released) {
                    /*
                     * NB 
                     * It seems that Netty closes a channel if it is released twice!
                     */
                    released = true;
                    pool.release(ch);
                }
            }
        }
        
        /**
         * Closes the channel if it has not been released yet.
         */
        public synchronized void close() {
            if (! released) {
                if (! closed) {
                    if (logPool.isDebugEnabled()) {
                        logPool.debug("WS channel closed [" + ch.id() + "]");
                    }
                    closed = true;
                    ch.close();
                    // NB putting a closed channel in the pool has no bad effect and further completes its life cycle
                    pool.release(ch);
                }
            }
        }
        
        public synchronized void onBroken(String message, Throwable cause) {
            assert ch.eventLoop().inEventLoop();
            log.error("Websocket write failed [" + ch.id() + "]: " + message, cause);
            close();
            networkListener.onBroken();
        }
        
        @Override
        public synchronized String toString() {
            return "" + ch.id();
        }
    }
    
    /**
     * Parses the messages coming from a channel and forwards them to the corresponding {@link RequestListener}. 
     */
    private static class WebSocketChannelHandler extends SimpleChannelInboundHandler<ByteBuf> {

        private final LineAssembler lineAssembler;
        private final RequestListenerDecorator reqListenerDecorator;

        public WebSocketChannelHandler(RequestListener networkListener, MyChannel ch) {
            this.reqListenerDecorator = new RequestListenerDecorator(networkListener, ch);
            this.lineAssembler = new LineAssembler(reqListenerDecorator);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            lineAssembler.readBytes(msg);
        }
        
        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            if (log.isDebugEnabled()) {
                Channel ch = ctx.channel();
                log.debug("WebSocket handler added [" + ch.id() + "]");
            }
            reqListenerDecorator.onOpen();
        }
        
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            if (log.isDebugEnabled()) {
                Channel ch = ctx.channel();
                log.debug("WebSocket active [" + ch.id() + "]");
            }
            ctx.fireChannelActive();
        }
        
        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (log.isDebugEnabled()) {
                log.debug("WebSocket disconnected [" + ctx.channel().id() + "]");
            }
            reqListenerDecorator.onClosed();
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (log.isDebugEnabled()) {
                log.error("WebSocket error [" + ctx.channel().id() + "]", cause);
            }
            reqListenerDecorator.onBroken();
        }
    } // end WebSocketChannelHandler
    
    /**
     * A {@link RequestListener} which releases the connection to its pool when the method {@code onMessage} encounters
     * the message {@code LOOP} or {@code END}. 
     */
    private static class RequestListenerDecorator implements RequestListener {
        
        private final RequestListener listener;
        private final MyChannel ch;
        
        public RequestListenerDecorator(RequestListener listener, MyChannel ch) {
            this.listener = listener;
            this.ch = ch;
        }

        @Override
        public void onMessage(String message) {
            listener.onMessage(message);
            Matcher mLoop = TextProtocol.LOOP_REGEX.matcher(message);
            Matcher mEnd = TextProtocol.END_REGEX.matcher(message);
            if (mLoop.matches()) {
                ch.release();
            } else if (mEnd.matches()) {
                ch.close();
            }
        }

        @Override
        public void onOpen() {
            listener.onOpen();
        }

        @Override
        public void onClosed() {
            listener.onClosed();
        }

        @Override
        public void onBroken() {
            listener.onBroken();
        }
    } // end RequestListenerDecorator
}
