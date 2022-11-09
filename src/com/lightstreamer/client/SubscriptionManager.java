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


package com.lightstreamer.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.lightstreamer.client.requests.ChangeSubscriptionRequest;
import com.lightstreamer.client.requests.RequestTutor;
import com.lightstreamer.client.requests.SubscribeRequest;
import com.lightstreamer.client.requests.UnsubscribeRequest;
import com.lightstreamer.client.session.InternalConnectionOptions;
import com.lightstreamer.client.session.SessionManager;
import com.lightstreamer.client.session.SessionThread;
import com.lightstreamer.client.session.SubscriptionsListener;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;
import com.lightstreamer.util.IdGenerator;

/**
 * 
 */
class SubscriptionManager {


  protected final Logger log = LogManager.getLogger(Constants.SUBSCRIPTIONS_LOG);
  
  private final Map<Integer,Subscription> subscriptions = new HashMap<Integer,Subscription>();
  /**
   * Map recording unsubscription requests which have been sent but whose corresponding REQOK/SUBOK messages 
   * have not yet been received.
   */
  private final Set<Integer> pendingDelete = new HashSet<Integer>();
  /**
   * Map recording unsubscription requests which have not yet been sent because the corresponding items are still subscribing. 
   */
  private final Set<Integer> pendingUnsubscribe = new HashSet<Integer>();
  private final Map<Integer,Integer> pendingSubscriptionChanges = new HashMap<Integer,Integer>();

//  private int nextId = 0;
  private boolean sessionAlive = false;
  private final SessionThread sessionThread;
  private final InternalConnectionOptions options;

  private SubscriptionsListener eventsListener = new EventsListener();

  private SessionManager manager;
  
  SubscriptionManager(SessionThread sessionThread, SessionManager manager, InternalConnectionOptions options) {
    this.sessionThread = sessionThread;
    this.options = options;
    this.manager = manager;
    
    manager.setSubscriptionsListener(this.eventsListener);
  }
  
  private long fixedTimeout = 0;
  //ATM used only for testing purposes
  void setFixedTimeout(long timeout) {
    fixedTimeout = timeout;
  }
  
  //ATM used only for testing purposes
  SubscriptionsListener getListener() {
    return this.eventsListener;
  }
  


  //this method is called from the eventsThread
  void add(final Subscription subscription) {
    sessionThread.queue(new Runnable() {
      @Override
      public void run() {
        doAdd(subscription);
      }
    });
  }
  
  public void doAdd(Subscription subscription) {
//    int subId = ++nextId;
    int subId = IdGenerator.getNextSubscriptionId();
    log.info("Adding subscription " + subId);
    
    subscriptions.put(subId,subscription);
    subscription.onAdd(subId,this,sessionThread); 
    
    if (sessionAlive) {
      subscribe(subscription);
    } else {
      subscription.onPause();
    }
  }
  
  //this method is called from the eventsThread
  void remove(final Subscription subscription) {
    
    sessionThread.queue(new Runnable() {
      @Override
      public void run() {
        doRemove(subscription);
      }
    });
    
  }
  
