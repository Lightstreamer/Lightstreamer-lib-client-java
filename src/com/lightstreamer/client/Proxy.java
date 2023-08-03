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
   * @param type the proxy type. Supported values are HTTP, SOCKS4 and SOCKS5.
   * @param host the proxy host
   * @param port the proxy port
   */
  public Proxy(@Nonnull String type, @Nonnull String host, int port) {
    this(type,host,port,null,null);
  }
  
  /**
   * This constructor will call {@link #Proxy(String, String, int, String, String)}
   * specifying a null null password.
   * @param type the proxy type. Supported values are HTTP, SOCKS4 and SOCKS5.
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
   * @param type the proxy type. Supported values are HTTP, SOCKS4 and SOCKS5.
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
    return "("+this.type+")"+this.host+":"+this.port;
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
