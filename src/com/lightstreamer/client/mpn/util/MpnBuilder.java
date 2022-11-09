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


package com.lightstreamer.client.mpn.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.JsonWriter;
import com.lightstreamer.client.mpn.MpnSubscription;
import com.lightstreamer.mpn.util.builders.GoogleNotificationBuilder;

/**
 * Utility class that provides methods to build or parse the JSON structure used to represent the format of a push notification.<BR>
 * It provides getters and setters for the fields of a push notification, following the format specified by Google's Firebase Cloud Messaging (FCM).
 * This format is compatible with {@link MpnSubscription#setNotificationFormat(String)}.
 * 
 * @see MpnSubscription#setNotificationFormat(String)
 */
public class MpnBuilder {

    private static final HashMap<String, Object> jsonWriteOptions = new HashMap<>();
    private static final HashMap<String, Object> jsonReadOptions = new HashMap<>();
    
    private final GoogleNotificationBuilder builder;
    
    static {
        jsonWriteOptions.put(JsonWriter.TYPE, false); // Omit class name in JSON strings
        jsonReadOptions.put(JsonReader.USE_MAPS, true); // Use maps in deserialization
    }
    
    /**
     * Creates an empty object to be used to create a push notification format from scratch.<BR>
     * Use setters methods to set the value of push notification fields.
     */
    public MpnBuilder() {
        builder = new GoogleNotificationBuilder();
    }
    
    /**
     * Creates an object based on the specified push notification format.<BR>
     * Use getter methods to obtain the value of push notification fields.
     * 
     * @param notificationFormat A JSON structure representing a push notification format.
     * 
     * @throws IllegalArgumentException if the notification is not a valid JSON structure.
     */
    @SuppressWarnings("unchecked") 
    public MpnBuilder(@Nonnull String notificationFormat) {
        Object obj= JsonReader.jsonToJava(notificationFormat, jsonReadOptions);
        if (!(obj instanceof Map))
            throw new IllegalArgumentException("Not a valid notification format");
        
        builder= new GoogleNotificationBuilder((Map<String, Object>) obj);
    }

    /**
     * Produces the JSON structure for the push notification format specified by this object.
     * @return the JSON structure for the push notification format.
     */
    public @Nonnull String build() {
        Map<String, Object> map = builder.build();
        String json = JsonWriter.objectToJson(map, jsonWriteOptions);
        return json;
    }

    /**
     * Sets the <code>android.collapse_key</code> field.
     * 
     * @param collapseKey A string to be used for the <code>android.collapse_key</code> field value, or null to clear it.
     * @return this MpnBuilder object, for fluent use.
     */
    public MpnBuilder collapseKey(String collapseKey) {
        builder.androidCollapseKey(collapseKey);
        return this;
    }
    
    /**
     * Gets the value of <code>android.collapse_key</code> field.
     * @return the value of <code>android.collapse_key</code> field, or null if absent.
     */
    public String collapseKey() {
        return builder.androidCollapseKey();
    }
    
    /**
     * Sets the <code>android.priority</code> field.
     * 
     * @param priority A string to be used for the <code>android.priority</code> field value, or null to clear it.
     * @return this MpnBuilder object, for fluent use.
     */
    public MpnBuilder priority(String priority) {
        builder.androidPriority(priority);
        return this;
    }
    
    /**
     * Gets the value of <code>android.priority</code> field.
     * @return the value of <code>android.priority</code> field, or null if absent.
     */
    public String priority() {
        return builder.androidPriority();
    }
    
    /**
     * @deprecated
     * The <code>content_available</code> is no more supported on Firebase Cloud Messaging.
     * 
     * @param contentAvailable Ignored.
     * @return this MpnBuilder object, for fluent use.
     */
    @Deprecated
    public MpnBuilder contentAvailable(String contentAvailable) {
        return this;
    }
    
    /**
     * @deprecated
     * The <code>content_available</code> is no more supported on Firebase Cloud Messaging.
     * 
     * @return always null.
     */
    @Deprecated
    public String contentAvailableAsString() {
        return null;
    }
            
    /**
     * @deprecated
     * The <code>content_available</code> is no more supported on Firebase Cloud Messaging.
     * 
     * @param contentAvailable Ignored.
     * @return this MpnBuilder object, for fluent use.
     */
    @Deprecated
    public MpnBuilder contentAvailable(Boolean contentAvailable) {
        return this;
    }
    
