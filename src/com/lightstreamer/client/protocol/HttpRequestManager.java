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


package com.lightstreamer.client.protocol;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.lightstreamer.client.Constants;
import com.lightstreamer.client.mpn.MpnRequest;
import com.lightstreamer.client.protocol.ControlResponseParser.ERRORParser;
import com.lightstreamer.client.protocol.ControlResponseParser.ParsingException;
import com.lightstreamer.client.protocol.TextProtocol.StreamListener;
import com.lightstreamer.client.requests.BindSessionRequest;
import com.lightstreamer.client.requests.ChangeSubscriptionRequest;
import com.lightstreamer.client.requests.ConstrainRequest;
import com.lightstreamer.client.requests.ControlRequest;
import com.lightstreamer.client.requests.CreateSessionRequest;
import com.lightstreamer.client.requests.DestroyRequest;
import com.lightstreamer.client.requests.ForceRebindRequest;
import com.lightstreamer.client.requests.LightstreamerRequest;
import com.lightstreamer.client.requests.MessageRequest;
import com.lightstreamer.client.requests.RecoverSessionRequest;
import com.lightstreamer.client.requests.RequestTutor;
import com.lightstreamer.client.requests.ReverseHeartbeatRequest;
import com.lightstreamer.client.requests.SessionRequest;
import com.lightstreamer.client.requests.SubscribeRequest;
import com.lightstreamer.client.requests.UnsubscribeRequest;
import com.lightstreamer.client.session.InternalConnectionOptions;
import com.lightstreamer.client.session.SessionThread;
import com.lightstreamer.client.transport.RequestHandle;
import com.lightstreamer.client.transport.RequestListener;
import com.lightstreamer.client.transport.Transport;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;
import com.lightstreamer.util.ListenableFuture;

/**
 * From "Unified Client API" document:
<blockquote>
<h3>Control Request Batching</h3>
Control connections are automatically serialized and batched:
the first request is sent as soon as possible, subsequent requests are batched together while
the previous connection is open (the concept of “open” may vary depending on the
technology in use; the purpose is to always have at max 1 open socket dedicated to control
requests). Note that during websocket sessions, there is no need to batch, nor there is need
to wait for a roundtrip before issuing a new control request, so that if a websocket is in use
control requests are all sent “as soon as possible” and only batched if the dequeing thread
finds more than one request ready when executing.
<p>
Note that as the server specifies a maximum length for control requests body contents a
batch may not contain all the available requests. Such limit must always be respected unless
one single request surpasses the limit: in that case the requests is sent on its own even if we
already know that the server will refuse it.
<p>
Note that each control request is always bound to a session. As a consequence if the related
session ends while the request is on the wire such request becomes completely useless:
when the related session is closed any socket that is currently used to send control
request(s) MUST be closed (it obviously does not apply to session running over websocket
since such sockets are closed together with the session end).
<p>
Some kind of Control Requests may not be compatible to be sent in the same batch. Due to
this the client will keep different lists and will choose which one to dequeue from via
roundrobin.
These are the different kinds of batches:
<ol>
<li>control: subscription, unsubscription and constraint (currently only bandwidth change
is performed through constraint requests)</li>
<li>msg: messages</li>
<li>heartbeat: reverse heartbeats. These are never batched and only sent if there was
silence on the control channel for a configurable time.</li>
<li>send_log: remote client logging; it is not mandatory to implement these messages</li>
<li>control: destroy requests are compatible with the first category but, while usually
control requests are sent to the currently active server instance address (unless it is
specified to ignore the server instance address), these requests must be sent to the
server where the old session was open. For this reason this requests are never
batched</li>
</ol>

<h3>Control Connection timeout algorithm</h3>
In case no response, either synchronous or asynchronous, for a certain control connection,
is not received within 4 seconds, the missing control request will be sent again to the
batching algorithm (note that the 4 second timeout starts when the request is sent on the
net, not when the request is sent to the batching algorithm). The timeout is then doubled
each time a request is sent again. Also the timeout is extended with the pollingInterval value
to prevent sending useless requests during “short polling” sessions.
IMPLEMENTATION NOTE: the WebSocket case has no synchronous request/responses.
IMPLEMENTATION NOTE: if any control response, excluding destroy requests, returns with
a “sync error”, the client will drop the current session and will open a new one.
IMPLEMENTATION NOTE: the Web/Node.js clients currently only handle the sync error
from synchronous responses (i.e. ignores ok or other kind of errors, including network errors
and waits for such notifications on the stream connection)
IMPLEMENTATION NOTE: the HTML might not have the chance to read the synchronous
responses (control.html cases and JSONP cases).
</blockquote> 
 *
 */
