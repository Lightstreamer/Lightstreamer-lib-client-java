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
