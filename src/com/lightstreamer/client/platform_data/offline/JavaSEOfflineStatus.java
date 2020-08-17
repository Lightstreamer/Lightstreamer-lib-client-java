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

import com.lightstreamer.client.platform_data.offline.OfflineStatus.NetworkStatusListener;
import com.lightstreamer.client.platform_data.offline.OfflineStatus.OfflineStatusInterface;

/**
 * 
 */
public class JavaSEOfflineStatus implements OfflineStatusInterface {
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
}
