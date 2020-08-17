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
