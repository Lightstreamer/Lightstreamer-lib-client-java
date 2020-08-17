package com.lightstreamer.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class IdGenerator {
    
    private static final AtomicLong requestIdGenerator = new AtomicLong();

    /**
     * Generates the next request id used as the value of the parameter LS_reqId.
     */
    public static final long getNextRequestId() {
        return requestIdGenerator.incrementAndGet();
    }
    
    private static final AtomicInteger subscriptionIdGenerator = new AtomicInteger();

    /**
     * Generates the next subscription id used as the value of the parameter LS_subId.
     */
    public static final int getNextSubscriptionId() {
        return subscriptionIdGenerator.incrementAndGet();
    }
}
