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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.lightstreamer.client.Constants;
import com.lightstreamer.client.mpn.MpnRequest;
import com.lightstreamer.client.requests.ChangeSubscriptionRequest;
import com.lightstreamer.client.requests.ConstrainRequest;
import com.lightstreamer.client.requests.DestroyRequest;
import com.lightstreamer.client.requests.ForceRebindRequest;
import com.lightstreamer.client.requests.ReverseHeartbeatRequest;
import com.lightstreamer.client.requests.MessageRequest;
import com.lightstreamer.client.requests.RequestTutor;
import com.lightstreamer.client.requests.SubscribeRequest;
import com.lightstreamer.client.requests.UnsubscribeRequest;
import com.lightstreamer.client.transport.RequestListener;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;

/**
 * 
 */
public class BatchRequest {

  //XXX refactoring using generics or subsclasses?
  public static final int MESSAGE = 1;
  public static final int HEARTBEAT = 2;
  //public static final int LOG = 3;
  public static final int CONTROL = 4;
  
  
  private static final String CONSTRAINT_KEY = "C";
  private static final String FORCE_REBIND_KEY = "F";
  private static final String CHANGE_SUB_KEY = "X";
  private static final String MPN_KEY = "M";
  
  
  Map<String,RequestObjects> keys = new HashMap<String,RequestObjects>();
  List<String> queue = new LinkedList<String>();
  
  

  protected final Logger log = LogManager.getLogger(Constants.SUBSCRIPTIONS_LOG);
  
  private int batchType;
  private int messageNextKey = 0;
  

  public BatchRequest(int type) {
    this.batchType = type;
  }
  
  public int getLength() {
    return queue.size();
  }
  
  public String getRequestName() {
    if (this.getLength() <= 0) {
      return null;
    }
    return keys.get(queue.get(0)).request.getRequestName();
  }
  
  public long getNextRequestLength() {
    if (this.getLength() <= 0) {
      return 0;
    }
    return keys.get(queue.get(0)).request.getTransportUnawareQueryString().length();
      // TODO we use the longest estimate, as we have no transport information here 
  }

  public RequestObjects shift() {
    if (this.getLength() <= 0) {
      return null;
    }
    
    String key = queue.remove(0);
    return keys.remove(key);
  }

  
  private void addRequestInternal(int key,RequestObjects request) {
    this.addRequestInternal(String.valueOf(key), request);
  }
  private void addRequestInternal(String key,RequestObjects request) {
    this.keys.put(key, request);
    this.queue.add(key);
  }
  private void substituteRequest(String key,RequestObjects newRequest) {
    this.keys.put(key, newRequest);
  }
  
  
 
  
  public boolean addRequestToBatch(MessageRequest request, RequestTutor tutor, RequestListener listener) {
    if (this.batchType != MESSAGE) {
      log.error("Unexpected request type was given to batch");
      return false;
    }
    
    //I should only add to queue, the sendMessages are always sent to the server
    RequestObjects message = new RequestObjects(request,tutor,listener);
    this.addRequestInternal(this.messageNextKey++,message);
    return true;
  }
  
  public boolean addRequestToBatch(ReverseHeartbeatRequest request, RequestTutor tutor, RequestListener listener) {
    if (this.batchType != HEARTBEAT) {
      log.error("Unexpected request type was given to batch");
      return false;
    }
    
    //I should only add to queue, the heart-beats are always sent to the server
    RequestObjects hb = new RequestObjects(request,tutor,listener);
    this.addRequestInternal(this.messageNextKey++,hb);
    return true;
  }
  
  public boolean addRequestToBatch(ConstrainRequest request, RequestTutor tutor, RequestListener listener) {
   if (this.batchType != CONTROL) {
      log.error("Unexpected request type was given to batch");
      return false;
    }
    
 //can we queue costrain rebind for 2 different sessions? (NO)
   
    String key = CONSTRAINT_KEY;
    
    RequestObjects requestObj = new  RequestObjects(request,tutor,listener);
    
    RequestObjects queuedRequest = this.keys.get(key);
    if (queuedRequest != null) {
      log.debug("Substituting CONSTRAIN request");
      queuedRequest.tutor.notifyAbort();
      this.substituteRequest(key,requestObj);
    } else {
      log.debug("Storing CONSTRAIN confirmed");
      this.addRequestInternal(key,requestObj);
    }
    
    return true;
  }
  
