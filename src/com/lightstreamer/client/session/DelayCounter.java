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


package com.lightstreamer.client.session;

import com.lightstreamer.client.Constants;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;

/**
 * Computes a growing delay in the following way:
 * <ul>
 * <li>the first 10 times when increase() is called, currentDelay equals Delay</li>
 * <li>the next times, currentDelay is doubled until it reaches the value of 60s</li>
 * </ul>
 * 
 * 
 * @since December 2018
 */
public class DelayCounter {
    
    private static final Logger log = LogManager.getLogger(Constants.SESSION_LOG);
    
    private int attempt;
    private long minDelay;
    private long maxDelay;
    private long currentDelay;
    private final String name;

    public DelayCounter(long delay, String name) {
        init(delay);
        this.name = name;
    }
        
    /**
     * Resets the delay.
     */
    public void reset(long delay) {
        init(delay);
        if (log.isDebugEnabled()) {
            log.debug("Reset " + name + ": " + currentDelay);
        }
    }

    /**
     * Increase the delay.
     */
    public void increase() {
        if (attempt >= 9 && currentDelay < maxDelay) {
            currentDelay *= 2;
            if (currentDelay > maxDelay) {
                currentDelay = maxDelay;
            }
            if (log.isDebugEnabled()) {
                log.debug("Increase " + name + ": " + currentDelay);
            }
        }
        attempt++;
    }
    
    /**
     * Increase the delay to the maximum value.
     */
    public void increaseToMax() {
        currentDelay = maxDelay;
        if (log.isDebugEnabled()) {
            log.debug("Increase " + name + " to max: " + currentDelay);
        }
    }
    
    public long getCurrentDelay() {
        return currentDelay;
    }
    
    public long getDelay() {
        return minDelay;
    }
    
    /**
     * Initializes the delay.
     */
    private void init(long delay) {
        this.currentDelay = delay;
        this.minDelay = delay;
        this.maxDelay = Math.max(60_000, delay);
        this.attempt = 0;
    }
}
