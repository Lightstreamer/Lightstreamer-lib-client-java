package com.lightstreamer.util;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.concurrent.ThreadSafe;

/**
 * A future to which listeners can be attached.
 * 
 * 
 * @since May 2018
 */
@ThreadSafe
public class ListenableFuture {
    
    private final List<Runnable> onFulfilledListeners = new ArrayList<>();
    private final List<Runnable> onRejectedListeners = new ArrayList<>();
    private State state = State.NOT_RESOLVED;
    
    /**
     * Returns a fulfilled future.
     */
    public static ListenableFuture fulfilled() {
        return new ListenableFuture().fulfill();
    }
    
    /**
     * Returns a rejected future.
     */
    public static ListenableFuture rejected() {
        return new ListenableFuture().reject();
    }
    
    /**
     * Adds a handler for the successful case. 
     */
    public synchronized ListenableFuture onFulfilled(Runnable listener) {
        onFulfilledListeners.add(listener);
        if (state == State.FULFILLED) {
            listener.run();
        }
        return this;
    }
    
    /**
     * Adds a handler for the error case.
     */
    public synchronized ListenableFuture onRejected(Runnable listener) {
        onRejectedListeners.add(listener);
        if (state == State.REJECTED) {
            listener.run();
        }
        return this;
    }
    
    /**
     * Sets the future as fulfilled.
     */
    public synchronized ListenableFuture fulfill() {
        if (state == State.NOT_RESOLVED) {
            state = State.FULFILLED;
            for (Runnable runnable : onFulfilledListeners) {
                runnable.run();
            }
        }
        return this;
    }
    
    /**
     * Sets the future as rejected.
     */
    public synchronized ListenableFuture reject() {
        if (state == State.NOT_RESOLVED) {
            state = State.REJECTED;
            for (Runnable runnable : onRejectedListeners) {
                runnable.run();
            }
        }
        return this;
    }
    
    /**
     * Aborts the operation. Attached handlers are not executed.
     */
    public synchronized ListenableFuture abort() {
        state = State.ABORTED;
        return this;
    }
    
    public synchronized State getState() {
        return state;
    }
    
    public enum State {
        NOT_RESOLVED, FULFILLED, REJECTED, ABORTED
    }
}
