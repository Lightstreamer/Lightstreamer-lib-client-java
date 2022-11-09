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
import javax.annotation.concurrent.ThreadSafe;

import com.lightstreamer.client.Constants;
import com.lightstreamer.client.LightstreamerClient;
import com.lightstreamer.client.events.EventsThread;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;
import com.lightstreamer.util.Assertions;
import com.lightstreamer.util.ListenerHolder;
import com.lightstreamer.util.Visitor;

/**
 * Abstract class representing a device that supports Mobile Push Notifications (MPN).<BR>
 * It contains device details and the listener needed to monitor its status.<BR>
 * <B>Note</B>: this abstract class is public due to implementation side effects. It is not meant to be implemented by the user.
 * See {@link com.lightstreamer.client.mpn.android.MpnDevice} for its concrete subclass.  
 */
public abstract class AbstractMpnDevice implements MpnDeviceInterface {
    
    protected static final Logger log = LogManager.getLogger(Constants.MPN_LOG);
    
    final State UNKNOWN = new UnknownState();
    final State REGISTERED = new RegisteredState();
    final State SUSPENDED = new SuspendedState();
    
    final EventsThread eventThread = EventsThread.instance;
    final DeviceEventManager eventManager = new DeviceEventManager();
    protected final PropertyHolder properties = new PropertyHolder(eventThread);
    
    /*
     * ------------------------------------------
     * 
     * Implementation of MpnDevice interface.
     * 
     * ------------------------------------------
     */
    
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
    // Keep javadoc in sync with MpnDeviceInterface
    @Override
    public void addListener(final MpnDeviceListener listener) {
        properties.addListener(listener, new Visitor<MpnDeviceListener>() {
            @Override
            public void visit(MpnDeviceListener listener) {
                listener.onListenStart(AbstractMpnDevice.this);
            }
        });
    }

    /**
     * Removes a listener from the MPN device object so that it will not receive events anymore.
     * 
     * @lifecycle A listener can be removed at any time.
     * 
     * @param listener The listener to be removed.
     * 
     * @see #addListener(MpnDeviceListener)
     */
    // Keep javadoc in sync with MpnDeviceInterface
    @Override
    public void removeListener(final MpnDeviceListener listener) {
        properties.removeListener(listener, new Visitor<MpnDeviceListener>() {
            @Override
            public void visit(MpnDeviceListener listener) {
                listener.onListenEnd(AbstractMpnDevice.this);
            }
        });
    }
    
    /**
     * List containing the {@link MpnDeviceListener} instances that were added to this MPN device object.
     * 
     * @return a list containing the listeners that were added to this device.
     * 
     * @see #addListener(MpnDeviceListener)
     */
    // Keep javadoc in sync with MpnDeviceInterface
    @Override
    public List<MpnDeviceListener> getListeners() {
        return properties.getListeners();
    }
    
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
    // Keep javadoc in sync with MpnDeviceInterface
    @Override
    public boolean isRegistered() {
        return state().isRegistered;
    }
    
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
    // Keep javadoc in sync with MpnDeviceInterface
    @Override
    public boolean isSuspended()  {
        return state().isSuspended;
    }
    
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
    // Keep javadoc in sync with MpnDeviceInterface
    @Override
    public @Nonnull String getStatus() {
        return state().status;
    }
    
    /**
     * The server-side timestamp of the device status.
     * 
     * @lifecycle This method can be called at any time.
     * 
     * @return The server-side timestamp of the device status, expressed as a Java time.
     * 
     * @see #getStatus()
     */
    // Keep javadoc in sync with MpnDeviceInterface
    @Override
    public long getStatusTimestamp()  {
        return properties.getStatusTimestamp();
    }
    
    /**
     * The application ID of this MPN device, corresponding to the package name of the application. In the {@link com.lightstreamer.client.mpn.android.MpnDevice} 
     * implementation it is determined automatically from the Application Context during creation and is used by the server as part of the device identification.
     * 
     * @lifecycle This method can be called at any time.
     * 
     * @return the MPN device application ID.
     */
    // Keep javadoc in sync with MpnDeviceInterface
    @Override
    public String getApplicationId() {
        return properties.getApplicationId();
    }

    /**
     * The device token of this MPN device. In the {@link com.lightstreamer.client.mpn.android.MpnDevice} implementation it is passed during creation and 
     * is used by the server as part of the device identification.
     * 
     * @lifecycle This method can be called at any time.
     * 
     * @return the MPN device token.
     */
    // Keep javadoc in sync with MpnDeviceInterface
    @Override
    public String getDeviceToken() {
        return properties.getDeviceToken();
    }

