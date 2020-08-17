/*
 * Copyright (c) 2004-2015 Weswit s.r.l., Via Campanini, 6 - 20124 Milano, Italy.
 * All rights reserved.
 * www.lightstreamer.com
 *
 * This software is the confidential and proprietary information of
 * Weswit s.r.l.
 * You shall not disclose such Confidential Information and shall use it
 * only in accordance with the terms of the license agreement you entered
 * into with Weswit s.r.l.
 */
package com.lightstreamer.client.transport.providers;

import com.lightstreamer.client.session.SessionThread;
import com.lightstreamer.util.AlternativeLoader;
import com.lightstreamer.util.threads.ThreadShutdownHook;

/**
 * A transport factory creates instances of a specific transport implementation.
 * <p>
 * <b>NB</b>
 * I use an abstract class instead of an interface because I need to add static methods returning
 * the default factory implementations.
 * 
 * @param <T> either {@link HttpProvider} or {@link WebSocketProvider} 
 */
public abstract class TransportFactory<T> {
    
    /*
     * Instance methods of a generic transport factory.
     */
    
    /**
     * Returns a new instance of a transport.
     */
    public abstract T getInstance(SessionThread thread);
    
    /**
     * Returns true if the transport implementation reads the whole response before passing it to the client.
     * When the response is buffered, the content-length should be small (about 4Mb).
     */
    public abstract boolean isResponseBuffered();

    /*
     * Below there are a few static methods providing the default factories for HTTP and WebSocket transports.
     * 
     * Default HTTP factory 
     */

    private static TransportFactory<HttpProvider> defaultHttpFactory;

    public static synchronized TransportFactory<HttpProvider> getDefaultHttpFactory() {
        if (defaultHttpFactory == null) {
            defaultHttpFactory = httpClassLoader.getAlternative();
            if (defaultHttpFactory == null) {
                System.err.println("NO HTTP PROVIDER CLASS AVAILABLE, SOMETHING WENT WRONG AT BUILD TIME, CONTACT LIGHTSTREAMER SUPPORT");
            }
        }
        return defaultHttpFactory;
    }
    
    public static synchronized void setDefaultHttpFactory(TransportFactory<HttpProvider> factory) {
        if (factory == null) {
            throw new IllegalArgumentException("Specify a factory");
        }
        defaultHttpFactory = factory;
    }
    
    private static final AlternativeLoader<TransportFactory<HttpProvider>> httpClassLoader = new AlternativeLoader<TransportFactory<HttpProvider>>() {
        @Override
        protected String[] getDefaultClassNames() {
            String[] classes = { 
                    "com.lightstreamer.client.transport.providers.netty.NettyHttpProviderFactory", 
                    "com.lightstreamer.client.transport.providers.oio.OIOHttpProviderFactory" };
            return classes;
        }
    };

    /*
     * Default WebSocket factory
     */
    
    private static TransportFactory<WebSocketProvider> defaultWSFactory;

    public static synchronized TransportFactory<WebSocketProvider> getDefaultWebSocketFactory() {
        if (defaultWSFactory == null) {
            defaultWSFactory = wsClassLoader.getAlternative();
        }
        return defaultWSFactory;
    }
    
    public static synchronized void setDefaultWebSocketFactory(TransportFactory<WebSocketProvider> factory) {
        defaultWSFactory = factory;
    }
    
    private static final AlternativeLoader<TransportFactory<WebSocketProvider>> wsClassLoader = new AlternativeLoader<TransportFactory<WebSocketProvider>>() {
        @Override
        protected String[] getDefaultClassNames() {
            String[] classes = { "com.lightstreamer.client.transport.providers.netty.WebSocketProviderFactory" };
            return classes;
        }
    };
    
    /*
     * Global transport shutdown hook
     */
    
    private static ThreadShutdownHook transportShutdownHook;
    
    /**
     * Returns the shutdown hook releasing the resources shared by the transport providers (e.g. socket pools).
     */
    public static synchronized ThreadShutdownHook getTransportShutdownHook() {
        if (transportShutdownHook == null) {
            transportShutdownHook = transportShutdownHookClassLoader.getAlternative();
        }
        return transportShutdownHook;
    }
    
    public static synchronized void setTranpsortShutdownHook(ThreadShutdownHook hook) {
        transportShutdownHook = hook;
    }
    
    private static final AlternativeLoader<ThreadShutdownHook> transportShutdownHookClassLoader = new AlternativeLoader<ThreadShutdownHook>() {
        @Override
        protected String[] getDefaultClassNames() {
            String[] classes = { "com.lightstreamer.client.transport.providers.netty.NettyShutdownHook" };
            return classes;
        }
    };
}
