/*
 * Copyright (c) 2004-2015 Weswit s.r.l., Via Campanini, 6 - 20124 Milano,
 * Italy. All rights reserved. www.lightstreamer.com This software is the
 * confidential and proprietary information of Weswit s.r.l. You shall not
 * disclose such Confidential Information and shall use it only in accordance
 * with the terms of the license agreement you entered into with Weswit s.r.l.
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
