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

import java.util.HashSet;
import java.util.Set;

public /* @exclude */ class Constants {
    
  public static final String TLCP_VERSION = "TLCP-2.1.0";
  
  /**
   * WARNING
   * if the flag is true, change TLCP_VERSION from 2.1.0 to 2.2.0
   */
  public static final boolean handleError5 = false;
  
  public static final String ACTIONS_LOG = "lightstreamer.actions";
  public static final String SESSION_LOG = "lightstreamer.session";
  public static final String SUBSCRIPTIONS_LOG = "lightstreamer.subscribe";
  public static final String PROTOCOL_LOG = "lightstreamer.protocol";
  public static final String TRANSPORT_LOG = "lightstreamer.stream";
  public static final String THREADS_LOG = "lightstreamer.threads";
  public static final String NETTY_LOG = "lightstreamer.netty";
  public static final String NETTY_POOL_LOG = "lightstreamer.netty.pool";
  public static final String REQUESTS_LOG = "lightstreamer.requests";
  public static final String UTILS_LOG = "lightstreamer.utils";
  public static final String MPN_LOG = "lightstreamer.mpn";
  public static final String HEARTBEAT_LOG = "lightstreamer.heartbeat";

  
  public static final String UNLIMITED =  "unlimited";
  public static final String AUTO = "auto";
  
  public static final Set<String> FORCED_TRANSPORTS = new HashSet<String>();
  static {
    FORCED_TRANSPORTS.add("HTTP");
    FORCED_TRANSPORTS.add("HTTP-POLLING");
    FORCED_TRANSPORTS.add("HTTP-STREAMING");
    FORCED_TRANSPORTS.add("WS");
    FORCED_TRANSPORTS.add("WS-POLLING");
    FORCED_TRANSPORTS.add("WS-STREAMING");
    FORCED_TRANSPORTS.add(null);
  }
  
  public static final Set<String> PROXY_TYPES = new HashSet<String>();
  static {
    PROXY_TYPES.add("HTTP");
    PROXY_TYPES.add("SOCKS4");
    PROXY_TYPES.add("SOCKS5");
  }
  
  public static final String COMMAND = "COMMAND";
  public static final String RAW = "RAW";
  public static final String MERGE = "MERGE";
  public static final String DISTINCT = "DISTINCT";
  
  public static final String UNORDERED_MESSAGES = "UNORDERED_MESSAGES";
  
  public static final Set<String> MODES = new HashSet<String>();
  static {
    MODES.add(MERGE);
    MODES.add(COMMAND);
    MODES.add(DISTINCT);
    MODES.add(RAW);
  }
  
  public static final String DISCONNECTED = "DISCONNECTED";
  public static final String WILL_RETRY = DISCONNECTED+":WILL-RETRY";
  public static final String TRYING_RECOVERY = DISCONNECTED+":TRYING-RECOVERY";
  public static final String CONNECTING = "CONNECTING";
  public static final String CONNECTED = "CONNECTED:";
  public static final String STALLED = "STALLED";
  public static final String HTTP_POLLING = "HTTP-POLLING";
  public static final String HTTP_STREAMING = "HTTP-STREAMING";
  public static final String SENSE = "STREAM-SENSING";
  public static final String WS_STREAMING = "WS-STREAMING";
  public static final String WS_POLLING = "WS-POLLING";
  
  public static final String WS_ALL = "WS";
  public static final String HTTP_ALL = "HTTP";
  
  public static final String DELETE = "DELETE";
  public static final String UPDATE = "UPDATE";
  public static final String ADD = "ADD";
  
  /**
   * Number of milliseconds after which an idle socket is closed.
   */
  public static final long CLOSE_SOCKET_TIMEOUT_MILLIS = 5000;
  
}
