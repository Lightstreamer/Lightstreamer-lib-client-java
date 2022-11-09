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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;

import com.lightstreamer.client.events.EventsThread;

/**
 * Thread-safe listener support.
 * 
 * 
 * @since October 2017
 */
@ThreadSafe
public class ListenerHolder<T> {
    
    protected final EventsThread eventThread;
    protected final Set<T> listeners = new HashSet<>();
    
    public ListenerHolder(EventsThread eventThread) {
        this.eventThread = eventThread;
    }
    
    /**
     * Adds the listener. If it is not present, executes the visitor operation on the added listener.
     */
    public synchronized void addListener(final T listener, final Visitor<T> visitor) {
        boolean isNew = listeners.add(listener);
        if (isNew) {
            eventThread.queue(new Runnable() {
                @Override
                public void run() {
                    visitor.visit(listener);
                }
            });
        }
    }
    
    /**
     * Removes the listener. If it is present, executes the visitor operation on the removed listener. 
     */
    public synchronized void removeListener(final T listener, final Visitor<T> visitor) {
        boolean contained = listeners.remove(listener);
        if (contained) {                
            eventThread.queue(new Runnable() {
                @Override
                public void run() {
                    visitor.visit(listener);
                }
            });
        }
    }
    
    /**
     * Gets the listeners.
     */
    public synchronized @Nonnull List<T> getListeners() {
        return new ArrayList<>(listeners);
    }
    
    /**
     * Executes the visitor operation for each listener.
     */
    public synchronized void forEachListener(final Visitor<T> visitor) {
        for (final T listener : listeners) {
            eventThread.queue(new Runnable() {
                @Override
                public void run() {
                    visitor.visit(listener);
                }
            });
        }
    }
}
