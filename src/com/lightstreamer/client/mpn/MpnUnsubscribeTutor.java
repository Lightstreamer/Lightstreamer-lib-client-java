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

import com.lightstreamer.client.mpn.MpnManager.MpnRequestManager;
import com.lightstreamer.client.session.SessionThread;

/**
 * Tutor of MPN unsubscription requests.
 */
public class MpnUnsubscribeTutor extends MpnTutor {

    private final MpnSubscription subscription;

    public MpnUnsubscribeTutor(long timeoutMs, SessionThread thread, MpnSubscription sub, MpnRequestManager mpnRequestManager, TutorContext context) {
        super(timeoutMs, thread, mpnRequestManager, context);
        this.subscription = sub;
    }

    @Override
    protected void doRecovery() {
        mpnRequestManager.sendUnsubscribeRequest(timeoutMs, subscription);
    }
}
