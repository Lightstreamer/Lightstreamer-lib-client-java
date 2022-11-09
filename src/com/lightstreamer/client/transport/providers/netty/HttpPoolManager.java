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

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.lightstreamer.client.Constants;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.AbstractChannelPoolMap;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.channel.pool.ChannelPoolMap;
import io.netty.channel.pool.SimpleChannelPool;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.resolver.NoopAddressResolverGroup;
import io.netty.util.ThreadDeathWatcher;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.Future;

public class HttpPoolManager {

    private static final Logger log = LogManager.getLogger(Constants.NETTY_POOL_LOG);

    private final AtomicReference<ChannelPoolMapWrapper> poolMapRef = new AtomicReference<ChannelPoolMapWrapper>();

    private final AtomicInteger nioThreadCounter = new AtomicInteger(0);
    
    private final AtomicInteger poolWrapperCounter = new AtomicInteger(0);

    /**
     * Returns a socket pool map wrapper assuring that only one instance is created.
     */
    private ChannelPoolMapWrapper getPoolMapWrapper() {
        while (true) {
            ChannelPoolMapWrapper poolMapWrapper = poolMapRef.get();
            if (poolMapWrapper == null) {
                log.debug("Creating new HTTP channel pool map");
                ChannelPoolMapWrapper poolWrapperNew = new ChannelPoolMapWrapper();
                if (poolMapRef.compareAndSet(null, poolWrapperNew)) {
                    poolWrapperNew.init();
                    log.debug("HTTP channel pool map [" + poolWrapperCounter + "] created and initialized");
                    return poolWrapperNew;
                }
                log.debug("HTTP channel pool map already created and initialized");
                continue;
            }
            return poolMapWrapper;
        }
    }

    // thread-safe
    public void close() {
        log.debug("Starting shutting down NettySocketPool...");
        ChannelPoolMapWrapper currentPoolWrapper = poolMapRef.getAndSet(null);
        if (currentPoolWrapper != null) {
            currentPoolWrapper.shutdown();
            log.debug("NettySocketPool shutdown");
        } else {
            log.debug("No available ChannelPool at the moment");
        }
    }

    public Future<Channel> acquire(NettyFullAddress address) {
        SimpleChannelPool pool = getPoolMapWrapper().getPoolMap().get(address);
        return pool.acquire();
    }

    public void release(NettyFullAddress address, Channel ch) {
        ChannelPoolMapWrapper poolMap = poolMapRef.get();
        if (poolMap != null) {
            SimpleChannelPool pool = poolMap.getPoolMap().get(address);            
            pool.release(ch);
        }
    }
    
    public HttpChannelPool getChannelPool(NettyFullAddress address) {
        return getPoolMapWrapper().getPoolMap().get(address);
    }
    
    // TEST ONLY
    protected ChannelPoolHandler decorateChannelPoolHandler(ChannelPoolHandler handler) {
        return handler;
    }
    
    /*
     * Support classes
     */

    /**
     * Wraps a socket pool map and assures that it is properly initialized when returned by {@link #getPoolMap()}.
     */
    private class ChannelPoolMapWrapper {
    
        private volatile EventLoopGroup group;
        
        private final AtomicBoolean closing = new AtomicBoolean(false);
        
        private final AtomicBoolean initLock = new AtomicBoolean(true);
        
        private volatile AbstractChannelPoolMap<NettyFullAddress, HttpChannelPool> poolMap;
    
        /*
         * thread-safe spinlock: the lock is released by method init()
         */
        public ChannelPoolMap<NettyFullAddress, HttpChannelPool> getPoolMap() {
            while(initLock.getAndSet(true)) {
            }
            initLock.set(false);
            return poolMap;
        }
        
