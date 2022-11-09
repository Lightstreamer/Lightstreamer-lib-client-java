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

import com.lightstreamer.client.LightstreamerClient;
import com.lightstreamer.client.mpn.MpnManager.MpnRequestManager;
import com.lightstreamer.client.mpn.MpnManager.MpnUnsubscribeFilter;
import com.lightstreamer.client.session.SessionThread;

/**
 * Tutor managing the deletion of the subscriptions satisfying the filter 
 * (see {@link LightstreamerClient#unsubscribeMpnSubscriptions(String)}).
 */
public class MpnUnsubscribeFilterTutor extends MpnTutor {

    private final MpnUnsubscribeFilter filter;

    public MpnUnsubscribeFilterTutor(long timeoutMs, SessionThread thread, MpnRequestManager mpnRequestManager, MpnUnsubscribeFilter filter, TutorContext context) {
        super(timeoutMs, thread, mpnRequestManager, context);
        this.filter = filter;
    }

    @Override
    protected void doRecovery() {
        mpnRequestManager.sendUnsubscribeFilteredRequest(timeoutMs, filter);
    }
    
    @Override
    public void onResponse() {
        super.onResponse();
        mpnRequestManager.onUnsubscriptionFilterResponse(filter);
    }
}