public class HttpRequestManager implements RequestManager {
  
  private static final String IDLE = "IDLE";
  private static final String WAITING = "WAITING";
  private static final String END = "END";
  private static final String ENDING = "ENDING";
  

  private final Logger log = LogManager.getLogger(Constants.REQUESTS_LOG);
  
  private final BatchRequest messageQueue = new BatchRequest(BatchRequest.MESSAGE);
  private final BatchRequest controlQueue = new BatchRequest(BatchRequest.CONTROL);
  //private TextProtocolRequestBatch logQueue = null;
  private final BatchRequest destroyQueue = new BatchRequest(BatchRequest.CONTROL);
  private final BatchRequest hbQueue = new BatchRequest(BatchRequest.HEARTBEAT);
  
  private final BatchRequest[] requestQueues = new  BatchRequest[] {
      messageQueue,controlQueue,destroyQueue,hbQueue
  };
  
  private long requestLimit = 0;
  private int nextQueue = 0; // handles turns (control-sendMessage-sendLog)

  private String status = IDLE;
  private int statusPhase = 1;
  private SessionThread sessionThread;
  private Transport transport;
  private Protocol protocol;
  private InternalConnectionOptions options;
  
  private RequestHandle activeConnection;
  
  private final FatalErrorListener errorListener;
  /**
   * List of requests that the manager has sent but no response has still arrived.
   * Must be cleared when {@link RequestListener#onClosed()} or {@link RequestListener#onBroken()}
   * is called.
   */
  private final LinkedList<RequestObjects> ongoingRequests = new LinkedList<>();
  
  HttpRequestManager(SessionThread thread, Transport transport, InternalConnectionOptions options) {
    this(thread, null, transport, options, null);
  }

  HttpRequestManager(SessionThread thread, Protocol protocol, Transport transport, InternalConnectionOptions options, FatalErrorListener errListener) {
    this.sessionThread = thread;
    this.transport = transport;
    this.protocol = protocol;
    this.options = options;
    this.errorListener = errListener;
  }
  
  private boolean is(String status) {
    return this.status.equals(status);
  }
  
  private boolean isNot(String status) {
    return !this.is(status);
  }
  
  @Override
  public void close(boolean waitPending) {
    if (!waitPending || this.activeConnection == null) {
      if (this.activeConnection != null) {
        if (requestQueues[this.nextQueue] != destroyQueue) {
          this.activeConnection.close(false);
        } //else do not bother destroy requests
      }
      this.changeStatus(END);
    } else {
      this.changeStatus(ENDING);
    }
  }
  
  
  private void changeStatus(String newStatus) {
    this.statusPhase++; //used to verify dequeue and sendHeartbeats calls
    log.info("Batch manager is now " + newStatus);
    this.status = newStatus;
  }
  
  @Override
  public void setRequestLimit(long newLimit) {
    this.requestLimit  = newLimit;
    log.debug("Batch length limit changed to " + newLimit);
  }
  
  private boolean addToProperBatch(LightstreamerRequest request, RequestTutor tutor, RequestListener listener) {
    if (request instanceof MessageRequest) {
      return this.messageQueue.addRequestToBatch((MessageRequest)request, tutor, listener);
    } else if (request instanceof ReverseHeartbeatRequest) {
      return this.hbQueue.addRequestToBatch((ReverseHeartbeatRequest)request, tutor, listener);
    } else if (request instanceof ConstrainRequest) {
      return this.controlQueue.addRequestToBatch((ConstrainRequest)request, tutor, listener);
    } else if (request instanceof ForceRebindRequest) {
      return this.controlQueue.addRequestToBatch((ForceRebindRequest)request, tutor, listener);
    } else if (request instanceof UnsubscribeRequest) {
      return this.controlQueue.addRequestToBatch((UnsubscribeRequest)request, tutor, listener);
    } else if (request instanceof SubscribeRequest) {
      return this.controlQueue.addRequestToBatch((SubscribeRequest)request, tutor, listener);
    } else if (request instanceof ChangeSubscriptionRequest) {
      return this.controlQueue.addRequestToBatch((ChangeSubscriptionRequest)request, tutor, listener);
    } else if (request instanceof DestroyRequest) {
      return this.destroyQueue.addRequestToBatch((DestroyRequest)request, tutor, listener);
    } else if (request instanceof MpnRequest) {
      return this.controlQueue.addRequestToBatch((MpnRequest)request, tutor, listener);
    }
    
    return false;
  }
  
