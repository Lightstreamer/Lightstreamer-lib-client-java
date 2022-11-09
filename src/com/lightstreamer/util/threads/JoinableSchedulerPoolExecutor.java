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


package com.lightstreamer.util.threads;

import com.lightstreamer.util.threads.providers.JoinableScheduler;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class JoinableSchedulerPoolExecutor extends ScheduledThreadPoolExecutor
    implements JoinableScheduler {

    private Object currentThreadLock = new Object();

    private volatile Thread currentThread = null;

    private static AtomicInteger threadCounter = new AtomicInteger();

    public JoinableSchedulerPoolExecutor(int nThreads,
                                         final String threadName,
                                         long keepAliveTime,
                                         TimeUnit unit) {
        super(nThreads);
        setThreadFactory(new ThreadFactory() {

            @Override
            public Thread newThread(Runnable task) {
                int count = threadCounter.incrementAndGet();
                return new Thread(task, threadName + " <" + count + ">") {
                    @Override
                    public void run() {
                        super.run();
                        synchronized (currentThreadLock) {
                            currentThread = Thread.currentThread();
                            currentThreadLock.notifyAll();
                        }
                    }
                };
            }
        });
        setKeepAliveTime(keepAliveTime, unit);
        super.allowCoreThreadTimeOut(true);
    }

    @Override
    public void join() {
        try {
            synchronized (currentThreadLock) {
                while (currentThread == null) {
                    currentThreadLock.wait();
                }

                currentThread.join();
                while (currentThread.isAlive()) {

                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public PendingTask schedule(Runnable task, long delayInMillis) {
        ScheduledFuture<?> pending =
            schedule(task, delayInMillis, TimeUnit.MILLISECONDS);
        return new FuturePendingTask(pending);
    }
}