  void doRemove(Subscription subscription) {
    int subId = subscription.getSubscriptionId();
    log.info("removing subscription " + subId);
    if (sessionAlive) {
        if (subscription.isSubscribing()) {
            pendingUnsubscribe.add(subId);
        } else if (subscription.isSubscribed()) {
            unsubscribe(subId);
        }
    }
    subscriptions.remove(subId);
    subscription.onRemove();
  }
  
  
  void changeFrequency(Subscription subscription) {
    log.info("Preparing subscription frequency change: " + subscription.getSubscriptionId());

    ChangeSubscriptionRequest request = subscription.generateFrequencyRequest();
    ChangeSubscriptionTutor tutor = new ChangeSubscriptionTutor(0,sessionThread,options,request);
    
    pendingSubscriptionChanges.put(subscription.getSubscriptionId(), request.getReconfId()); //if reconfId is newer we don't care about the older one
    
    manager.sendSubscriptionChange(request,tutor);
  }
  
 
  private void changeFrequency(Subscription subscription, long timeoutMs, int reconfId) {
    log.info("Preparing subscription frequency change again: " + subscription.getSubscriptionId());

    ChangeSubscriptionRequest request = subscription.generateFrequencyRequest(reconfId);
    ChangeSubscriptionTutor tutor = new ChangeSubscriptionTutor(timeoutMs,sessionThread,options,request);
    
    pendingSubscriptionChanges.put(subscription.getSubscriptionId(), request.getReconfId()); //if reconfId is newer we don't care about the older one
    
    manager.sendSubscriptionChange(request,tutor);
  }
  
  
  private void subscribe(Subscription subscription) {
    //can't be off but might be inactive: to check that we have to synchronize, we probably don't want to do that
    //we might want to introduce a method shouldSend to the RequestTutor, better relay on the batch algorithm to abort 
    //useless requests
    
    log.info("Preparing subscription: " + subscription.getSubscriptionId());
    
    SubscribeRequest request = subscription.generateSubscribeRequest();
    SubscribeTutor tutor = new SubscribeTutor(subscription.getSubscriptionId(),subscription.getPhase(), sessionThread,0);
    
    manager.sendSubscription(request,tutor);
  }
  
  private void resubscribe(Subscription subscription, long timeoutMs) {
    log.info("Preparing to send subscription again: " + subscription.getSubscriptionId());
    
    SubscribeRequest request = subscription.generateSubscribeRequest();
    SubscribeTutor tutor = new SubscribeTutor(subscription.getSubscriptionId(),subscription.getPhase(), sessionThread,timeoutMs);
    
    manager.sendSubscription(request,tutor);
  }
  
  void sendAllSubscriptions() {
    //we clone just to avoid unexpected issues as in the pauseAllSubscriptions case
    //(see comment there for details)
    HashMap<Integer,Subscription> copy = new HashMap<Integer, Subscription>(subscriptions);
    
    for (Map.Entry<Integer,Subscription> subscriptionPair : copy.entrySet()) {
     
      Subscription subscription = subscriptionPair.getValue();
      
      if (subscription.isSubTable()) {
        log.error("Second level subscriptions should not be in the list of paused subscriptions");
        return;
      }
      
      subscription.onStart(); //wake up
      
      subscribe(subscription);
    }
    
  }
  
  void pauseAllSubscriptions() {
    //NOTE calling onPause on a two level subscriptions triggers doRemove calls 
    //for second-level subscriptions. 
    //To avoid unexpected behavior caused by remove calls while iterating
    //we either clone the list of subscriptions before iterating, or we 
    //iterate first to remove second-level subscriptions from the collection
    //and then iterate again to call the onPause on first-level subscriptions.
    //In the second case we should also avoid calling remove on the doRemove 
    //methods. 
    //To avoid complications I chose to go down the clone path.
    
    HashMap<Integer,Subscription> copy = new HashMap<Integer, Subscription>(subscriptions);
    
    
    for (Map.Entry<Integer,Subscription> subscriptionPair : copy.entrySet()) {
      Subscription subscription = subscriptionPair.getValue();
      
      if (subscription.isSubTable()) {
        //no need to pause these, will be removed soon
        return;
      }
      
      subscription.onPause(); //
    }
  }
  
  void clearAllPending() {
    this.pendingSubscriptionChanges.clear();
    this.pendingDelete.clear();
    this.pendingUnsubscribe.clear();
  }
  
  void unsubscribe(int subscriptionId) {
    log.info("Preparing to send unsubscription: " + subscriptionId);
    pendingDelete.add(subscriptionId);
    pendingUnsubscribe.remove(subscriptionId);
    
    UnsubscribeRequest request = new UnsubscribeRequest(subscriptionId);
    UnsubscribeTutor tutor = new UnsubscribeTutor(subscriptionId,sessionThread,0); 
  
    manager.sendUnsubscription(request,tutor);
  }
  
