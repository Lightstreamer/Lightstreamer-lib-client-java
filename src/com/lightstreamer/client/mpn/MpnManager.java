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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.annotation.concurrent.ThreadSafe;

import com.lightstreamer.client.Constants;
import com.lightstreamer.client.ItemUpdate;
import com.lightstreamer.client.LightstreamerClient;
import com.lightstreamer.client.Subscription;
import com.lightstreamer.client.session.SessionManager;
import com.lightstreamer.client.session.SessionThread;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;
import com.lightstreamer.util.Assertions;
import com.lightstreamer.util.IdGenerator;

/**
 * Manages the life-cycle of the MPN requests. 
 * <br>It is composed of several building blocks:
 * <ul>
 * <li><b>eventManager</b>: it is the interface with the SessionManager and forwards
 * the event coming from the transport layer to the MPN manager state machine</li>
 * <li><b>requestManager</b>: it builds the MPN requests and dispatches them to the SessionManager,
 * which passes them to the transport layer. It is also the interface with the tutors, which use the
 * requestManager to retransmit the timed-out requests</li>
 * <li><b>subscribeManager, unsubscribeManager, unsubscribeFilterManager, badgeManager</b>: each of them is responsible of
 * specific class of requests (subscriptions, unsubscriptions, filtered unsubscriptions and badge resettings respectively)</li>
 * <li><b>DEV- and SUBS- listeners</b>: DEV- is the special item providing information on the device, while
 * SUBS- is the special item concerned with the adding/deleting/updating of the MPN items cached by the manager</li>
 * <li><b>state machine</b>: it coordinates the activities of the MPN manager (see diagram below)</i></li>
 * </ul>
 * 
 * <p>
 * <b>Glossary</b>:
 * <ul>
 * <li><i>ephemeralSubId</i>: corresponds to the value of the parameter LS_subId of a MPN subscription request.
 * It is also the first argument of the message MPNOK sent by the server to the client to acknowledge a subscription.</li> 
 * <li><i>mpnSubId</i>: corresponds to the value of the second argument of the message MPNOK. 
 * It is also the value of the parameter PN_subscriptionId of a subscription/unsubscription request.</li>
 * </ul>
 * 
 * <p>
 * <b>State diagram of the manager:</b>
 * <div>
 * <img src="{@docRoot}/../docs/mpn/mpn-manager.png">
 * </div>
 * 
 * 
 * @since March 2017
 */
public class MpnManager {
    
    private static final Logger log = LogManager.getLogger(Constants.MPN_LOG);
    private static final Logger logOnSubUpd = LogManager.getLogger(Constants.MPN_LOG + ".onSubUpd");
    
    public final MpnRequestManager requestManager = new MpnRequestManager();
    public final MpnEventManager eventManager = new MpnEventManager();
    private final SubscribeManager subscribeManager = new SubscribeManager();
    private final UnsubscribeManager unsubscribeManager = new UnsubscribeManager();
    private final UnsubscribeFilterManager unsubscribeFilterManager = new UnsubscribeFilterManager();
    private final BadgeManager badgeManager = new BadgeManager();
    private final DEV_Manager dev_manager = new DEV_Manager();
    private final SUBS_Manager subs_manager = new SUBS_Manager();
    
    private AbstractMpnDevice mpnDevice;
    private final SubscriptionList subscriptions = new SubscriptionList();
    
    private final SessionManager sessionManager;
    private final LightstreamerClient lsClient;
    private final SessionThread sessionThread;
    
    public MpnManager(SessionManager sessionManager, LightstreamerClient lsClient, SessionThread sessionThread) {
        this.sessionManager = sessionManager;
        this.lsClient = lsClient;
        this.sessionThread = sessionThread;
    }
    
    /**
     * TEST ONLY: check that there are no pending requests
     */
    public void checkInvariants() {
        subscribeManager.checkInvariants();
        unsubscribeManager.checkInvariants();
        unsubscribeFilterManager.checkInvariants();
        badgeManager.checkInvariants();
    }
    
    /**
     * Resets internal data structures when starting a new registration.
     */
    private void resetDevice(AbstractMpnDevice device) {
        /* unsbuscribe form DEV- and SUBS- */
        dev_manager.unsubscribeFromDEVAdapter();
        subs_manager.unsubscribeFromSUBSAdapter();
        /* abort pending requests */
        requestManager.createTutorContext();
        /* clear internal data structures */
        subscribeManager.reset();
        unsubscribeManager.reset();
        unsubscribeFilterManager.reset();
        badgeManager.reset();
        /* set the new device */
        mpnDevice = device;
    }
    
    /* 
     * **********************************
     * API exposed to LightstreamerClient
     * **********************************
     */
    
    /**
     * See {@link LightstreamerClient#registerForMpn(MpnDeviceInterface)}.
     */
    public void registerForMpn(MpnDeviceInterface device) {
        assert Assertions.isSessionThread();
        state.register((AbstractMpnDevice) device);
    }

    /**
     * See {@link LightstreamerClient#subscribe(MpnSubscription, boolean)}.
     */
    public void subscribe(MpnSubscription sub, boolean coalescing) {
        assert Assertions.isSessionThread();
        sub.coalescing = coalescing;
        state.subscribe(sub);
    }

    /**
     * See {@link LightstreamerClient#unsubscribe(MpnSubscription)}.
     */
    public void unsubscribe(MpnSubscription sub) {
        assert Assertions.isSessionThread();
        state.unsubscribe(sub);
    }

    /**
     * See {@link LightstreamerClient#unsubscribeMpnSubscriptions(String)}.
     */
    public void unsubscribeFilter(String filter) {
        assert Assertions.isSessionThread();
        state.unsubscribeFilter(filter);
    }

    /**
     * See {@link LightstreamerClient#resetMpnBadge()}.
     */
    public void resetBadge() {
        assert Assertions.isSessionThread();
        state.resetBadge();
    }

    /**
     * See {@link LightstreamerClient#getMpnSubscriptions(String)}.
     */
    public synchronized List<MpnSubscription> getSubscriptions(String filter) {
        assert ! Assertions.isSessionThread();
        return subscriptions.getSubscriptions(filter);
    }
    
    /**
     * See {@link LightstreamerClient#findMpnSubscription(String)}.
     */
    public synchronized MpnSubscription findSubscription(String subId) {
        assert ! Assertions.isSessionThread();
        return subscriptions.findSubscription(subId);
    }
    
    String getDeviceId() {
        return mpnDevice == null ? null : mpnDevice.getDeviceId();
    }
    
    /**
     * The list of active MPN subscriptions.
     * <p>
     * Each subscription in the list is subscribed: it has received a MPNOK (user subscription) or
     * it has been published by the MPN internal adapter (server subscription).   
     * <p>
     * <b>NB</b>The class must be thread-safe because the list can be accessed by the user
     * (see {@link LightstreamerClient#getMpnSubscriptions(String)} and {@link LightstreamerClient#findMpnSubscription(String)}).
     */
    @ThreadSafe
    private static class SubscriptionList {
        /**
         * Maps the mpnSubId (i.e. the values of the second argument of the message MPNOK)
         * with the corresponding subscription objects.
         * <p>
         * The state of the contained objects is <b>subscribed</b>.
         */
        final HashMap<String, LinkedList<MpnSubscription>> subscriptions = new HashMap<>();
        
        /**
         * Returns the objects in the subscription list satisfying the filter 
         * (that can be null, "ALL", "SUBSCRIBED" or "TRIGGERED").
         * See {@link LightstreamerClient#getMpnSubscriptions(String)}.
         * <p>
         * NB For each subId, only the last added subscription is returned.
         */
        synchronized List<MpnSubscription> getSubscriptions(String filter) {
            ArrayList<MpnSubscription> ls = new ArrayList<>(subscriptions.size());
            boolean all = (filter == null || "ALL".equals(filter));
            for (LinkedList<MpnSubscription> subs : subscriptions.values()) {
                MpnSubscription sub = subs.getLast();
                if (all || sub.getStatus().equals(filter)) {
                    ls.add(sub);
                }
            }
            return ls;
        }
        
