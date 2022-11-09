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


package com.lightstreamer.client.session;

import com.lightstreamer.client.Constants;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;

/**
 * 
 */
public class SlowingHandler {
  
  // Moving average coefficient used to gather delay samplings
  private static final double MOMENTUM = 0.5;
  // Max admitted delay, after that we start slowing 
  private static final double MAX_MEAN = 7000;
  // If the delay reaches this value we suppose the client machine went to sleep
  private static final double HUGE_DELAY = 20000;
  //if the mean is below this level we reset it
  private static final double IGNORE_MEAN = 60;
  
  private final Logger log = LogManager.getLogger(Constants.SESSION_LOG);
  // Timestamp representing the first message from the server. This will be the reference against which delays will be calculated 
  private double refTime = 0;
  // Current mean delay
  private double meanElaborationDelay = 0;
  private boolean firstMeanCalculated = false; //do I need it?
  private boolean hugeFlag = false;
  private InternalConnectionOptions options;
  
  
  
  SlowingHandler(InternalConnectionOptions options) {
    
    this.options = options;
  }
  
  long getDelay () {
    /*if (!this.options.isSlowingEnabled()) {
      return 0;
    }*/
    //Math.round is only used to get the long value 
    return this.firstMeanCalculated && this.meanElaborationDelay > 0 ? Math.round(Math.floor(this.meanElaborationDelay)) : 0;
  }

  double getMeanElaborationDelay() {
    return meanElaborationDelay;
  }
  
  void setMeanElaborationDelay(double newMean) {
    this.firstMeanCalculated = true;
    this.meanElaborationDelay = newMean;
  }
  
  /**
   * this is called during the OK execution 
   */
  void startSync(boolean isStreaming, boolean forced, double currTime) {
    
    if (isStreaming || forced) {
      // per convenzione, se e' richiesto il polling in modo esplicito
      // azzeriamo la stima sul ritardo del ciclo di poll
      this.meanElaborationDelay = 0; //in case of streaming this is already 0, unless inheriting the session from someone else 
      this.hugeFlag = false;
      
    } else {
      // we keep the current estimate: it will simply increase the
      // life of the session on the server
    }
    
    this.refTime = currTime;
  }
  
  /**
   * This is called just before sending a new Poll to the server (create_session excluded)
   */
  void testPollSync(long millis, double currTime) {
    //We call it to keep track of the delay, but we avoid any action: if the delay 
    //grows too much we will get a sync error from the server.
    this.testSync(millis,currTime);
  }

  /**
   * This is called every time a sync message is received
   */
  boolean syncCheck(long seconds, boolean isStreaming, double currTime)  {
    //
    
    log.debug("Sync message evaluation; received value: " + seconds);
    
    // check synchronization
    if (isStreaming) {
      // during polling sync messages are not received
      boolean syncProblem = this.testSync(seconds * 1000,currTime);

      if (!syncProblem) {
        return true;
        
      } else if (this.options.isSlowingEnabled()) {
        log.info("Slow connection detected");
        return false;
        
        // the meanElaborationDelay calculated during the streaming 
        // will be given to the polling session as an estimate
        // of the delay expected after each poll
      }
  
    } else {
      log.warn("Unexpected synchronization call during polling session");
    }
    
    return true;
  }
  
  private boolean testSync(long millis, double currTime) {
    if (this.refTime == 0) {
      log.error("Reference timestamp missing");
      //this cannot happen..btw we handle the case signaling a delay problem
      return true;
    }
    
    double diffTime = currTime - this.refTime - millis;
    // calculate moving average
    if (!this.firstMeanCalculated) {
      this.setMeanElaborationDelay(diffTime);
      log.debug("First sync message, check not performed");
      return false;
      
    } else {
      
      //detect unlucky sleep
      //we should avoid false positive...how? 
      //If we got a huge delay we forgive the first one but not the second one 
      if (diffTime > HUGE_DELAY && diffTime > this.meanElaborationDelay*2) {
        this.hugeFlag = !this.hugeFlag;
        if (this.hugeFlag) {
          log.info("Huge delay detected by sync signals. Restored from standby/hibernation?");
          //let's try to ignore this check, the pc probably slept; we should get a sync error soon
          //i.e. we skip the current sampling
          return this.meanElaborationDelay > MAX_MEAN;
        }
      } 
      
      
      this.setMeanElaborationDelay(this.meanElaborationDelay * MOMENTUM + diffTime * (1 - MOMENTUM));

      if (this.meanElaborationDelay < IGNORE_MEAN) { //let us be forgiving 
        this.setMeanElaborationDelay(0);
        log.debug("No delay detected by sync signals");
        return false;
            
      } else if (this.meanElaborationDelay > MAX_MEAN) {
        // this is bad, we're late
        log.debug("Delay detected by sync signals: " + this.meanElaborationDelay);
        return true;
          
      } else {
        log.debug("No delay detected by sync signals");
        return false;
          
      }
    }
  }
  
  private int simulationCount = 0;
  private void simulateDelay() {
    this.refTime -= 2000;
    
    simulationCount++;
    if (simulationCount == 4) {
      this.refTime -= 200000;
    } else if(simulationCount == 5) {
      this.refTime += 200000;
    }
  }
  
  
}