  void reunsubscribe(int subscriptionId, long timeoutMs) {
    log.info("Preparing to send unsubscription again: " + subscriptionId);
    
    UnsubscribeRequest request = new UnsubscribeRequest(subscriptionId);
    UnsubscribeTutor tutor = new UnsubscribeTutor(subscriptionId,sessionThread,timeoutMs); 
    
    manager.sendUnsubscription(request,tutor);
  }
  
  
  private class EventsListener implements SubscriptionsListener {

    @Override
    public void onSessionStart() {
      sessionAlive = true;
      sendAllSubscriptions();
    }

    @Override
    public void onSessionClose() {
      sessionAlive = false;
      pauseAllSubscriptions();
      clearAllPending();
    }
    
    private Subscription extractSubscriptionOrUnsubscribe(int subscriptionId) {
      Subscription subscription = subscriptions.get(subscriptionId);
      if (subscription != null) {
        return subscription;
      }
     
      //the subscription was removed
        //either we have a delete that is now pending
        //or we skipped the unsubscribe because we didn't know 
        //the status of the subscription (may only happen in case of
        //synchronous onSubscription events or during
        //onSubscription events)
      if (!pendingDelete.contains(subscriptionId)) {
        unsubscribe(subscriptionId);
      }
      return null;

    }

    @Override
    public void onUpdateReceived(int subscriptionId, int item,
        ArrayList<String> args) {
     
      
      Subscription subscription = extractSubscriptionOrUnsubscribe(subscriptionId);
      if (subscription == null) {
        log.debug(subscriptionId + " missing subscription, discarding update");
        return;
      }
      
      if (log.isDebugEnabled()) {
        log.info(subscriptionId + " received an update");
      }
      
      subscription.update(args,item,false);
        
    }
     

    @Override
    public void onEndOfSnapshotEvent(int subscriptionId, int item) {
      Subscription subscription = extractSubscriptionOrUnsubscribe(subscriptionId);
      if (subscription == null) {
        log.debug(subscriptionId + " missing subscription, discarding end of snapshot event");
        return;
      }
      
      if (log.isDebugEnabled()) {
        log.info(subscriptionId + " received end of snapshot event");
      }
      
      subscription.endOfSnapshot(item);
    }

    @Override
    public void onClearSnapshotEvent(int subscriptionId, int item) {
      Subscription subscription = extractSubscriptionOrUnsubscribe(subscriptionId);
      if (subscription == null) {
        log.debug(subscriptionId + " missing subscription, discarding clear snapshot event");
        return;
      }
      
      if (log.isDebugEnabled()) {
        log.info(subscriptionId + " received clear snapshot event");
      }
      
      subscription.clearSnapshot(item);
    }

    @Override
    public void onLostUpdatesEvent(int subscriptionId, int item, int lost) {
      Subscription subscription = extractSubscriptionOrUnsubscribe(subscriptionId);
      if (subscription == null) {
        log.debug(subscriptionId + " missing subscription, discarding lost updates event");
        return;
      }
      
      if (log.isDebugEnabled()) {
        log.info(subscriptionId + " received lost updates event");
      }
      
      subscription.lostUpdates(item,lost);
    }
    
    @Override
    public void onConfigurationEvent(int subscriptionId, String frequency) {
      Subscription subscription = extractSubscriptionOrUnsubscribe(subscriptionId);
      if (subscription == null) {
        log.debug(subscriptionId + " missing subscription, discarding configuration event");
        return;
      }
      
      if (log.isDebugEnabled()) {
        log.info(subscriptionId + " received configuration event");
      }
      
      subscription.configure(frequency);
    }
    
    @Override
    public void onUnsubscriptionAck(int subscriptionId) {
        /* this method was extracted from onUnsubscription() to stop the retransmissions when a REQOK is received */
        pendingDelete.remove(subscriptionId);
        if (pendingUnsubscribe.contains(subscriptionId)) {
            unsubscribe(subscriptionId);
        }
    }

