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
