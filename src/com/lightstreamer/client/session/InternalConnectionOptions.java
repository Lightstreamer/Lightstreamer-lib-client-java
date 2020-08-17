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
package com.lightstreamer.client.session;

import java.util.Map;

import com.lightstreamer.client.ClientListener;
import com.lightstreamer.client.Constants;
import com.lightstreamer.client.Proxy;
import com.lightstreamer.client.events.ClientListenerPropertyChangeEvent;
import com.lightstreamer.client.events.EventDispatcher;
import com.lightstreamer.client.transport.providers.TransportFactory;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;
import com.lightstreamer.util.Number;

public class InternalConnectionOptions {
  
  private long contentLength = 50_000_000;
  private boolean earlyWSOpenEnabled = false;
  private long firstRetryMaxDelay = 100;
  private long forceBindTimeout = 2000; //not exposed
  private String forcedTransport = null;
  private Map<String,String> httpExtraHeaders = null;
  private boolean httpExtraHeadersOnSessionCreationOnly = false; // does not make much sense here, we still keep it, no need to differentiate
  private long idleTimeout = 19000;
  private long keepaliveInterval = 0;
  private double requestedMaxBandwidth = 0;
  private String realMaxBandwidth = null;
  /**
   * This attribute is hidden: when the bandwidth is "unmanaged", the client sees it as "unlimited".
   */
  private boolean unmanagedBandwidth = false;
  private long pollingInterval = 0;
  private long reconnectTimeout = 3000;
  private final DelayCounter currentRetryDelay = new DelayCounter(4000, "currentRetryDelay");
  private final DelayCounter currentConnectTimeout = new DelayCounter(4000, "currentConnectTimeout");
  private long reverseHeartbeatInterval = 0;
  private boolean serverInstanceAddressIgnored = false;
  private boolean slowingEnabled = false;
  private long stalledTimeout = 2000;
  private long sessionRecoveryTimeout = 15000;
  private long switchCheckTimeout = 4000; //not exposed
  private Proxy proxy;
  
  private final Logger log = LogManager.getLogger(Constants.ACTIONS_LOG);
  private final EventDispatcher<ClientListener> eventDispatcher;
  private final ClientListener internalListener;  
  
  public InternalConnectionOptions(EventDispatcher<ClientListener> eventDispatcher, ClientListener internalListener) {
    this.eventDispatcher = eventDispatcher;
    this.internalListener = internalListener;
    if (TransportFactory.getDefaultHttpFactory().isResponseBuffered()) {
        this.contentLength = 4_000_000;
    }
  }
  
  public synchronized long getCurrentConnectTimeout() {
      return currentConnectTimeout.getCurrentDelay();
  }

  public synchronized long getContentLength() {
    return contentLength;
  }

  public synchronized long getFirstRetryMaxDelay() {
    return firstRetryMaxDelay;
  }
  
  public synchronized long getForceBindTimeout() {
    return forceBindTimeout;
  }

  public synchronized String getForcedTransport() {
    return forcedTransport;
  }

  public synchronized Map<String,String> getHttpExtraHeaders() {
    return httpExtraHeaders;
  }

  public synchronized long getIdleTimeout() {
    return idleTimeout;
  }

  public synchronized long getKeepaliveInterval() {
    return keepaliveInterval;
  }

  public synchronized String getRequestedMaxBandwidth() {
    if (this.requestedMaxBandwidth == 0) {
      return "unlimited";
    }
    return String.valueOf(this.requestedMaxBandwidth);
  }
  
  public synchronized double getInternalMaxBandwidth() {
    return requestedMaxBandwidth;
  }
  
  public synchronized String getRealMaxBandwidth() {
      return realMaxBandwidth;
  }

  public synchronized void setInternalRealMaxBandwidth(String bandwidth) {
      this.realMaxBandwidth = bandwidth;
      this.eventDispatcher.dispatchEvent(new ClientListenerPropertyChangeEvent("realMaxBandwidth"));
  }
  
  public synchronized boolean isBandwidthUnmanaged() {
      return unmanagedBandwidth;
  }

