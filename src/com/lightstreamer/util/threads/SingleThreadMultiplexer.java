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
package com.lightstreamer.util.threads;

import com.lightstreamer.client.Constants;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;
import com.lightstreamer.util.threads.providers.ExecutorFactory;
import com.lightstreamer.util.threads.providers.JoinableScheduler;

/**
 * 
 */
public class SingleThreadMultiplexer<S> implements ThreadMultiplexer<S> {
    
    private static final Logger log = LogManager.getLogger(Constants.THREADS_LOG);

    private JoinableScheduler executor ;
    
    public SingleThreadMultiplexer() {
        executor = ExecutorFactory.getDefaultExecutorFactory().getScheduledExecutor(1, "Session Thread", 1000);
    }

    @Override
    public void await() {
        executor.join();
    }
    
    @Override
    public void execute(S source, Runnable runnable) {
        executor.schedule(new LoggingRunnable(runnable), 0);
    }

    @Override
    public PendingTask schedule(S source, Runnable task, long delayMillis) {
        return executor.schedule(new LoggingRunnable(task), delayMillis);
    }

}
