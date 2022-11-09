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
