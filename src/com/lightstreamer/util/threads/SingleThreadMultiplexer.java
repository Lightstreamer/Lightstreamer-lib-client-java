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