        /**
         * Returns the last added subscription having the specified subId.
         * If there are no subscriptions with the specified subId, returns null.
         */
        synchronized MpnSubscription findSubscription(String subId) {
            LinkedList<MpnSubscription> ls = subscriptions.get(subId);
            return ls == null ? null : ls.getLast();
        }
        
        /**
         * Returns true if there is a subscribed items with the given subId.
         */
        synchronized boolean isSubscribed(String subId) {
            return subscriptions.containsKey(subId);
        }
        
        /**
         * Adds the subscription.
         */
        synchronized void add(String subId, MpnSubscription sub) {
            LinkedList<MpnSubscription> ls = subscriptions.get(subId);
            if (ls == null) {
                ls = new LinkedList<>();
                subscriptions.put(subId, ls);
            }
            ls.add(sub);
        }
        
        /**
         * Removes all the subscription having the specified subId.
         */
        synchronized void remove(String subId, Visitor visitor) {
            LinkedList<MpnSubscription> ls = subscriptions.remove(subId);
            forEach(ls, visitor);
        }
        
        /**
         * Clears the list.
         */
        synchronized void clear() {
            subscriptions.clear();
        }
        
        /**
         * Executes the visitor for each subscription having the given subId.
         */
        synchronized void forEachWithSubId(String subId, Visitor visitor) {
            LinkedList<MpnSubscription> ls = subscriptions.get(subId);
            forEach(ls, visitor);
        }
        
        /**
         * Executes the visitor for each subscription in the given list.
         */
        private synchronized void forEach(List<MpnSubscription> ls, Visitor visitor) {
            if (ls != null && ! ls.isEmpty()) {
                for (MpnSubscription sub : ls) {
                    visitor.visit(sub);
                }
                visitor.afterVisit();
                
            } else {
                visitor.onEmpty();
            }
        }
        
        /**
         * Subscription list visitor. 
         */
        static abstract class Visitor {
            /**
             * Called when the visited list is empty.
             */
            void onEmpty() {}
            /**
             * Called for each subscription in the visited list.
             */
            abstract void visit(MpnSubscription sub);
            /**
             * Called after all the subscriptions in the list has been visited.
             * <br>NB The method is NOT called if the visited list is empty. 
             */
            void afterVisit() {}
        }
    } // SubscriptionList
    
    /**
     * API exposed to SessionManager
     */
    public class MpnEventManager {
        
        /**
         * Fired when a new session is stared.
         */
        public void onSessionStart() {
            assert Assertions.isSessionThread();
            state.onSessionStart();
        }

        /**
         * Fired when the current session is closed.
         * 
         * @param recovery if the flag is true, the closing is due to an automatic recovery.
         * Otherwise the closing is due to a disconnection made by the user
         * (see {@link LightstreamerClient#disconnect()}).
         */
        public void onSessionClose(boolean recovery) {
            assert Assertions.isSessionThread();
            state.onSessionClose(recovery);
        }
        
        /**
         * Fired when the message MPNREG is received.
         */
        public void onRegisterOK(String deviceId, String adapterName) {
            assert Assertions.isSessionThread();
            state.onRegisterOK(deviceId, adapterName);
        }

        /**
         * Fired when a REQERR is received as a response of a register request.
         */
        public void onRegisterError(int code, String message) {
            assert Assertions.isSessionThread();
            state.onRegisterError(code, message);
        }

        /**
         * Fired when the message MPNOK is received.
         * <p>
         * <b>NB</b> Each subscription request has two identifiers: a subId, which is used to identify the 
         * subscription and corresponds to the second argument of the message MPNOK, and an ephemeralSubId,
         * which temporarily identifies the request until MPNOK is received and corresponds to the parameter
         * LS_subId of the subscription request.
         */
        public void onSubscribeOK(String ephemeralSubId, String subId) {
            assert Assertions.isSessionThread();
            subscribeManager.onUserSubscribeOK(ephemeralSubId, subId);
        }

        /**
         * Fired when a REQERR is received as a response of a subscription request.
         */
        public void onSubscribeError(String subId, int code, String message) {
            assert Assertions.isSessionThread();
            subscribeManager.onSubscribeError(subId, code, message);
        }
        
        /**
         * Fired when a REQERR is received as a response of an unsubscription request.
         */
        public void onUnsubscribeError(String subId, int code, String message) {
            assert Assertions.isSessionThread();
            unsubscribeManager.onUnsubscribeError(subId, code, message);
        }

        /**
         * Fired when the message MPNDEL is received.
         */
        public void onUnsubscribeOK(String subId) {
            assert Assertions.isSessionThread();
            subscribeManager.onUnsubscribeOK(subId);
        }

        /**
         * Fired when the message MPNZERO is received.
         */
        public void onResetBadgeOK(String deviceId) {
            assert Assertions.isSessionThread();
            badgeManager.onResetBadgeOK(deviceId);
        }

        /**
         * Fired when a REQERR is received as a response of a reset-badge request.
         */
        public void onMpnBadgeResetError(int code, String message) {
            assert Assertions.isSessionThread();
            badgeManager.onMpnBadgeResetError(code, message);
        }
    } // MpnEventManager
    
    /**
     * API exposed to {@link MpnTutor}
     */
    class MpnRequestManager {
        
        // the context shared by all the tutors belonging to the same session
        MpnTutor.TutorContext tutorContext = new MpnTutor.TutorContext();

        /**
         * Dismisses the old context and creates a new one.
         */
        void createTutorContext() {
            // dismiss the old context and create a new one
            tutorContext.dismissed = true;
            tutorContext = new MpnTutor.TutorContext();
        }

        /**
         * Sends a registration request.
         */
        void sendRegisterRequest(final long timeoutMs) {
            MpnRegisterRequest request = new MpnRegisterRequest(mpnDevice);
            MpnRegisterTutor tutor = new MpnRegisterTutor(timeoutMs, sessionThread, requestManager, tutorContext);
            sessionManager.sendMpnRegistration(request, tutor);
        }

        /**
         * Sends a subscription request.
         */
        void sendSubscribeRequest(long timeoutMs, String ephemeralSubId, MpnSubscription sub) {
            assert Assertions.isSessionThread();
            MpnSubscribeRequest request = new MpnSubscribeRequest(ephemeralSubId, mpnDevice.getDeviceId(), sub);
            MpnSubscribeTutor tutor = new MpnSubscribeTutor(timeoutMs, sessionThread, ephemeralSubId, sub, requestManager, tutorContext);
            sessionManager.sendMpnSubscription(request, tutor);
        }

        /**
         * Sends an unsubscription request.
         */
        void sendUnsubscribeRequest(long timeoutMs, MpnSubscription sub) {
            assert Assertions.isSessionThread();
            MpnUnsubscribeRequest request = new MpnUnsubscribeRequest(mpnDevice.getDeviceId(), sub);
            MpnUnsubscribeTutor tutor = new MpnUnsubscribeTutor(timeoutMs, sessionThread, sub, requestManager, tutorContext);
            sessionManager.sendMpnUnsubscription(request, tutor);
        }
        
        /**
         * Sends a filtered unsubscription request.
         */
        void sendUnsubscribeFilteredRequest(long timeoutMs, MpnUnsubscribeFilter unsubFilter) {
            assert Assertions.isSessionThread();
            MpnUnsubscribeFilterRequest request = new MpnUnsubscribeFilterRequest(mpnDevice.getDeviceId(), unsubFilter.filter);
            MpnUnsubscribeFilterTutor tutor = new MpnUnsubscribeFilterTutor(timeoutMs, sessionThread, requestManager, unsubFilter, tutorContext);
            sessionManager.sendMpnFilteredUnsubscription(request, tutor);
        }