    /**
     * @deprecated
     * The <code>content_available</code> is no more supported on Firebase Cloud Messaging.
     * 
     * @return always null.
     */
    @Deprecated
    public Boolean contentAvailableAsBoolean() {
        return null;
    }

    /**
     * Sets the <code>android.ttl</code> field with a string value.
     * 
     * @param timeToLive A string to be used for the <code>android.ttl</code> field value, or null to clear it.
     * @return this MpnBuilder object, for fluent use.
     */
    public MpnBuilder timeToLive(String timeToLive) {
        builder.androidTimeToLive(timeToLive);
        return this;
    }
    
    /**
     * Gets the value of <code>android.ttl</code> field as a string.
     * @return a string with the value of <code>android.ttl</code> field, or null if absent.
     */
    public String timeToLiveAsString() {
        return builder.androidTimeToLiveAsString();
    }
            
    /**
     * Sets the <code>android.ttl</code> field with an integer value.
     * 
     * @param timeToLive An integer to be used for the <code>android.ttl</code> field value, or null to clear it.
     * @return this MpnBuilder object, for fluent use.
     */
    public MpnBuilder timeToLive(Integer timeToLive) {
        builder.androidTimeToLive(timeToLive);
        return this;
    }
    
    /**
     * Gets the value of <code>android.ttl</code> field as an integer.
     * @return an integer with the value of <code>android.ttl</code> field, or null if absent.
     */
    public Integer timeToLiveAsInteger() {
        return builder.androidTimeToLiveAsInteger();
    }
    
    /**
     * Sets the <code>android.notification.title</code> field.
     * 
     * @param title A string to be used for the <code>android.notification.title</code> field value, or null to clear it.
     * @return this MpnBuilder object, for fluent use.
     */
    public MpnBuilder title(String title) {        
        builder.androidTitle(title);
        return this;
    }
    
    /**
     * Gets the value of <code>android.notification.title</code> field.
     * @return the value of <code>android.notification.title</code> field, or null if absent.
     */
    public String title() {
        return builder.androidTitle();
    }

    /**
     * Sets the <code>android.notification.title_loc_key</code> field.
     * 
     * @param titleLocKey A string to be used for the <code>android.notification.title_loc_key</code> field value, or null to clear it.
     * @return this MpnBuilder object, for fluent use.
     */
    public MpnBuilder titleLocKey(String titleLocKey) {        
        builder.androidTitleLocKey(titleLocKey);
        return this;
    }
    
    /**
     * Gets the value of <code>android.notification.title_loc_key</code> field.
     * @return the value of <code>android.notification.title_loc_key</code> field, or null if absent.
     */
    public String titleLocKey() {
        return builder.androidTitleLocKey();
    }

    /**
     * Sets the <code>android.notification.title_loc_args</code> field.
     * 
     * @param titleLocArguments A list of strings to be used for the <code>android.notification.title_loc_args</code> field value, or null to clear it.
     * @return this MpnBuilder object, for fluent use.
     */
    public MpnBuilder titleLocArguments(List<String> titleLocArguments) {        
        builder.androidTitleLocArguments(titleLocArguments);
        return this;
    }
    
    /**
     * Gets the value of <code>android.notification.title_loc_args</code> field.
     * @return a list of strings with the value of <code>android.notification.title_loc_args</code> field, or null if absent.
     */
    public List<String> titleLocArguments() {
        return builder.androidTitleLocArguments();
    }

    /**
     * Sets the <code>android.notification.body</code> field.
     * 
     * @param body A string to be used for the <code>android.notification.body</code> field value, or null to clear it.
     * @return this MpnBuilder object, for fluent use.
     */
    public MpnBuilder body(String body) {        
        builder.androidBody(body);
        return this;
    }
    
    /**
     * Gets the value of <code>android.notification.body</code> field.
     * @return the value of <code>android.notification.body</code> field, or null if absent.
     */
    public String body() {
        return builder.androidBody();
    }

    /**
     * Sets the <code>android.notification.body_loc_key</code> field.
     * 
     * @param bodyLocKey A string to be used for the <code>android.notification.body_loc_key</code> field value, or null to clear it.
     * @return this MpnBuilder object, for fluent use.
     */
    public MpnBuilder bodyLocKey(String bodyLocKey) {        
        builder.androidBodyLocKey(bodyLocKey);
        return this;
    }
    
