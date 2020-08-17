package com.lightstreamer.util.threads;

import com.lightstreamer.util.threads.providers.JoinableScheduler;
import com.lightstreamer.util.threads.providers.JoinableExecutor;
import com.lightstreamer.util.threads.providers.ExecutorFactory;

import java.util.concurrent.TimeUnit;

/**
 * The default implementation of an {@code ExecutorFactory}.
 *
 */
public class DefaultExecutorFactory extends ExecutorFactory {

    @Override
    public JoinableExecutor getExecutor(int nThreads, String threadName, long keepAliveTime) {
        return new JoinablePoolExecutor(nThreads, threadName, keepAliveTime, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public JoinableScheduler getScheduledExecutor(int nThreads, String threadName, long keepAliveTime) {
        return new JoinableSchedulerPoolExecutor(nThreads, threadName, keepAliveTime, TimeUnit.MILLISECONDS);
    }
}
