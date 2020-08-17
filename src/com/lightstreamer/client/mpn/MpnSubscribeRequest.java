package com.lightstreamer.client.mpn;

/**
 * An MPN subscription request.
 * <br>
 * If a PN_subscriptionId is present, the request is meant to modify the setting of the corresponding subscription.
 * <br>
 * If a PN_coalescing is present, the request is meant to modify the setting of the subscription having similar
 * setting (typically the same adapter and items). If there is no such subscription, a new one is created.
 */
public class MpnSubscribeRequest extends MpnRequest {
    
    private final String subscriptionId;

    public MpnSubscribeRequest(String subId, String deviceId, MpnSubscription sub) {
        subscriptionId = subId;
        addParameter("LS_subId", subscriptionId);
        addParameter("LS_op", "activate");
        if (sub.getDataAdapter() != null) {
            addParameter("LS_data_adapter", sub.getDataAdapter());
        }
        addParameter("LS_group", sub.getItemsDescriptor());
        addParameter("LS_schema", sub.getFieldsDescriptor());
        addParameter("LS_mode", sub.getMode());
        addParameter("PN_deviceId", deviceId);
        if (sub.getSubscriptionId() != null) {
            // a request to the server to modify the subscription having the specified id
            addParameter("PN_subscriptionId", sub.getSubscriptionId());
        }
        if (sub.getNotificationFormat() != null) {
            addParameter("PN_notificationFormat", sub.getNotificationFormat());
        }
        if (sub.getTriggerExpression() != null) {
            addParameter("PN_trigger", sub.getTriggerExpression());
        }
        if (sub.coalescing) {
            addParameter("PN_coalescing", "true");
        }
        if (sub.getRequestedBufferSize() != null) {
            addParameter("LS_requested_buffer_size", sub.getRequestedBufferSize());
        }
        if (sub.getRequestedMaxFrequency() != null) {
            addParameter("LS_requested_max_frequency", sub.getRequestedMaxFrequency());
        }
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }
}
