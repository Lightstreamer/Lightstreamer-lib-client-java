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


package com.lightstreamer.client.mpn.android;

import java.io.IOException;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import android.content.Context;

/**
 * Interface of the facade over Android runtime.
 * This should be the only point of access to Android API. 
 * 
 * 
 * @since July 2017
 */
@ParametersAreNonnullByDefault
public interface AndroidRuntime {
    
    boolean isGooglePlayServicesAvailable(Context appContext);

    /**
     * Returns the package name declared in AndroidManifest.xml.
     */
    String getPackageName(Context appContext);
    
    /**
     * Returns the version code declared in AndroidManifest.xml.
     */
    int getPackageVersion(Context appContext);
    
    /**
     * Reads the value of the key LS_registration_id (i.e. the device token) from the Shared Preferences.
     */
    @Nullable String readTokenFromSharedPreferences(Context appContext);
    
    /**
     * Writes the device token value in the Shared Preferences under the key LS_registration_id.
     */
    void writeTokenToSharedPreferences(Context appContext, String value);
    
    /**
     * Reads the device token used by mobile push notifications. Suitable in case of multiple senders. Can be blocking.
     */
    String getToken(String authorizedEntity, String scope) throws IOException; 
    
    /**
     * Reads the device token used by mobile push notifications. Suitable in case of single sender. Can be blocking.
     */
    String getToken() throws IOException; 
}
