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
package com.lightstreamer.client.events;

import com.lightstreamer.client.ClientListener;
import com.lightstreamer.client.LightstreamerClient;

public class ClientListenerEndEvent implements Event<ClientListener> {

  private final LightstreamerClient client;

  public ClientListenerEndEvent(LightstreamerClient client) {
    this.client = client;
  }
  
  @Override
  public void applyTo(ClientListener listener) {
    listener.onListenEnd(client);
  }

}
