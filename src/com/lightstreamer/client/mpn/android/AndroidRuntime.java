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
