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

import java.util.regex.Pattern;

/**
 * 
 */
public class Number {
  public static final boolean ACCEPT_ZERO = true;
  public static final boolean DONT_ACCEPT_ZERO = false;
  public static void verifyPositive(double num, boolean zeroAccepted) {
    boolean positive = isPositive(num, zeroAccepted);
    if (!positive) {
      if (zeroAccepted) {
        throw new IllegalArgumentException("The given value is not valid. Use a positive number or 0");
      } else {
        throw new IllegalArgumentException("The given value is not valid. Use a positive number");
      }
    }
  }
  public static boolean isPositive(double num, boolean zeroAccepted) {
    if (zeroAccepted) {
      if (num < 0) {
        return false;
      }
    } else if (num <= 0) {
      return false;
    }
    return true;
  }
  
  private static final Pattern pattern = Pattern.compile("^[+-]?\\d*\\.?\\d+$"); 
  public static boolean isNumber(String num) {
    return pattern.matcher(num).matches();
  }
}
