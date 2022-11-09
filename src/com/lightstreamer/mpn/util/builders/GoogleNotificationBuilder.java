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


package com.lightstreamer.mpn.util.builders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GoogleNotificationBuilder extends NotificationBuilder {

    public GoogleNotificationBuilder() {}
    
    public GoogleNotificationBuilder(Map<String, Object> descriptor) {
        super(descriptor);
    }
    
    public String androidCollapseKey() {
        return (String) getMap("android").get("collapse_key");
    }
    
    public GoogleNotificationBuilder androidCollapseKey(String collapseKey) {
        if (collapseKey != null)
            getMap("android").put("collapse_key", collapseKey);
        else
            getMap("android").remove("collapse_key");
        
        return this;
    }
    
    public String androidPriority() {
        return (String) getMap("android").get("priority");
    }
    
    public GoogleNotificationBuilder androidPriority(String priority) {
        if (priority != null)
            getMap("android").put("priority", priority);
        else
            getMap("android").remove("priority");
        
        return this;
    }
    
    public String androidTimeToLiveAsString() {
        Object timeToLive= getMap("android").get("ttl");
        
        if (timeToLive == null)
            return null;
        else 
            return timeToLive.toString();
    }

    public Integer androidTimeToLiveAsInteger() {
        Object timeToLive= getMap("android").get("ttl");
        
        if (timeToLive == null)
            return null;
        else 
            return Integer.parseInt(timeToLive.toString());
    }

    public GoogleNotificationBuilder androidTimeToLive(String timeToLive) {
        if (timeToLive != null)
            getMap("android").put("ttl", timeToLive);
        else
            getMap("android").remove("ttl");
        
        return this;
    }
            
    public GoogleNotificationBuilder androidTimeToLive(Integer timeToLive) {
        if (timeToLive != null)
            getMap("android").put("ttl", timeToLive);
        else
            getMap("android").remove("ttl");
        
        return this;
    }
    
    public String androidTitle() {
        return (String) getMap("android.notification").get("title");
    }
    
    public GoogleNotificationBuilder androidTitle(String title) {        
        if (title != null)
            getMap("android.notification").put("title", title);
        else
            getMap("android.notification").remove("title");
        
        return this;
    }

    public String androidTitleLocKey() {
        return (String) getMap("android.notification").get("title_loc_key");
    }
    
    public GoogleNotificationBuilder androidTitleLocKey(String titleLocKey) {        
        if (titleLocKey != null)
            getMap("android.notification").put("title_loc_key", titleLocKey);
        else
            getMap("android.notification").remove("title_loc_key");
        
        return this;
    }

    @SuppressWarnings("unchecked") 
    public List<String> androidTitleLocArguments() {
        return (List<String>) getMap("android.notification").get("title_loc_args");
    }
    
    public GoogleNotificationBuilder androidTitleLocArguments(List<String> titleLocArguments) {        
        if (titleLocArguments != null)
            getMap("android.notification").put("title_loc_args", titleLocArguments);
        else
            getMap("android.notification").remove("title_loc_args");
        
        return this;
    }

    public String androidBody() {
        return (String) getMap("android.notification").get("body");
    }
    
    public GoogleNotificationBuilder androidBody(String body) {        
        if (body != null)
            getMap("android.notification").put("body", body);
        else
            getMap("android.notification").remove("body");
        
        return this;
    }

    public String androidBodyLocKey() {
        return (String) getMap("android.notification").get("body_loc_key");
    }
    
    public GoogleNotificationBuilder androidBodyLocKey(String bodyLocKey) {        
        if (bodyLocKey != null)
            getMap("android.notification").put("body_loc_key", bodyLocKey);
        else
            getMap("android.notification").remove("body_loc_key");
        
        return this;
    }

    @SuppressWarnings("unchecked") 
    public List<String> androidBodyLocArguments() {
        return (List<String>) getMap("android.notification").get("body_loc_args");
    }
    
    public GoogleNotificationBuilder androidBodyLocArguments(List<String> bodyLocArguments) {        
        if (bodyLocArguments != null)
            getMap("android.notification").put("body_loc_args", bodyLocArguments);
        else
            getMap("android.notification").remove("body_loc_args");
        
        return this;
    }
    
    public String androidIcon() {
        return (String) getMap("android.notification").get("icon");
    }
    
    public GoogleNotificationBuilder androidIcon(String icon) {        
        if (icon != null)
            getMap("android.notification").put("icon", icon);
        else
            getMap("android.notification").remove("icon");
        
        return this;
    }

    public String androidSound() {
        return (String) getMap("android.notification").get("sound");
    }
    
    public GoogleNotificationBuilder androidSound(String sound) {        
        if (sound != null)
            getMap("android.notification").put("sound", sound);
        else
            getMap("android.notification").remove("sound");
        
        return this;
    }
    
    public String androidTag() {
        return (String) getMap("android.notification").get("tag");
    }
    
    public GoogleNotificationBuilder androidTag(String tag) {        
        if (tag != null)
            getMap("android.notification").put("tag", tag);
        else
            getMap("android.notification").remove("tag");
        
        return this;
    }
    
    public String androidColor() {
        return (String) getMap("android.notification").get("color");
    }
    
    public GoogleNotificationBuilder androidColor(String color) {        
        if (color != null)
            getMap("android.notification").put("color", color);
        else
            getMap("android.notification").remove("color");
        
        return this;
    }
    
    public String androidClickAction() {
        return (String) getMap("android.notification").get("click_action");
    }
    
    public GoogleNotificationBuilder androidClickAction(String clickAction) {        
        if (clickAction != null)
            getMap("android.notification").put("click_action", clickAction);
        else
            getMap("android.notification").remove("click_action");
        
        return this;
    }
        
    @SuppressWarnings({ "unchecked", "rawtypes" }) 
    public Map<String, String> androidData() {
        Map data= getMap("android.data");
        
        if (data == null)
            return null;
        else
            return new HashMap<String, String>(data);
    }
    
    public GoogleNotificationBuilder androidData(Map<String, String> data) {        
        if (data != null)
            getMap("android.data").putAll(data);
        else
            getMap("android").remove("data");
        
        return this;
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" }) 
    public Map<String, String> webpushHeaders() {
        Map headers= getMap("webpush.headers");
        
        if (headers == null)
            return null;
        else
            return new HashMap<String, String>(headers);
    }
    
    public GoogleNotificationBuilder webpushHeaders(Map<String, String> headers) {        
        if (headers != null)
            getMap("webpush.headers").putAll(headers);
        else
            getMap("webpush").remove("headers");
        
        return this;
    }

    public String webpushTitle() {
        return (String) getMap("webpush.notification").get("title");
    }
    
    public GoogleNotificationBuilder webpushTitle(String title) {        
        if (title != null)
            getMap("webpush.notification").put("title", title);
        else
            getMap("webpush.notification").remove("title");
        
        return this;
    }

    public String webpushBody() {
        return (String) getMap("webpush.notification").get("body");
    }
    
    public GoogleNotificationBuilder webpushBody(String body) {        
        if (body != null)
            getMap("webpush.notification").put("body", body);
        else
            getMap("webpush.notification").remove("body");
        
        return this;
    }
    
    public String webpushIcon() {
        return (String) getMap("webpush.notification").get("icon");
    }
    
    public GoogleNotificationBuilder webpushIcon(String icon) {        
        if (icon != null)
            getMap("webpush.notification").put("icon", icon);
        else
            getMap("webpush.notification").remove("icon");
        
        return this;
    }
        
    @SuppressWarnings({ "unchecked", "rawtypes" }) 
    public Map<String, String> webpushData() {
        Map data= getMap("webpush.data");
        
        if (data == null)
            return null;
        else
            return new HashMap<String, String>(data);
    }
    
    public GoogleNotificationBuilder webpushData(Map<String, String> data) {        
        if (data != null)
            getMap("webpush.data").putAll(data);
        else
            getMap("webpush").remove("data");
        
        return this;
    }
    
    public String baseTitle() {
        return (String) getMap("notification").get("title");
    }
    
    public GoogleNotificationBuilder baseTitle(String title) {        
        if (title != null)
            getMap("notification").put("title", title);
        else
            getMap("notification").remove("title");
        
        return this;
    }
    
    public String baseBody() {
        return (String) getMap("notification").get("body");
    }
    
    public GoogleNotificationBuilder baseBody(String body) {        
        if (body != null)
            getMap("notification").put("body", body);
        else
            getMap("notification").remove("body");
        
        return this;
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" }) 
    public Map<String, String> baseData() {
        Map data= getMap("data");
        
        if (data == null)
            return null;
        else
            return new HashMap<String, String>(data);
    }
    
    public GoogleNotificationBuilder baseData(Map<String, String> data) {        
        if (data != null)
            getMap("data").putAll(data);
        else
            _descriptor.remove("data");
        
        return this;
    }
}