    /**
     * The previous device token of this MPN device. In the {@link com.lightstreamer.client.mpn.android.MpnDevice} implementation it is obtained automatically from 
     * the Shared Preferences storage during creation and is used by the server to restore MPN subscriptions associated with this previous token. May be null if 
     * no MPN device has been registered yet on the application.
     * 
     * @lifecycle This method can be called at any time.
     * 
     * @return the previous MPN device token, or null if no MPN device has been registered yet.
     */
    // Keep javadoc in sync with MpnDeviceInterface
    @Override
    public String getPreviousDeviceToken() {
        return properties.getPrevDeviceToken();
    }
    
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
    // Keep javadoc in sync with MpnDeviceInterface
    @Override
    public String getDeviceId() {
        return properties.getDeviceId();
    }
    
    /*
     * ------------------------------------------
     * 
     * Methods exposed to MpnManager.
     * 
     * ------------------------------------------
     */
    
    String getAdapterName() {
        assert Assertions.isSessionThread();
        return properties.getAdapterName();
    }
    
    /**
     * API exposed to {@link MpnManager}
     */
    class DeviceEventManager {
        
        /**
         * Fired when the current session closes.
         * 
         * @param recovery if the flag is true, the closing is due to an automatic recovery.
         * Otherwise the closing is due to a disconnection made by the user
         * (see {@link LightstreamerClient#disconnect()}).
         */
        void onSessionClose(boolean recovery) {
            assert Assertions.isSessionThread();
            if (recovery) {
                // keep current state
                
            } else {
                // reset state
                state().onSessionClose();
                properties.setDeviceId(null);
                properties.setAdapterName(null);
                properties.setStatusTimestamp(0);
            }
        }
        
        void onRegisterOK(String _deviceId, String _adapterName) {
            assert Assertions.isSessionThread();
            properties.setDeviceId(_deviceId);
            properties.setAdapterName(_adapterName);
        }
        
        void onRegisterError(int code, String message) {
            assert Assertions.isSessionThread();
            state().error(code, message);
        }
        
        void onResetBadgeOK() {
//BEGIN_ANDROID_EXCLUDE
            assert Assertions.isSessionThread();
            properties.forEachListener(new Visitor<MpnDeviceListener>() {
                @Override
                public void visit(MpnDeviceListener listener) {
                    listener.onBadgeReset();
                }
            });
//END_ANDROID_EXCLUDE
        }
        
        void onMpnBadgeResetError(final int code, final String message) {
//BEGIN_ANDROID_EXCLUDE
            assert Assertions.isSessionThread();
            properties.forEachListener(new Visitor<MpnDeviceListener>() {
                @Override
                public void visit(MpnDeviceListener listener) {
                    listener.onBadgeResetFailed(code, message);
                }
            });
//END_ANDROID_EXCLUDE
        }
        
        void onStatusChange(String status, long timestamp) {
            assert Assertions.isSessionThread();
            switch (status) {
            case "ACTIVE":
                state().activate(timestamp);
                break;
            case "SUSPENDED":
                state().suspend(timestamp);
                break;
            default:
                throw new AssertionError();
            }
        }
        
        void onSubscriptionsUpdated() {
            assert Assertions.isSessionThread();
            properties.forEachListener(new Visitor<MpnDeviceListener>() {
                @Override
                public void visit(MpnDeviceListener listener) {
                    listener.onSubscriptionsUpdated();
                }
            });
        }
    } // DeviceEventManager
    
    /*
     * ------------------------------------------
     * 
     * States of the device.
     * 
     * ------------------------------------------
     */
    
    State state() {
        return properties.getState();
    }
    
    class UnknownState extends State {
        UnknownState() {
            super(false, false, "UNKNOWN");
        }
        
        @Override
        void activate(long timestamp) {
            properties.setStatusTimestamp(timestamp);
            next(REGISTERED, "activate");
            fireOnRegistered();
        }

        @Override
        void suspend(long timestamp) {
            properties.setStatusTimestamp(timestamp);
            next(SUSPENDED, "suspend");
            fireOnSuspended();
        }

        @Override
        void error(int code, String message) {
            next(UNKNOWN, "error");
            fireOnRegistrationFailed(code, message);
        }
    } // UnknownState
    
    class RegisteredState extends State {
        RegisteredState() {
            super(true, false, "REGISTERED");
        }
        
        @Override
        void activate(long timestamp) {
            // stay registered
            // two activations in a row can happen if the client creates a new session after a session error:
            // the first activation happens in the first session, while the second activation happens in
            // the second session
        }
        
        @Override
        void suspend(long timestamp) {
            properties.setStatusTimestamp(timestamp);
            next(SUSPENDED, "suspend");
            fireOnSuspended();
        }
    } // RegisteredState
    
    class SuspendedState extends State {
        SuspendedState() {
            super(true, true, "SUSPENDED");
        }
        
