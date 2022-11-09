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
