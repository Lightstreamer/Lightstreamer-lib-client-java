package com.lightstreamer.util.threads;

import com.lightstreamer.util.threads.providers.JoinableExecutor;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class JoinablePoolExecutor extends ThreadPoolExecutor
    implements JoinableExecutor {

    private Object currentThreadLock = new Object();

    private volatile Thread currentThread = null;

    private static AtomicInteger threadCounter = new AtomicInteger();

    public JoinablePoolExecutor(int nThreads,
                                final String threadName,
                                long keepAliveTime,
                                TimeUnit unit) {
        super(nThreads, nThreads, keepAliveTime, unit,
            new LinkedBlockingQueue<Runnable>());
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
    
}