        /**
         * Sends a reset-badge request.
         */
        void sendResetBadgeRequest(long timeoutMs) {
            assert Assertions.isSessionThread();
            MpnResetBadgeRequest request = new MpnResetBadgeRequest(mpnDevice.getDeviceId());
            MpnResetBadgeTutor tutor = new MpnResetBadgeTutor(timeoutMs, sessionThread, requestManager, tutorContext);
            sessionManager.sendMpnResetBadge(request, tutor);
        }
        
        /**
         * Sends the requests on the waiting list.
         */
        void sendWaitings() {
            /* send pending mpn subscriptions */
            subscribeManager.subscribeWaitings();
            /* send pending mpn unsubscriptions */
            unsubscribeManager.unsubscribeWaitings();
            unsubscribeFilterManager.unsubscribeWaitings();
            /* send pending reset badge */
            badgeManager.resetWaiting();
        }
        
        /**
         * 
         * Fired when the message REQOK/REQERR is received as a response to a filtered unsubscription.
         */
        void onUnsubscriptionFilterResponse(MpnUnsubscribeFilter filter) {
            unsubscribeFilterManager.onUnsubscribeRepsone(filter);
        }
    } // MpnRequestManager
    
    /**
     * A mapping from ephemeralSubId to pending subscription requests.
     * A request is pending when the request has been sent to the server but the client has not received a response
     * (i.e. MPNOK/REQERR message).
     */
    private static class PendingSubscriptions {
        
        /**
         * Maps the ephemeralSubId (i.e. the values of the parameters LS_subId of the subscribe requests)
         * with the corresponding subscriptions.
         */
        final HashMap<String, PendingRequest> pendings = new HashMap<>();
        
        /**
         * Associates a subscription with an ephemeralSubId.
         */
        void put(String ephemeralSubId, MpnSubscription sub) {
            pendings.put(ephemeralSubId, new PendingRequest(sub));
        }
        
        /**
         * Remove the request corresponding to an ephemeralSubId.
         */
        PendingRequest remove(String ephemeralSubId) {
            PendingRequest req = pendings.remove(ephemeralSubId);
            return req;
        }
        
        /**
         * Returns the list of the currently pending subscriptions.
         */
        List<MpnSubscription> values() {
            ArrayList<MpnSubscription> ls = new ArrayList<>(pendings.size());
            for (PendingRequest req : pendings.values()) {
                ls.add(req.subscription);
            }
            return ls;
        }
        
        boolean isEmpty() {
            return pendings.isEmpty();
        }
        
        void clear() {
            pendings.clear();
        }
        
        /**
         * Returns the pending request corresponding to the specified subscription.
         */
        PendingRequest getPendingRequest(MpnSubscription sub) {
            for (PendingRequest req : pendings.values()) {
                if (req.subscription == sub) {
                    return req;
                }
            }
            return null;
        }        
    }
    
    /**
     * A request which has been sent to the server, but whose response has not been received.
     * A listener can be attached to a pending request. When the request is completed, the listener is fired.
     */
    private static class PendingRequest {
        final MpnSubscription subscription;
        OnCompleteHandler handler;
        
        PendingRequest(MpnSubscription sub) {
            this.subscription = sub;
        }
        
        void setOnCompleteHandler(OnCompleteHandler handler) {
            assert this.handler == null;
            this.handler = handler;
        }
        
        /**
         * This method must be called when the response arrives.
         * If there is an attached listener, it is fired.
         * 
         * @param success true if the response is right. False when the response is an error.
         */
        void onComplete(final boolean success) {
            if (handler != null) {
                handler.onComplete(success);
                handler = null;
            }
        }
        
        interface OnCompleteHandler {
            
            /**
             * Signals that a {@link PendingRequest} has been carried out.
             * 
             * @param success true if the response is right. False when the response is an error.
             */
            void onComplete(boolean success);
        }
    }
    
    /**
     * Manages the life cycle of the subscription requests.
     * 
     * <p>
     * <b>1 - SUBSCRIPTION STATES</b><br>
     * A subscription can be in the following states:
     * <ul>
     * <li><b>waiting</b>: the subscription is waiting for the device to register. 
     * After that the subscription becomes pending and the corresponding request is sent.</li>
     * <li><b>pending</b>: the corresponding request has been sent but no response has been received yet.</li>
     * <li><b>subscribed</b>: an object is subscribed when either a subscription request receives 
     * the message MPNOK or the MPN internal adapter publishes a new subscription.</li>
     * </ul>
     * 
     * <p>
     * <b>2 - USER AND SERVER SUBSCRIPTIONS</b><br>
     * An item can be subscribed in two ways: 
     * either when the user calls the method {@link LightstreamerClient#subscribe(MpnSubscription, boolean)}
     * or when the MPN internal adapter publishes a new subscription.
     * To distinguish the two cases, the subscriptions created in the first case are called <i>user subscriptions</i>,
     * while the others are called <i>server subscriptions</i>.
     * User and server subscriptions are stored in the <i>subscription list</i>.<br>
     * There is an important point to stress: since the MPN internal adapter publishes both the user and the server subscriptions,
     * in order to distinguish them, I make the following crucial assumption:
     * <i>when the user requests a new subscription, the message MPNOK is always sent before the MPN internal adapter
     * publishes the subscription</i>.<br>
     * From this assumption I draws the following rules:
     * <ol>
     * <li>MPNOK messages always refer to user subscriptions</li>
     * <li>a user subscription is added to the subscription list when the client receives the corresponding MPNOK message</li>
     * <li>published subscriptions which are not in the subscription list always refer to server subscriptions</li>
     * <li>a server subscription is added to the subscription list when the server sends the first update of the subscription</li>
     * </ol>
     */
    private class SubscribeManager {
        /**
         * Maps the ephemeralSubId (i.e. the values of the parameters LS_subId of the subscribe requests)
         * with the corresponding subscription requests.
         * <p>
         * The state of the contained subscriptions is <b>pending</b>.
         */
        final PendingSubscriptions pendings = new PendingSubscriptions();
        /**
         * The state of the contained objects is <b>waiting</b>.
         */
        final LinkedList<MpnSubscription> waitings = new LinkedList<>();
        /**
         * Since the rules about the firing of the event {@code onSubscriptionsUpdated} are complex,
         * this dedicated object manages them.
         */
        final OnSubscriptionsUpdatedEventManager onSubUpdEventManager = new OnSubscriptionsUpdatedEventManager();
        
        void checkInvariants() {
            assert pendings.isEmpty();
            assert waitings.isEmpty();
        }
        
        /**
         * Resets the internal data structures to start a new registration.
         */
        void reset() {
            subscriptions.clear();
            pendings.clear();
            waitings.clear();
            onSubUpdEventManager.reset();
        }

        /**
         * Adds the subscription to the waiting list.
         */
        void addWaiting(MpnSubscription sub) {
            waitings.add(sub);
        }
        
        /**
         * Removes the subscription from the waiting list.
         * Returns true if the element was in the waiting list.
         */
        boolean removeWaiting(MpnSubscription sub) {
            return waitings.remove(sub);
        }

        /**
         * Subscribes the objects on the waiting list, which are put in the pending list.
         */
        void subscribeWaitings() {
            for (MpnSubscription sub : waitings) {
                subscribe(sub);
            }
            waitings.clear();
        }
        
        /**
         * Returns the pending request corresponding to the specified subscription.
         */
        PendingRequest getPendingRequest(MpnSubscription sub) {
            return pendings.getPendingRequest(sub);
        }

