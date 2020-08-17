package com.lightstreamer.mpn.util.builders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppleNotificationBuilder extends NotificationBuilder {
    
    public AppleNotificationBuilder() {}
    
    public AppleNotificationBuilder(Map<String, Object> descriptor) {
        super(descriptor);
    }
    
    public String alert() {
        Object alert= getMap("aps").get("alert");
        
        if (alert == null)
            return null;
        else if (alert instanceof String)
            return (String) alert;
        else
            throw new IllegalStateException("Alert is not a string");
    }
    
    public AppleNotificationBuilder alert(String alert) {
        if (alert != null)
            getMap("aps").put("alert", alert);
        else
            getMap("aps").remove("alert");
        
        return this;
    }
    
    public String sound() {
        return (String) getMap("aps").get("sound");
    }
    
    public AppleNotificationBuilder sound(String sound) {
        if (sound != null)
            getMap("aps").put("sound", sound);
        else
            getMap("aps").remove("sound");
        
        return this;
    }
            
    public String badgeAsString() {
        Object badge= getMap("aps").get("badge");
        
        if (badge == null)
            return null;
        else 
            return badge.toString();
    }
    
    public Integer badgeAsInteger() {
        Object badge= getMap("aps").get("badge");
        
        if (badge == null)
            return null;
        else 
            return Integer.parseInt(badge.toString());
    }

    public AppleNotificationBuilder badge(String badge) {
        if (badge != null)
            getMap("aps").put("badge", badge);
        else
            getMap("aps").remove("badge");
        
        return this;
    }

    public AppleNotificationBuilder badge(Integer badge) {
        if (badge != null)
            getMap("aps").put("badge", badge);
        else
            getMap("aps").remove("badge");
        
        return this;
    }

    public String contentAvailableAsString() {
        Object contentAvailable= getMap("aps").get("content-available");
        
        if (contentAvailable == null)
            return null;
        else 
            return contentAvailable.toString();
    }

    public Integer contentAvailableAsInteger() {
        Object contentAvailable= getMap("aps").get("content-available");
        
        if (contentAvailable == null)
            return null;
        else 
            return Integer.parseInt(contentAvailable.toString());
    }

    public AppleNotificationBuilder contentAvailable(String contentAvailable) {
        if (contentAvailable != null)
            getMap("aps").put("content-available", contentAvailable);
        else
            getMap("aps").remove("content-available");
        
        return this;
    }

    public AppleNotificationBuilder contentAvailable(Integer contentAvailable) {
        if (contentAvailable != null)
            getMap("aps").put("content-available", contentAvailable);
        else
            getMap("aps").remove("content-available");
        
        return this;
    }
    
    public String mutableContentAsString() {
        Object mutableContent= getMap("aps").get("mutable-content");
        
        if (mutableContent == null)
            return null;
        else 
            return mutableContent.toString();
    }

    public Integer mutableContentAsInteger() {
        Object mutableContent= getMap("aps").get("mutable-content");
        
        if (mutableContent == null)
            return null;
        else 
            return Integer.parseInt(mutableContent.toString());
    }

    public AppleNotificationBuilder mutableContent(String mutableContent) {
        if (mutableContent != null)
            getMap("aps").put("mutable-content", mutableContent);
        else
            getMap("aps").remove("mutable-content");
        
        return this;
    }

    public AppleNotificationBuilder mutableContent(Integer mutableContent) {
        if (mutableContent != null)
            getMap("aps").put("mutable-content", mutableContent);
        else
            getMap("aps").remove("mutable-content");
        
        return this;
    }

    public String category() {
        return (String) getMap("aps").get("category");
    }
    
    public AppleNotificationBuilder category(String category) {
        if (category != null)
            getMap("aps").put("category", category);
        else
            getMap("aps").remove("category");
        
        return this;
    }
            
    public String threadId() {
        return (String) getMap("aps").get("thread-id");
    }
    
    public AppleNotificationBuilder threadId(String threadId) {
        if (threadId != null)
            getMap("aps").put("thread-id", threadId);
        else
            getMap("aps").remove("thread-id");
        
        return this;
    }
    
    public String title() {
        return (String) getMap("aps.alert").get("title");
    }
    
    public AppleNotificationBuilder title(String title) {
        if (title != null)
            getMap("aps.alert").put("title", title);
        else
            getMap("aps.alert").remove("title");
        
        return this;
    }

    public String titleLocKey() {
        return (String) getMap("aps.alert").get("title-loc-key");
    }
    
    public AppleNotificationBuilder titleLocKey(String titleLocKey) {
        if (titleLocKey != null)
            getMap("aps.alert").put("title-loc-key", titleLocKey);
        else
            getMap("aps.alert").remove("title-loc-key");
        
        return this;
    }

    @SuppressWarnings("unchecked") 
    public List<String> titleLocArguments() {
        return (List<String>) getMap("aps.alert").get("title-loc-args");
    }
    
    public AppleNotificationBuilder titleLocArguments(List<String> titleLocArguments) {
        if (titleLocArguments != null)
            getMap("aps.alert").put("title-loc-args", titleLocArguments);
        else
            getMap("aps.alert").remove("title-loc-args");
        
        return this;
    }
    
    public String subtitle() {
        return (String) getMap("aps.alert").get("subtitle");
    }
    
    public AppleNotificationBuilder subtitle(String subtitle) {
        if (subtitle != null)
            getMap("aps.alert").put("subtitle", subtitle);
        else
            getMap("aps.alert").remove("subtitle");
        
        return this;
    }

    public String body() {
        return (String) getMap("aps.alert").get("body");
    }
    
    public AppleNotificationBuilder body(String body) {
        if (body != null)
            getMap("aps.alert").put("body", body);
        else
            getMap("aps.alert").remove("body");
        
        return this;
    }

    public String bodyLocKey() {
        return (String) getMap("aps.alert").get("loc-key");
    }
    
    public AppleNotificationBuilder bodyLocKey(String bodyLocKey) {
        if (bodyLocKey != null)
            getMap("aps.alert").put("loc-key", bodyLocKey);
        else
            getMap("aps.alert").remove("loc-key");
        
        return this;
    }

    @SuppressWarnings("unchecked") 
    public List<String> bodyLocArguments() {
        return (List<String>) getMap("aps.alert").get("loc-args");
    }
    
    public AppleNotificationBuilder bodyLocArguments(List<String> bodyLocArguments) {
        if (bodyLocArguments != null)
            getMap("aps.alert").put("loc-args", bodyLocArguments);
        else
            getMap("aps.alert").remove("loc-args");
        
        return this;
    }
    
    public String locActionKey() {
        return (String) getMap("aps.alert").get("action-loc-key");
    }
    
    public AppleNotificationBuilder locActionKey(String locActionKey) {
        if (locActionKey != null)
            getMap("aps.alert").put("action-loc-key", locActionKey);
        else
            getMap("aps.alert").remove("action-loc-key");
        
        return this;
    }

    public String launchImage() {
        return (String) getMap("aps.alert").get("launch-image");
    }
    
    public AppleNotificationBuilder launchImage(String launchImage) {
        if (launchImage != null)
            getMap("aps.alert").put("launch-image", launchImage);
        else
            getMap("aps.alert").remove("launch-image");
        
        return this;
    }
    
    public String action() {
        return (String) getMap("aps.alert").get("action");
    }
    
    public AppleNotificationBuilder action(String action) {
        if (action != null)
            getMap("aps.alert").put("action", action);
        else
            getMap("aps.alert").remove("action");
        
        return this;
    }
    
    @SuppressWarnings("unchecked") 
    public List<String> urlArguments() {
        return (List<String>) getMap("aps").get("url-args");
    }
    
    public AppleNotificationBuilder urlArguments(List<String> urlArguments) {
        if (urlArguments != null)
            getMap("aps").put("url-args", urlArguments);
        else
            getMap("aps").remove("url-args");
        
        return this;
    }

    public Map<String, Object> customData() {
        Map<String, Object> customData= new HashMap<String, Object>(_descriptor);
        customData.remove("aps");

        return customData;
    }
    
    public AppleNotificationBuilder customData(Map<String, Object> customData) {
        for (String key : _descriptor.keySet()) {
            if (key.equals("aps"))
                continue;
            
            _descriptor.remove(key);
        }

        if (customData != null)
            _descriptor.putAll(customData);
        
        return this;
    }
}
