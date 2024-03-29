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

import com.lightstreamer.util.AlternativeLoader;
import com.lightstreamer.util.threads.DefaultExecutorFactory;

/**
 * A Factory of <i>joinable</i> Executors or Schedulers.
 * <p>
 * The entry point of the factory is the
 * {@linkplain #getDefaultExecutorFactory()} static method, which provides an
 * instance of the class {@link DefaultExecutorFactory}. To provide a custom
 * implementation, it is required to pass it to the
 * {@linkplain #setDefaultExecutorFactory(ExecutorFactory)}, before the library
 * is actually used.
 */
public class ExecutorFactory {

    private static final AlternativeLoader<ExecutorFactory> loader =
        new AlternativeLoader<ExecutorFactory>() {

            @Override
            protected String[] getDefaultClassNames() {
                String[] classes =
                    { "com.lightstreamer.util.threads.DefaultExecutorFactory" };
                return classes;
            }

        };

    private static volatile ExecutorFactory defaultExecutorFactory;

    public static void setDefaultExecutorFactory(ExecutorFactory factory) {
        if (factory == null) {
            throw new IllegalArgumentException("Specify a factory");
        }

        defaultExecutorFactory = factory;
    }

    /**
     * Returns the current <code>ExecutorFactory</code>, defined as the default
     * factory.
     * 
     * @return the current default <code>ExecutorFactory</code> instance
     */
    public static ExecutorFactory getDefaultExecutorFactory() {
        if (defaultExecutorFactory == null) {
            synchronized (ExecutorFactory.class) {
                defaultExecutorFactory = loader.getAlternative();

                if (defaultExecutorFactory == null) {
                    System.err.println(
                        "NO THREADEXECUTOR FACTORY CLASS AVAILABLE, SOMETHING WENT WRONG AT BUILD TIME, CONTACT LIGHTSTREAMER SUPPORT");
                    defaultExecutorFactory = new ExecutorFactory();
                }
            }

        }

        return defaultExecutorFactory;
    }

    /**
     * Configure and returns a new {@code JoinableExecutor} instance, as per the
     * specified parameters.
     * 
     * @param nThreads
     *            the number of threads of the thread pool
     * @param threadName
     *            the suffix to use for the name of every newly created thread
     * @param keepAliveTime
     *            the keep-alive time specified in milliseconds.
     * @return a new instance of {@code JoinableExecutor}
     */
    public JoinableExecutor getExecutor(int nThreads, String threadName,
        long keepAliveTime) {
        return null;
    }

    /**
     * Configure and returns a new {@code JoinableScheduler} instance, as per
     * the specified parameters.
     * 
     * @param nThreads
     *            the number of threads of the thread pool
     * @param threadName
     *            the suffix to use for the name of every newly created thread
     * @param keepAliveTime
     *            the keep-alive time specified in milliseconds.
     * @return a new instance of {@code JoinableScheduler}
     */
    public JoinableScheduler getScheduledExecutor(int nThreads,
        String threadName, long keepAliveTime) {
        return null;
    }

}