        /**
         * Tries to subscribe the object, which is put in the pending list.
         */
        void subscribe(MpnSubscription sub) {
            String ephemeralSubId = String.valueOf(IdGenerator.getNextSubscriptionId());
            pendings.put(ephemeralSubId, sub);
            requestManager.sendSubscribeRequest(0, ephemeralSubId, sub);
        }

        /**
         * Fired when the message MPNOK of an user subscription is received.
         * The user subscription is put in the subscription list and the method {@link MpnDeviceListener#onSubscriptionsUpdated()} 
         * is triggered. 
         */
        void onUserSubscribeOK(String ephemeralSubId, String mpnSubId) {
            /* remove from pending */
            PendingRequest req = pendings.remove(ephemeralSubId);
            if (req == null) {
                log.warn("Discarded unexpected subscription: subId=" + ephemeralSubId);
                return;
            }
            MpnSubscription sub = req.subscription;
            /* add to subscriptions */
            subscriptions.add(mpnSubId, sub);
            /* set subscription id */
            sub.eventManager.onSubscribeOK(mpnSubId);
            /* fire device listeners */
            onSubUpdEventManager.fireOnSubscriptionsUpdated("MPNOK");
            /* fire listeners waiting for the completion of the request */
            req.onComplete(true);
        }

        /**
         * Fired when the MPN internal adapter sends the first update of a server subscription.
         * The server subscription is put in the subscription list and the method {@link MpnDeviceListener#onSubscriptionsUpdated()} 
         * is triggered. 
         */
        void onServerSubscribeOK(String subId, MpnSubscription sub) {
            subscriptions.add(subId, sub);
            /* fire device listeners */
            onSubUpdEventManager.onSeverSubscriptionOK(subId);
        }
        
        /**
         * Fired when the MPN internal adapter publishes a subscription.
         */
        void onAddedSubscription(String subId) {
            if (subscriptions.isSubscribed(subId)) {
                /*
                 * This is an user subscription. We can ignore the adding since
                 * the subscription is already in the subscription list.
                 */
                
            } else {
                /*
                 * This is a new server subscription.
                 */
                onSubUpdEventManager.onNewServerSubscription(subId);
            }
        }
        
        /**
         * Fired when the MPN internal adapter sends the end-of-snapshot message.
         */
        void onEndOfSnapshot() {
            onSubUpdEventManager.onEndOfSnapshot();
        }

        /**
         * Fired when the user subscription fails.
         * The subscription is removed from the pending list and the method {@link MpnSubscriptionListener#onSubscriptionError(int, String)}
         * is triggered.
         */
        void onSubscribeError(String subId, int code, String message) {
            PendingRequest req = pendings.remove(subId);
            if (req == null) {
                log.warn("Discarded unexpected subscription error subId=" + subId);
                return;
            }
            MpnSubscription sub = req.subscription;
            sub.eventManager.onSubscribeError(code, message);
            /* fire listeners waiting for the completion of the request */
            req.onComplete(false);
        }

        /**
         * Fired when the message MPNDEL of a subscription is received.
         */
        void onUnsubscribeOK(final String subId) {
            if (log.isDebugEnabled()) {
                log.debug("MPNDEL subId=" + subId);
            }
            unsubscribeManager.onUnsubscribeOK(subId);
        }

        /**
         * Fired when the MPN internal adapter removes a subscription.
         * The subscription is removed from the subscription list, and the method
         * {@link MpnDeviceListener#onSubscriptionsUpdated()} is triggered.
         */
        void onDelete(final String subId) {
            subscriptions.remove(subId, new SubscriptionList.Visitor() {
                @Override
                void onEmpty() {
                    log.warn("MPN subscription not found subId=" + subId);
                }
                @Override
                void visit(MpnSubscription sub) {
                    sub.eventManager.simulateUnsubscribe();
                }
                @Override
                void afterVisit() {
                    /* fire device listeners */
                    onSubUpdEventManager.fireOnSubscriptionsUpdated("DELETE");
                }
            });
        }
        
        /**
         * Fired when an unsubscription request receives REQERR.
         * The subscription is removed from the subscription list, and the method
         * {@link MpnDeviceListener#onSubscriptionsUpdated()} is triggered.
         */
        void onUnsubscribeError(final String subId, final int code, final String message) {
            subscriptions.remove(subId, new SubscriptionList.Visitor() {
                @Override
                void onEmpty() {
                    log.warn("MPN subscription not found subId=" + subId);
                }
                @Override
                void visit(MpnSubscription sub) {
                    sub.eventManager.onUnsubscribeError(code, message);
                }
                @Override
                void afterVisit() {
                    /* fire device listeners */
                    onSubUpdEventManager.fireOnSubscriptionsUpdated("REQERR");
                }
            });
        }
        
        /**
         * Fired when the current session is closed.
         * The pending subscriptions are marked as waiting so they will be sent when a new session
         * becomes available.
         * 
         * @param recovery if the flag is true, the closing is due to an automatic recovery.
         * Otherwise the closing is due to a disconnection made by the user
         * (see {@link LightstreamerClient#disconnect()}).
         */
        void onSessionClose(boolean recovery) {
            if (recovery) {
                waitings.addAll(pendings.values());
                pendings.clear();
                
            } else {
                waitings.clear();
                pendings.clear();
                subscriptions.clear();
                /* fire the device listeners because there are no more subscriptions */
                onSubUpdEventManager.fireOnSubscriptionsUpdated("Session close");
            }
            onSubUpdEventManager.reset();
        }
    } // SubscribeManager
    
    /**
     * Manager of {@link MpnDeviceListener#onSubscriptionsUpdated()} event.
     * <p>
     * The event is fired when:
     * <ul>
     * <li>MPNOK message is received</li>
     * <li>the MPN internal adapter adds/deletes a subscription</li>
     * <li>the MPN internal adapter sends the end-of-snapshot message (*)</li>
     * <li>the session is closed</li>
     * </ul>
     * 
     * (*) When the end-of-snapshot arrives, the event onSubscriptionsUpdated is not fired immediately
     * but only when all the subscriptions in the snapshot have received the first update.
     * So the user can see the subscriptions in the snapshot all at once.
     * <p>
     * <b>State diagram of the manager:</b>
     * <div>
     * <img src="{@docRoot}/../docs/mpn/onSubscriptionsUpdated.png">
     * </div>
     */
    private class OnSubscriptionsUpdatedEventManager {
        
        private OnSubUpdState state = OnSubUpdState.INIT;
        /**
         * SubIds of the subscriptions in the snapshot sent by the MPN internal adapter SUBS-.
         */
        private Set<String> expectedSubIds = new HashSet<>();        
        
        void reset() {
            next(OnSubUpdState.INIT, "reset");
            expectedSubIds.clear();            
        }
        
        /**
         * Must be called when a server subscription is published by the MPN internal adapter.
         */
        void onNewServerSubscription(String subId) {
            if (logOnSubUpd.isDebugEnabled()) {
                logOnSubUpd.debug("Server subscription added subId=" + subId);
            }
            switch (state) {
            case INIT:
                expectedSubIds.add(subId);
                next(OnSubUpdState.NOT_EMPTY, "ADD");
                break;
                
            case NOT_EMPTY:
                expectedSubIds.add(subId);
                break;
                
            case EOS:
            case DONE:
                // ignore
                break;
                
            default:
                throwError("onNewServerSubscription (subId=" + subId + ")");
            }
        }
        
