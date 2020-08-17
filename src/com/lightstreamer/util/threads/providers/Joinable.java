/*
 * Copyright (c) 2004-2015 Weswit s.r.l., Via Campanini, 6 - 20124 Milano,
 * Italy. All rights reserved. www.lightstreamer.com This software is the
 * confidential and proprietary information of Weswit s.r.l. You shall not
 * disclose such Confidential Information and shall use it only in accordance
 * with the terms of the license agreement you entered into with Weswit s.r.l.
 */
package com.lightstreamer.util.threads.providers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Root interface for <i>joinable</i> executors and schedulers.
 * <p>
 * Executors and Schedulers are said <i>joinable</i> if their internal working threads are
 * terminated if no more task arrive, therefore allowing a graceful completion of involved threads
 * without no need to explicitly invoke {@link ExecutorService.shutdown} or
 * {@link ScheduledExecutorService.shutdown}
 * method.
 *
 */
public interface Joinable {

    /**
     * Waits forever for this joinable executor (or scheduler) to die.
     *
     * @throws RuntimeException
     *             which wraps an {@link InterruptedExetpion} if any thread has
     *             interrupted the current thread.
     */
    void join();

}