  public boolean addRequestToBatch(ForceRebindRequest request, RequestTutor tutor, RequestListener listener) {
    if (this.batchType != CONTROL) {
       log.error("Unexpected request type was given to batch");
       return false;
     }
    
    //can we queue force rebind for 2 different sessions? (NO)
     
     String key = FORCE_REBIND_KEY;
     
     RequestObjects requestObj = new  RequestObjects(request,tutor,listener);
     
     RequestObjects queuedRequest = this.keys.get(key);
     if (queuedRequest != null) {
       log.debug("Substituting FORCE REBIND request");
       queuedRequest.tutor.notifyAbort();
       this.substituteRequest(key,requestObj);
     } else {
       log.debug("Storing FORCE REBIND confirmed");
       this.addRequestInternal(key,requestObj);
     }
     
     return true;
  }
  
  public boolean addRequestToBatch(UnsubscribeRequest request, RequestTutor tutor, RequestListener listener) {
    if (this.batchType != CONTROL) {
       log.error("Unexpected request type was given to batch");
       return false;
     }
     
     String key = String.valueOf(request.getSubscriptionId());
     
     RequestObjects requestObj = new  RequestObjects(request,tutor,listener);
     
     RequestObjects queuedRequest = this.keys.get(key);
     if (queuedRequest != null) {
       
       if (queuedRequest.request instanceof SubscribeRequest) { //can't be the first attempt, otherwise the unsubscribe request would not be here
         
         log.debug("Substituting SUBSCRIBE request with UNSUBSCRIBE"); 
         queuedRequest.tutor.notifyAbort();
         this.substituteRequest(key,requestObj);
         
       } else {
         //delete already queued, should not happen, still, we don't have nothing to do
       }
       
       
     } else {
       log.debug("Storing UNSUBSCRIBE confirmed");
       this.addRequestInternal(key,requestObj);
     }
     
     return true;
  }
  
  public boolean addRequestToBatch(SubscribeRequest request, RequestTutor tutor, RequestListener listener) {
    if (this.batchType != CONTROL) {
       log.error("Unexpected request type was given to batch");
       return false;
     }
     
     String key = String.valueOf(request.getSubscriptionId());
     
     RequestObjects requestObj = new  RequestObjects(request,tutor,listener);
     
     RequestObjects queuedRequest = this.keys.get(key);
     if (queuedRequest != null) {
       
       //can never happen that an ADD request substitutes a REMOVE request for 2 reasons:
       //  *if those requests are part of the same session than to remove and re-add a table
       //   changes its key.
       //  *if those requests are not part of the same session than during session change
       //   all pending request are removed.
       //so, all cases should pass from the if (requestType == ControlRequest.REMOVE) case
       
       // thus, this is an unexpected case, let's handle it anyway
       log.debug("Substituting request with SUBSCRIBE");
       queuedRequest.tutor.notifyAbort();
       this.substituteRequest(key,requestObj);
     } else {
       log.debug("Storing SUBSCRIBE confirmed");
       this.addRequestInternal(key,requestObj);
     }
     
     return true;
  }
  
  public boolean addRequestToBatch(ChangeSubscriptionRequest request, RequestTutor tutor, RequestListener listener) {
    if (this.batchType != CONTROL) {
       log.error("Unexpected request type was given to batch");
       return false;
     }
     
     String key = CHANGE_SUB_KEY+request.getSubscriptionId();
     
     RequestObjects requestObj = new  RequestObjects(request,tutor,listener);
     
     RequestObjects queuedRequest = this.keys.get(key);
     if (queuedRequest != null) {
       //this change frequency request is newer, replace the old one
       log.debug("Substituting FREQUENCY request");
       queuedRequest.tutor.notifyAbort();
       this.substituteRequest(key,requestObj);
     } else {
       log.debug("Storing FREQUENCY confirmed");
       this.addRequestInternal(key,requestObj);
     }
     
     return true;
  }
  
  public boolean addRequestToBatch(DestroyRequest request, RequestTutor tutor, RequestListener listener) {
    if (this.batchType != CONTROL) {
       log.error("Unexpected request type was given to batch");
       return false;
     }
     
     String key = request.getSession();
     
     RequestObjects requestObj = new  RequestObjects(request,tutor,listener);
     
     RequestObjects queuedRequest = this.keys.get(key);
     if (queuedRequest != null) {
       log.debug("Substituting DESTROY request");
       queuedRequest.tutor.notifyAbort();
       this.substituteRequest(key,requestObj);
     } else {
       log.debug("Storing DESTROY confirmed");
       this.addRequestInternal(key,requestObj);
     }
     
     return true;
  }
  
  public boolean addRequestToBatch(MpnRequest request, RequestTutor tutor, RequestListener listener) {
      if (this.batchType != CONTROL) {
          log.error("Unexpected request type was given to batch");
          return false;
      }
      RequestObjects requestObj = new  RequestObjects(request, tutor, listener);
      this.addRequestInternal(MPN_KEY + request.getRequestId(), requestObj);
      return true;
  }
}