        /**
         * Must be called when the MPN internal adapter sends the end-of-snapshot message.
         */
        void onEndOfSnapshot() {
            switch (state) {
            case INIT:
                fireOnSubscriptionsUpdated("empty EOS");
                next(OnSubUpdState.DONE, "EOS");
                break;
                
            case NOT_EMPTY:
                if (expectedSubIds.isEmpty()) {
                    next(OnSubUpdState.EOS, "EOS");
                    onEmpty();
                } else {                    
                    next(OnSubUpdState.EOS, "EOS");
                }
                break;
                
            default:
                throwError("onEndOfSnapshot");
            }
        }
        
        /**
         * Must be called when a server subscription is put in the subscription list
         * (i.e. when it receives the first update).
         * <br>
         * The method fires {@link MpnDeviceListener#onSubscriptionsUpdated()} when:
         * <ol>
         * <li>ALL the subscriptions in the snapshot are in the subscription list, or</li>
         * <li>a subscription NOT in the snapshot is added to the subscription list</li>
         * </ol>
         */
        void onSeverSubscriptionOK(String subId) {
            if (logOnSubUpd.isDebugEnabled()) {
                logOnSubUpd.debug("Server subscription subscribed subId=" + subId);
            }
            switch (state) {
            case NOT_EMPTY:
                expectedSubIds.remove(subId);
                break;
                
            case EOS:
                expectedSubIds.remove(subId);
                if (expectedSubIds.isEmpty()) {
                    onEmpty();
                } else {
                    // stay here
                }
                break;
                
            case DONE:
                fireOnSubscriptionsUpdated("UPD");
                break;
                
            default:
                throwError("onSeverSubscriptionOK (subId=" + subId + ")");
            }
        }
        
        /**
         * Internal transition called when the snapshot set is empty.
         */
        void onEmpty() {
            switch (state) {
            case EOS:
                fireOnSubscriptionsUpdated("EOS");
                next(OnSubUpdState.DONE, "EMPTY");
                break;
                
            default:
                throwError("onEmpty");
            }
        }
        
        /**
         * Fires {@link MpnDeviceListener#onSubscriptionsUpdated()}.
         */
        void fireOnSubscriptionsUpdated(String event) {
            if (logOnSubUpd.isDebugEnabled()) {
                logOnSubUpd.debug("onSubscriptionsUpdated fired event=" + event);
            }
            if (mpnDevice != null) {
                mpnDevice.eventManager.onSubscriptionsUpdated();
            }
        }
        
        void next(OnSubUpdState next, String event) {
            if (logOnSubUpd.isDebugEnabled()) {
                logOnSubUpd.debug("OnSubscriptionsUpdated state change (" + getDeviceId() + ") on '" + event + "': " + state.name() + " -> " + next.name());
            }
            state = next;
        }
        
        void throwError(String event) {
            throw new AssertionError("Unexpected event '" + event + "' in state " + state.name() + " (" + getDeviceId() + ")");
        }
    } // OnSubscriptionsUpdatedEventManager
    
    /**
     * States OnSubscriptionsUpdatedEventManager. 
     * See the diagram <img src="{@docRoot}/../docs/mpn/onSubscriptionsUpdated.png">
     */
    private enum OnSubUpdState {
        /**
         * Initial state.
         */
        INIT, 
        /**
         * Filling the snapshot set. 
         * End-of-snapshot has not yet arrived.
         */
        NOT_EMPTY, 
        /**
         * Waiting for the first update of every subscription in the snapshot.
         * End-of-snapshot has arrived.
         */
        EOS,
        /**
         * All the subscriptions in the snapshot received an update
         * (or the snapshot was empty).
         */
        DONE
    }

    /**
     * Manages the life cycle of the unsubscription requests.
     * <p>
     * An unsubscription can be in the following states:
     * <ul>
     * <li><b>waiting</b>: the unsubscription is waiting for the device to register. 
     * After that the unsubscription becomes pending and the corresponding request is sent.</li>
     * <li><b>pending</b>: the corresponding request has been sent but no response has been received yet.</li>
     * </ul>
     */
    private class UnsubscribeManager {
        /**
         * Maps the mpnSubId (i.e. the values of the second argument of the message MPNOK)
         * with the corresponding unsubscription objects.
         * <p>
         * The state of the contained objects is <b>pending</b>.
         */
        final HashMap<String, MpnSubscription> pendings = new HashMap<>();
        /**
         * The state of the contained objects is <b>waiting</b>.
         */
        final LinkedList<MpnSubscription> waitings = new LinkedList<>();
        
        void checkInvariants() {
            assert pendings.isEmpty();
            assert waitings.isEmpty();
        }
        
        /**
         * Resets the internal data structures to start a new registration.
         */
        void reset() {
            pendings.clear();
            waitings.clear();
        }
        
        /**
         * Adds the unsubscription to the waiting list.
         */
        void addWaiting(MpnSubscription sub) {
            waitings.add(sub);
        }
        
        /**
         * Unsubscribes the objects on the waiting list, which are put in the pending list.
         */
        void unsubscribeWaitings() {
            for (MpnSubscription sub : waitings) {
                unsubscribe(sub);
            }
            waitings.clear();
        }

        /**
         * Tries to unsubscribe the object, which is put in the pending list.
         */
        void unsubscribe(MpnSubscription sub) {
            String subId = sub.getSubscriptionId();
            assert subId != null;
            assert ! pendings.containsKey(subId);
            pendings.put(subId, sub);
            requestManager.sendUnsubscribeRequest(0, sub);
        }
        
        /**
         * Fired when the message MPNDEL is received.
         * The unsubscription is removed from the pending list.
         */
        void onUnsubscribeOK(String subId) {
            pendings.remove(subId);
            // when the unsubscription is made using a filter, there is no pending unsubscription.
            // so subIb may not be in pendings.
        }
        
        /**
         * Fired when a REQERR is received as a response of an unsubscription request.
         */
        void onUnsubscribeError(String subId, int code, String message) {
            MpnSubscription sub = pendings.remove(subId);
            if (sub == null) {
                log.warn("Discarded unexpected unsubscription error subId=" + subId);
                return;
            }
            subscribeManager.onUnsubscribeError(subId, code, message);
        }

        /**
         * Fired when the current session is closed.
         * The pending unsubscriptions are marked as waiting so they will be sent when a new session
         * becomes available.
         * 
         * @param recovery if the flag is true, the closing is due to an automatic recovery.
         * Otherwise the closing is due to a disconnection made by the user
         * (see {@link LightstreamerClient#disconnect()}).
         */
        void onSessionClose(boolean recovery) {
            if (recovery) {
                waitings.addAll(pendings.values());
                pendings.clear();
                
            } else {
                waitings.clear();
                pendings.clear();
            }
        }
    } // UnsubscribeManager
    
    /**
     * Manages the life cycle of the filtered unsubscription requests.
     * <p>
     * A filtered unsubscription can be in the following states:
     * <ul>
     * <li><b>waiting</b>: the filtered unsubscription is waiting for the device to register. 
     * After that the filtered unsubscription becomes pending and the corresponding request is sent.</li>
     * <li><b>pending</b>: the corresponding request has been sent but no response has been received yet.</li>
     * </ul>
     */
    private class UnsubscribeFilterManager {
        /**
         * The state of the contained objects is <b>pending</b>.
         */
        final HashSet<MpnUnsubscribeFilter> pendings = new HashSet<>();
        /**
         * The state of the contained objects is <b>waiting</b>.
         */
        final LinkedList<MpnUnsubscribeFilter> waitings = new LinkedList<>();
        
        void checkInvariants() {
            assert pendings.isEmpty();
            assert waitings.isEmpty();
        }
        
        /**
         * Resets the internal data structures to start a new registration.
         */
        void reset() {
            pendings.clear();
            waitings.clear();
        }

        /**
         * Adds the filtered unsubscription to the waiting list.
         */
        void addWaiting(String filter) {
            waitings.add(new MpnUnsubscribeFilter(filter));
        }
        
