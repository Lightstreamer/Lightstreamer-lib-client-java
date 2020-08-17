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
 *  <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
 */
public class AndroidOfflineStatus implements OfflineStatusInterface {
  
  @Override
  public boolean isOffline(String server) {
    
    //TODO need to add the android stubs
    //http://developer.android.com/reference/android/net/ConnectivityManager.html
    
    //TODO how do I get Context?
    /*ConnectivityManager cm =
        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo netInfo = cm.getActiveNetworkInfo();
    return netInfo == null || !netInfo.isConnected();*/
    
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