    @Override
    public void onUnsubscription(int subscriptionId) {
      log.info(subscriptionId + " succesfully unsubscribed");
      pendingDelete.remove(subscriptionId);
      if (pendingUnsubscribe.contains(subscriptionId)) {
          unsubscribe(subscriptionId);
      }
      
      if (subscriptions.containsKey(subscriptionId)) {
        log.error("Unexpected unsubscription event");
        return;
      }
    }
    
    @Override
    public void onSubscriptionAck(int subscriptionId) {
        /* this method was extracted from onSubscription() to stop the retransmissions when a REQOK is received */
        Subscription subscription = extractSubscriptionOrUnsubscribe(subscriptionId);
        if (subscription == null) {
            log.debug(subscriptionId + " missing subscription, discarding subscribed event");
            return;
        }
        subscription.onSubscriptionAck();
    }

    @Override
    public void onSubscription(int subscriptionId, int totalItems, int totalFields, int keyPosition, int commandPosition) {
      Subscription subscription = extractSubscriptionOrUnsubscribe(subscriptionId);
      if (subscription == null) {
        log.debug(subscriptionId + " missing subscription, discarding subscribed event");
        return;
      }
      log.info(subscriptionId + " succesfully subscribed");
      subscription.onSubscribed(commandPosition, keyPosition, totalItems, totalFields);
    }

    @Override
    public void onSubscription(int subscriptionId, long reconfId) {
      Integer waitingId = pendingSubscriptionChanges.get(subscriptionId);
      if (waitingId == null) {
        //don't care anymore
        return;
      }
     
      //if lower we're still waiting the newer one
      //if equal we're done
      //higher is not possible
      if (reconfId == waitingId) {
        pendingSubscriptionChanges.remove(subscriptionId);
      }
    }

    @Override
    public void onSubscriptionError(int subscriptionId, int errorCode,
        String errorMessage) {
      Subscription subscription = extractSubscriptionOrUnsubscribe(subscriptionId);
      if (subscription == null) {
        log.debug(subscriptionId + " missing subscription, discarding error");
        return;
      }
      log.info(subscriptionId + " subscription error");
      subscription.onSubscriptionError(errorCode,errorMessage); 
    }
    
  }
  
  private abstract class SubscriptionsTutor extends RequestTutor {
    public SubscriptionsTutor(long currentTimeout, SessionThread thread,
        InternalConnectionOptions connectionOptions) {
      super(currentTimeout, thread, connectionOptions);
    }

    @Override
    protected boolean isTimeoutFixed() {
      return fixedTimeout>0;
    }

    @Override
    protected long getFixedTimeout() {
      return fixedTimeout;
    }
  }
  
  private class UnsubscribeTutor extends SubscriptionsTutor {
    
    private int subscriptionId;

    public UnsubscribeTutor(int subscriptionId, SessionThread thread, long timeoutMs) {
      super(timeoutMs,thread,options);
      this.subscriptionId = subscriptionId;
    }

    @Override
    protected boolean verifySuccess() {
      return !pendingDelete.contains(this.subscriptionId);
    }

    @Override
    protected void doRecovery() {
      reunsubscribe(this.subscriptionId,this.timeoutMs);
    }

       @Override
    public void notifyAbort() {
      //get rid of it
      pendingDelete.remove(this.subscriptionId);
      pendingUnsubscribe.remove(this.subscriptionId);
    }

    @Override
    public boolean shouldBeSent() {
      return pendingDelete.contains(this.subscriptionId);
    }

  }

  
  private class SubscribeTutor extends SubscriptionsTutor {

    private int subscriptionId;
    private int subscriptionPhase;

    public SubscribeTutor(int subscriptionId, int subscriptionPhase, SessionThread thread, long timeoutMs) {
      super(timeoutMs,thread,options);
      this.subscriptionId = subscriptionId;
      this.subscriptionPhase = subscriptionPhase;
    }  
    
