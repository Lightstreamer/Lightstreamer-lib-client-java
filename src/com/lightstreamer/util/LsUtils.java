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


package com.lightstreamer.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

public class LsUtils {
    
    /**
     * Returns URI from string.
     */
    public static @Nonnull URI uri(String uri) {
        try {
            return new URI(uri);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }
    
    /**
     * Returns whether the URI is secured.
     */
    public static boolean isSSL(URI uri) {
        String scheme = uri.getScheme();
        return "https".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme);
    }
    
    /**
     * Returns the port of an URI.
     */
    public static int port(URI uri) {
        int port = uri.getPort();
        if (port == -1) {
            return isSSL(uri) ? 443 : 80;
        } else {
            return port;
        }
    }
    
    /**
     * Implementation from http://commons.apache.org/proper/commons-lang/javadocs/api-3.8.1/org/apache/commons/lang3/StringUtils.html
     */
    public static String join(final Object[] array, final char separator) {
        if (array == null) {
            return null;
        }
        int startIndex = 0;
        int endIndex = array.length;
        final int noOfItems = endIndex - startIndex;
        if (noOfItems <= 0) {
            return "";
        }
        final StringBuilder buf = new StringBuilder(noOfItems);
        for (int i = startIndex; i < endIndex; i++) {
            if (i > startIndex) {
                buf.append(separator);
            }
            if (array[i] != null) {
                buf.append(array[i]);
            }
        }
        return buf.toString();
    }
    
    /**
     * Implementation from http://commons.apache.org/proper/commons-lang/javadocs/api-3.8.1/org/apache/commons/lang3/StringUtils.html
     */
    public static String[] split(final String str, final char separatorChar) {
        // Performance tuned for 2.0 (JDK1.4)

        boolean preserveAllTokens = false;
        if (str == null) {
            return null;
        }
        final int len = str.length();
        if (len == 0) {
            return new String[0];
        }
        final List<String> list = new ArrayList<>();
        int i = 0, start = 0;
        boolean match = false;
        boolean lastMatch = false;
        while (i < len) {
            if (str.charAt(i) == separatorChar) {
                if (match || preserveAllTokens) {
                    list.add(str.substring(start, i));
                    match = false;
                    lastMatch = true;
                }
                start = ++i;
                continue;
            }
            lastMatch = false;
            match = true;
            i++;
        }
        if (match || preserveAllTokens && lastMatch) {
            list.add(str.substring(start, i));
        }
        return list.toArray(new String[list.size()]);
    }
    
    public static boolean equals(Object o1, Object o2) {
        return o1 == o2 || (o1 != null && o1.equals(o2));
    }
    
    public static boolean notEquals(Object o1, Object o2) {
        return ! equals(o1, o2);
    }
}
