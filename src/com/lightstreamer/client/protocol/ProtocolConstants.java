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

/**
 * 
 */
public class ProtocolConstants {
  /**
   * Answer sent by the Server to signal accepted requests.
   */
  public static final String conokCommand = "CONOK";

  /**
   * Constant sent by the Server in case no data has been sent
   * for a configured time.
   */
  public static final String probeCommand = "PROBE";

  /**
   * Constant sent by the Server before closing the connection
   * due to the content length consumption. Upon reception of this
   * constant, the client will rebind the session in a new connection.
   */
  public static final String loopCommand = "LOOP";

  /**
   * Constant sent by the Server before closing the connection because
   * of a server-side explicit decision. Upon reception of this constant,
   * the client should not try to recover by opening a new session.
   */
  public static final String endCommand = "END";

  /**
   * Answer sent by the Server to signal the refusal of a request.
   * This applies only to legal requests. Malformed requests may receive
   * an unpredictable answer.
   */
  public static final String conerrCommand = "CONERR";

  /**
   * End of snapshot marker, written after the Item code.
   */
  public static final String endOfSnapshotMarker = "EOS";

  /**
   * Overflow notification marker, written after the Item code.
   */
  public static final String overflowMarker = "OV";
  
  /**
   * Clear-snapshot notification marker, written after the Item code.
   */
  public static final String clearSnapshotMarker = "CS,";
  
  /**
   * Message notification marker, written as first thing on message notification messages
   */
  public static final String msgMarker = "MSG";
  
  public static final String subscribeMarker = "SUB";
  
  public static final String unsubscribeMarker = "UNSUB,";
  
  public static final String constrainMarker = "CONS,";
  
  public static final String syncMarker = "SYNC,";
  
  public static final String updateMarker = "U,";

  public static final String configurationMarker = "CONF,";
  
  public static final String serverNameMarker = "SERVNAME,";

  public static final String clientIpMarker = "CLIENTIP,";

  public static final String progMarker = "PROG,";

  public static final String noopMarker = "NOOP,";
  
  public static final String reqokMarker = "REQOK,";

  public static final String reqerrMarker = "REQERR,";
  
  public static final String errorMarker = "ERROR,";
  
  public static final String mpnRegisterMarker = "MPNREG,";

  public static final String mpnSubscribeMarker = "MPNOK,";  

  public static final String mpnUnsubscribeMarker = "MPNDEL,";

  public static final String mpnResetBadgeMarker = "MPNZERO,";

  public static final String UNCHANGED = "UNCHANGED";

  protected static final boolean SYNC_RESPONSE = false;
  protected static final boolean ASYNC_RESPONSE = true;
  
  public static final String END_LINE = "\r\n";
}
