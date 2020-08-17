package com.lightstreamer.mpn.util.builders;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class NotificationBuilder {
    protected Map<String, Object> _descriptor;
    
    public NotificationBuilder(Map<String, Object> descriptor) {
        _descriptor= new HashMap<String, Object>(descriptor);
    }

    public NotificationBuilder() {
        _descriptor= new HashMap<String, Object>();
    }
    
    @SuppressWarnings("unchecked") 
    protected Map<String, Object> getMap(String path) {
        Map<String, Object> descriptor= _descriptor;
        
        for (String name : path.split("\\.")) {
            Map<String, Object> objectMap= (Map<String, Object>) descriptor.get(name);
            if (objectMap == null) {
                objectMap= new HashMap<String, Object>();
                descriptor.put(name,  objectMap);
            }
            
            descriptor= objectMap;
        }
        
        return descriptor;
    }
    
    public Map<String, Object> build() {
        return Collections.unmodifiableMap(_descriptor);
    }
}