        /**
         * Unsubscribes the objects on the waiting list, which are put in the pending list.
         */
        void unsubscribeWaitings() {
            for (MpnUnsubscribeFilter filter : waitings) {
                unsubscribe(filter);
            }
            waitings.clear();
        }
        
        /**
         * Tries to unsubscribe the object, which is put in the pending list.
         */
        void unsubscribe(MpnUnsubscribeFilter filter) {
            assert ! pendings.contains(filter);
            pendings.add(filter);
            requestManager.sendUnsubscribeFilteredRequest(0, filter);
        }
        
        /**
         * Fired when the message REQOK/REQERR is received.
         * The filtered unsubscription is removed from the pending list.
         */
        void onUnsubscribeRepsone(MpnUnsubscribeFilter filter) {
            boolean contained = pendings.remove(filter);
            assert contained;
        }
        
        /**
         * Fired when the current session is closed.
         * The pending filtered unsubscriptions are marked as waiting so they will be sent when a new session
         * becomes available.
         * 
         * @param recovery if the flag is true, the closing is due to an automatic recovery.
         * Otherwise the closing is due to a disconnection made by the user
         * (see {@link LightstreamerClient#disconnect()}).
         */
        void onSessionClose(boolean recovery) {
            if (recovery) {
                waitings.addAll(pendings);
                pendings.clear();
                
            } else {
                waitings.clear();
                pendings.clear();
            }
        }
    } // UnsubscribeFilterManager
    
    /**
     * A filtered unsubscription can simultaneously unsubscribes from all the items sharing a given state.
     * <p>
     * The filter can be:
     * <ul>
     * <li>ALL: unsubscribes all the items</li>
     * <li>ACTIVE: unsubscribes all the active (i.e. subscribed) items</li>
     * <li>TRIGGERED: unsubscribes all the triggered items</li>
     * </ul>
     */
    static class MpnUnsubscribeFilter {
        final String filter;
        
        MpnUnsubscribeFilter(String filter) {
            assert filter == null || filter.equals("ALL") || filter.equals("SUBSCRIBED") || filter.equals("TRIGGERED");
            if (filter != null) {
                filter = filter.toUpperCase();
            }
            if (filter == null) {
                // the value null is processed as the value ALL
                filter = "ALL";
            } else if (filter.equals("SUBSCRIBED")) {
                // NB SUBSCRIBED is translated to ACTIVE because the server expects this name
                filter = "ACTIVE";
            }
            this.filter = filter;
        }
    }
    
    /**
     * Manages the life cycle of the reset-badge requests.
     * <p>
     * A request can be in the following states:
     * <ul>
     * <li><b>waiting</b>: the request is waiting for the device to register. 
     * After that the request becomes pending and it is sent.</li>
     * <li><b>pending</b>: the request has been sent but no response has been received yet.</li>
     * </ul>
     */
    private class BadgeManager {
        private boolean pending;
        private boolean waiting;
        
        void checkInvariants() {
            assert ! pending;
            assert ! waiting;
        }
        
        /**
         * Resets the internal data structures to start a new registration.
         */
        void reset() {
            pending = false;
            waiting = false;
        }

        /**
         * Marks the request as waiting.
         */
        void addWaiting() {
            waiting = true;
        }
        
        /**
         * If there is a waiting request, sends it.
         * Then the request is marked as pending. 
         */
        void resetWaiting() {
            if (waiting) {
                resetBadge();
            }
        }
        
        /**
         * Tries to reset the badge.
         * The request is marked as pending. 
         */
        void resetBadge() {
            waiting = false;
            pending = true;
            requestManager.sendResetBadgeRequest(0);
        }
        
        /**
         * Fired when the message MPNZERO is received.
         * The request is unmarked as pending and the method {@link MpnDeviceListener#onBadgeReset()} is triggered.
         */
        void onResetBadgeOK(String deviceId) {
            assert deviceId.equals(mpnDevice.getDeviceId());
            assert pending;
            pending = false;
            mpnDevice.eventManager.onResetBadgeOK();
        }
        
        /**
         * Fired when the reset fails.
         * The request is unmarked as pending and the method {@link MpnDeviceListener#onBadgeResetFailed(int, String)}
         * is triggered.
         */
        void onMpnBadgeResetError(int code, String message) {
            assert pending;
            pending = false;
            mpnDevice.eventManager.onMpnBadgeResetError(code, message);
        }
        
        /**
         * Fired when the current session is closed.
         * If there is a pending request, it is marked as waiting so it will be sent when a new session 
         * becomes available.
         * 
         * @param recovery if the flag is true, the closing is due to an automatic recovery.
         * Otherwise the closing is due to a disconnection made by the user
         * (see {@link LightstreamerClient#disconnect()}).
         */
        void onSessionClose(boolean recovery) {
            if (recovery) {
                if (pending) {
                    waiting = true;
                    pending = false;
                }
                
            } else {
                waiting = false;
                pending = false;
            }
        }
    } // BadgeManager
    
    /*
     * Support for internal adapters
     */
    
    /**
     * Manages the subscription/unsubscription of the DEV- adapter publishing info about the current MPN device. 
     */
    private class DEV_Manager {
        
        private Subscription subscription;
        private DeviceListener listener;
        
        /**
         * Subscribes to DEV- adapter.
         */
        void subscribeToDEVAdapter(String adapterName) {
            assert Assertions.isSessionThread();
            assert subscription == null;
            assert listener == null;
            listener = new DeviceListener();
            subscription = new Subscription(
                    "MERGE", 
                    "DEV-" + mpnDevice.getDeviceId(), 
                    new String[] { "status", "status_timestamp" });
            subscription.setDataAdapter(adapterName);
            subscription.setRequestedMaxFrequency("unfiltered");
            subscription.addListener(listener);
            lsClient.subscribe(subscription);
        }
        
        /**
         * Unsubscribes from DEV- adapterName.
         */
        void unsubscribeFromDEVAdapter() {
            assert Assertions.isSessionThread();
            if (subscription != null) {
                lsClient.unsubscribe(subscription);
                subscription.removeListener(listener);
                listener.dismissed = true;
                subscription = null;
                listener = null;
            }
        }
    } // DEV_Manager
    
    /**
     * Listener of the item DEV- publishing the status of the device.
     */
    private class DeviceListener extends VoidSubscriptionListener {
        
        /**
         * When dismissed, incoming messages are discarded.
         */
        private boolean dismissed = false;
        
        @Override
        public void onItemUpdate(final ItemUpdate itemUpdate) {
            sessionThread.queue(new Runnable() {
                @Override
                public void run() {
                    if (dismissed) {
                        return;
                    }
                    String nextStatus = itemUpdate.getValue("status");
                    long timestamp = Long.parseLong(itemUpdate.getValue("status_timestamp"));
                    mpnDevice.eventManager.onStatusChange(nextStatus, timestamp);
                }
            });
        }
    } // DeviceListener
    
    /**
     * Manages the subscription/unsubscription of the SUBS- adapter publishing info about the MPN items. 
     */
    private class SUBS_Manager {
        
        private Subscription subscription;
        private SubscriptionListener listener;
        
        /**
         * Subscribes to SUBS- adapter.
         */
        void subscribeToSUBSAdapter(String adapterName) {
            assert Assertions.isSessionThread();
            assert subscription == null;
            assert listener == null;
            listener = new SubscriptionListener();
            subscription = new Subscription(
                    "COMMAND", 
                    "SUBS-" + mpnDevice.getDeviceId(), 
                    new String[] { "key", "command" });
            subscription.setDataAdapter(adapterName);
            subscription.setRequestedMaxFrequency("unfiltered");
            subscription.setCommandSecondLevelFields(new String[] { 
                    "status", "status_timestamp", "notification_format", "trigger", "group", 
                    "schema", "adapter", "mode", "requested_buffer_size", "requested_max_frequency" });
            subscription.setCommandSecondLevelDataAdapter(adapterName);
            subscription.addListener(listener);
            lsClient.subscribe(subscription);
        }
        
