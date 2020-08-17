package com.lightstreamer.client.mpn;

/**
 * An MPN registration request.
 */
public class MpnRegisterRequest extends MpnRequest {
    
    public MpnRegisterRequest(MpnDeviceInterface device) {
        addParameter("LS_op", "register");
        addParameter("PN_type", device.getPlatform());
        addParameter("PN_appId", device.getApplicationId());
        if (device.getPreviousDeviceToken() == null) {
            addParameter("PN_deviceToken", device.getDeviceToken());            
        } else {
            addParameter("PN_deviceToken", device.getPreviousDeviceToken());
            addParameter("PN_newDeviceToken", device.getDeviceToken());
        }
    }
}