    /**
     * Gets the value of <code>android.notification.body_loc_key</code> field.
     * @return the value of <code>android.notification.body_loc_key</code> field, or null if absent.
     */
    public String bodyLocKey() {
        return builder.androidBodyLocKey();
    }

    /**
     * Sets the <code>android.notification.body_loc_args</code> field.
     * 
     * @param bodyLocArguments A list of strings to be used for the <code>android.notification.body_loc_args</code> field value, or null to clear it.
     * @return this MpnBuilder object, for fluent use.
     */
    public MpnBuilder bodyLocArguments(List<String> bodyLocArguments) {        
        builder.androidBodyLocArguments(bodyLocArguments);
        return this;
    }
    
    /**
     * Gets the value of <code>android.notification.body_loc_args</code> field.
     * @return a list of strings with the value of <code>android.notification.body_loc_args</code> field, or null if absent.
     */
    public List<String> bodyLocArguments() {
        return builder.androidBodyLocArguments();
    }
    
    /**
     * Sets the <code>android.notification.icon</code> field.
     * 
     * @param icon A string to be used for the <code>android.notification.icon</code> field value, or null to clear it.
     * @return this MpnBuilder object, for fluent use.
     */
    public MpnBuilder icon(String icon) {        
        builder.androidIcon(icon);
        return this;
    }
    
    /**
     * Gets the value of <code>android.notification.icon</code> field.
     * @return the value of <code>android.notification.icon</code> field, or null if absent.
     */
    public String icon() {
        return builder.androidIcon();
    }

    /**
     * Sets the <code>android.notification.sound</code> field.
     * 
     * @param sound A string to be used for the <code>android.notification.sound</code> field value, or null to clear it.
     * @return this MpnBuilder object, for fluent use.
     */
    public MpnBuilder sound(String sound) {        
        builder.androidSound(sound);
        return this;
    }
    
    /**
     * Gets the value of <code>android.notification.sound</code> field.
     * @return the value of <code>android.notification.sound</code> field, or null if absent.
     */
    public String sound() {
        return builder.androidSound();
    }
    
    /**
     * Sets the <code>android.notification.tag</code> field.
     * 
     * @param tag A string to be used for the <code>android.notification.tag</code> field value, or null to clear it.
     * @return this MpnBuilder object, for fluent use.
     */
    public MpnBuilder tag(String tag) {        
        builder.androidTag(tag);
        return this;
    }
    
    /**
     * Gets the value of <code>android.notification.tag</code> field.
     * @return the value of <code>android.notification.tag</code> field, or null if absent.
     */
    public String tag() {
        return builder.androidTag();
    }
    
    /**
     * Sets the <code>android.notification.color</code> field.
     * 
     * @param color A string to be used for the <code>android.notification.color</code> field value, or null to clear it.
     * @return this MpnBuilder object, for fluent use.
     */
    public MpnBuilder color(String color) {        
        builder.androidColor(color);
        return this;
    }
    
    /**
     * Gets the value of <code>android.notification.color</code> field.
     * @return the value of <code>android.notification.color</code> field, or null if absent.
     */
    public String color() {
        return builder.androidColor();
    }
    
    /**
     * Sets the <code>android.notification.click_action</code> field.
     * 
     * @param clickAction A string to be used for the <code>android.notification.click_action</code> field value, or null to clear it.
     * @return this MpnBuilder object, for fluent use.
     */
    public MpnBuilder clickAction(String clickAction) {        
        builder.androidClickAction(clickAction);
        return this;
    }
    
    /**
     * Gets the value of <code>android.notification.click_action</code> field.
     * @return the value of <code>android.notification.click_action</code> field, or null if absent.
     */
    public String clickAction() {
        return builder.androidClickAction();
    }
     
    /**
     * Sets sub-fields of the <code>android.data</code> field.
     * 
     * @param data A map to be used for sub-fields of the <code>android.data</code> field, or null to clear it.
     * @return this MpnBuilder object, for fluent use.
     */
    public MpnBuilder data(Map<String, String> data) {        
        builder.androidData(data);
        return this;
    }
    
    /**
     * Gets sub-fields of the <code>android.data</code> field.
     * @return A map with sub-fields of the <code>android.data</code> field, or null if absent. 
     */
    public Map<String, String> data() {
        return builder.androidData();
    }
}
