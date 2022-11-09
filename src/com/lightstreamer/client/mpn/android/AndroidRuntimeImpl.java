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

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;

/**
 * Facade over Android runtime.
 * Mobile push notifications are provided by Firebase.
 * 
 * 
 * @since July 2017
 */
public class AndroidRuntimeImpl implements AndroidRuntime {
    
    private static final String PREFS_REG_ID = "LS_registration_id";
    
    @Override
    public boolean isGooglePlayServicesAvailable(Context appContext) {
        return GooglePlayServicesUtil.isGooglePlayServicesAvailable(appContext) == ConnectionResult.SUCCESS;
    }

    @Override
    public String getPackageName(Context appContext) {
        return appContext.getPackageName();
    }

    @Override
    public int getPackageVersion(Context appContext) {
        String packageName = getPackageName(appContext);
        try {
            return appContext.getPackageManager().getPackageInfo(packageName, 0).versionCode;
        } catch (NameNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String readTokenFromSharedPreferences(Context appContext) {
        String packageName = getPackageName(appContext);
        final SharedPreferences prefs = appContext.getSharedPreferences(packageName, Context.MODE_PRIVATE);
        return prefs.getString(PREFS_REG_ID, null);
    }

    @Override
    public void writeTokenToSharedPreferences(Context appContext, String value) {
        String packageName = getPackageName(appContext);
        final SharedPreferences prefs = appContext.getSharedPreferences(packageName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor= prefs.edit();
        editor.putString(PREFS_REG_ID, value);
        editor.commit();
    }
    
    @Override
    public String getToken(String authorizedEntity, String scope) throws IOException {
        throw new IOException();
    }

    @Override
    public String getToken() throws IOException {
        throw new IOException();
    }
}
