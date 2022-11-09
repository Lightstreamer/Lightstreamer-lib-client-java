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


package com.lightstreamer.client.requests;

import com.lightstreamer.client.Constants;
import com.lightstreamer.client.session.InternalConnectionOptions;
import com.lightstreamer.client.session.ServerSession;
import com.lightstreamer.client.session.Session;
import com.lightstreamer.client.session.Session.ForceRebindTutor;
import com.lightstreamer.client.session.SessionThread;
import com.lightstreamer.client.transport.RequestListener;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;


public abstract class RequestTutor {
    
  protected static final Logger log = LogManager.getLogger(Constants.REQUESTS_LOG);

  
  public static final long MIN_TIMEOUT;
  
  static {
      /*
       * The retransmission timeout should not be changed in normal conditions.
       * It was introduced for the sake of cascading clustering applications.
       */
      MIN_TIMEOUT = Long.getLong("com.lightstreamer.retransmission.timeout", 4_000);
  }
  
  protected long timeoutMs;
  protected final SessionThread sessionThread;
  protected final InternalConnectionOptions connectionOptions;
  protected final Session session;
  protected final ServerSession serverSession;
  /**
   * Flag assuring that only one timeout at once is pending.
   * When the flag is false {@link #startTimeout()} has no effect.
   */
  private boolean timeoutIsRunning = false;
  
  protected boolean discarded = false;
  
  public RequestTutor(SessionThread thread, InternalConnectionOptions connectionOptions) {
    this(0,thread,connectionOptions);
  }

  public RequestTutor(long currentTimeout, SessionThread thread, InternalConnectionOptions connectionOptions) {
    
    this.sessionThread = thread;
    this.connectionOptions = connectionOptions;
    this.session = sessionThread.getSessionManager().getSession();
    this.serverSession = sessionThread.getSessionManager().getServerSession();
    
    if (this.isTimeoutFixed()) {
        this.timeoutMs = this.getFixedTimeout();
    } else {
        this.timeoutMs = currentTimeout > 0 ? currentTimeout*2 : MIN_TIMEOUT;
    }
  }
  
  long getTimeout() {
    return this.timeoutMs;
  }
  
  /**
   * When the argument is false, the tutor starts a timer whose purpose is to send again the request 
   * if a response doesn't arrive after the timeout elapsed. 
   * Generally the method is called with false argument when {@link RequestListener#onOpen()} fires.
   * <p>
   * When the argument is true, the tutor sends again the request.
   * Generally the method is called with true argument when {@link RequestListener#onBroken()} or
   * {@link RequestListener#onClosed()} fires and there is a problem.
   */
  public void notifySender(boolean failed) {
    if (failed) {
      this.doRecovery();
    } else {
      this.startTimeout();
      
    }
  }
  
  protected void startTimeout() {
      if (! timeoutIsRunning) {
          timeoutIsRunning = true;
          sessionThread.schedule(new Runnable() {
              
              public void run() {
                  onTimeout();
              }
              
          }, getTimeout());
      }
  }
  
  public void discard() {
      discarded = true;
  }
  
  protected void onTimeout() {
      timeoutIsRunning = false;
      /*
       * The method is responsible for retransmitting requests which have no response within a timeout interval.
       * The main rules are:
       * 1) when the session transport is HTTP or the request is a force_rebind, 
       * the request must be retransmitted as soon as the timeout expires
       * 2) when the session transport is WebSocket, the request must be retransmitted only if the transport has changed
       *    (since WebSocket is a reliable transport, it would be useless to transmit on the same WebSocket).
       */
      boolean success = verifySuccess();
      if (discarded || success) {
          // stop retransmissions
          // discard the tutor
        
      } else if (serverSession.isClosed()) {
          assert ! success;
          // stop retransmissions
          // discard the tutor
          
      } else if (serverSession.isTransportHttp()
              // force_rebind is always sent via HTTP
              || this instanceof ForceRebindTutor) {
          assert ! success;
          assert serverSession.isOpen();
          // always retransmit when the transport is HTTP
          // discard the tutor
          doRecovery();
          
      } else if (! serverSession.isSameStreamConnection(session)) {
          assert ! success;
          assert serverSession.isOpen();
          assert serverSession.isTransportWS();
          // session has changed: retry the transmission
          // discard the tutor
          doRecovery();
          
      } else {
          assert ! success;
          assert serverSession.isOpen();
          assert serverSession.isTransportWS();
          assert serverSession.isSameStreamConnection(session);
          // reschedule the tutor
          if (! isTimeoutFixed()) {
              assert timeoutMs >= MIN_TIMEOUT;
              timeoutMs *= 2;
          }
          startTimeout();
      }
  }
  
  public abstract boolean shouldBeSent();
  protected abstract boolean verifySuccess();
  protected abstract void doRecovery(); 
  /**
   * called if the request will be willingly not sent (e.g. ADD not sent because a REMOVE was received before the ADD was on the net)
   */
  public abstract void notifyAbort();
  protected abstract boolean isTimeoutFixed();
  protected abstract long getFixedTimeout();
  
}
