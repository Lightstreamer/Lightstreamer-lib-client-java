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

import com.lightstreamer.client.platform_data.offline.OfflineStatus;
import com.lightstreamer.client.platform_data.offline.OfflineStatus.NetworkStatusListener;

/**
 * 
 */
public class OfflineCheck {

  private static final int MAYBE_ONLINE_TIMEOUT = 20000;
  private static final int OFFLINE_CHECKS_PROTECTION = 1;
  private static final long OFFLINE_TIMEOUT = 1000;
  int maybeOnline = 1;
  int maybePhase = 1;
  private SessionThread thread;
  
  
  public OfflineCheck(SessionThread thread) {
    this.thread = thread;
  }

  public boolean shouldDelay(String server) {
    if (OfflineStatus.isOffline(server)) {
      if (maybeOnline <= 0) { //first time (1) we try anyway
        return true;
      } else {
        maybeOnline--;
        if (maybeOnline == 0) {
          final int ph = this.maybePhase;
          
          //avoid to lock on the offline flag, once in MAYBE_ONLINE_TIMEOUT seconds reset the flag
          thread.schedule(new Runnable() {
            @Override
            public void run() {
              resetMaybeOnline(ph);
            }          
          },MAYBE_ONLINE_TIMEOUT);
          
        }
      }
      
    }
    return false;
  }
  
  public void resetMaybeOnline() {
    this.resetMaybeOnline(this.maybePhase);
  }
  
  private void resetMaybeOnline(int mp) {
    if (mp != maybePhase) {
      return;
    }
    maybePhase++;
    maybeOnline = OFFLINE_CHECKS_PROTECTION;
  }
  
  public long getDelay() {
    return OFFLINE_TIMEOUT;
  }
  
  public void addStatusListener(NetworkStatusListener listener, String server) {
      OfflineStatus.getDefault().addListener(listener, server);
  }
  
  public void removeStatusListener(NetworkStatusListener listener) {
      OfflineStatus.getDefault().removeListener(listener);
  }

}
