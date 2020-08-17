/*
 * Copyright (c) 2004-2015 Weswit s.r.l., Via Campanini, 6 - 20124 Milano, Italy.
 * All rights reserved.
 * www.lightstreamer.com
 *
 * This software is the confidential and proprietary information of
 * Weswit s.r.l.
 * You shall not disclose such Confidential Information and shall use it
 * only in accordance with the terms of the license agreement you entered
 * into with Weswit s.r.l.
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