  @Override
  public void copyTo(ControlRequestHandler newHandler) {
    //XXX we might want to skip destroy requests and send them on the network instead
    
    if (! ongoingRequests.isEmpty()) {
        for (RequestObjects req : ongoingRequests) {
            newHandler.addRequest(req.request, req.tutor, req.listener);
        }
        ongoingRequests.clear();
    }
    for (int i=0; i<this.requestQueues.length; i++) {
      RequestObjects migrating;
      while ((migrating = this.requestQueues[i].shift()) != null) {
        newHandler.addRequest(migrating.request, migrating.tutor, migrating.listener);
      }
    }
    
    newHandler.setRequestLimit(this.requestLimit);
  }
  
  @Override
  public void addRequest(LightstreamerRequest request, RequestTutor tutor, RequestListener listener) {

 /*   
    
    NOTE: if we don't queue right away we risk to lose the request: I don't see a reason not to queue right away
   
    final int sc = this.phase;
    
    thread.queue(new Runnable() {
      @Override
      public void run() {
        addRequest(request,tutor,listener,sc);
      }
    });
  }
  
  private void addRequest(LightstreamerRequest request, RequestTutor tutor, RequestListener listener, int callPhase) {
    if (!checkPhase(request,callPhase)) {
      return;
    }
    
  
  */
    assert request instanceof ControlRequest || request instanceof MessageRequest || request instanceof ReverseHeartbeatRequest;

    if (this.is(END) || this.is(ENDING)) {
      log.error("Unexpected call on dismissed batch manager: " + request.getTransportUnawareQueryString());
      throw new IllegalStateException("Unexpected call on dismissed batch manager");
    }
    
    this.addToProperBatch(request, tutor, listener);
    
    if (this.is(IDLE)) {
      this.dequeue(SYNC_DEQUEUE, "add");
    } else {
      //we're already busy, we'll dequeue when we'll be back
      log.debug("Request manager busy: the request will be sent later " + request);
    }
  }
  
  public RequestHandle createSession(CreateSessionRequest request, StreamListener reqListener, long tcpConnectTimeout, long tcpReadTimeout) {
      return transport.sendRequest(protocol, request, 
              reqListener, 
              options.getHttpExtraHeaders(), 
              options.getProxy(), 
              tcpConnectTimeout, 
              tcpReadTimeout);
  }
  
  @Override
  public RequestHandle bindSession(BindSessionRequest request, StreamListener reqListener, long tcpConnectTimeout, long tcpReadTimeout, ListenableFuture requestFuture) {
      RequestHandle handle = transport.sendRequest(protocol, request, 
              reqListener, 
              options.isHttpExtraHeadersOnSessionCreationOnly() ? null : options.getHttpExtraHeaders(),
                      options.getProxy(), 
                      tcpConnectTimeout, 
                      tcpReadTimeout);
      requestFuture.fulfill();
      return handle;
  }
  
  public RequestHandle recoverSession(RecoverSessionRequest request, StreamListener reqListener, long tcpConnectTimeout, long tcpReadTimeout) {
          return transport.sendRequest(protocol, request, 
                  reqListener, 
                  options.isHttpExtraHeadersOnSessionCreationOnly() ? null : options.getHttpExtraHeaders(),
                  options.getProxy(), 
                  tcpConnectTimeout, 
                  tcpReadTimeout);
  }

  private static long  SYNC_DEQUEUE = -1;
  private static long  ASYNC_DEQUEUE = 0;
  
  
  private void dequeue(long delay, final String who) {
    if (delay == SYNC_DEQUEUE) {
      log.debug("Ready to dequeue control requests to be sent to server");
      this.dequeueControlRequests(this.statusPhase,who);
      
    } else {
      final int sc = this.statusPhase;
      Runnable task = new Runnable() {
        public void run() {
          dequeueControlRequests(sc,"async."+who);
        }
      };
      
      if (delay == ASYNC_DEQUEUE) {
        sessionThread.queue(task);
      } else  {
        sessionThread.schedule(task, delay);
      }     
    }
  }
  
  
  
