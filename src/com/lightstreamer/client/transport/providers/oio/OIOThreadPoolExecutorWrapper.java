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
package com.lightstreamer.client.transport.providers.oio;

import com.lightstreamer.client.Constants;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class OIOThreadPoolExecutorWrapper {
    
    private final static AtomicInteger count = new AtomicInteger(0);

    private static final Logger log = LogManager.getLogger(Constants.THREADS_LOG);
    
    private static final LinkedBlockingDeque<OIOThreadPoolExecutorWrapper> pool = new LinkedBlockingDeque<>();
    
    static {
        release(new OIOThreadPoolExecutorWrapper());
    }
    
    public static OIOThreadPoolExecutorWrapper get() {
        try {
            log.debug("Waiting for a OIOWrapper");
            return pool.take();
        } catch (InterruptedException e1) {
            log.error("", e1);
            return null;
        } finally {
            log.debug("OIOWrapper got");
        }
    }
    
    public static void release(OIOThreadPoolExecutorWrapper executorWrapper) {
        try {
            pool.put(executorWrapper);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    public static void  close() {
        OIOThreadPoolExecutorWrapper executorWrapper = get();
        executorWrapper.threads.shutdown();
        release(new OIOThreadPoolExecutorWrapper());
    }

    private ThreadPoolExecutor threads;
    
    private OIOThreadPoolExecutorWrapper() {
        threads =
            new ThreadPoolExecutor(0, Integer.MAX_VALUE, 500L, TimeUnit.MILLISECONDS,
                new SynchronousQueue<Runnable>(), new ThreadFactory() {

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "OIO Thread " + count.incrementAndGet());
                        t.setDaemon(true);
                        return t;
                    }

                }) {

                @Override
                protected void terminated() {
                    log.debug("OIOThreadPool terminated");
                }
            };
    }
    
    public void execute(Runnable r) {
        threads.execute(r);
    }
}
