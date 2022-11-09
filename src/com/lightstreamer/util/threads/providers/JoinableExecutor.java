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

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Interface which defines a basic thread executor whose internal working
 * threads are terminated if no task arrive within a specified keep-alive time.
 *
 */
public interface JoinableExecutor extends Joinable {

    /**
     * Executes the given command at some time in the future.
     * 
     * @see Executor#execute(Runnable)
     *
     * @param command
     *            the runnable task
     * @throws RejectedExecutionException
     *             if this task cannot be accepted for execution
     * @throws NullPointerException
     *             if command is null
     */
    void execute(Runnable task);

}