  private void dequeueControlRequests(int statusPhase, String who) {
    if(statusPhase != this.statusPhase) {
      return;
    }
    
    if (this.isNot(IDLE)) {
      if (this.is(WAITING)) {
        //might happen if an async dequeue is surpassed by a sync one
        return;
      } else if (this.is(END)) {
        //game over
        return;
      } else if (is(ENDING)) {
        log.error("dequeue call on unexpected status");
        this.changeStatus(END);
        return;
      }
    }
    
    log.info("starting dequeuing ("+who+")");
    
    int c = 0;
    while(c < this.requestQueues.length ) {
      
      //switch the flag to change turn
      nextQueue = nextQueue < requestQueues.length-1 ? nextQueue+1 : 0;
      
      if (requestQueues[nextQueue].getLength() > 0) {
        boolean sent = sendBatch(requestQueues[nextQueue]);
        if (sent) {
          changeStatus(WAITING);
          return;
        } 
      }
      c++;
    }
    
    //nothing to send, we're still IDLE
    log.info("Nothing to send");
    
  }

  private boolean sendBatch(BatchRequest batch) {
    if (batch.getLength() <= 0) {
      //something wrong o_O
      log.error("Unexpected call");
      
      //XXX exit here??
    }
    
    BatchedListener combinedRequestListener = new BatchedListener();
    BatchedRequest combinedRequest = new BatchedRequest();
    
    /* find the first request to be sent: it provides the server address and the request name for the whole combined request */
    RequestObjects first = null;
    while (first == null && batch.getLength() > 0) {
        first = batch.shift();
        if (first.tutor.shouldBeSent()) {

            combinedRequest.setServer(first.request.getTargetServer());
            combinedRequest.setRequestName(first.request.getRequestName());

            combinedRequest.add(first.request);
            combinedRequestListener.add(first.listener);
            ongoingRequests.add(first);
        } else {
            first.tutor.notifyAbort();
            first = null;
        }
    }
    if (combinedRequest.length() == 0) {
        //nothing to send
        return false;
    }
    /* add the other requests to the combined request: they share the server address and the request name */
    while ((requestLimit == 0 || (combinedRequest.length()  + batch.getNextRequestLength()) < requestLimit) && batch.getLength() > 0) {
        RequestObjects next = batch.shift();
        if (next.tutor.shouldBeSent()) {
            combinedRequest.add(next.request);
            combinedRequestListener.add(next.listener);
            ongoingRequests.add(next);
        } else {
            next.tutor.notifyAbort();
        }
    }
    
    if (log.isDebugEnabled()) {
        log.debug("Sending " + combinedRequestListener.size() + " batched requests");
        log.debug("Batch: " + combinedRequest.getRequestName() + "\n" + combinedRequest.getTransportAwareQueryString(null, true));
    }
    
    activeConnection = transport.sendRequest(protocol, combinedRequest, 
            combinedRequestListener, 
            options.isHttpExtraHeadersOnSessionCreationOnly() ? null : options.getHttpExtraHeaders(),
            options.getProxy(), 
            options.getTCPConnectTimeout(), 
            options.getTCPReadTimeout());
    return true;
  }
  
  private boolean onComplete(String why) {
    if (this.is(END)) {
      //don't care
      return false;
    } else if (this.is(ENDING)) {
      changeStatus(END);
    } else {
      //should be waiting
      if (this.is(IDLE)) {
        log.error("Unexpected batch manager status at connection end");
      }
      
      log.info("Batch completed");
      
      changeStatus(IDLE);
      
      dequeue(ASYNC_DEQUEUE,"closed"); //prepare the future
    }
    activeConnection = null;
    return true;
  }
  
  public interface FatalErrorListener {

      void onError(int errorCode, String errorMessage);
  }
  
  /**
   * The exception is thrown when a control request returns an ERROR message.
   * When this happens, usually the right action is to close the current session without recovery.
   */
  private static class ProtocolErrorException extends Exception {
      private static final long serialVersionUID = 1L;
      private final int errorCode;
      