  public synchronized void setBandwidthUnmanaged(boolean unmanaged) {
      this.unmanagedBandwidth = unmanaged;
  }

  public synchronized long getPollingInterval() {
    return pollingInterval;
  }

  public synchronized long getReconnectTimeout() {
    return reconnectTimeout;
  }

  public synchronized long getRetryDelay() {
    return currentRetryDelay.getDelay();
  }
  
  public synchronized long getCurrentRetryDelay() {
      return currentRetryDelay.getCurrentDelay();
  }

  public synchronized long getReverseHeartbeatInterval() {
    return reverseHeartbeatInterval;
  }

  public synchronized long getStalledTimeout() {
    return stalledTimeout;
  }
  
  public synchronized long getSessionRecoveryTimeout() {
    return sessionRecoveryTimeout;
  }

  public synchronized Proxy getProxy() {
    return this.proxy;
  }
  
  public synchronized long getSwitchCheckTimeout() {
    return switchCheckTimeout;
  }
  
  public synchronized long getTCPConnectTimeout() {
    return currentRetryDelay.getCurrentDelay() + 1000;
  }

  public synchronized long getTCPReadTimeout() {
    return this.keepaliveInterval+this.stalledTimeout+1000;
  }
  
  public synchronized boolean isEarlyWSOpenEnabled() {
    return earlyWSOpenEnabled;
  }

  public synchronized boolean isHttpExtraHeadersOnSessionCreationOnly() {
    return httpExtraHeadersOnSessionCreationOnly;
  }

  public synchronized boolean isServerInstanceAddressIgnored() {
    return serverInstanceAddressIgnored;
  }

  public synchronized boolean isSlowingEnabled() {
    return slowingEnabled;
  }
  
  public synchronized void increaseConnectTimeout() {
      currentConnectTimeout.increase();
  }
  
  public synchronized void increaseRetryDelay() {
      currentRetryDelay.increase();
  }
  
  public synchronized void increaseConnectTimeoutToMax() {
      currentConnectTimeout.increaseToMax();
  }

  public synchronized void resetRetryDelay() {
      currentRetryDelay.reset(getRetryDelay());
  }
  
  public synchronized void resetConnectTimeout() {
      currentConnectTimeout.reset(getRetryDelay());
  }

  public synchronized void setContentLength(long contentLength) {
    Number.verifyPositive(contentLength,Number.DONT_ACCEPT_ZERO);
    
    this.contentLength = contentLength;
    
    this.eventDispatcher.dispatchEvent(new ClientListenerPropertyChangeEvent("contentLength"));
    
    log.info("Content Length value changed to " + contentLength);
  }
  
  public synchronized void setEarlyWSOpenEnabled(boolean earlyWSOpenEnabled) {
    this.earlyWSOpenEnabled = earlyWSOpenEnabled;
    
    this.eventDispatcher.dispatchEvent(new ClientListenerPropertyChangeEvent("earlyWSOpenEnabled"));
    
    log.info("Early WS Open Enabled value changed to " + earlyWSOpenEnabled);
  }

  public synchronized void setFirstRetryMaxDelay(long firstRetryMaxDelay) {
    Number.verifyPositive(firstRetryMaxDelay,Number.DONT_ACCEPT_ZERO);
    
    this.firstRetryMaxDelay = firstRetryMaxDelay;
    
    this.eventDispatcher.dispatchEvent(new ClientListenerPropertyChangeEvent("firstRetryMaxDelay"));
    
    log.info("First Retry Max Delay value changed to " + firstRetryMaxDelay);
  }
 
  public synchronized void setForceBindTimeout(long forceBindTimeout) {
    this.forceBindTimeout = forceBindTimeout;
  }
  
  public synchronized void setForcedTransport(String forcedTransport) {
    if (!Constants.FORCED_TRANSPORTS.contains(forcedTransport)) {
      throw new IllegalArgumentException("The given value is not valid. Use one of: HTTP-STREAMING, HTTP-POLLING, WS-STREAMING, WS-POLLING, WS, HTTP, or null");
    }
    
    this.forcedTransport = forcedTransport;
    
    this.eventDispatcher.dispatchEvent(new ClientListenerPropertyChangeEvent("forcedTransport"));
    this.internalListener.onPropertyChange("forcedTransport");
    
    log.info("Forced Transport value changed to " + forcedTransport);
    
  }

