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
