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
package com.lightstreamer.client.events;

import com.lightstreamer.client.Constants;
import com.lightstreamer.client.LightstreamerClient;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;
import com.lightstreamer.util.threads.providers.ExecutorFactory;
import com.lightstreamer.util.threads.providers.JoinableExecutor;

/*
 * An instance of this class is used to handle client calls and dispatch events as
 * described in the Thread Safeness section of the Unified Client APIs.
 */

public class EventsThread {
    
    /**
     * Instance shared by all the {@link LightstreamerClient}.
     */
    public static final EventsThread instance = new EventsThread();

    private static final Logger log = LogManager.getLogger(Constants.THREADS_LOG);

    private final JoinableExecutor queue;

    // only for tests
    public EventsThread() {
//        queue = EventsThreadFactory.INSTANCE.getEventsThread();
        queue = ExecutorFactory.getDefaultExecutorFactory().getExecutor(1, "EventsThread", 1000);
    }

    public void queue(Runnable task) {
        queue.execute(task);
    }

    public void await() {
        log.debug("Waiting for tasks of EventsThread to get completed...");
        queue.join();
        log.debug("Tasks completed");
    }
    
    /*
    private static class EventsThreadFactory {
        
        static final EventsThreadFactory INSTANCE = new EventsThreadFactory();
        
        private final boolean dedicatedEventsThread;
        private JoinableExecutor singletonEventsThread;
        
        private EventsThreadFactory() {
            dedicatedEventsThread = "dedicated".equals(System.getProperty("com.lightstreamer.client.session.events.thread"));
        }
        
        synchronized JoinableExecutor getEventsThread() {
            JoinableExecutor eventsThread;
            if (dedicatedEventsThread) {
                eventsThread = ExecutorFactory.getDefaultExecutorFactory().getExecutor(1, "EventsThread", 1000);
            } else {
                if (singletonEventsThread == null) {
                    singletonEventsThread = ExecutorFactory.getDefaultExecutorFactory().getExecutor(1, "EventsThread", 1000);
                }
                eventsThread = singletonEventsThread;
            }
            return eventsThread;
        }
    }
    */
}
