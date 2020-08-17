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
