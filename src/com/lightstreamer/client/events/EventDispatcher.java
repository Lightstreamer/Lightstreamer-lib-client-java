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
package com.lightstreamer.client.events;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.lightstreamer.client.Constants;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;

public class EventDispatcher<T> {
  
  private final Map<T,ListenerWrapper> listeners = new HashMap<T,ListenerWrapper>(); 
  private final EventsThread eventThread;
  
  private final Logger log = LogManager.getLogger(Constants.ACTIONS_LOG);
  
  public EventDispatcher(EventsThread thread) {
    if (thread == null) {
      throw new NullPointerException("an EventsThread is required");
    }
    this.eventThread = thread;
  }
  
  public synchronized void addListener(T listener, Event<T> startEvent) {
    if (listeners.containsKey(listener)) {
      return;
    }
    
    ListenerWrapper wrapper = new ListenerWrapper(listener);
    listeners.put(listener, wrapper);
    
    this.dispatchEventToListener(startEvent, wrapper, true);
  }
  
  public synchronized void removeListener(T listener, Event<T> endEvent) {
    ListenerWrapper wrapper = listeners.remove(listener);
    if (wrapper == null) {
      //wrapper does not exists
      return;
    }
    
    wrapper.alive.set(false);
    
    this.dispatchEventToListener(endEvent, wrapper, true);
  }
  
  public synchronized void dispatchEvent(final Event<T> event) {
    for (Map.Entry<T, ListenerWrapper> entry : listeners.entrySet()) {
      this.dispatchEventToListener(event, entry.getValue(), false);
    }
    
  }
  
  public synchronized int size() {
    return listeners.size();
  }
  
  public synchronized List<T> getListeners() {
    Set<Map.Entry<T, ListenerWrapper>> listenerEntries= listeners.entrySet();
    ArrayList<T> listenerList= new ArrayList<T>(listenerEntries.size());
    
    for (Map.Entry<T, ListenerWrapper> entry : listenerEntries) {
      listenerList.add(entry.getValue().listener);
    }
    
    return listenerList;
  }
  
  private void dispatchEventToListener(final Event<T> event, final ListenerWrapper wrapper, final boolean forced) {
    
    if (event == null) {
      //should not happen, widely used during tests
      return;
    }
    eventThread.queue(new Runnable() {
      @Override
      public void run() {
          if (wrapper.alive.get() || forced) {
            try {
              event.applyTo(wrapper.listener);
            } catch(Error | RuntimeException e) {
              log.error("Exception caught while executing event on custom code",e);
            }
          }
      }});
    
  }
  
  public void dispatchSingleEvent(final Event<T> event, final T listener) {
    eventThread.queue(new Runnable() {
      @Override
      public void run() {
          try {
            event.applyTo(listener);
          } catch(Error | RuntimeException e) {
            log.error("Exception caught while executing event on custom code",e);
          }
      }});
  }
  
  class ListenerWrapper {
    T listener;
    AtomicBoolean alive = new AtomicBoolean(true);

    public ListenerWrapper(T listener) {
      super();
      this.listener = listener;
    }
    
  }
}