      public ProtocolErrorException(String errorCode, String errorMessage) {
          super(errorMessage);
          this.errorCode = Integer.parseInt(errorCode);
      }
      
      public int getErrorCode() {
          return errorCode;
      }
  }

  
  private class BatchedRequest extends LightstreamerRequest {

    private StringBuilder fullRequest = new StringBuilder();
    private String requestName;
    
    public void setRequestName(String requestName) {
      this.requestName = requestName;
    }

    public void add(LightstreamerRequest request) {
      if (fullRequest.length() > 0) {
        fullRequest.append("\r\n");
      }
      fullRequest.append(request.getTransportAwareQueryString(null, true));
    }

    @Override
    public String getRequestName() {
      return this.requestName;
    }

    public long length() {
      return fullRequest.length();
    }
    
    @Override
    public String getTransportUnawareQueryString() {
        // the caller isn't aware of the transport, but we are
      return this.fullRequest.toString();
    }

    @Override
    public String getTransportAwareQueryString(String defaultSessionId, boolean ackIsForced) {
      assert(ackIsForced);
        // the caller must be aligned with the transport assumed here
      return this.fullRequest.toString();
    }
    
  }
  
  private class BatchedListener implements RequestListener {

    boolean completed = false;
    final ArrayList<String> messages = new ArrayList<>();
    final List<RequestListener> listeners = new LinkedList<RequestListener>();
   
    public int size() {
      return listeners.size();
    }
    
    @Override
    public void onMessage(String message) {
        messages.add(message);
    }

    public void add(RequestListener listener) {
      listeners.add(listener);
    }

    @Override
    public void onOpen() {
      if (is(END)) {
        //don't care
        return;
      }
      for (RequestListener listener : listeners) {
        listener.onOpen();
      }
    }
    
    private void dispatchMessages() throws ProtocolErrorException {
        /* Abnormal conditions are:
         * - the presence of ERROR messages
         * - an unexpected number of responses
         */
        if (messages.size() == 1 && messages.get(0).startsWith("ERROR")) {
            log.error("Control request returned an ERROR message: " + messages);
            String message = messages.get(0);
            try {
                ERRORParser parser = new ERRORParser(message);
                throw new ProtocolErrorException("" + parser.errorCode, parser.errorMsg);
                
            } catch (ParsingException e) {
                throw new ProtocolErrorException("61", "Unexpected response to control request: " + message);
            }
            
        } else if (messages.size() != listeners.size()) {
            log.error("Control request returned an unexpected number of responses: " + messages);
            throw new ProtocolErrorException("61", "The number of received responses is different from the number of batched requests");
            
        } else {
            // check whether there is an ERROR message
            for (String msg : messages) {
                if (msg.startsWith("ERROR")) {
                    log.error("Control request returned at least an ERROR message: " + messages);
                    throw new ProtocolErrorException("61", "A batch of requests returned at least an ERROR message");
                }
            }
        }
        /* no ERROR message: process the responses */
        for (int i=0; i < messages.size(); i++) {
            listeners.get(i).onMessage(messages.get(i));
        }
    }
    
    @Override
    public void onClosed() {
        ongoingRequests.clear();
        if (is(END)) {
            //don't care
            return;
        }
        try {
            if (!completed) {
                if (onComplete("closed")) {
                    if (this.messages.size() > 0) {
                        dispatchMessages();
                    }
                }
                completed = true;
            }

            for (RequestListener listener : listeners) {
                listener.onClosed();
            }
        } catch (ProtocolErrorException e) {
            if (errorListener != null) {
                errorListener.onError(e.getErrorCode(), e.getMessage());
            }
        }
    }

    @Override
    public void onBroken() {
        ongoingRequests.clear();
        if (is(END)) {
            //don't care
            return;
        }
        try {
            if (!completed) {
                if (onComplete("broken")) {
                    //we might be able to salvage something if size() > 0
                    if (this.messages.size() > 0) {
                        dispatchMessages();
                    }
                }
                completed = true;
            }

            for (RequestListener listener : listeners) {
                listener.onBroken();
            }
        } catch (ProtocolErrorException e) {
            if (errorListener != null) {
                errorListener.onError(e.getErrorCode(), e.getMessage());
            }
        }
    }
    
  }
  
}
