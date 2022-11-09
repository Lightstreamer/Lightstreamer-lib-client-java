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

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 
 */
public class StaticAssignmentMultiplexer<S> implements ThreadMultiplexer<S> {

    static List<ScheduledExecutorService> threads = new LinkedList<ScheduledExecutorService>();

    static {
        int cores = Runtime.getRuntime().availableProcessors();
        for (int i = 1; i <= cores; i++) {
            final int n = i;
            ScheduledExecutorService thread =
                Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "Session Thread " + n);
                        t.setDaemon(false);
                        return t;
                    }
                });
            threads.add(thread);
        }
    }

    private static AtomicInteger nextThreadIdx = new AtomicInteger(-1);

    private static ScheduledExecutorService getThreadByRoundRobin() {
        int prev, next;
        do {
            prev = nextThreadIdx.get();
            next = (prev + 1) % threads.size();
        } while (!nextThreadIdx.compareAndSet(prev, next));

        ScheduledExecutorService thread = threads.get(next);
        return thread;
    }

    ConcurrentHashMap<S, ScheduledExecutorService> associations =
        new ConcurrentHashMap<S, ScheduledExecutorService>();

    public void register(S source) {
        if (associations.containsKey(source)) {
            throw new IllegalStateException(
                "Must register only once per source: you probably want to do it in the constructor");
        }
        ScheduledExecutorService thread = getThreadByRoundRobin();
        associations.put(source, thread);
    }

    @Override
    public void execute(S source, Runnable runnable) {
        ScheduledExecutorService thread = associations.get(source);
        thread.execute(runnable);
    }

    @Override
    public PendingTask schedule(S source, Runnable task, long delayMillis) {
        ScheduledExecutorService thread = associations.get(source);
        ScheduledFuture<?> pending = thread.schedule(task, delayMillis, TimeUnit.MILLISECONDS);
        return new FuturePendingTask(pending);
    }

    @Override
    public void await() {

    }

}
