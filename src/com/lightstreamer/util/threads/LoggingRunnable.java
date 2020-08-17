package com.lightstreamer.util.threads;

import com.lightstreamer.client.Constants;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;

public class LoggingRunnable implements Runnable {
    
    private static final Logger log = LogManager.getLogger(Constants.THREADS_LOG);
    
    private Runnable task;

    public LoggingRunnable(Runnable task) {
        this.task = task;
    }

    @Override
    public void run() {
        try {
            task.run();
        } catch (Throwable thr) {
            log.error("", thr);
            throw thr;
        }
    }
}
