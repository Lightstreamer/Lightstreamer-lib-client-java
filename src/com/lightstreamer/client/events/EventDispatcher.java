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
