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
