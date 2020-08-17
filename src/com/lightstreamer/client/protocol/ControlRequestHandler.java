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
package com.lightstreamer.client.protocol;

import com.lightstreamer.client.requests.LightstreamerRequest;
import com.lightstreamer.client.requests.RequestTutor;
import com.lightstreamer.client.transport.RequestListener;

public interface ControlRequestHandler {

  /**
   * Adds a control/message request.
   */
  void addRequest(LightstreamerRequest request, RequestTutor tutor, RequestListener reqListener);
  
  void setRequestLimit(long limitNum);
  
  void copyTo(ControlRequestHandler newHandler);

  void close(boolean waitPending);
}
