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
 * An MPN request to delete the subscriptions satisfying a filter (the possible values are ALL, ACTIVE or TRIGGERED).
 */
public class MpnUnsubscribeFilterRequest extends MpnRequest {
    
    public MpnUnsubscribeFilterRequest(String deviceId, String filter) {
        assert filter.equals("ALL") || filter.equals("ACTIVE") || filter.equals("TRIGGERED");
        addParameter("LS_op", "deactivate");
        addParameter("PN_deviceId", deviceId);
        if (! "ALL".equals(filter)) {            
            addParameter("PN_subscriptionStatus", filter);
        }
    }
}
