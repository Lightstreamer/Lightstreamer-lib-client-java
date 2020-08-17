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
package com.lightstreamer.client.session;

/**
 * 
 */
public interface MessagesListener {
  
  void onSessionStart();
  void onSessionClose();
  
  void onMessageAck(String sequence, int number);

  void onMessageOk(String sequence, int number);

  void onMessageDeny(String sequence, int denyCode, String denyMessage,
      int number);

  void onMessageDiscarded(String sequence, int number);

  void onMessageError(String sequence, int errorCode, String errorMessage,
      int number);
}
