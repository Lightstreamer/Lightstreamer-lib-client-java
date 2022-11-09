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