        void init() {
            poolWrapperCounter.incrementAndGet();
    
            group = new NioEventLoopGroup(0, new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "Netty Thread " + nioThreadCounter.incrementAndGet());
                    t.setDaemon(false);
                    return t;
                }
            });
            final Bootstrap cb = new Bootstrap();
            cb.group(group).channel(NioSocketChannel.class);
    
            poolMap = new HttpChannelPoolMap(cb);
            initLock.set(false);
        }
    
        void shutdown() {
            if (closing.compareAndSet(false, true)) {
                log.debug("Shutting down of ChannelPoolWrapper[" + poolWrapperCounter.get() + "]");
                try {
                    log.debug("Starting graceful group shutdown...");
                    this.group.shutdownGracefully();
                    
                    log.debug("Awaiting for group to terminate...");
                    this.group.awaitTermination(10, TimeUnit.SECONDS);
                    log.debug("Group terminated");
                    
                    log.debug("Waiting for global event executor to shut down...");
                    if (ThreadDeathWatcher.awaitInactivity(2, TimeUnit.SECONDS)) {
                        log.debug("Global event executor finished shutting down.");
                    } else {
                        log.debug("Global event executor failed to shut down.");
                    }
                } catch (InterruptedException e) {
                    log.error("Netty shutdown error", e);
                }
                
                FastThreadLocal.destroy();
                log.debug("FastThreadLocal destroyed");
            } else {
                log.debug("Pool already shutting down");
            }
        }
    }

    /**
     * A map of socket pools. Each pool is tied to a distinct server address.
     */
    private class HttpChannelPoolMap extends AbstractChannelPoolMap<NettyFullAddress, HttpChannelPool> {
        private final Bootstrap cb;
    
        private HttpChannelPoolMap(Bootstrap cb) {
            this.cb = cb;
        }
    
        @Override
        protected HttpChannelPool newPool(final NettyFullAddress key) {
            /*
             * When there is a proxy configured, the client must not try to resolve the server address 
             * because the server could be on a network unreachable to the client 
             */
            Bootstrap poolBootstrap = cb.clone();
            if (key.getProxy() != null) {
                poolBootstrap.resolver(NoopAddressResolverGroup.INSTANCE);
            }
            poolBootstrap.remoteAddress(key.getAddress());
            if (log.isDebugEnabled()) {
                log.debug("New HTTP channel pool created. Remote address: " + key.getAddress());
            }
            ChannelPoolHandler handler = decorateChannelPoolHandler(new HttpChannelPoolHandler(key));
            return new HttpChannelPool(key, poolBootstrap, handler);
        }
    }

    /**
     * Decorates the acquiring and releasing operations of the sockets.
     */
    private static class HttpChannelPoolHandler extends BaseChannelPoolHandler {
        private final NettyFullAddress key;
        
        private HttpChannelPoolHandler(NettyFullAddress key) {
            this.key = key;
        }
    
        @Override
        public void channelReleased(final Channel ch) throws Exception {
            super.channelReleased(ch);
            if (log.isDebugEnabled()) {                                    
                log.debug("HTTP channel released [" + ch.id() + "]");
            }
        }
    
        @Override
        public void channelAcquired(Channel ch) throws Exception {
            super.channelAcquired(ch);
            if (log.isDebugEnabled()) {                                    
                log.debug("HTTP channel acquired [" + ch.id() + "]");
            }
        }
    
        @Override
        public void channelCreated(Channel ch) throws Exception {
            super.channelCreated(ch);
            PipelineUtils.populateHttpPipeline(ch, key, new NettySocketHandler());
            if (log.isDebugEnabled()) {                                    
                log.debug("HTTP channel created [" + ch.id() + "]");
            }
        }
    }
    
    public static class HttpChannelPool extends SimpleChannelPool {

        private final Bootstrap bootstrap;
        private final NettyFullAddress remoteAddress;

        public HttpChannelPool(NettyFullAddress remoteAddress, Bootstrap bootstrap, ChannelPoolHandler handler) {
            super(bootstrap, handler);
            this.remoteAddress = remoteAddress;
            this.bootstrap = bootstrap;
        }

        public NettyFullAddress getRemoteAddress() {
            return remoteAddress;
        }
        
        public Bootstrap getBootstrap() {
            return bootstrap;
        }
    }
}