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

/**
 * 
 */
public class RequestsHelper {
  
  private static class MyHandmadeURL {

      public final String schema;
      public final String host;
      public final String port;
      public final String path;
      
      public MyHandmadeURL(String schema, String host, String port, String path) {
          this.schema = schema;
          this.host = host;
          this.port = port;
          this.path = path;
      }

      public MyHandmadeURL(String url) {
          int schemaEnd = url.indexOf("://");
          if (schemaEnd != -1) {
              schema = url.substring(0, schemaEnd);
              url = url.substring(schemaEnd + 3);
          } else {
              schema = null;
          }
          int pathStart = url.indexOf("/");
          if (pathStart != -1) {
              path = url.substring(pathStart);
              url = url.substring(0, pathStart);
          } else {
              path = null;
          }
          int portStart = extractPortStart(url);
          if (portStart != -1) {
              port = url.substring(portStart);
              host = url.substring(0, portStart - 1);
          } else {
              port = null;
              host = url;
          }
      }
      
      public String getURL() {
          String url = host;
          if (schema != null) {
              url = schema + "://" + url;
          }
          if (port != null) {
              url += ":" + port;
          }
          if (path != null) {
              url += path;
          }
          if (url.substring(url.length() - 1) != "/") {
              url += "/";
          }
          return url;
      }
      
      private static int extractPortStart(String address) {
          int portStarts = address.indexOf(":");
          if (portStarts <= -1) {
              return -1;
          }
          if (address.indexOf("]") > -1) {
              portStarts = address.indexOf("]:");
              if (portStarts <= -1) {
                  return -1;
              }
              return portStarts + 2;
          } else if (portStarts != address.lastIndexOf(":")) {
              return -1;
          } else {
              return portStarts + 1;
          }
      }
  }
  
  /**
   * @param addressToUse
   * @param controlLink
   * @return
   */
  public static String completeControlLink(String extractFrom, String controlLink) {
      MyHandmadeURL baseUrl = new MyHandmadeURL(extractFrom);
      MyHandmadeURL clUrl = new MyHandmadeURL(controlLink);
      
      MyHandmadeURL fullClUrl = new MyHandmadeURL(
              clUrl.schema != null ? clUrl.schema : baseUrl.schema,
              clUrl.host,
              clUrl.port != null ? clUrl.port : baseUrl.port,
              clUrl.path);
      return fullClUrl.getURL();
  }
  
}
