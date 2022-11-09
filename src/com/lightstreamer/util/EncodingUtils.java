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

import java.io.UnsupportedEncodingException;

public class EncodingUtils {
    
    /**
     * Converts a string containing sequences as {@code %<hex digit><hex digit>} into a new string 
     * where such sequences are transformed in UTF-8 encoded characters. <br> 
     * For example the string "a%C3%A8" is converted to "aè" because the sequence 'C3 A8' is 
     * the UTF-8 encoding of the character 'è'.
     */
    public static String unquote(String s) {
        assert s != null;
        try {
            // to save space and time the input byte sequence is also used to store the converted byte sequence.
            // this is possible because the length of the converted sequence is equal to or shorter than the original one.
            byte[] bb = s.getBytes("UTF-8");
            int i = 0, j = 0;
            while (i < bb.length) {
                assert i >= j;
                if (bb[i] == '%') {
                    int firstHexDigit  = hexToNum(bb[i + 1]);
                    int secondHexDigit = hexToNum(bb[i + 2]);
                    bb[j++] = (byte) ((firstHexDigit << 4) + secondHexDigit); // i.e (firstHexDigit * 16) + secondHexDigit
                    i += 3;
                    
                } else {
                    bb[j++] = bb[i++];
                }
            }
            // j contains the length of the converted string
            String ss = new String(bb, 0, j, "UTF-8");
            return ss;
            
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e); // should not happen
        }
    }
    
    /**
     * Converts an ASCII-encoded hex digit in its numeric value.
     */
    private static int hexToNum(int ascii) {
        assert "0123456789abcdefABCDEF".indexOf(ascii) != -1; // ascii is a hex digit
        int hex;
        // NB ascii characters '0', 'A', 'a' have codes 30, 41 and 61
        if ((hex = ascii - 'a' + 10) > 9) {
            // NB (ascii - 'a' + 10 > 9) <=> (ascii >= 'a')
            // and thus ascii is in the range 'a'..'f' because
            // '0' and 'A' have codes smaller than 'a'
            assert 'a' <= ascii && ascii <= 'f';
            assert 10 <= hex && hex <= 15;
            
        } else if ((hex = ascii - 'A' + 10) > 9) {
            // NB (ascii - 'A' + 10 > 9) <=> (ascii >= 'A')
            // and thus ascii is in the range 'A'..'F' because
            // '0' has a code smaller than 'A' 
            // and the range 'a'..'f' is excluded
            assert 'A' <= ascii && ascii <= 'F';
            assert 10 <= hex && hex <= 15;
            
        } else {
            // NB ascii is in the range '0'..'9'
            // because the ranges 'a'..'f' and 'A'..'F' are excluded
            hex =  ascii - '0';
            assert '0' <= ascii && ascii <= '9';
            assert 0 <= hex && hex <= 9;
        }
        assert 0 <= hex && hex <= 15;
        return hex;
    }
}
