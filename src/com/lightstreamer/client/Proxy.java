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
package com.lightstreamer.client;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;



/**
 * Simple class representing a Proxy configuration. <BR>
 * 
 * An instance of this class can be used through {@link ConnectionOptions#setProxy(Proxy)} to
 * instruct a LightstreamerClient to connect to the Lightstreamer Server passing through a proxy.
 */
public class Proxy {
  
  protected String host;
  protected int port;
  protected String user;
  protected String password;
  protected String type;
  
  public /* @exclude */ Proxy(@Nonnull Proxy proxy) {
    this(proxy.type,proxy.host,proxy.port,proxy.user,proxy.password);
  }

  /**
   * This constructor will call {@link #Proxy(String, String, int, String, String)}
   * specifying null user and null password.
   * @param type the proxy type
   * @param host the proxy host
   * @param port the proxy port
   */
  public Proxy(@Nonnull String type, @Nonnull String host, int port) {
    this(type,host,port,null,null);
  }
  
  /**
   * This constructor will call {@link #Proxy(String, String, int, String, String)}
   * specifying a null null password.
   * @param type the proxy type
   * @param host the proxy host
   * @param port the proxy port
   * @param user the user name to be used to validate against the proxy
   */
  public Proxy(@Nonnull String type, @Nonnull String host, int port, @Nullable String user) {
    this(type,host,port,user,null);
  }
  
  /**
   * Creates a Proxy instance containing all the information required by the {@link LightstreamerClient}
   * to connect to a Lightstreamer server passing through a proxy. <BR>
   * Once created the Proxy instance has to be passed to the {@link LightstreamerClient#connectionOptions}
   * instance using the {@link ConnectionOptions#setProxy(Proxy)} method.
   * 
BEGIN_ANDROID_DOC_ONLY
   * <BR><BR>
   * Note: user and password are ignored. If authentication is required by the proxy in use
   * it is necessary to replace the default java {@link java.net.Authenticator} with a custom one containing 
   * the necessary logic to authenticate the user against the proxy.  
END_ANDROID_DOC_ONLY
   *
   * @param type the proxy type
   * @param host the proxy host
   * @param port the proxy port
   * @param user the user name to be used to validate against the proxy
   * @param password the password to be used to validate against the proxy
   */
  public Proxy(@Nonnull String type, @Nonnull String host, int port, @Nullable String user, @Nullable String password) {
    if (!Constants.PROXY_TYPES.contains(type)) {
      throw new IllegalArgumentException("The given type is not valid. Use one of: HTTP, SOCKS4 or SOCKS5");
    }
    
    this.host = host;
    this.port = port;
    this.user = user;
    this.password = password;
    this.type = type;
  }
  
  @Override
  public String toString() {
    return this.host+":"+this.port;
  }
  
  @Override
  public boolean equals(Object obj) {
    
    if (obj == null) {
      return false;
    }
    
    Proxy proxy = (Proxy) obj;
    boolean isEqual = true;
    
    isEqual &= this.port == proxy.port;
    
    if (this.host == null) {
      isEqual &= proxy.host == null;
    } else {
      isEqual &= this.host.equals(proxy.host);
    }
    
    if (this.user == null) {
      isEqual &= proxy.user == null;
    } else {
      isEqual &= this.user.equals(proxy.user);
    }
    
    if (this.password == null) {
      isEqual &= proxy.password == null;
    } else {
      isEqual &= this.password.equals(proxy.password);
    }
    
    isEqual &= this.type.equals(proxy.type);
    
    return isEqual;
    
  }
  
  @Override 
  public int hashCode() {
    return Objects.hash(this.host, this.type, this.port, this.user, this.password);
  }
  
}
