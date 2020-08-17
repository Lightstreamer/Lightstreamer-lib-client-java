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
package com.lightstreamer.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;

import com.lightstreamer.client.events.ClientMessageAbortEvent;
import com.lightstreamer.client.events.ClientMessageDenyEvent;
import com.lightstreamer.client.events.ClientMessageDiscardedEvent;
import com.lightstreamer.client.events.ClientMessageErrorEvent;
import com.lightstreamer.client.events.ClientMessageProcessedEvent;
import com.lightstreamer.client.events.EventDispatcher;
import com.lightstreamer.client.events.EventsThread;
import com.lightstreamer.client.requests.MessageRequest;
import com.lightstreamer.client.requests.RequestTutor;
import com.lightstreamer.client.session.InternalConnectionOptions;
import com.lightstreamer.client.session.MessagesListener;
import com.lightstreamer.client.session.SessionManager;
import com.lightstreamer.client.session.SessionThread;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;
import com.lightstreamer.util.Matrix;

class MessageManager {

  private MessagesListener eventsListener = new EventsListener();
  private SessionThread sessionThread;
  private SessionManager manager;
  private InternalConnectionOptions options;
  
  private Matrix<String,Integer,MessageWrap> forwardedMessages = new Matrix<String,Integer,MessageWrap>();
  private Matrix<String,Integer,MessageWrap> pendingMessages = new Matrix<String,Integer,MessageWrap>();
  private Map<String,Integer> sequences = new HashMap<String,Integer>();
  
  private final Logger log = LogManager.getLogger(Constants.SUBSCRIPTIONS_LOG);
  
  private int phase = 0;

  private boolean sessionAlive = false;
  private EventDispatcher<ClientMessageListener> dispatcher;

  MessageManager(EventsThread eventsThread, SessionThread sessionThread, SessionManager manager, InternalConnectionOptions options) {
    this.sessionThread = sessionThread;
    this.manager = manager;
    this.options = options;
    
    this.dispatcher = new EventDispatcher<ClientMessageListener>(eventsThread);
    
    manager.setMessagesListener(this.eventsListener);
  }
  
  private long fixedTimeout = 0;
  
  //ATM used only for testing purposes
  void setFixedTimeout(long timeout) {
    fixedTimeout = timeout;
  }

