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
package com.lightstreamer.client.requests;

import com.lightstreamer.client.session.InternalConnectionOptions;
import com.lightstreamer.client.session.SessionThread;

/**
 * 
 */
public class VoidTutor extends RequestTutor {

  public VoidTutor(SessionThread thread,
      InternalConnectionOptions connectionOptions) {
    super(thread, connectionOptions);
  }

  @Override
  public boolean shouldBeSent() {
    return true;
  }

  @Override
  protected boolean verifySuccess() {
    return true;
  }

  @Override
  protected void doRecovery() {
  }

  @Override
  public void notifyAbort() {
  }

  @Override
  protected boolean isTimeoutFixed() {
    return false;
  }

  @Override
  protected long getFixedTimeout() {
    return 0;
  }
  
  @Override
  protected void startTimeout() {
      /* 
       * doesn't schedule a task on session thread since the void tutor doesn't need retransmissions
       */
  }

}
