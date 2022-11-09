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


package com.lightstreamer.client.mpn;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.lightstreamer.client.LightstreamerClient;

/**
 * Interface representing a device that supports Mobile Push Notifications (MPN).<BR>
 * It contains device details and the listener needed to monitor its status.<BR>
 * After creation, an MpnDevice object is in "unknown" state. It must then be passed to the Lightstreamer Server with the
 * {@link LightstreamerClient#registerForMpn(MpnDeviceInterface)} method, which enables the client to subscribe MPN subscriptions and sends the device details to the
 * server's MPN Module, where it is assigned a permanent device ID and its state is switched to "registered".<BR>
 * Upon registration on the server, active MPN subscriptions of the device are received and exposed with the {@link LightstreamerClient#getMpnSubscriptions(String)}
 * method.<BR>
 * An MpnDevice's state may become "suspended" if errors occur during push notification delivery. In this case MPN subscriptions stop sending notifications
 * and the device state is reset to "registered" at the first subsequent registration.<BR>
 * <B>Note</B>: this interface is public due to implementation side effects. It is not meant to be implemented by the user.
 * See {@link com.lightstreamer.client.mpn.android.MpnDevice} for its implementation.  
 */
public interface MpnDeviceInterface {

    /**
     * Adds a listener that will receive events from the MPN device object.<BR>
     * The same listener can be added to several different MPN device objects.<BR>
     * 
     * @lifecycle A listener can be added at any time. A call to add a listener already present will be ignored.
     * 
     * @param listener An object that will receive the events as documented in the {@link MpnDeviceListener} interface.
     * 
     * @see #removeListener(MpnDeviceListener)
     */
    // Keep javadoc in sync with AbstractMpnDevice
    void addListener(@Nonnull MpnDeviceListener listener);

    /**
     * Removes a listener from the MPN device object so that it will not receive events anymore.
     * 
     * @lifecycle A listener can be removed at any time.
     * 
     * @param listener The listener to be removed.
     * 
     * @see #addListener(MpnDeviceListener)
     */
    // Keep javadoc in sync with AbstractMpnDevice
    void removeListener(@Nonnull MpnDeviceListener listener);

    /**
     * The platform identifier of this MPN device. In the {@link com.lightstreamer.client.mpn.android.MpnDevice} implementation it equals to the constant <code>Google</code>
     * and is used by the server as part of the device identification.
     *  
     * @lifecycle This method can be called at any time.
     * 
     * @return the MPN device platform.
     */
    // Keep javadoc in sync with MpnDevice
    @Nonnull
    String getPlatform();

    /**
     * The application ID of this MPN device, corresponding to the package name of the application. In the {@link com.lightstreamer.client.mpn.android.MpnDevice} 
     * implementation it is determined automatically from the Application Context during creation and is used by the server as part of the device identification.
     * 
     * @lifecycle This method can be called at any time.
     * 
     * @return the MPN device application ID.
     */
    // Keep javadoc in sync with AbstractMpnDevice
    @Nonnull
    String getApplicationId();

    /**
     * The device token of this MPN device. In the {@link com.lightstreamer.client.mpn.android.MpnDevice} implementation it is passed during creation and 
     * is used by the server as part of the device identification.
     * 
     * @lifecycle This method can be called at any time.
     * 
     * @return the MPN device token.
     */
    // Keep javadoc in sync with AbstractMpnDevice
    @Nonnull
    String getDeviceToken();

    /**
     * The previous device token of this MPN device. In the {@link com.lightstreamer.client.mpn.android.MpnDevice} implementation it is obtained automatically from 
     * the Shared Preferences storage during creation and is used by the server to restore MPN subscriptions associated with this previous token. May be null if 
     * no MPN device has been registered yet on the application.
     * 
     * @lifecycle This method can be called at any time.
     * 
     * @return the previous MPN device token, or null if no MPN device has been registered yet.
     */
    // Keep javadoc in sync with AbstractMpnDevice
    @Nullable
    String getPreviousDeviceToken();

    /**
     * Checks whether the MPN device object is currently registered on the server or not.<BR>
     * This flag is switched to true by server sent registration events, and back to false in case of client disconnection or server sent suspension events.
     * 
     * @lifecycle This method can be called at any time.
     * 
     * @return true if the MPN device object is currently registered on the server.
     * 
     * @see #getStatus()
     */
    // Keep javadoc in sync with AbstractMpnDevice
    boolean isRegistered();

    /**
     * Checks whether the MPN device object is currently suspended on the server or not.<BR>
     * An MPN device may be suspended if errors occur during push notification delivery.<BR>
     * This flag is switched to true by server sent suspension events, and back to false in case of client disconnection or server sent resume events.
     * 
     * @lifecycle This method can be called at any time.
     * 
     * @return true if the MPN device object is currently suspended on the server.
     * 
     * @see #getStatus()
     */
    // Keep javadoc in sync with AbstractMpnDevice
    boolean isSuspended();

    /**
     * The status of the device.<BR>
     * The status can be:<ul>
     * <li><code>UNKNOWN</code>: when the MPN device object has just been created or deleted. In this status {@link #isRegistered()} and {@link #isSuspended()} are both false.</li>
     * <li><code>REGISTERED</code>: when the MPN device object has been successfully registered on the server. In this status {@link #isRegistered()} is true and
     * {@link #isSuspended()} is false.</li>
     * <li><code>SUSPENDED</code>: when a server error occurred while sending push notifications to this MPN device and consequently it has been suspended. In this status 
     * {@link #isRegistered()} and {@link #isSuspended()} are both true.</li>
     * </ul>
     * 
     * @lifecycle This method can be called at any time.
     * 
     * @return the status of the device.
     * 
     * @see #isRegistered()
     * @see #isSuspended()
     */
    // Keep javadoc in sync with AbstractMpnDevice
    @Nonnull
    String getStatus();

    /**
     * The server-side timestamp of the device status.
     * 
     * @lifecycle This method can be called at any time.
     * 
     * @return The server-side timestamp of the device status, expressed as a Java time.
     * 
     * @see #getStatus()
     */
    // Keep javadoc in sync with AbstractMpnDevice
    long getStatusTimestamp();
    
    /**
     * The server-side unique persistent ID of the device.<BR>
     * The ID is available only after the MPN device object has been successfully registered on the server. I.e. when its status is <code>REGISTERED</code> or
     * <code>SUSPENDED</code>.<BR>
     * Note: a device token change, if the previous device token was correctly stored on the Shared Preferences storage, does not cause the device ID to change: the
     * server moves previous MPN subscriptions from the previous token to the new one and the device ID remains unaltered.
     * 
     * @lifecycle This method can be called at any time.
     * 
     * @return the MPN device ID.
     */
    // Keep javadoc in sync with AbstractMpnDevice
    @Nullable
    String getDeviceId();
    
    /**
     * List containing the {@link MpnDeviceListener} instances that were added to this MPN device object.
     * 
     * @return a list containing the listeners that were added to this device.
     * 
     * @see #addListener(MpnDeviceListener)
     */
    // Keep javadoc in sync with AbstractMpnDevice
    @Nonnull
    List<MpnDeviceListener> getListeners();
}