    @Override
    public void notifySender(boolean failed) {
      Subscription subscription = subscriptions.get(subscriptionId);
      if (subscription == null) {
          log.warn("Subscription not found [" + subscriptionId + "/" + manager.getSessionId() + "]");
          return;
      }
      if (!subscription.checkPhase(subscriptionPhase)) {
        //we don't care
        return;
      }
      
      super.notifySender(failed);
      if (!failed) {
        subscription.onSubscriptionSent();
        this.subscriptionPhase = subscription.getPhase();
      }
    }
    
    @Override
    protected boolean verifySuccess() {
      Subscription subscription = subscriptions.get(subscriptionId);
      if (subscription == null) {
        //subscription was removed, no need to keep going, let's say it's a success
        return true;
      }
      if (!subscription.checkPhase(subscriptionPhase)) {
        //something else happened, consider it a success
        return true;
      }
      return subscription.isSubscribed(); //== return false
    }

    @Override
    protected void doRecovery() {
      Subscription subscription = subscriptions.get(subscriptionId);
      if (subscription == null) {
        //subscription was removed, no need to keep going
        return;
      }
      if (!subscription.checkPhase(subscriptionPhase)) {
        //something else happened
        return;
      }
      resubscribe(subscription,this.timeoutMs);
    }

    @Override
    public void notifyAbort() {
      // we don't have anything to do, it means that a 
      //delete was queued before the add was sent
      //so the subscription should not exists anymore
      
      /*if (subscriptions.containsKey(this.subscriptionId)) {
        //might actually happen if we stop a 2nd subscription effort
        log.error("Was not expecting to find the subscription as it was supposedly removed");
      }*/
    }

    @Override
    public boolean shouldBeSent() {
      Subscription subscription = subscriptions.get(subscriptionId);
      if (subscription == null) {
        //subscription was removed, no need to send the request
        return false;
      }
      if (!subscription.checkPhase(subscriptionPhase)) {
        return false;
      }
      return true;
    }
    
  }
  
  private class ChangeSubscriptionTutor extends SubscriptionsTutor {

    private ChangeSubscriptionRequest request;

    public ChangeSubscriptionTutor(long currentTimeout, SessionThread thread,
        InternalConnectionOptions connectionOptions, ChangeSubscriptionRequest request) {
      super(currentTimeout, thread, connectionOptions);

      this.request = request;
    
    }

    @Override
    protected boolean verifySuccess() {

      Integer waitingId = pendingSubscriptionChanges.get(this.request.getSubscriptionId());
      if (waitingId == null) {
        return true;
      }
      
      Integer reconfId = this.request.getReconfId();
      
      //if lower we don't care about this anymore
      //if equal we're still waiting
      //higher is not possible
      return reconfId < waitingId;
    }

    @Override
    protected void doRecovery() {
      Subscription subscription = subscriptions.get(this.request.getSubscriptionId());
      if (subscription == null) {
        //subscription was removed, no need to keep going
        return;
      }
      changeFrequency(subscription,this.timeoutMs,this.request.getReconfId());
    }

    @Override
    public void notifyAbort() {
      Integer waitingId = pendingSubscriptionChanges.get(this.request.getSubscriptionId());
      if (waitingId == null) {
        return;
      }
      
      Integer reconfId = this.request.getReconfId();
      if (waitingId.equals(reconfId)) {
        pendingSubscriptionChanges.remove(this.request.getSubscriptionId());
      }
      
    }

    @Override
    public boolean shouldBeSent() {
      Subscription subscription = subscriptions.get(this.request.getSubscriptionId());
      if (subscription == null) {
        //subscription was removed, no need to keep going
        return false;
      }
      
      Integer waitingId = pendingSubscriptionChanges.get(this.request.getSubscriptionId());
      if (waitingId == null) {
        return false;
      }
      
      Integer reconfId = this.request.getReconfId();
      
      //if lower we don't care about this anymore
      //if equal we're still waiting
      //higher is not possible
      return reconfId.equals(waitingId);
      
    }
    
  }
  
  

}
