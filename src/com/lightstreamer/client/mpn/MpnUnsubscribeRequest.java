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

/**
 * An MPN request to delete a subscription.
 */
public class MpnUnsubscribeRequest extends MpnRequest {
    
    private final String subscriptionId;
    
    public MpnUnsubscribeRequest(String deviceId, MpnSubscription sub) {
        subscriptionId = sub.getSubscriptionId();
        addParameter("LS_op", "deactivate");
        addParameter("PN_deviceId", deviceId);
        addParameter("PN_subscriptionId", sub.getSubscriptionId());
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }
}
