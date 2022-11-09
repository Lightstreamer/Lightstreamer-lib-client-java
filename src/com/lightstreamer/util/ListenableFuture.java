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
