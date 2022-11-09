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