        @Override
        void activate(long timestamp) {
            properties.setStatusTimestamp(timestamp);
            next(REGISTERED, "activate");
            fireOnResumed();
        }
        
        @Override
        void suspend(long timestamp) {
            // stay suspended
            // two suspensions in a row can happen if the client creates a new session after a session error
            // the first suspension happens in the first session, while the second suspension happens in
            // the second session
        }
    } // SuspendedState

    /**
     * <b>State transition diagram:</b>
     * <div>
     * <img src="{@docRoot}/../docs/mpn/mpn-device.png">
     * </div>
     */
    abstract class State {
        final boolean isRegistered;
        final boolean isSuspended;
        @Nonnull final String status;
        
        State(boolean isRegistered, boolean isSuspended, @Nonnull String status) {
            this.isRegistered = isRegistered;
            this.isSuspended = isSuspended;
            this.status = status;
        }

        void activate(long timestamp) {
            throwError("activate");
        }
        
        void suspend(long timestamp) {
            throwError("suspend");
        }
        
        void error(int code, String message) {
            throwError("error (" + code + " - " + message + ")");
        }
        
        void onSessionClose() {
            next(UNKNOWN, "onSessionClose");
        }
        
        void next(State next, String event) {
            properties.nextState(next, event);
        }
        
        void throwError(String event) {
            throw new AssertionError("Unexpected event '" + event + "' in state " + this + " (" + getDeviceId() + ")");
        }
        
        void fireOnRegistered() {
            properties.forEachListener(new Visitor<MpnDeviceListener>() {
                @Override
                public void visit(MpnDeviceListener listener) {
                    listener.onRegistered();
                }
            });
        }
        
        void fireOnResumed() {
            properties.forEachListener(new Visitor<MpnDeviceListener>() {
                @Override
                public void visit(MpnDeviceListener listener) {
                    listener.onResumed();
                }
            });
        }
        
        void fireOnSuspended() {
            properties.forEachListener(new Visitor<MpnDeviceListener>() {
                @Override
                public void visit(MpnDeviceListener listener) {
                    listener.onSuspended();
                }
            });
        }
        
        void fireOnRegistrationFailed(final int code, final String message) {
            properties.forEachListener(new Visitor<MpnDeviceListener>() {
                @Override
                public void visit(MpnDeviceListener listener) {
                    listener.onRegistrationFailed(code, message);
                }
            });
        }
        
        @Override
        public String toString() {
            return this.getClass().getSimpleName();
        }
    } // State
    
    /**
     * Class holding the properties of a device.
     * <p>
     * <b>NB</b>
     * Since the properties are set by the Session Thread but they can be read by the user,
     * methods must be synchronized.
     */
    @ThreadSafe
    protected class PropertyHolder extends ListenerHolder<MpnDeviceListener> {
        
        private long statusTimestamp = 0;
        private String deviceId;
        private String adapterName;
        private String applicationId;
        private String deviceToken;
        private String prevDeviceToken;
        private State state = UNKNOWN;
        
        public PropertyHolder(EventsThread eventThread) {
            super(eventThread);
        }
        
        public synchronized State getState() {
            return state;
        }
        
        public synchronized void nextState(State next, String event) {
            if (log.isDebugEnabled()) {
                log.debug("MpnDevice state change (" + deviceId + ") on '"+ event + "': " + state + " -> " + next);
            } 
            if (next != state) {
                state = next;
                forEachListener(new Visitor<MpnDeviceListener>() {
                    @Override
                    public void visit(MpnDeviceListener listener) {
                        listener.onStatusChanged(state.status, statusTimestamp);
                    }
                });
            }
        }
        
        public synchronized long getStatusTimestamp() {
            return statusTimestamp;
        }
        public synchronized void setStatusTimestamp(long statusTimestamp) {
            this.statusTimestamp = statusTimestamp;
        }
        public synchronized String getDeviceId() {
            return deviceId;
        }
        public synchronized void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }
        public synchronized String getAdapterName() {
            return adapterName;
        }
        public synchronized void setAdapterName(String adapterName) {
            this.adapterName = adapterName;
        }
        public synchronized String getApplicationId() {
            return applicationId;
        }
        public synchronized void setApplicationId(String applicationId) {
            this.applicationId = applicationId;
        }
        public synchronized String getDeviceToken() {
            return deviceToken;
        }
        public synchronized void setDeviceToken(String deviceToken) {
            this.deviceToken = deviceToken;
        }
        public synchronized String getPrevDeviceToken() {
            return prevDeviceToken;
        }
        public synchronized void setPrevDeviceToken(String prevDeviceToken) {
            this.prevDeviceToken = prevDeviceToken;
        }
    } // PropertyHolder
}