  public synchronized void setHttpExtraHeaders(Map<String,String> httpExtraHeaders) {
    this.httpExtraHeaders = httpExtraHeaders;
    
    this.eventDispatcher.dispatchEvent(new ClientListenerPropertyChangeEvent("httpExtraHeaders"));
    
    log.info("Extra headers Map changed");
  }

  public synchronized void setHttpExtraHeadersOnSessionCreationOnly(
      boolean httpExtraHeadersOnSessionCreationOnly) {
    this.httpExtraHeadersOnSessionCreationOnly = httpExtraHeadersOnSessionCreationOnly;
    
    this.eventDispatcher.dispatchEvent(new ClientListenerPropertyChangeEvent("httpExtraHeadersOnSessionCreationOnly"));
    
    log.info("Extra Headers On Session Creation Only flag changed to " + httpExtraHeadersOnSessionCreationOnly);
    
  }
  
  public synchronized void setIdleTimeout(long idleTimeout) {
    Number.verifyPositive(idleTimeout,Number.ACCEPT_ZERO);
    
    this.idleTimeout = idleTimeout;
    
    this.eventDispatcher.dispatchEvent(new ClientListenerPropertyChangeEvent("idleTimeout"));
    
    log.info("Idle Timeout value changed to " + idleTimeout);
  }
  
  public synchronized void setKeepaliveInterval(long keepaliveInterval) {
    Number.verifyPositive(keepaliveInterval,Number.ACCEPT_ZERO);
    
    this.keepaliveInterval = keepaliveInterval;
    
    this.eventDispatcher.dispatchEvent(new ClientListenerPropertyChangeEvent("keepaliveInterval"));
    
    log.info("Keepalive Interval value changed to " + keepaliveInterval);
  }

  public synchronized void setRequestedMaxBandwidth(String maxBandwidth) {
    setMaxBandwidthInternal(maxBandwidth,false);
  }
  
  synchronized void setMaxBandwidthInternal(String maxBandwidth) {
    setMaxBandwidthInternal(maxBandwidth,true);
  }
  
  private synchronized void setMaxBandwidthInternal(String maxBandwidth,boolean serverCall) {
    if (maxBandwidth.toLowerCase().equals(Constants.UNLIMITED)) {
      this.requestedMaxBandwidth = 0;
      log.info("Max Bandwidth value changed to unlimited");
    } else {
      double tmp = 0; 
      try {
        tmp = Double.parseDouble(maxBandwidth);
      } catch (NumberFormatException nfe) {
        throw new IllegalArgumentException("The given value is a not valid value for setRequestedMaxBandwidth. Use a positive number or the string \"unlimited\"",nfe); 
      }
      
      //server sends 0.0 to represent UNLIMITED
      Number.verifyPositive(tmp,serverCall?Number.ACCEPT_ZERO:Number.DONT_ACCEPT_ZERO);
      
      this.requestedMaxBandwidth = tmp;
      
      log.info("Max Bandwidth value changed to " + this.requestedMaxBandwidth);
    }
    
    this.internalListener.onPropertyChange("requestedMaxBandwidth");
    this.eventDispatcher.dispatchEvent(new ClientListenerPropertyChangeEvent("requestedMaxBandwidth"));
  }
  
  public synchronized void setPollingInterval(long pollingInterval) {
    Number.verifyPositive(pollingInterval,Number.ACCEPT_ZERO);
    
    this.pollingInterval = pollingInterval;
    
    this.eventDispatcher.dispatchEvent(new ClientListenerPropertyChangeEvent("pollingInterval"));
    
    log.info("Polling Interval value changed to " + this.pollingInterval);
  }
  
