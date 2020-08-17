/*
 * Copyright (c) 2004-2015 Weswit s.r.l., Via Campanini, 6 - 20124 Milano, Italy.
 * All rights reserved.
 * www.lightstreamer.com
 *
 * This software is the confidential and proprietary information of
 * Weswit s.r.l.
 * You shall not disclose such Confidential Information and shall use it
 * only in accordance with the terms of the license agreement you entered
 * into with Weswit s.r.l.
 */
package com.lightstreamer.client.transport.providers.oio;

import com.lightstreamer.client.session.SessionThread;
import com.lightstreamer.client.transport.providers.HttpProvider;
import com.lightstreamer.client.transport.providers.TransportFactory;

public class OIOHttpProviderFactory extends TransportFactory<HttpProvider> {

    @Override
    public HttpProvider getInstance(SessionThread thread) {
        return new OIOHttpProvider(thread);
    }
    
    @Override
    public boolean isResponseBuffered() {
        return true;
    }
}
