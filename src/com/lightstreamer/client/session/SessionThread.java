/*
 * Copyright (c) 2004-2015 Weswit s.r.l., Via Campanini, 6 - 20124 Milano, Italy.
 * All rights reserved.
 * www.lightstreamer.com
 * This software is the confidential and proprietary information of
 * Weswit s.r.l.
 * You shall not disclose such Confidential Information and shall use it
 * only in accordance with the terms of the license agreement you entered
 * into with Weswit s.r.l.
 */
package com.lightstreamer.client.session;

import java.util.concurrent.atomic.AtomicReference;

import com.lightstreamer.client.Constants;
import com.lightstreamer.client.LightstreamerClient;
import com.lightstreamer.client.transport.providers.TransportFactory;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;
import com.lightstreamer.util.threads.PendingTask;
import com.lightstreamer.util.threads.SingleThreadMultiplexer;
import com.lightstreamer.util.threads.ThreadMultiplexer;
import com.lightstreamer.util.threads.ThreadShutdownHook;

/**
 * An instance of this class is used to dispatch calls to the Session and Protocol layer.
 * Both calls from the API layer (i.e. from the EventsThread) to the Session classes
 * and from the network layer to the protocol layer are scheduled on this thread.
 * <p>
 * If the property "com.lightstreamer.client.session.thread" is set to "dedicated", then there is a session thread per client.
 * Otherwise a single thread is shared between all the clients.
 */
public class SessionThread {

//    static {
//        useSingleThreadMultiplexer();
//    }

    private static final Logger log = LogManager.getLogger(Constants.THREADS_LOG);

    private final ThreadMultiplexer<SessionThread> threads;

//    public synchronized static void useStaticMultiplexer() {
//        threads = new StaticAssignmentMultiplexer<SessionThread>();
//    }
//
//    public synchronized static void useSingleThreadMultiplexer() {
//        threads = new SingleThreadMultiplexer<SessionThread>();
//    }

    private final AtomicReference<ThreadShutdownHook> shutdownHookReference = new AtomicReference<ThreadShutdownHook>();
    
    private final AtomicReference<ThreadShutdownHook> wsShutdownHookReference = new AtomicReference<ThreadShutdownHook>();

    private volatile SessionManager sessionManager;
    
    public SessionThread() {
//        if (threads instanceof StaticAssignmentMultiplexer) {
//            ((StaticAssignmentMultiplexer<SessionThread>) threads).register(this);
//        }
        threads = SessionThreadFactory.INSTANCE.getSessionThread();
    }

    public void registerShutdownHook(ThreadShutdownHook shutdownHook) {
        // This allows to register the passed hook only once and therefore to have only one reference
        shutdownHookReference.compareAndSet(null, shutdownHook);
    }
    
    public void registerWebSocketShutdownHook(ThreadShutdownHook shutdownHook) {
        // This allows to register the passed hook only once and therefore to have only one reference
        wsShutdownHookReference.compareAndSet(null, shutdownHook);
    }

    public void await() {
    	/* close session thread */
        log.debug("Waiting for tasks of SessionThread to get completed...");
        threads.await();
        log.debug("Tasks of SessionThread completed");
        /* close HTTP provider */
        ThreadShutdownHook hook = shutdownHookReference.get();
        if (hook != null) {
            log.debug("Invoking the HTTP Shutdown Hook");
            hook.onShutdown();
            log.debug("HTTP Shutdown Hook invoked");
        } else {
            // In case of iOS client, no ThreadShutdownHook is provided
            log.warn("No HTTP Shutdown Hook provided");
        }
        /* close WebSocket provider */
        ThreadShutdownHook wsHook = wsShutdownHookReference.get();
        if (wsHook != null) {
            log.debug("Invoking the WebSocket Shutdown Hook");
            wsHook.onShutdown();
            log.debug("WebSocket Shutdown Hook invoked");
        } else {
            log.warn("No WebSocket Shutdown Hook provided");
        }
        /* close global transport resources (e.g. socket pools) */
        ThreadShutdownHook transportShutdownHook = TransportFactory.getTransportShutdownHook();
        if (transportShutdownHook != null) {
            log.debug("Invoking the Transport Shutdown Hook");
            transportShutdownHook.onShutdown();
            log.debug("Transport Shutdown Hook invoked");
        } else {
            log.warn("No Transport Shutdown Hook provided");
        }
    }

    public void queue(final Runnable task) {
        threads.execute(this, decorateTask(task));
    }

    public PendingTask schedule(final Runnable task, long delayMillis) {
        return threads.schedule(this, decorateTask(task), delayMillis);
    }
    
    /**
     * Sets the SessionManager. 
     * <p>
     * <b>NB</b> There is a circular dependency between the classes SessionManager and SessionThread
     * and further this method is called by the user thread (see the implementation of {@link LightstreamerClient#LightstreamerClient(String, String)})
     * but it is used by the session thread, so the attribute {@code sessionManager} must be volatile.
     */
    public void setSessionManager(SessionManager sm) {
        this.sessionManager = sm;
    }
    
    public SessionManager getSessionManager() {
        assert sessionManager != null;
        return sessionManager;
    }
    
    /**
     * Decorates the task adding the following behavior:
     * <ol>
     * <li>when an escaped exception is caught, closes the session.</li>
     * </ol>
     */
    private Runnable decorateTask(final Runnable task) {
        return new Runnable() {
            @Override
            public void run() {
                assert sessionManager != null;
                try {
                    task.run();
                    
                } catch (Throwable e) {
                    log.error("Uncaught exception", e);
                    sessionManager.onFatalError(e);
                }
            }
        };
    }
    
    private static class SessionThreadFactory {
        
        static final SessionThreadFactory INSTANCE = new SessionThreadFactory();
        
        private final boolean dedicatedSessionThread;
        private ThreadMultiplexer<SessionThread> singletonSessionThread;
        
        private SessionThreadFactory() {
            dedicatedSessionThread = "dedicated".equals(System.getProperty("com.lightstreamer.client.session.thread"));
        }
        
        synchronized ThreadMultiplexer<SessionThread> getSessionThread() {
            ThreadMultiplexer<SessionThread> sessionThread;
            if (dedicatedSessionThread) {
                sessionThread = new SingleThreadMultiplexer<SessionThread>();
            } else {
                if (singletonSessionThread == null) {
                    singletonSessionThread = new SingleThreadMultiplexer<SessionThread>();
                }
                sessionThread = singletonSessionThread;
            }
            return sessionThread;
        }
    }
}