  public synchronized void setReconnectTimeout(long reconnectTimeout) {
    Number.verifyPositive(reconnectTimeout,Number.DONT_ACCEPT_ZERO);
    
    this.reconnectTimeout = reconnectTimeout;
    
    this.eventDispatcher.dispatchEvent(new ClientListenerPropertyChangeEvent("reconnectTimeout"));
    
    log.info("Reconnect Timeout value changed to " + this.reconnectTimeout);
  }
  
  public synchronized void setRetryDelay(long retryDelay) {
    Number.verifyPositive(retryDelay,Number.DONT_ACCEPT_ZERO);
    
    this.currentRetryDelay.reset(retryDelay);
    this.currentConnectTimeout.reset(retryDelay);
    
    this.eventDispatcher.dispatchEvent(new ClientListenerPropertyChangeEvent("retryDelay"));
    
    log.info("Retry Delay value changed to " + retryDelay);
  }

  public synchronized void setReverseHeartbeatInterval(long reverseHeartbeatInterval) {
    Number.verifyPositive(reverseHeartbeatInterval,Number.ACCEPT_ZERO);
    
    this.reverseHeartbeatInterval = reverseHeartbeatInterval;
    
    this.eventDispatcher.dispatchEvent(new ClientListenerPropertyChangeEvent("reverseHeartbeatInterval"));
    this.internalListener.onPropertyChange("reverseHeartbeatInterval");
    
    log.info("Reverse Heartbeat Interval value changed to " + this.reverseHeartbeatInterval);
    
  }
  
  public synchronized void setServerInstanceAddressIgnored(boolean serverInstanceAddressIgnored) {
    this.serverInstanceAddressIgnored = serverInstanceAddressIgnored;
    
    this.eventDispatcher.dispatchEvent(new ClientListenerPropertyChangeEvent("serverInstanceAddressIgnored"));
    
    log.info("Server Instance Address Ignored flag changed to " + this.serverInstanceAddressIgnored);
  }

  public synchronized void setSlowingEnabled(boolean slowingEnabled) {
    this.slowingEnabled = slowingEnabled;
    
    this.eventDispatcher.dispatchEvent(new ClientListenerPropertyChangeEvent("slowingEnabled"));
    
    log.info("Slowing Enabled flag changed to " + this.slowingEnabled);
  }

  public synchronized void setStalledTimeout(long stalledTimeout) {
    Number.verifyPositive(stalledTimeout,Number.DONT_ACCEPT_ZERO);
    
    this.stalledTimeout = stalledTimeout;
    
    this.eventDispatcher.dispatchEvent(new ClientListenerPropertyChangeEvent("stalledTimeout"));
    
    log.info("Stalled Timeout value changed to " + this.stalledTimeout);
  }
  
  public synchronized void setSessionRecoveryTimeout(long sessionRecoveryTimeout) {
    Number.verifyPositive(sessionRecoveryTimeout,Number.ACCEPT_ZERO);

    this.sessionRecoveryTimeout = sessionRecoveryTimeout;
    
    this.eventDispatcher.dispatchEvent(new ClientListenerPropertyChangeEvent("sessionRecoveryTimeout"));
    
    log.info("Session Recovery Timeout value changed to " + this.sessionRecoveryTimeout);
  }
  
  public synchronized void setSwitchCheckTimeout(long switchCheckTimeout) {
    this.switchCheckTimeout = switchCheckTimeout;
  }
  
  public synchronized void setProxy(Proxy proxy) {
    this.proxy = proxy;
    
    this.eventDispatcher.dispatchEvent(new ClientListenerPropertyChangeEvent("proxy"));
    
    log.info("Proxy configuration changed " + this.proxy);
  }

    /**
     * Returns the {@code EventDispatcher}.
     * 
     * @deprecated This method is meant to be used ONLY as a workaround for iOS implementation, as
     *             it requires to send a non Unified API and platform specific event through the
     *             {@code ClientListener} interface.
     *             
     * @return the {@code EventDispatcher}
     */
    @Deprecated
    public EventDispatcher<ClientListener> getEventDisapatcher() {
        return eventDispatcher;
    }
  
    // xDomainStreamingEnabled - cookieHandlingRequired - spinFixTimeout - spinFixEnabled -
    // corsXHREnabled -> JS only
    
}
