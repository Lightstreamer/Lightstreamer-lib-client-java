package com.lightstreamer.client.requests;

import com.lightstreamer.util.IdGenerator;

public abstract class NumberedRequest extends LightstreamerRequest {

    protected final long requestId = IdGenerator.getNextRequestId();

    public NumberedRequest() {
        addParameter("LS_reqId", requestId);
    }

    public final long getRequestId() {
        return requestId;
    }
}