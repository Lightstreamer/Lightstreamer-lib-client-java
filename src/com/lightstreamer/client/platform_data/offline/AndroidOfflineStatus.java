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