        /**
         * Unsubscribes from SUBS- adapter.
         */
        void unsubscribeFromSUBSAdapter() {
            assert Assertions.isSessionThread();
            if (subscription != null) {
                lsClient.unsubscribe(subscription);
                subscription.removeListener(listener);
                listener.dismissed = true;
                subscription = null;
                listener = null;
            }
        }
    } // SUBS_Manager
    
    /**
     * Listener of the item SUBS- publishing the adding/removal of the MPN items on the server.
     * SUBS- is a two-level command-mode item. The second level publishes the information
     * of a single MPN item. 
     */
    private class SubscriptionListener extends VoidSubscriptionListener {
        
        /**
         * When dismissed, incoming messages are discarded.
         */
        private boolean dismissed = false;
        
        @Override
        public void onItemUpdate(final ItemUpdate itemUpdate) {
            sessionThread.queue(new Runnable() {
                @Override
                public void run() {
                    if (dismissed) {
                        return;
                    }
                    
                    String command = itemUpdate.getValue("command");
                    switch (command) {
                    case "UPDATE":
                        onUpdate(itemUpdate);
                        break;
                    case "ADD":
                        onAdd(itemUpdate);
                        break;
                    case "DELETE":
                        onDelete(itemUpdate);
                        break;
                    default:
                        assert false : command;
                    }
                }
            });
        }
        
        @Override
        public void onEndOfSnapshot(String itemName, int itemPos) {
            sessionThread.queue(new Runnable() {
                @Override
                public void run() {
                    if (dismissed) {
                        return;
                    }
                    
                    subscribeManager.onEndOfSnapshot();
                }
            });
        }

        /**
         * Manages both the updating of an existing subscription and the adding of a new server subscription.
         * The adding is recognized by the fact that the subscription is not in the subscription list
         * kept by the MpnManager.
         */
        void onUpdate(final ItemUpdate itemUpdate) {
            assert Assertions.isSessionThread();
            final String subId = getSubId(itemUpdate);
            subscriptions.forEachWithSubId(subId, new SubscriptionList.Visitor() {
                @Override
                void onEmpty() {
                    doCreateNewSubscription(subId, itemUpdate);
                }
                @Override
                void visit(MpnSubscription sub) {
                    doUpdateExistingSubscription(sub, itemUpdate);
                }
            });
        }

        /**
         * Updates the subscription.
         * When a subscription field is updated, the method {@link MpnSubscriptionListener#onPropertyChanged(String)} is triggered.
         */
        void doUpdateExistingSubscription(MpnSubscription sub, ItemUpdate itemUpdate) {
            assert Assertions.isSessionThread();
            boolean needsInitialization = sub.needsInitialization;
            if (needsInitialization || itemUpdate.isValueChanged("mode")) {
                String mode = itemUpdate.getValue("mode");
                sub.eventManager.onChangeMode(mode);
            }
            if (needsInitialization || itemUpdate.isValueChanged("group")) {
                String group = itemUpdate.getValue("group");
                sub.eventManager.onChangeGroup(group);
            }
            if (needsInitialization || itemUpdate.isValueChanged("schema")) {
                String schema = itemUpdate.getValue("schema");
                sub.eventManager.onChangeSchema(schema);
            }
            if (needsInitialization || itemUpdate.isValueChanged("adapter")) {
                String adapter = itemUpdate.getValue("adapter");
                sub.eventManager.onChangeAdapter(adapter);
            }
            if (needsInitialization || itemUpdate.isValueChanged("notification_format")) {
                String format = itemUpdate.getValue("notification_format");
                sub.eventManager.onChangeFormat(format);
            }
            if (needsInitialization || itemUpdate.isValueChanged("trigger")) {
                String trigger = itemUpdate.getValue("trigger");
                sub.eventManager.onChangeTrigger(trigger);
            }
            if (needsInitialization || itemUpdate.isValueChanged("requested_buffer_size")) {
                String size = itemUpdate.getValue("requested_buffer_size");
                sub.eventManager.onChangeRequestedBufferSize(size);
            }
            if (needsInitialization || itemUpdate.isValueChanged("requested_max_frequency")) {
                String freq = itemUpdate.getValue("requested_max_frequency");
                sub.eventManager.onChangeRequestedMaxFrequency(freq);
            }
            if (needsInitialization || itemUpdate.isValueChanged("status_timestamp")) {
                String ts = itemUpdate.getValue("status_timestamp");
                long timestamp = (ts == null ? 0 : Long.parseLong(ts));
                sub.eventManager.onChangeTimestamp(timestamp);
            }
            if (needsInitialization || itemUpdate.isValueChanged("status")) {
                String next = itemUpdate.getValue("status");
                String ts = itemUpdate.getValue("status_timestamp");
                long timestamp = (ts == null ? 0 : Long.parseLong(ts));
                sub.eventManager.onStatusChange(next, timestamp);
            }
            /* subscription is now initialized */
            if (needsInitialization) {                
                sub.needsInitialization = false;
            }
        }

        /**
         * Adds a new server subscription.
         * The method {@link MpnSubscriptionListener#onPropertyChanged(String)} is triggered for each property. 
         */
        void doCreateNewSubscription(String subId, ItemUpdate itemUpdate) {
            assert Assertions.isSessionThread();
            /* add new subscription */
            String mode = itemUpdate.getValue("mode");
            String group = itemUpdate.getValue("group");
            String schema = itemUpdate.getValue("schema");
            String adapter = itemUpdate.getValue("adapter");
            String format = itemUpdate.getValue("notification_format");
            String trigger = itemUpdate.getValue("trigger");
            String buffSize = itemUpdate.getValue("requested_buffer_size");
            String freq = itemUpdate.getValue("requested_max_frequency");
            String status = itemUpdate.getValue("status");
            long timestamp = Long.parseLong(itemUpdate.getValue("status_timestamp"));
            MpnSubscription sub = new MpnSubscription(mode);
            sub.eventManager.onChangeGroup(group);
            sub.eventManager.onChangeSchema(schema);
            sub.eventManager.onChangeAdapter(adapter);
            sub.eventManager.onChangeFormat(format);
            sub.eventManager.onChangeTrigger(trigger);
            sub.eventManager.onChangeRequestedBufferSize(buffSize);
            sub.eventManager.onChangeRequestedMaxFrequency(freq);
            sub.setSubscriptionId(subId);
            sub.eventManager.simulateSubscribe(status, timestamp);
            sub.needsInitialization = false;
            subscribeManager.onServerSubscribeOK(subId, sub);
        }
        
        /**
         * Deletes a subscription.
         * The method {@link MpnSubscriptionListener#onUnsubscription()} is triggered.
         */
        void onDelete(ItemUpdate itemUpdate) {
            assert Assertions.isSessionThread();
            String subId = getSubId(itemUpdate);
            subscribeManager.onDelete(subId);
        }
        
        /**
         * Manages the publishing of a subscription.
         */
        void onAdd(ItemUpdate itemUpdate) {
            assert Assertions.isSessionThread();
            String subId = getSubId(itemUpdate);
            subscribeManager.onAddedSubscription(subId);
        }
        
        /**
         * Returns the subscription id of an item.
         * The id is stored in the field {@code key} of the update and is coded as {@code SUB-<id>}.
         */
        String getSubId(ItemUpdate itemUpdate) {
            String key = itemUpdate.getValue("key");
            assert key.startsWith("SUB-");
            return key.substring(4); // strip SUB- prefix
        }
    } // SubscriptionListener
    
