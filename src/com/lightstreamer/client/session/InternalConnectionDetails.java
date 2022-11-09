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

import java.net.MalformedURLException;
import java.net.URL;

import com.lightstreamer.client.ClientListener;
import com.lightstreamer.client.Constants;
import com.lightstreamer.client.events.ClientListenerPropertyChangeEvent;
import com.lightstreamer.client.events.EventDispatcher;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;

public class InternalConnectionDetails{

  private EventDispatcher<ClientListener> eventDispatcher;
  private final Logger log = LogManager.getLogger(Constants.ACTIONS_LOG);
  
  private String serverInstanceAddress = null;
  private String serverSocketName = null;
  private String clientIp = null;
  private String password = null;
  private String adapterSet = null;
  private String serverAddress = null;
  private String user = null;
  private String sessionId = null;
  

  public InternalConnectionDetails(EventDispatcher<ClientListener> eventDispatcher) {
    this.eventDispatcher = eventDispatcher;
  }
  
  public synchronized String getAdapterSet() {
    return adapterSet;
  }
  public synchronized void setAdapterSet(String adapterSet) {
    this.adapterSet = adapterSet;
    
    this.eventDispatcher.dispatchEvent(new ClientListenerPropertyChangeEvent("adapterSet"));
    
    log.info("Adapter Set value changed to " + adapterSet);
  }
  
  public synchronized String getServerAddress() {
    return serverAddress;
  }
  public synchronized void setServerAddress(String serverAddress) {
    if (!serverAddress.endsWith("/")) {
      serverAddress += "/";
    }
    verifyServerAddress(serverAddress); //will throw IllegalArgumentException if not
    
    this.serverAddress = serverAddress;
    
    this.eventDispatcher.dispatchEvent(new ClientListenerPropertyChangeEvent("serverAddress"));
    
    log.info("Server Address value changed to " + serverAddress);
  }
  
  public synchronized String getUser() {
    return user;
  }
  public synchronized void setUser(String user) {
    this.user = user;
    
    this.eventDispatcher.dispatchEvent(new ClientListenerPropertyChangeEvent("user"));
    
    log.info("User value changed to " + user);
  }
  
  public synchronized String getServerInstanceAddress() {
    return serverInstanceAddress;
  }
  synchronized void setServerInstanceAddress(String serverInstanceAddress) {
    this.serverInstanceAddress = serverInstanceAddress;
    
    this.eventDispatcher.dispatchEvent(new ClientListenerPropertyChangeEvent("serverInstanceAddress"));
    
    log.info("Server Instance Address value changed to " + serverInstanceAddress);
  }
  
  public synchronized String getServerSocketName() {
    return serverSocketName;
  }
  synchronized void setServerSocketName(String serverSocketName) {
    this.serverSocketName = serverSocketName;
    
    this.eventDispatcher.dispatchEvent(new ClientListenerPropertyChangeEvent("serverSocketName"));
    
    log.info("Server Socket Name value changed to " + serverSocketName);
  }
  
  public synchronized String getClientIp() {
      return clientIp;
  }
  synchronized void setClientIp(String clientIp) {
    this.clientIp = clientIp;
      
    this.eventDispatcher.dispatchEvent(new ClientListenerPropertyChangeEvent("clientIp"));
      
    log.info("Client IP value changed to " + clientIp);
  }
    
  public synchronized String getSessionId() {
    return sessionId;
  }
  synchronized void setSessionId(String sessionId) {
    this.sessionId = sessionId;
    
    this.eventDispatcher.dispatchEvent(new ClientListenerPropertyChangeEvent("sessionId"));
    
    log.info("Session ID value changed to " + sessionId);
  }
  
  public synchronized void setPassword(String password) {
    this.password = password;
    
    this.eventDispatcher.dispatchEvent(new ClientListenerPropertyChangeEvent("password"));
    
    log.info("Password value changed");
  }
  synchronized String getPassword() {
    return this.password;
  }
  
  private static void verifyServerAddress(String serverAddress) {
      
      URL url;
      try {
        url = new URL(serverAddress);
      } catch (MalformedURLException e) {
        throw new IllegalArgumentException("The given server address is not valid",e);
      }
      
      String protocol = url.getProtocol();
      if (!protocol.equals("http") && !protocol.equals("https") ) {
        throw new IllegalArgumentException("The given server address has not a valid scheme");
      }
         
      
      if (url.getQuery() != null || url.getUserInfo() != null || url.getRef() != null) {
        throw new IllegalArgumentException("The given server address is not valid, remove the query");
      }
      
      //url.getPath(); //is admitted
      //url.getFile(); //file == path+query
  
    }
}