  private <T> T getFromSessionThread(final Callable<T> fun) {
      // TODO which library class should we leverage instead?
      if (Thread.currentThread().getName().contains("Session Thread")) {
          // TODO use a more direct way to identify the session thread
          try {
            return fun.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
      }
      class Container<V> {
          public V value;
      }
      final Container<T> container = new Container<T>();
      final Semaphore sem = new Semaphore(0);
      sessionThread.queue(new Runnable() {
          @Override
          public void run() {
              try {
                  container.value = fun.call();
              } catch (Exception e) {
              }
              sem.release();
          }
      });
      try {
          sem.acquire();
      } catch (Exception e) {
          throw new RuntimeException(e);
      }
      return container.value;
  }
  
  //ATM used only for testing purposes;
  // may be invoked in any thread
  boolean isForwardedListEmpty() {
      return getFromSessionThread(new Callable<Boolean>() {
          @Override
          public Boolean call() throws Exception {
              return MessageManager.this.forwardedMessages.isEmpty();
          }
      });
  }
  
  //ATM used only for testing purposes;
  // may be invoked in any thread
  boolean isPendingListEmpty() {
      return getFromSessionThread(new Callable<Boolean>() {
          @Override
          public Boolean call() throws Exception {
              return MessageManager.this.pendingMessages.isEmpty();
          }
      });
  }
  
  //ATM used only for testing purposes
  MessagesListener getListener() {
    return this.eventsListener;
  }
  
  
  /**
   * this is the only method called by the EventsThread, everything else comes from the SessionThread
   */
  public void send(final String message, final String seq, final int delayTimeout, final ClientMessageListener listener,
      final boolean enqueueWhileDisconnected) {
    if (log.isDebugEnabled()) {
      log.debug("Evaluating message to be sent to server: " + message);
    }
    
    sessionThread.queue(new Runnable() {

      @Override
      public void run() {
        
        if (sessionAlive) {
          sendMessage(message,seq,delayTimeout,listener);
          
        } else if (enqueueWhileDisconnected) {
          queueMessage(message,seq,delayTimeout,listener);
          
        } else if (listener != null) {
          if (log.isDebugEnabled()) {
            log.debug("Client is disconnected, abort message: " + message);
          }
          dispatcher.dispatchSingleEvent(new ClientMessageAbortEvent(message, false), listener);
        }
        
      }
      
    });
  }
  
  private int getNextSequenceNumber(String sequence) {
    Integer num = sequences.get(sequence);
    if (num == null) {
      sequences.put(sequence,2);
      return 1;
    }
    
    int next = num+1;
    sequences.put(sequence,next);
    return num;
  }
  
  void resendMessage(MessageWrap envelope) {
    String sequence = envelope.request.getSequence();
    int number = envelope.request.getMessageNumber();
    if (log.isDebugEnabled()) {
      log.debug("No ack was received for a message; preparing it again: " + sequence+"|"+number);
    }
    
    //replace envelope
    envelope = envelope.makeClone();
    forwardMessage(sequence,number,envelope);
  }
  
  private void sendMessage(String message, String sequence, int delayTimeout, ClientMessageListener listener) {
    int number = getNextSequenceNumber(sequence);
    
    if (log.isDebugEnabled()) {
      log.debug("Preparing message: " + sequence+"|"+number);
    }
    
    MessageRequest request = new MessageRequest(message, sequence, number, delayTimeout, listener!=null);
    
    MessageWrap envelope = new MessageWrap(request,message,listener);
    forwardMessage(sequence,number,envelope);
  }
  
  private void forwardMessage(String sequence, int number, MessageWrap envelope) {
    forwardedMessages.insert(envelope, envelope.request.getSequence(), envelope.request.getMessageNumber());
    
    RequestTutor messageTutor = new MessageTutor(sessionThread,0,envelope,phase);
    manager.sendMessage(envelope.request, messageTutor);
  }
  
  
  private void queueMessage(String message, String sequence, int delayTimeout, ClientMessageListener listener) {
    int number = getNextSequenceNumber(sequence); 
    
    if (log.isDebugEnabled()) {
      log.debug("Client is disconnected, queue message for later use: " + sequence+"|"+number);
    }
    
    MessageRequest request = new MessageRequest(message, sequence, number, delayTimeout, listener!=null);
    MessageWrap envelope = new MessageWrap(request,message,listener);
    
    pendingMessages.insert(envelope, sequence, number);
  }
  
  private void cleanMessage(String sequence, int number) {
    if (log.isDebugEnabled()) {
      log.debug("Message handled, cleaning structures: " + sequence+"|"+number);
    }
    forwardedMessages.del(sequence, number);
  }
  
  void onSent(MessageWrap envelope) {
    envelope.sentOnNetwork = true;
    
    if (!envelope.request.needsAck()) {
      //we will receive no other notifications for this message
      //dismiss related structures
      
      String sequence = envelope.request.getSequence();
      int number = envelope.request.getMessageNumber();
      if (log.isDebugEnabled()) {
        log.debug("Not waiting for ack, message lifecycle reached its end: " + sequence + "|" + number);
      }
      cleanMessage(sequence,number);
    }
  }
  
  void onAck(String sequence, int number) {
    if (log.isDebugEnabled()) {
      log.debug("Ack received for message: " + sequence+"|"+number);
    }
    
    MessageWrap envelope = forwardedMessages.get(sequence, number);
    if (envelope != null) {
      if (envelope.ack)  {
        log.warn("Unexpected double ack for message: " + sequence+"|"+number);
      } else {
        envelope.ack = true;
      }
      
      if (envelope.listener == null) {
        if (log.isDebugEnabled()) {
          log.debug("Ack received, no outcome expected, message lifecycle reached its end: " + sequence + "|" + number);
        }
        cleanMessage(sequence,number);
      }
    } else {
        log.warn("Unexpected pair LS_sequence|LS_msg_prog: " + sequence + "|" + number);
    }
  }
  
  void onOk(String sequence, int number) {
    if (log.isDebugEnabled()) {
      log.debug("OK received for message: " + sequence+"|"+number);
    }
    
    MessageWrap envelope = forwardedMessages.get(sequence, number);
    if (envelope != null) {
        if (envelope.listener != null) {
            dispatcher.dispatchSingleEvent(new ClientMessageProcessedEvent(envelope.message), envelope.listener);
        }
        cleanMessage(sequence,number);        
    } else {
        log.warn("Unexpected pair LS_sequence|LS_msg_prog: " + sequence + "|" + number);
    }
  }
  
  void onDeny(String sequence, int number, String denyMessage, int code) {
    if (log.isDebugEnabled()) {
      log.debug("Denial received for message: " + sequence+"|"+number);
    }
    
    MessageWrap envelope = forwardedMessages.get(sequence, number);
    if (envelope != null) {
        if (envelope.listener != null) {
            dispatcher.dispatchSingleEvent(new ClientMessageDenyEvent(envelope.message,code,denyMessage), envelope.listener);
        }
        cleanMessage(sequence,number);        
    } else {
        log.warn("Unexpected pair LS_sequence|LS_msg_prog: " + sequence + "|" + number);
    }
  }
  
  void onDiscarded(String sequence, int number) {
    if (log.isDebugEnabled()) {
      log.debug("Discard received for message: " + sequence+"|"+number);
    }
    
    MessageWrap envelope = forwardedMessages.get(sequence, number);
    if (envelope != null) {
        if (envelope.listener != null) {
            dispatcher.dispatchSingleEvent(new ClientMessageDiscardedEvent(envelope.message), envelope.listener);
        }
        cleanMessage(sequence,number);        
    } else {
        log.warn("Unexpected pair LS_sequence|LS_msg_prog: " + sequence + "|" + number);
    }
  }
  
  void onError(String sequence, int number, String errorMessage, int code) {
    if (log.isDebugEnabled()) {
      log.debug("Error received for message: " + sequence+"|"+number);
    }
    
    MessageWrap envelope = forwardedMessages.get(sequence, number);
    // envelope may not be in forwardedMessages because it has been removed (for example when LS_ack=false)
    if (envelope != null) {
        if (envelope.listener != null) {
            if (code != 32 && code != 33) {
                /* errors 32 and 33 must not be notified to the user
                 * because they are due to late responses of the server */                
                dispatcher.dispatchSingleEvent(new ClientMessageErrorEvent(envelope.message), envelope.listener);
            }
        }
        cleanMessage(sequence,number);        
    } else {
        log.warn("Unexpected pair LS_sequence|LS_msg_prog: " + sequence + "|" + number);
    }
  }
  
  private void reset() {
    log.info("Reset message handler");
    sessionAlive = false;
    log.debug("Aborting pending messages"); 
    abortAll(pendingMessages);
    abortAll(forwardedMessages);
    
    //reset the counters
    sequences = new HashMap<String,Integer>();
    
    //these maps should already be empty
    if (!forwardedMessages.isEmpty() || !pendingMessages.isEmpty()) {
      log.error("Unexpected: there are still messages in the structures");
      forwardedMessages = new Matrix<String,Integer,MessageWrap>();
      pendingMessages = new Matrix<String,Integer,MessageWrap>();
    }
    
    //avoid the Tutors late checks
    phase++;    
  }
  
  private void start() {
    log.info("Start message handler");
    sessionAlive = true;
    sendPending();
  }
  
  private void abortAll(Matrix<String,Integer,MessageWrap> messages) {
    // called at session end: we have to call abort on all the messages we had no answer for
    // we have to call the listeners in the proper order (within each sequence)
    List<MessageWrap> forwarded = messages.sortAndCleanMatrix();
    
    for (MessageWrap envelope : forwarded) {
      if (envelope.listener != null) {
        dispatcher.dispatchSingleEvent(new ClientMessageAbortEvent(envelope.message, envelope.sentOnNetwork), envelope.listener);
      }
    }
  }
  
  private void sendPending() {
    // called at session start: we have to forward all the enqueued messages
    log.debug("Sending queued messages"); 
    
    List<MessageWrap> pendings = pendingMessages.sortAndCleanMatrix();
    
    for (MessageWrap envelope : pendings) {
      forwardMessage(envelope.request.getSequence(),envelope.request.getMessageNumber(),envelope);
    }
  } 
  
  private boolean checkMessagePhase(int phase) {
    return this.phase == phase;
  }
  
  private class EventsListener implements MessagesListener {

    @Override
    public void onSessionStart() {
      start();
    }

    @Override
    public void onSessionClose() {
      reset();
    }

    @Override
    public void onMessageAck(String sequence, int number) {
      onAck(sequence,number);
    }

    @Override
    public void onMessageOk(String sequence, int number) {
      onOk(sequence,number);
    }

    @Override
    public void onMessageDeny(String sequence, int denyCode,
        String denyMessage, int number) {
      onDeny(sequence,number,denyMessage,denyCode);
    }

    @Override
    public void onMessageDiscarded(String sequence, int number) {
      onDiscarded(sequence,number);
    }

    @Override
    public void onMessageError(String sequence, int errorCode,
        String errorMessage, int number) {
      onError(sequence,number,errorMessage,errorCode);
    }
   
  }
  
  private class MessageWrap {
    public boolean sentOnNetwork = false;
    public MessageRequest request;
    public ClientMessageListener listener;
    public String message;
    public boolean ack = false;
    
    public MessageWrap(MessageRequest request, String message, ClientMessageListener listener) {
      this.request = request;
      this.listener = listener;
      this.message = message;
    }
    
    public MessageWrap makeClone() {
      // NB the cloning is necessary so the clone gets a new requestId
      MessageRequest requestClone = new MessageRequest(this.request);
      return new MessageWrap(requestClone,this.message,this.listener);
    }
  }
  
  private class MessageTutor extends RequestTutor {
        
    private MessageWrap envelope;
    private int phase;

    public MessageTutor(SessionThread thread, int timeoutMs, MessageWrap envelope, int phase) {
      super(timeoutMs,thread,options);
      this.envelope = envelope;
      this.phase = phase;
    }

    @Override
    public void notifySender(boolean failed) {
      super.notifySender(failed);
     
      if (!failed) {
        onSent(envelope);
      } 
    }

    @Override
    protected boolean verifySuccess() {
      if (checkMessagePhase(this.phase)) {
      //phase is correct
        String sequence = envelope.request.getSequence();
        int number = envelope.request.getMessageNumber();
        
        if (forwardedMessages.get(sequence, number) != null) {
        //the message is still in the queue
          if (!envelope.ack) { 
          //the message has not been acknowledged yet
            return false;
          }
        }
      }
     
      return true;
    }

    @Override
    protected void doRecovery() {
      resendMessage(envelope);
    }

    @Override
    public void notifyAbort() {
      // nothing to do (can't happen, this is called if
      // the request is dismissed because useless (e.g.: an 
      // unsubsription request can be aborted this way if
      // the related subscription request was not actually sent)
    }

    @Override
    protected boolean isTimeoutFixed() {
      return fixedTimeout>0;
    }

    @Override
    protected long getFixedTimeout() {
      return fixedTimeout;
    }

    @Override
    public boolean shouldBeSent() {
      return true;
    }
    
  }

  
}
