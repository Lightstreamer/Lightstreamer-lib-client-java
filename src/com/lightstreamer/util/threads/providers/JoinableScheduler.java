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


package com.lightstreamer.util.threads.providers;

import com.lightstreamer.util.threads.PendingTask;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Interface which defines a basic thread scheduler whose internal working threads are terminated if
 * no task arrive within a specified keep-alive time.
 */
public interface JoinableScheduler extends Joinable {

    /**
     * Creates and executes a one-shot action that becomes enabled after the given delay.
     *
     * @param command
     *            the task to execute
     * @param delay
     *            the time in milliseconds from now to delay execution
     * @return a PendingTask representing pending completion of the task
     * @throws RejectedExecutionException
     *             if the task cannot be scheduled for execution
     * @throws NullPointerException
     *             if command is null
     * @see ScheduledExecutorService#schedule(Runnable, long, java.util.concurrent.TimeUnit)
     */
    PendingTask schedule(Runnable task, long delayInMillis);

}
