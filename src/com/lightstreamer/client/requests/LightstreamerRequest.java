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


package com.lightstreamer.client.requests;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 
 */
public abstract class LightstreamerRequest {
  
  private final StringBuilder buffer = new StringBuilder(256);
  private String targetServer;
  private String session;
  
  protected static AtomicInteger unique = new AtomicInteger(1);
  
  public void setServer(String targetServer) {
    //targetServer might be ignored (e.g. in case of a websocket connection)
    this.targetServer = targetServer;
  }
  
  public void setSession(String session) {
    this.session = session;
  }
  
  public String getSession() {
      return session;
  }

  public abstract String getRequestName();
  
  protected static String encode(String value) {
    /* encodes too much:
    try {
      return URLEncoder.encode(value,"UTF-8");
    } catch (UnsupportedEncodingException e) {
      //can't happen
    }
    return null;
    */

    return percentEncodeTLCP(value);
  }
  
  protected static void addUnquotedParameter(StringBuilder buffer, String name, String value) {
    buffer.append(name);
    buffer.append("=");
    buffer.append(value);
    //buffer.append("&"); supposed to be the last parameter in the request
    
    //prepend the unquoted size
    int len = buffer.length();
    buffer.insert(0,"&");
    buffer.insert(0,len);
    buffer.insert(0,"LS_unq=");
    
  }
  
  protected static void addParameter(StringBuilder buffer, String name, String value) {
    buffer.append(name);
    buffer.append("=");
    buffer.append(encode(value));
    buffer.append("&");
  }
  
  protected static void addParameter(StringBuilder buffer, String name, double value) {
    buffer.append(name);
    buffer.append("=");
    String doubleString = Double.toString(value);
    if (doubleString.endsWith(".0")) {
      buffer.append(doubleString.substring(0, doubleString.length()-2));
    } else {
      buffer.append(doubleString);
    }
    buffer.append("&");
  }
  
  protected static void addParameter(StringBuilder buffer, String name, long value) {
    buffer.append(name);
    buffer.append("=");
    buffer.append(value);
    buffer.append("&");
  }
  
  protected void addParameter(String name, String value) {
    addParameter(this.buffer,name,value);
  }
  
  protected void addParameter(String name, double value) {
    addParameter(this.buffer,name,value);
  }
  
  protected void addParameter(String name, long value) {
    addParameter(this.buffer,name,value);
  }
  
  public void addUnique() {
    this.addParameter("LS_unique", LightstreamerRequest.unique.getAndIncrement());
  }
  
  protected StringBuilder getQueryStringBuilder(String defaultSessionId) {
    StringBuilder result = new StringBuilder(buffer);
    if (this.session != null) {
        boolean sessionUnneeded = (defaultSessionId != null && defaultSessionId.equals(this.session));
        if (! sessionUnneeded) {
            /*
             * LS_session is written when there is no default sessionId or the default is different from this.session
             * (the last case should never happen in practically)
             */
            addParameter(result, "LS_session", this.session);
        }
    }
    if (result.length() == 0) {
        /* empty query string is not allowed by the server: add an empty line */
        result.append("\r\n");
    }
    return result;
  }
  
  public String getTransportUnawareQueryString() {
    return this.getQueryStringBuilder(null).toString();
  }

  public String getTransportAwareQueryString(String defaultSessionId, boolean ackIsForced) {
    return this.getQueryStringBuilder(defaultSessionId).toString();
  }

  public String getTargetServer() {
    return targetServer;
  }
  
  public boolean isSessionRequest() {
    return false;
  }
  
  /**
   * Method percentEncodeTLCP.
   * Operates percent-encoding, but only on the reserved characters of the TLCP syntax
   * (in particular, all non-ascii characters are preserved).
   *
   * @param  str  ...
   */
  private static String percentEncodeTLCP(String str) {
      int specials = 0;
      // preliminary step to determine the amount of memory needed
      int len = str.length();
      for (int i = 0; i < len; i++) {
          char c = str.charAt(i);
          if (isSpecial(c)) {
              specials++;
          }
      }

      if (specials > 0) {
          int quotedLength = len + specials * 2;
          char[] quoted = new char[quotedLength];
          int j = 0;

          for (int i = 0; i < len; i++) {
              char c = str.charAt(i);
              if (isSpecial(c)) {
                  assert((c & 0x7F) == c);
                  // percent-encoding; must be UTF-8, but we only have
                  // to encode simple ascii characters
                  quoted[j++] = '%';
                  quoted[j++] = (char) (hex[(c >> 4) & 0xF]);
                  quoted[j++] = (char) (hex[c & 0xF]);
              } else {
                  quoted[j++] = c;
              }
          }
          assert(j == quotedLength);
          return new String(quoted);
      } else {
          return str;
      }
  }
  
  private static final byte[] hex = new byte[16];

  static {
      for (int i = 0; i <= 9; i++) {
          hex[i] = (byte) ('0' + i);
      }
      for (int i = 10; i < 16; i++) {
          hex[i] = (byte) ('A' + (i - 10));
      }
  }

  private static final boolean isSpecial(int b) {
      if ((b == '\r') || (b == '\n')) {
          // line delimiters
          return true;
      } else if (b == '%' || b == '+') {
          // used for percent-encoding
          return true;
      } else if ((b == '&') || (b == '=')) {
          // parameter delimiters
          return true;
      } else {
          // includes all non-ascii characters
          return false;
      }
  }
  
  @Override
  public String toString() {
      return getRequestName() + " " + buffer.toString();
  }

}
