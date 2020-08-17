package com.lightstreamer.client.mpn.android;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.lightstreamer.client.LightstreamerClient;
import com.lightstreamer.client.mpn.AbstractMpnDevice;
import com.lightstreamer.client.mpn.MpnDeviceInterface;
import com.lightstreamer.client.mpn.MpnSubscription;

import android.content.Context;

/**
 * Class representing a device that supports Mobile Push Notifications (MPN).<BR>
 * It contains device details and the listener needed to monitor its status.<BR>
 * An MPN device is created from the application context, the sender ID (a.k.a. authorized entity) and a device token (a.k.a. registration token) obtained from 
 * Firebase Cloud Messaging APIs, and must be registered on the {@link LightstreamerClient} in order to successfully subscribe an MPN subscription. 
 * See {@link MpnSubscription}.<BR>
 * After creation, an MpnDevice object is in "unknown" state. It must then be passed to the Lightstreamer Server with the
 * {@link LightstreamerClient#registerForMpn(MpnDeviceInterface)} method, which enables the client to subscribe MPN subscriptions and sends the device details to the
 * server's MPN Module, where it is assigned a permanent device ID and its state is switched to "registered".<BR>
 * Upon registration on the server, active MPN subscriptions of the device are received and exposed with the {@link LightstreamerClient#getMpnSubscriptions(String)}
 * method.<BR>
 * An MpnDevice's state may become "suspended" if errors occur during push notification delivery. In this case MPN subscriptions stop sending notifications
 * and the device state is reset to "registered" at the first subsequent registration.
 */
public class MpnDevice extends AbstractMpnDevice {
    
    static {
        /*
         * Initialize the Android runtime.
         * Test classes should call AndroidRuntimeFactory.setRuntime() to override the default runtime.
         */
        AndroidRuntimeFactory.initRuntime(new AndroidRuntimeImpl());
    }
        
    /**
     * Creates an object to be used to describe an MPN device that is going to be registered to the MPN Module of Lightstreamer Server.<BR>
     * During creation the MpnDevice tries to acquires some more details:<ul>
     * <li>The package name, through the Application Context.</li>
     * <li>Any previously registered device token, from the Shared Preferences storage.</li>
     * </ul>
     * It then saves the current device token on the Shared Preferences storage. Saving and retrieving the previous device token is used to handle automatically
     * the cases where the token changes, such as when the app state is restored from a device backup. The MPN Module of Lightstreamer Server is able to move
     * MPN subscriptions associated with the previous token to the new one.
     * 
     * @param appContext the Application Context
     * @param token the device token
     * 
     * @throws IllegalArgumentException if {@code context}, {@code senderId} or {@code token} are null.
     */
    public MpnDevice(final @Nonnull Context appContext, final @Nonnull String token) {
        /*
         *  See old MpnGCMRegistrar class
         */

        if (appContext == null) {
            throw new IllegalArgumentException("Please specify a valid appContext");
        }
        
        if (token == null) {
            throw new IllegalArgumentException("Please specify a valid token");
        }
        
        AndroidRuntime rt = AndroidRuntimeFactory.getRuntime();
        
        /* Check if classes for Google Play Services are there */
        try {
            Class.forName("com.google.android.gms.common.GooglePlayServicesUtil");
        
        } catch (Exception e) {
            log.debug("Exception while looking for Google Play Services: " + e.getMessage());
            throw new IllegalStateException("Couldn't check for availability of Google Play Services");
        }
        
        /* Check if Google Play Services are available */
        if (! rt.isGooglePlayServicesAvailable(appContext)) {
            log.debug("Google Play Services not available");
            throw new IllegalStateException("Google Play Services Not Available");
        }
        
        // Check last registration ID
        final String previousToken = rt.readTokenFromSharedPreferences(appContext);
        if (previousToken != null) {
            log.debug("Previous registration ID found (" + previousToken + ")");
        } else {
            log.debug("No previous registration ID found");
        }
        
        log.debug("Registration ID obtained (" + token + "), storing...");
        rt.writeTokenToSharedPreferences(appContext, token);
        log.debug("Registration ID stored");
        
        // Extract app package name 
        final String packageName = rt.getPackageName(appContext);

        /* Set device attributes */
        properties.setApplicationId(packageName);
        properties.setDeviceToken(token);
        properties.setPrevDeviceToken(previousToken);
    }
    
    /**
     * The platform identifier of this MPN device. It equals to the constant <code>Google</code> and is used by the server as part of the device identification.
     * 
     * @lifecycle This method can be called at any time.
     * 
     * @return the MPN device platform.
     */
    // Keep javadoc in sync with MpnDeviceInterface
    @Override
    public String getPlatform() {
        return "Google";
    }
}