    private final State NO_SESSION = new NoSessionState();
    private final State SESSION_OK = new SessionOkState();
    private final State READY = new ReadyState();
    private final State REGISTERING = new RegisteringState();
    private final State REGISTERED = new RegisteredState();
    private State state = NO_SESSION;
    
    /**
     * State machine of the MpnManager. Each state is implemented by a subclass which overrides the methods
     * representing valid transitions.
     */
    private class State {
        /**
         * Fired when a new session starts.
         */
        void onSessionStart() {
            throwError("onSessionStart");
        }
        
        /**
         * Fired when the current session closes.
         * 
         * @param recovery if the flag is true, the closing is due to an automatic recovery.
         * Otherwise the closing is due to a disconnection made by the user
         * (see {@link LightstreamerClient#disconnect()}).
         */
        void onSessionClose(boolean recovery) {
            dev_manager.unsubscribeFromDEVAdapter();
            subs_manager.unsubscribeFromSUBSAdapter();
            requestManager.createTutorContext();
            subscribeManager.onSessionClose(recovery);
            unsubscribeManager.onSessionClose(recovery);
            unsubscribeFilterManager.onSessionClose(recovery);
            badgeManager.onSessionClose(recovery);
            if (mpnDevice != null) {
                mpnDevice.eventManager.onSessionClose(recovery);
            }
        }
        
        /**
         * Fired when the user registers the device.
         */
        void register(AbstractMpnDevice device) {
            throwError("register");
        }
        
        /**
         * Fired when the message MPNREG is received.
         */
        void onRegisterOK(String deviceId, String adapterName) {
            throwError("onRegisterOK");
        }
        
        /**
         * Fired when the registration fails.
         */
        void onRegisterError(int code, String message) {
            throwError("onRegisterError");
        }
        
        /**
         * Fired when the user subscribes to an item.
         */
        void subscribe(MpnSubscription sub) {
            subscribeManager.addWaiting(sub);
        }

        /**
         * Fired when the user unsubscribes from an item.
         */
        void unsubscribe(MpnSubscription sub) {
            /*
             * Sometimes a subscription is immediately followed by an unsubscription.
             * If the subscription has not been sent over the network (i.e. it is in the waiting list), 
             * we can avoid to send both the subscription and the unsubscription requests 
             * and simply make a state transition on the subscription object.
             */
            boolean subscribeRequestWasWaiting = subscribeManager.removeWaiting(sub);
            if (subscribeRequestWasWaiting) {
                sub.eventManager.cancelSubscription();
                
            } else {
                
                /* send the unsubscription request when the the device is registered */
                unsubscribeManager.addWaiting(sub);
            }
        }

        /**
         * Fired when the user unsubscribes from a set of items using a filter.
         */
        void unsubscribeFilter(String filter) {
            unsubscribeFilterManager.addWaiting(filter);
        }
        
        /**
         * Fired when the user resets the badge.
         */
        void resetBadge() {
            badgeManager.addWaiting();
        }
        
        /**
         * Changes the current state.
         */
        void next(State next, String event) {
            if (log.isDebugEnabled()) {
                log.debug("MpnManager state change (" + getDeviceId() + ") on '" + event + "': " + state + " -> " + next);
            }                
            state = next;
        }
        
        /**
         * Signals that the state transition is not valid.
         */
        void throwError(String event) {
            throw new AssertionError("Unexpected event '" + event + "' in state " + this + " (" + getDeviceId() + ")");
        }
        
        @Override
        public String toString() {
            return getClass().getSimpleName();
        }
    } // State

    /**
     * In this state the session is not available, so the requests must be buffered. 
     */
    private class NoSessionState extends State {
        @Override
        void onSessionStart() {
            next(SESSION_OK, "onSessionStart");
        }
        
        @Override
        void onSessionClose(boolean recovery) {
            super.onSessionClose(recovery);
            next(NO_SESSION, "onSessionClose");
        }
        
        @Override
        void register(AbstractMpnDevice device) {
            resetDevice(device);
            next(READY, "register");
        }
    } // NoSessionState
    
    /**
     * In this state the session is established, but the user has provided no device.
     */
    private class SessionOkState extends State {
        @Override
        void onSessionClose(boolean recovery) {
            super.onSessionClose(recovery);
            next(NO_SESSION, "onSessionClose");
        }
        
        @Override
        void register(AbstractMpnDevice device) {
            resetDevice(device);
            requestManager.sendRegisterRequest(0);
            next(REGISTERING, "register");
        }
    } // SessionOkState

    /**
     * In this state the user has provided a device, but the registration is
     * suspended because there is no session available.
     */
    private class ReadyState extends State {
        @Override
        void onSessionStart() {
            requestManager.sendRegisterRequest(0);
            next(REGISTERING, "onSessionStart");
        }

        @Override
        void onSessionClose(boolean recovery) {
            super.onSessionClose(recovery);
            next(READY, "onSessionClose");
        }
        
        @Override
        void register(AbstractMpnDevice device) {
            resetDevice(device);
            next(READY, "register");
        }
    } // ReadyState
    
    /**
     * In this state the manager is trying to register the provided device.
     */
    private class RegisteringState extends State {
        @Override
        void onSessionClose(boolean recovery) {
            super.onSessionClose(recovery);
            next(READY, "onSessionClose");
        }
        
        @Override
        void register(AbstractMpnDevice device) {
            resetDevice(device);
            requestManager.sendRegisterRequest(0);
            next(REGISTERING, "register");
        }
        
        @Override
        void onRegisterOK(String deviceId, String adapterName) {
            mpnDevice.eventManager.onRegisterOK(deviceId, adapterName);
            requestManager.sendWaitings();
            dev_manager.subscribeToDEVAdapter(adapterName);
            subs_manager.subscribeToSUBSAdapter(adapterName);
            next(REGISTERED, "onRegisterOK");
        }
        
        @Override
        void onRegisterError(int code, String message) {
            mpnDevice.eventManager.onRegisterError(code, message);
            next(SESSION_OK, "onRegisterError");
        }
    } // RegisteringState
    
    /**
     * In this state the device is registered and the manager is ready to send MPN requests.
     */
    private class RegisteredState extends State {
        @Override
        void onSessionClose(boolean recovery) {
            super.onSessionClose(recovery);
            next(READY, "onSessionClose");
        }
        
        @Override
        void register(AbstractMpnDevice device) {
            resetDevice(device);
            requestManager.sendRegisterRequest(0);
            next(REGISTERING, "register");
        }
        
        @Override
        void subscribe(MpnSubscription sub) {
            subscribeManager.subscribe(sub);
        }
        
        @Override
        void unsubscribe(final MpnSubscription sub) {
            /*
             * Sometimes the user requests an unsubscription when the corresponding subscription has been sent 
             * over the network and the client is awaiting the response.
             * In this case we must await the subscription response and then send the unsubscription request.
             */
            PendingRequest req = subscribeManager.getPendingRequest(sub);
            if (req == null) {
                /* there is no pending subscription */
                unsubscribeManager.unsubscribe(sub);
                
            } else {
                /* set the listener that will fire the unsubscription when the subscription request is completed */
                req.setOnCompleteHandler(new PendingRequest.OnCompleteHandler() {
                    @Override
                    public void onComplete(boolean success) {
                        if (success) {
                            unsubscribeManager.unsubscribe(sub);
                        } else {
                            /*
                             * ignore the unsubscription request since the subscription request failed
                             */
                        }
                    }
                });
            }
        }
        
        @Override
        void unsubscribeFilter(String filter) {
            unsubscribeFilterManager.unsubscribe(new MpnUnsubscribeFilter(filter));
        }
        
        @Override
        void resetBadge() {
            badgeManager.resetBadge();
        }
    } // RegisteredState
}
