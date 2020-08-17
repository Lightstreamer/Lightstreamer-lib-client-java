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
package com.lightstreamer.client.platform_data.offline;

import javax.annotation.concurrent.GuardedBy;

import com.lightstreamer.util.AlternativeLoader;

/**
 * 
 */
public class OfflineStatus {

  private static final AlternativeLoader<OfflineStatusInterface> loader = new AlternativeLoader<OfflineStatusInterface>() {
    @Override
    protected String[] getDefaultClassNames() {
      String[] classes = { "com.lightstreamer.client.platform_data.offline.JavaSEOfflineStatus", 
          "com.lightstreamer.client.platform_data.offline.AndroidOfflineStatus" };
 
      return classes;
    }   
  };
  
  @GuardedBy("OfflineStatus.class")
  private static OfflineStatusInterface implementation;
  
  public static synchronized void setDefault(OfflineStatusInterface givenImplementation) {
    if (givenImplementation == null) {
      throw new IllegalArgumentException("Specify an implementation");
    }
    implementation = givenImplementation;
  }
  
  public static synchronized OfflineStatusInterface getDefault() {
      if (implementation == null) {
          implementation = loader.getAlternative();

          if (implementation == null) {
              System.err.println("NO OFFLINE-CHECK CLASS AVAILABLE, SOMETHING WENT WRONG AT BUILD TIME, CONTACT LIGHTSTREAMER SUPPORT");
              implementation = new OfflineStatusInterface() {
                  @Override
                  public boolean isOffline(String server) {
                      return false;
                  }
                  @Override
                  public void addListener(NetworkStatusListener listener, String server) {
                      // network status not available
                  }
                  @Override
                  public void removeListener(NetworkStatusListener listener) {
                      // network status not available
                  }
              };
          }
      }
      return implementation;
  }
  
  public static boolean isOffline(String server) {
    return getDefault().isOffline(server);
  }
  
  
  public interface OfflineStatusInterface {
    public boolean isOffline(String server);
    public void addListener(NetworkStatusListener listener, String server);
    public void removeListener(NetworkStatusListener listener);
  }

  public interface NetworkStatusListener {
      public void onOffline();
      public void onOnline();
  }
}
