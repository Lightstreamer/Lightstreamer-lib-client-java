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
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.lightstreamer.client.events.EventDispatcher;
import com.lightstreamer.client.events.SubscriptionListenerClearSnapshotEvent;
import com.lightstreamer.client.events.SubscriptionListenerCommandSecondLevelItemLostUpdatesEvent;
import com.lightstreamer.client.events.SubscriptionListenerCommandSecondLevelSubscriptionErrorEvent;
import com.lightstreamer.client.events.SubscriptionListenerConfigurationEvent;
import com.lightstreamer.client.events.SubscriptionListenerEndEvent;
import com.lightstreamer.client.events.SubscriptionListenerEndOfSnapshotEvent;
import com.lightstreamer.client.events.SubscriptionListenerItemLostUpdatesEvent;
import com.lightstreamer.client.events.SubscriptionListenerItemUpdateEvent;
import com.lightstreamer.client.events.SubscriptionListenerStartEvent;
import com.lightstreamer.client.events.SubscriptionListenerSubscriptionErrorEvent;
import com.lightstreamer.client.events.SubscriptionListenerSubscriptionEvent;
import com.lightstreamer.client.events.SubscriptionListenerUnsubscriptionEvent;
import com.lightstreamer.client.protocol.ProtocolConstants;
import com.lightstreamer.client.requests.ChangeSubscriptionRequest;
import com.lightstreamer.client.requests.SubscribeRequest;
import com.lightstreamer.client.session.SessionThread;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;
import com.lightstreamer.util.ConcurrentMatrix;
import com.lightstreamer.util.Descriptor;
import com.lightstreamer.util.ListDescriptor;
import com.lightstreamer.util.Matrix;
import com.lightstreamer.util.NameDescriptor;
import com.lightstreamer.util.Number;

/**
 * Class representing a Subscription to be submitted to a Lightstreamer Server. It contains 
 * subscription details and the listeners needed to process the real-time data. <BR>
 * After the creation, a Subscription object is in the "inactive" state. When a Subscription 
 * object is subscribed to on a LightstreamerClient object, through the 
 * {@link LightstreamerClient#subscribe(Subscription)} method, its state becomes "active". 
 * This means that the client activates a subscription to the required items through 
 * Lightstreamer Server and the Subscription object begins to receive real-time events. <BR>
 * A Subscription can be configured to use either an Item Group or an Item List to specify the 
 * items to be subscribed to and using either a Field Schema or Field List to specify the fields. <BR>
 * "Item Group" and "Item List" are defined as follows:
 * <ul>
 *  <li>"Item Group": an Item Group is a String identifier representing a list of items. 
 *  Such Item Group has to be expanded into a list of items by the getItems method of the 
 *  MetadataProvider of the associated Adapter Set. When using an Item Group, items in the 
 *  subscription are identified by their 1-based index within the group.<BR>
 *  It is possible to configure the Subscription to use an "Item Group" using the 
 *  {@link #setItemGroup(String)} method.</li> 
 *  <li>"Item List": an Item List is an array of Strings each one representing an item. 
 *  For the Item List to be correctly interpreted a LiteralBasedProvider or a MetadataProvider 
 *  with a compatible implementation of getItems has to be configured in the associated 
 *  Adapter Set.<BR>
 *  Note that no item in the list can be empty, can contain spaces or can be a number.<BR>
 *  When using an Item List, items in the subscription are identified by their name or 
 *  by their 1-based index within the list.<BR>
 *  It is possible to configure the Subscription to use an "Item List" using the 
 *  {@link #setItems(String[])} method or by specifying it in the constructor.</li>
 * </ul>
 * "Field Schema" and "Field List" are defined as follows:
 * <ul>
 *  <li>"Field Schema": a Field Schema is a String identifier representing a list of fields. 
 *  Such Field Schema has to be expanded into a list of fields by the getFields method of 
 *  the MetadataProvider of the associated Adapter Set. When using a Field Schema, fields 
 *  in the subscription are identified by their 1-based index within the schema.<BR>
 *  It is possible to configure the Subscription to use a "Field Schema" using the 
 *  {@link #setFieldSchema(String)} method.</li>
 *  <li>"Field List": a Field List is an array of Strings each one representing a field. 
 *  For the Field List to be correctly interpreted a LiteralBasedProvider or a MetadataProvider 
 *  with a compatible implementation of getFields has to be configured in the associated 
 *  Adapter Set.<BR>
 *  Note that no field in the list can be empty or can contain spaces.<BR>
 *  When using a Field List, fields in the subscription are identified by their name or 
 *  by their 1-based index within the list.<BR>
 *  It is possible to configure the Subscription to use a "Field List" using the 
 *  {@link #setFields(String[])} method or by specifying it in the constructor.</li>
 * </ul>
 */
public class Subscription {
  
  private static final String NO_ITEMS = "Please specify a valid item or item list";
  private static final String NO_FIELDS = "Invalid Subscription, please specify a field list or field schema";
  private static final String IS_ALIVE = "Cannot modify an active Subscription, please unsubscribe before applying any change";
  private static final String NOT_ALIVE = "Subscription is not active";
  private static final String INVALID_MODE =  "The given value is not a valid subscription mode. Admitted values are MERGE, DISTINCT, RAW, COMMAND";
  private static final String NO_VALID_FIELDS = "Please specify a valid field list";
  private static final String NO_GROUP_NOR_LIST = "The  item list/item group of this Subscription was not initiated";
  private static final String NO_SCHEMA_NOR_LIST = "The field list/field schema of this Subscription was not initiated";
  private static final String MAX_BUF_EXC = "The given value is not valid for this setting; use null, 'unlimited' or a positive integer instead";
  private static final String NO_SECOND_LEVEL = "Second level field list is only available on COMMAND Subscriptions";
  private static final String NO_COMMAND = "This method can only be used on COMMAND subscriptions";
  private static final String NO_SUB_SCHEMA_NOR_LIST = "The second level of this Subscription was not initiated";
  private static final String RAW_NO_SNAPSHOT = "Snapshot is not permitted if RAW was specified as mode";
  private static final String NUMERIC_DISTINCT_ONLY = "Numeric values are only allowed when the subscription mode is DISTINCT";
  private static final String REQ_SNAP_EXC = "The given value is not valid for this setting; use null, 'yes', 'no' or a positive number instead";
  private static final String ILLEGAL_FREQ_EXC = "Can't change the frequency from/to 'unfiltered' or to null while the Subscription is active";
  private static final String MAX_FREQ_EXC = "The given value is not valid for this setting; use null, 'unlimited', 'unfiltered' or a positive number instead";
  private static final String INVALID_SECOND_LEVEL_KEY = "The received key value is not a valid name for an Item";
  
  private static final String SIMPLE = "SIMPLE";
  private static final String METAPUSH = "METAPUSH";
  private static final String MULTIMETAPUSH = "MULTIMETAPUSH";
  
  private static final String OFF = "OFF";
  private static final String WAITING = "WAITING";
  private static final String PAUSED = "PAUSED";
  private static final String SUBSCRIBING = "SUBSCRIBING";
  private static final String PUSHING = "PUSHING";
  
  static final int FREQUENCY_NULL = -2;
  static final int FREQUENCY_UNFILTERED = -1;
  static final int FREQUENCY_UNLIMITED = 0;

  static final int BUFFER_NULL = -1;
  static final int BUFFER_UNLIMITED = 0;
  
  private static final boolean CLEAN = true;
  private static final boolean DONT_CLEAN = false;
  
  
  private final Logger log = LogManager.getLogger(Constants.ACTIONS_LOG);
  
  private EventDispatcher<SubscriptionListener> dispatcher = new EventDispatcher<SubscriptionListener>(LightstreamerClient.eventsThread);
  
  private boolean isActive = false;
  
  Descriptor itemDescriptor;
  Descriptor fieldDescriptor;
  private int commandCode = -1;
  private int keyCode = -1;
  
  private int nextReconfId = 1;
  
  //data
  private String dataAdapter = null;
  private String mode = null;
  private String isRequiredSnapshot = null;
  private String selector = null;
  int requestedBufferSize = BUFFER_NULL;
  private ConcurrentMatrix<Integer,Integer> oldValuesByItem = new ConcurrentMatrix<Integer,Integer>(); //concurrent to handle getValue calls
  private ConcurrentMatrix<String,Integer> oldValuesByKey = new ConcurrentMatrix<String,Integer>();  //concurrent to handle getValue calls
  //2nd level data
  private String underDataAdapter = null;
  private Descriptor subFieldDescriptor;
  private Matrix<Integer,String,Subscription> subTables =  new Matrix<Integer,String,Subscription>();
      //completely useless (may directly use the constant)
      //I save it here so that I can modify it to DISTINCT in the SubscriptionUnusualEvents test.
  protected String subMode = Constants.MERGE;
  private double aggregatedRealMaxFrequency = FREQUENCY_NULL;
  private boolean subTableFlag = false;
  
  private String behavior = null;
  double requestedMaxFrequency = FREQUENCY_NULL;
  private double localRealMaxFrequency = FREQUENCY_NULL;
    // NOTE: used only for two-level subscriptions (for both first and second level)
    // not needed for normal subscriptions, where the information reaches the listener directly 
  private int subscriptionId = -1;
  
  

  
  private String tablePhaseType = OFF; 
  private int tablePhase = 0;
  private SubscriptionManager manager;
  private SessionThread sessionThread;
  private SnapshotManager[] snapshotByItem;
  
  
  
  
  
  /**
   * Creates an object to be used to describe a Subscription that is going to be subscribed to 
   * through Lightstreamer Server. The object can be supplied to 
   * {@link LightstreamerClient#subscribe(Subscription)} and 
   * {@link LightstreamerClient#unsubscribe(Subscription)}, in order to bring the Subscription 
   * to "active" or back to "inactive" state. <BR>
   * Note that all of the methods used to describe the subscription to the server can only be 
   * called while the instance is in the "inactive" state; the only exception is 
   * {@link #setRequestedMaxFrequency(String)}.
   *
   * @param subscriptionMode the subscription mode for the items, required by Lightstreamer Server. 
   * Permitted values are:
   * <ul>
   *  <li>MERGE</li>
   *  <li>DISTINCT</li>
   *  <li>RAW</li>
   *  <li>COMMAND</li>
   * </ul>
   * @param items an array of items to be subscribed to through Lightstreamer server. <BR>
   * It is also possible specify the "Item List" or "Item Group" later through 
   * {@link #setItems(String[])} and {@link #setItemGroup}.
   * @param fields an array of fields for the items to be subscribed to through Lightstreamer Server. <BR>
   * It is also possible to specify the "Field List" or "Field Schema" later through 
   * {@link #setFields(String[])} and {@link #setFieldSchema(String)}.
   * @throws IllegalArgumentException If no or invalid subscription mode is passed.
   * @throws IllegalArgumentException If either the items or the fields array is left null.
   * @throws IllegalArgumentException If the specified "Item List" or "Field List" is not valid; 
   * see {@link #setItems(String[])} and {@link #setFields(String[])} for details.
   */
  public Subscription(@Nonnull String subscriptionMode, @Nonnull String[] items, @Nonnull String[] fields) {
  //Please specify a valid item or item list
  //Please specify a valid field list

    init(subscriptionMode,items,fields);
  }
  
  /**
   * Creates an object to be used to describe a Subscription that is going to be subscribed to 
   * through Lightstreamer Server. The object can be supplied to 
   * {@link LightstreamerClient#subscribe(Subscription)} and 
   * {@link LightstreamerClient#unsubscribe(Subscription)}, in order to bring the Subscription 
   * to "active" or back to "inactive" state. <BR>
   * Note that all of the methods used to describe the subscription to the server can only be 
   * called while the instance is in the "inactive" state; the only exception is 
   * {@link #setRequestedMaxFrequency(String)}.
   *
   * @param subscriptionMode the subscription mode for the items, required by Lightstreamer Server. 
   * Permitted values are:
   * <ul>
   *  <li>MERGE</li>
   *  <li>DISTINCT</li>
   *  <li>RAW</li>
   *  <li>COMMAND</li>
   * </ul>
   * @param item the item name to be subscribed to through Lightstreamer Server.  
   * @param fields an array of fields for the items to be subscribed to through Lightstreamer Server. <BR>
   * It is also possible to specify the "Field List" or "Field Schema" later through 
   * {@link #setFields(String[])} and {@link #setFieldSchema(String)}.
   * @throws IllegalArgumentException If no or invalid subscription mode is passed.
   * @throws IllegalArgumentException If either the item or the fields array is left null.
   * @throws IllegalArgumentException If the specified "Field List" is not valid; 
   * see {@link #setFields(String[])} for details..
   */
  public Subscription(@Nonnull String subscriptionMode, @Nonnull String item, @Nonnull String[] fields) {
  //Please specify a valid item or item list
  //Please specify a valid field list

    init(subscriptionMode, new String[]{item}, fields);
  }
    
  /**
   * Creates an object to be used to describe a Subscription that is going to be subscribed to 
   * through Lightstreamer Server. The object can be supplied to 
   * {@link LightstreamerClient#subscribe(Subscription)} and 
   * {@link LightstreamerClient#unsubscribe(Subscription)}, in order to bring the Subscription 
   * to "active" or back to "inactive" state. <BR>
   * Note that all of the methods used to describe the subscription to the server can only be 
   * called while the instance is in the "inactive" state; the only exception is 
   * {@link #setRequestedMaxFrequency(String)}.
   *
   * @param subscriptionMode the subscription mode for the items, required by Lightstreamer Server. 
   * Permitted values are:
   * <ul>
   *  <li>MERGE</li>
   *  <li>DISTINCT</li>
   *  <li>RAW</li>
   *  <li>COMMAND</li>
   * </ul>
   */
  public Subscription(@Nonnull String subscriptionMode) {
    init(subscriptionMode,null,null);
  }
  
  private void init(String subscriptionMode, String[] items, String[] fields) {
    if (subscriptionMode == null) {
      throw new IllegalArgumentException(INVALID_MODE);
    }
    
    subscriptionMode = subscriptionMode.toUpperCase();
    if (!Constants.MODES.contains(subscriptionMode)) {
      throw new IllegalArgumentException(INVALID_MODE);
    }
    
    this.mode = subscriptionMode;
    
    
    //DEFAULT: "yes" if mode is MERGE, DISTINCT, or COMMAND; null if mode is RAW 
    this.isRequiredSnapshot = subscriptionMode.equals(Constants.RAW) ? null : "yes";
    
    this.behavior = this.mode.equals(Constants.COMMAND) ? METAPUSH : SIMPLE;
    
/////////////////Setup   
    if (items != null) {
      if (fields == null) {
        throw new IllegalArgumentException(NO_VALID_FIELDS);
      }
      this.setItems(items);
      this.setFields(fields);
    } else if (fields != null) {
      throw new IllegalArgumentException(NO_ITEMS);
    }

  }
  
  
  /**
   * Adds a listener that will receive events from the Subscription instance. <BR> 
   * The same listener can be added to several different Subscription instances.
   *
   * @lifecycle A listener can be added at any time. A call to add a listener already 
   * present will be ignored.
   * 
   * @param listener An object that will receive the events as documented in the 
   * SubscriptionListener interface.
   * 
   * @see #removeListener(SubscriptionListener)
   */
  public synchronized void addListener(@Nonnull SubscriptionListener listener) {
    this.dispatcher.addListener(listener, new SubscriptionListenerStartEvent(this));
  }
  
  /**
   * Removes a listener from the Subscription instance so that it will not receive 
   * events anymore.
   * 
   * @lifecycle a listener can be removed at any time.
   * 
   * @param listener The listener to be removed.
   * 
   * @see #addListener(SubscriptionListener)
   */
  public synchronized void removeListener(@Nonnull SubscriptionListener listener) {
    this.dispatcher.removeListener(listener, new SubscriptionListenerEndEvent(this));
  }
  
  /**
   * Returns a list containing the {@link SubscriptionListener} instances that were 
   * added to this client.
   * @return a list containing the listeners that were added to this client. 
   * @see #addListener(SubscriptionListener)
   */
  @Nonnull 
  public synchronized List<SubscriptionListener> getListeners() {
    return this.dispatcher.getListeners();
  }
  
  
  
  
  /**  
   * Inquiry method that checks if the Subscription is currently "active" or not.
   * Most of the Subscription properties cannot be modified if a Subscription 
   * is "active". <BR>
   * The status of a Subscription is changed to "active" through the  
   * {@link LightstreamerClient#subscribe(Subscription)} method and back to 
   * "inactive" through the {@link LightstreamerClient#unsubscribe(Subscription)} one.
   * 
   * @lifecycle This method can be called at any time.
   * 
   * @return true/false if the Subscription is "active" or not.
   * 
   * @see LightstreamerClient#subscribe(Subscription)
   * @see LightstreamerClient#unsubscribe(Subscription)
   */
  public synchronized boolean isActive() {
    return this.isActive;
  }
  /**  
   * Inquiry method that checks if the Subscription is currently subscribed to
   * through the server or not. <BR>
   * This flag is switched to true by server sent Subscription events, and 
   * back to false in case of client disconnection, 
   * {@link LightstreamerClient#unsubscribe(Subscription)} calls and server 
   * sent unsubscription events. 
   * 
   * @lifecycle This method can be called at any time.
   * 
   * @return true/false if the Subscription is subscribed to
   * through the server or not.
   */
  public synchronized boolean isSubscribed() {
    return this.is(PUSHING); //might change while we check, no need to worry
  }
  /**
   * Inquiry method that can be used to read the name of the Data Adapter specified for this 
   * Subscription through {@link #setDataAdapter(String)}.
   * @lifecycle This method can be called at any time.
   * @return the name of the Data Adapter; returns null if no name has been configured, 
   * so that the "DEFAULT" Adapter Set is used.
   */
  @Nullable 
  public synchronized String getDataAdapter() {
    return this.dataAdapter;
  }
  
  /**
   * Setter method that sets the name of the Data Adapter
   * (within the Adapter Set used by the current session)
   * that supplies all the items for this Subscription. <BR>
   * The Data Adapter name is configured on the server side through
   * the "name" attribute of the "data_provider" element, in the
   * "adapters.xml" file that defines the Adapter Set (a missing attribute
   * configures the "DEFAULT" name). <BR>
   * Note that if more than one Data Adapter is needed to supply all the
   * items in a set of items, then it is not possible to group all the
   * items of the set in a single Subscription. Multiple Subscriptions
   * have to be defined.
   *
   * @default The default Data Adapter for the Adapter Set,
   * configured as "DEFAULT" on the Server.
   *
   * @lifecycle This method can only be called while the Subscription
   * instance is in its "inactive" state.
   * 
   * @throws IllegalStateException if the Subscription is currently 
   * "active".
   *
   * @param dataAdapter the name of the Data Adapter. A null value 
   * is equivalent to the "DEFAULT" name.
   *  
   * @see ConnectionDetails#setAdapterSet(String)
   */
  public synchronized void setDataAdapter(@Nullable String dataAdapter) {
    this.notAliveCheck();
    
    this.dataAdapter = dataAdapter;
    if (log.isDebugEnabled()) {
      log.debug("Adapter Set assigned: "+ dataAdapter);
    }
  }
  
  
  
  /**
   * Inquiry method that can be used to read the mode specified for this
   * Subscription.
   * 
   * @lifecycle This method can be called at any time.
   * 
   * @return the Subscription mode specified in the constructor.
   */
  @Nonnull 
  public synchronized String getMode() {
    return this.mode;
  }
  /**
   * Inquiry method that can be used to read the "Item List" specified for this Subscription. 
   * Note that if the single-item-constructor was used, this method will return an array 
   * of length 1 containing such item.
   *
   * @lifecycle This method can only be called if the Subscription has been initialized 
   * with an "Item List".
   * @throws IllegalStateException if the Subscription was initialized with an "Item Group" 
   * or was not initialized at all.
   * @return the "Item List" to be subscribed to through the server.
   */
  @Nonnull 
  public synchronized String[] getItems() {
    
    if (this.itemDescriptor == null) {
      throw new IllegalStateException(NO_GROUP_NOR_LIST);
    } else if (this.itemDescriptor instanceof NameDescriptor) {
      throw new IllegalStateException("This Subscription was initiated using an item group, use getItemGroup instead of using getItems");
    }
    
    
    return ((ListDescriptor) this.itemDescriptor).getOriginal();
  }
  
  /**
   * Setter method that sets the "Item List" to be subscribed to through 
   * Lightstreamer Server. <BR>
   * Any call to this method will override any "Item List" or "Item Group"
   * previously specified.
   * 
   * @lifecycle This method can only be called while the Subscription
   * instance is in its "inactive" state.
   * 
   * @throws IllegalArgumentException if any of the item names in the "Item List"
   * contains a space or is a number or is empty/null.
   * @throws IllegalStateException if the Subscription is currently 
   * "active".
   * 
   * @param items an array of items to be subscribed to through the server. 
   */
  public synchronized void setItems(@Nullable String[] items) {
    this.notAliveCheck();
    
    ListDescriptor.checkItemNames(items,"An item");
    
    this.itemDescriptor = items == null ? null : new ListDescriptor(items);

    debugDescriptor("Item list assigned: ", this.itemDescriptor);
  }
    
  /**
   * Inquiry method that can be used to read the item group specified for this Subscription.
   *
   * @lifecycle This method can only be called if the Subscription has been initialized
   * using an "Item Group"
   * @throws IllegalStateException if the Subscription was initialized with an "Item List" 
   * or was not initialized at all.
   * @return the "Item Group" to be subscribed to through the server.
   */
  @Nonnull 
  public synchronized String getItemGroup() {
    if (this.itemDescriptor == null) {
      throw new IllegalStateException(NO_GROUP_NOR_LIST);
    } else if (this.itemDescriptor instanceof ListDescriptor) {
      throw new IllegalStateException("This Subscription was initiated using an item list, use getItems instead of using getItemGroup");
    }
    
    
    return ((NameDescriptor) this.itemDescriptor).getOriginal();
  }
  
  
  
  /**
   * Setter method that sets the "Item Group" to be subscribed to through 
   * Lightstreamer Server. <BR>
   * Any call to this method will override any "Item List" or "Item Group"
   * previously specified.
   * 
   * @lifecycle This method can only be called while the Subscription
   * instance is in its "inactive" state.
   * 
   * @throws IllegalStateException if the Subscription is currently 
   * "active".
   * 
   * @param groupName A String to be expanded into an item list by the
   * Metadata Adapter. 
   */
  public synchronized void setItemGroup(@Nullable String groupName) {
    this.notAliveCheck();

    this.itemDescriptor = groupName == null ? null : new NameDescriptor(groupName);
    
    debugDescriptor("Item group assigned: ", this.itemDescriptor);
  }
  /**
   * Inquiry method that can be used to read the "Field List" specified for this Subscription.
   *
   * @lifecycle  This method can only be called if the Subscription has been initialized 
   * using a "Field List".
   * @throws IllegalStateException if the Subscription was initialized with a "Field Schema" 
   * or was not initialized at all.
   * @return the "Field List" to be subscribed to through the server.
   */
  @Nonnull 
  public synchronized String[] getFields() {
    if (this.fieldDescriptor == null) {
      throw new IllegalStateException(NO_SCHEMA_NOR_LIST);
    } else if (this.fieldDescriptor instanceof NameDescriptor) {
      throw new IllegalStateException("This Subscription was initiated using a field schema, use getFieldSchema instead of using getFields");
    }
    
    return ((ListDescriptor) this.fieldDescriptor).getOriginal();
  }
  
  /**
   * Setter method that sets the "Field List" to be subscribed to through 
   * Lightstreamer Server. <BR>
   * Any call to this method will override any "Field List" or "Field Schema"
   * previously specified.
   * 
   * @lifecycle This method can only be called while the Subscription
   * instance is in its "inactive" state.
   * 
   * @throws IllegalArgumentException if any of the field names in the list
   * contains a space or is empty/null.
   * @throws IllegalStateException if the Subscription is currently 
   * "active".
   * 
   * @param fields an array of fields to be subscribed to through the server. 
   */
  public synchronized void setFields(@Nullable String[] fields) {
    this.notAliveCheck();
    
    ListDescriptor.checkFieldNames(fields,"A field");
    
    Descriptor tmp = fields == null ? null : new ListDescriptor(fields);
    
    this.setSchemaReadMetapushFields(tmp);
    
    this.fieldDescriptor = tmp;
    
    debugDescriptor("Field list assigned: ", fieldDescriptor);
  }
    
  /**
   * Inquiry method that can be used to read the field schema specified for this Subscription.
   *
   * @lifecycle This method can only be called if the Subscription has been initialized 
   * using a "Field Schema"
   * @throws IllegalStateException if the Subscription was initialized with a "Field List" or was 
   * not initialized at all.
   * @return the "Field Schema" to be subscribed to through the server.
   */
  @Nonnull 
  public synchronized String getFieldSchema() {
    if (this.fieldDescriptor == null) {
      throw new IllegalStateException(NO_SCHEMA_NOR_LIST);
    } else if (this.fieldDescriptor instanceof ListDescriptor) {
      throw new IllegalStateException("This Subscription was initiated using a field schema, use getFieldSchema instead of using getFields");
    }
    
    return ((NameDescriptor) this.fieldDescriptor).getOriginal();
  }
  
  /**
   * Setter method that sets the "Field Schema" to be subscribed to through 
   * Lightstreamer Server. <BR>
   * Any call to this method will override any "Field List" or "Field Schema"
   * previously specified.
   * 
   * @lifecycle This method can only be called while the Subscription
   * instance is in its "inactive" state.
   * 
   * @throws IllegalStateException if the Subscription is currently 
   * "active".
   * 
   * @param schemaName A String to be expanded into a field list by the
   * Metadata Adapter. 
   */
  public synchronized void setFieldSchema(@Nullable String schemaName) {
    this.notAliveCheck();
    
    this.fieldDescriptor = schemaName == null ? null : new NameDescriptor(schemaName);
    
    debugDescriptor("Field group assigned: ", this.fieldDescriptor);
  }

  /**
   * Inquiry method that can be used to read the buffer size, configured though
   * {@link #setRequestedBufferSize}, to be requested to the Server for 
   * this Subscription.
   * 
   * @lifecycle This method can be called at any time.
   * 
   * @return  An integer number, representing the buffer size to be requested to the server,
   * or the string "unlimited", or null.
   */
  @Nullable 
  public synchronized String getRequestedBufferSize() {
    if (this.requestedBufferSize == BUFFER_NULL) {
      return null;
    } else if(this.requestedBufferSize == BUFFER_UNLIMITED) {
      return "unlimited";
    }
    return String.valueOf(this.requestedBufferSize);
  }
  /**
   * Setter method that sets the length to be requested to Lightstreamer
   * Server for the internal queuing buffers for the items in the Subscription.
   * A Queuing buffer is used by the Server to accumulate a burst
   * of updates for an item, so that they can all be sent to the client,
   * despite of bandwidth or frequency limits. It can be used only when the
   * subscription mode is MERGE or DISTINCT and unfiltered dispatching has
   * not been requested. Note that the Server may pose an upper limit on the
   * size of its internal buffers.
   *
   * @default null, meaning to lean on the Server default based on the subscription
   * mode. This means that the buffer size will be 1 for MERGE 
   * subscriptions and "unlimited" for DISTINCT subscriptions. See 
   * the "General Concepts" document for further details.
   *
   * @lifecycle This method can only be called while the Subscription
   * instance is in its "inactive" state.
   * 
   * @throws IllegalStateException if the Subscription is currently 
   * "active".
   * @throws IllegalArgumentException if the specified value is not
   * null nor "unlimited" nor a valid positive integer number.
   *
   * @param size  An integer number, representing the length of the internal queuing buffers
   * to be used in the Server. If the string "unlimited" is supplied, then no buffer
   * size limit is requested (the check is case insensitive). It is also possible
   * to supply a null value to stick to the Server default (which currently
   * depends on the subscription mode).
   *
   * @see Subscription#setRequestedMaxFrequency(String)
   */
  public synchronized void setRequestedBufferSize(@Nullable String size) {
    this.notAliveCheck();
    
    //can be
    //null -> make it -1
    //unlimited -> make it 0
    //>0 integer
    
    if (size == null) {
      this.requestedBufferSize = BUFFER_NULL;
    } else {
      size = new String(size);
      size = toLowerCase(size);
      if (size.equals("unlimited")) {
        this.requestedBufferSize = BUFFER_UNLIMITED;
      } else {
        
        int tmp;
        try {
          tmp = Integer.parseInt(size);
        } catch (NumberFormatException nfe) {
          throw new IllegalArgumentException(MAX_BUF_EXC,nfe); 
        }
        
        if (!Number.isPositive(tmp, Number.DONT_ACCEPT_ZERO)) {
          throw new IllegalArgumentException(MAX_BUF_EXC); 
        }
        this.requestedBufferSize = tmp;
      }
    }
    
    if (log.isDebugEnabled()) {
      log.debug("Requested Buffer Size assigned: "+ this.requestedBufferSize);
    }
  }
  /**
   * Inquiry method that can be used to read the snapshot preferences, 
   * configured through {@link #setRequestedSnapshot(String)}, to be requested 
   * to the Server for this Subscription.
   * 
   * @lifecycle This method can be called at any time.
   * 
   * @return  "yes", "no", null, or an integer number.
   */
  @Nullable 
  public synchronized String getRequestedSnapshot() {
    return this.isRequiredSnapshot;
  }
  /**
   * Setter method that enables/disables snapshot delivery request for the
   * items in the Subscription. The snapshot can be requested only if the
   * Subscription mode is MERGE, DISTINCT or COMMAND.
   *
   * @default "yes" if the Subscription mode is not "RAW",
   * null otherwise.
   * 
   * @lifecycle This method can only be called while the Subscription
   * instance is in its "inactive" state.
   * 
   * @throws IllegalStateException if the Subscription is currently 
   * "active".
   * @throws IllegalArgumentException if the specified value is not
   * "yes" nor "no" nor null nor a valid integer positive number.
   * @throws IllegalArgumentException if the specified value is not
   * compatible with the mode of the Subscription: 
   * <ul>
   *  <li>In case of a RAW Subscription only null is a valid value;</li>
   *  <li>In case of a non-DISTINCT Subscription only null "yes" and "no" are
   *  valid values.</li>
   * </ul>
   *
   * @param required "yes"/"no" to request/not request snapshot
   * delivery (the check is case insensitive). If the Subscription mode is 
   * DISTINCT, instead of "yes", it is also possible to supply an integer number, 
   * to specify the requested length of the snapshot (though the length of 
   * the received snapshot may be less than requested, because of insufficient 
   * data or server side limits);
   * passing "yes"  means that the snapshot length should be determined
   * only by the Server. Null is also a valid value; if specified, no snapshot 
   * preference will be sent to the server that will decide itself whether
   * or not to send any snapshot. 
   * 
   * @see ItemUpdate#isSnapshot
   */
  public synchronized void setRequestedSnapshot(@Nullable String required) {
    this.notAliveCheck();

    if (required != null) {
      required = toLowerCase(required);
      
      if (required.equals("no")) {  
        //the string "no" - admitted for all modes     
      } else if (this.mode.equals(Constants.RAW)) {
        //RAW does not accept anything else
        throw new IllegalStateException(RAW_NO_SNAPSHOT);
      
      } else if (required.equals("yes")) {
        //the string "yes" - admitted for MERGE, DISTINCT, COMMAND modes
      } else if (Number.isNumber(required)) {
        //a String to be parsed as a >0 int - admitted for DISTINCT mode
        
        if (!this.mode.equals(Constants.DISTINCT)) {
          throw new IllegalStateException(NUMERIC_DISTINCT_ONLY);
        }
        
        int tmp;
        try {
          tmp = Integer.parseInt(required);
        } catch(NumberFormatException nfe) {
          throw new IllegalArgumentException(REQ_SNAP_EXC,nfe);
        }
        
        if (!Number.isPositive(tmp, Number.DONT_ACCEPT_ZERO)) {
          throw new IllegalArgumentException(REQ_SNAP_EXC); 
        }
      
      }  else {
        throw new IllegalArgumentException(REQ_SNAP_EXC);
      }
    }
    
    this.isRequiredSnapshot = required;
    
    if (log.isDebugEnabled()) {
      log.debug("Snapshot Required assigned: "+ this.isRequiredSnapshot);
    }
  }
  /**
   * Inquiry method that can be used to read the max frequency, configured
   * through {@link #setRequestedMaxFrequency(String)}, to be requested to the 
   * Server for this Subscription.
   * 
   * @lifecycle This method can be called at any time.
   * 
   * @return  A decimal number, representing the max frequency to be requested to the server
   * (expressed in updates per second), or the strings "unlimited" or "unfiltered", or null.
   */
  @Nullable 
  public synchronized String getRequestedMaxFrequency() {
    if (this.requestedMaxFrequency == FREQUENCY_UNFILTERED) {
      return "unfiltered";
    } else if (this.requestedMaxFrequency == FREQUENCY_NULL) {
      return null;
    } else if (this.requestedMaxFrequency == FREQUENCY_UNLIMITED) {
      return "unlimited";
    } else {
      return String.valueOf(this.requestedMaxFrequency);
    }
  }
  
  /**
   * Setter method that sets the maximum update frequency to be requested to
   * Lightstreamer Server for all the items in the Subscription. It can
   * be used only if the Subscription mode is MERGE, DISTINCT or
   * COMMAND (in the latter case, the frequency limitation applies to the
   * UPDATE events for each single key). For Subscriptions with two-level behavior
   * (see {@link Subscription#setCommandSecondLevelFields(String[])} and {@link Subscription#setCommandSecondLevelFieldSchema(String)})
   * , the specified frequency limit applies to both first-level and second-level items. <BR>
   * Note that frequency limits on the items can also be set on the
   * server side and this request can only be issued in order to furtherly
   * reduce the frequency, not to rise it beyond these limits. <BR>
   * This method can also be used to request unfiltered dispatching
   * for the items in the Subscription. However, unfiltered dispatching
   * requests may be refused if any frequency limit is posed on the server
   * side for some item.
   *
   * @general_edition_note A further global frequency limit could also be imposed by the Server,
   * depending on Edition and License Type; this specific limit also applies to RAW mode and
   * to unfiltered dispatching.
   * To know what features are enabled by your license, please see the License tab of the
   * Monitoring Dashboard (by default, available at /dashboard).
   *
   * @default null, meaning to lean on the Server default based on the subscription
   * mode. This consists, for all modes, in not applying any frequency 
   * limit to the subscription (the same as "unlimited"); see the "General Concepts"
   * document for further details.
   *
   * @lifecycle This method can can be called at any time with some
   * differences based on the Subscription status:
   * <ul>
   * <li>If the Subscription instance is in its "inactive" state then
   * this method can be called at will.</li>
   * <li>If the Subscription instance is in its "active" state then the method
   * can still be called unless the current value is "unfiltered" or the
   * supplied value is "unfiltered" or null.
   * If the Subscription instance is in its "active" state and the
   * connection to the server is currently open, then a request
   * to change the frequency of the Subscription on the fly is sent to the server.</li>
   * </ul>
   * 
   * @throws IllegalStateException if the Subscription is currently 
   * "active" and the current value of this property is "unfiltered".
   * @throws IllegalStateException if the Subscription is currently 
   * "active" and the given parameter is null or "unfiltered".
   * @throws IllegalArgumentException if the specified value is not
   * null nor one of the special "unlimited" and "unfiltered" values nor
   * a valid positive number.
   *
   * @param freq  A decimal number, representing the maximum update frequency (expressed in updates
   * per second) for each item in the Subscription; for instance, with a setting
   * of 0.5, for each single item, no more than one update every 2 seconds
   * will be received. If the string "unlimited" is supplied, then no frequency
   * limit is requested. It is also possible to supply the string 
   * "unfiltered", to ask for unfiltered dispatching, if it is allowed for the 
   * items, or a null value to stick to the Server default (which currently
   * corresponds to "unlimited").
   * The check for the string constants is case insensitive.
   */
  public synchronized void setRequestedMaxFrequency(@Nullable String freq) {
    //can be:
    //null -> do not send to the server -> -2
    //unfiltered -> -1
    //unlimited -> 0
    //>0 double
    
    final double prev =  this.requestedMaxFrequency;

    if (freq != null) {
      freq = freq.toLowerCase();
    }

    //double orig = this.requestedMaxFrequency;
    if (this.isActive()) {
      if (freq == null) {
        //null was given
        throw new IllegalStateException(ILLEGAL_FREQ_EXC);
      } else if (freq.equals("unfiltered") || this.requestedMaxFrequency == FREQUENCY_UNFILTERED) {
        //currently unfiltered or unfiltered was given
        throw new IllegalStateException(ILLEGAL_FREQ_EXC);
      }
    }
    
    if (freq == null) {
      this.requestedMaxFrequency = FREQUENCY_NULL;
    } else if (freq.equals("unfiltered")) {
      this.requestedMaxFrequency = FREQUENCY_UNFILTERED;
    } else if(freq.equals("unlimited")) {
      this.requestedMaxFrequency = FREQUENCY_UNLIMITED;
    } else {
      double tmp;
      try {
        tmp = Double.parseDouble(freq);
      } catch(NumberFormatException nfe) {
        throw new IllegalArgumentException(MAX_FREQ_EXC,nfe);
      }
      
      if (!Number.isPositive(tmp, Number.DONT_ACCEPT_ZERO)) {
        throw new IllegalArgumentException(MAX_FREQ_EXC);
      }
      
      this.requestedMaxFrequency = tmp;
    }
    
    if (log.isDebugEnabled()) {
      log.debug("Requested Max Frequency assigned: " +  this.requestedMaxFrequency);
    }
    
    if (sessionThread == null) { //onAdd event not yet fired, exit: the frequency will be correct from the beginning
      return;
    }
    
    final String frequency = freq;
    final Subscription that = this;
    sessionThread.queue(new Runnable() {

      @Override
      public void run() {
        //XXX when this executes the frequency might be changed again, in that case this request will be aborted. We might skip a request altogether when that's the case  
        
        if (is(WAITING) || is(SUBSCRIBING) || is(PUSHING)) {
          //our subscription request was already forwarded to the Session machine, let's send a follow-up
          if (prev != requestedMaxFrequency) {
            //the frequency actually changed
            manager.changeFrequency(that);
            
            //might happen that we send a useless request
            
            if (behavior.equals(MULTIMETAPUSH)) {
              subTables.forEachElement(new Matrix.ElementCallback<Integer, String, Subscription>() {
                @Override
                public boolean onElement(Subscription secondLevelSubscription, Integer row, String col) {
                  secondLevelSubscription.setRequestedMaxFrequency(frequency);
                  return false;
                }
              });
              
            }
            
          }
        }
      }
     
    });
    
  }
  /**
   * Inquiry method that can be used to read the selector name  
   * specified for this Subscription through {@link #setSelector(String)}.
   * 
   * @lifecycle This method can be called at any time.
   * 
   * @return the name of the selector.
   */
  @Nullable 
  public synchronized String getSelector() {
    return this.selector;
  }
  /**
   * Setter method that sets the selector name for all the items in the
   * Subscription. The selector is a filter on the updates received. It is
   * executed on the Server and implemented by the Metadata Adapter.
   *
   * @default null (no selector).
   *
   * @lifecycle This method can only be called while the Subscription
   * instance is in its "inactive" state.
   * 
   * @throws IllegalStateException if the Subscription is currently 
   * "active".
   *
   * @param selector name of a selector, to be recognized by the
   * Metadata Adapter, or null to unset the selector.
   */
  public synchronized void setSelector(@Nullable String selector) {
    this.notAliveCheck();
    
    this.selector = selector;
    if (log.isDebugEnabled()) {
      log.debug("Selector assigned: "+selector);
    }
  }
  /**
   * Returns the position of the "command" field in a COMMAND Subscription. <BR>
   * This method can only be used if the Subscription mode is COMMAND and the Subscription 
   * was initialized using a "Field Schema".
   *
   * @lifecycle This method can be called at any time after the first 
   * {@link SubscriptionListener#onSubscription} event.
   * @throws IllegalStateException if the Subscription mode is not COMMAND or if the 
   * {@link SubscriptionListener#onSubscription} event for this Subscription was not 
   * yet fired.
   * @throws IllegalStateException if a "Field List" was specified.
   * @return the 1-based position of the "command" field within the "Field Schema".
   */
  public synchronized int getCommandPosition() {
    this.commandCheck();
    
    if (this.fieldDescriptor instanceof ListDescriptor) {
      throw new IllegalStateException("This Subscription was initiated using a field list, command field is always 'command'");
    }
    
    if (this.commandCode == -1) {
      throw new IllegalStateException("The position of the command field is currently unknown");
    }

    return this.commandCode;
  }
  
  /**
   * Returns the position of the "key" field in a COMMAND Subscription. <BR>
   * This method can only be used if the Subscription mode is COMMAND
   * and the Subscription was initialized using a "Field Schema".
   *  
   * @lifecycle This method can be called at any time.
   * 
   * @throws IllegalStateException if the Subscription mode is not 
   * COMMAND or if the {@link SubscriptionListener#onSubscription} event for this Subscription
   * was not yet fired.
   * 
   * @return the 1-based position of the "key" field within the "Field Schema".
   */
  public synchronized int getKeyPosition() {
    this.commandCheck();
    
    if (this.fieldDescriptor instanceof ListDescriptor) {
      throw new IllegalStateException("This Subscription was initiated using a field list, key field is always 'key'");
    }
    
    if (this.keyCode == -1) {
      throw new IllegalStateException("The position of the key field is currently unknown");
    }
    
    return this.keyCode;
  }
  /**
   * Inquiry method that can be used to read the second-level Data Adapter name configured 
   * through {@link #setCommandSecondLevelDataAdapter(String)}.
   *
   * @lifecycle This method can be called at any time.
   * @throws IllegalStateException if the Subscription mode is not COMMAND
   * @return the name of the second-level Data Adapter.
   * @see #setCommandSecondLevelDataAdapter(String)
   */
  @Nullable 
  public synchronized String getCommandSecondLevelDataAdapter() {
    return this.underDataAdapter;
  }
  
  /**
   * Setter method that sets the name of the second-level Data Adapter (within 
   * the Adapter Set used by the current session) that supplies all the 
   * second-level items. <BR>
   * All the possible second-level items should be supplied in "MERGE" mode 
   * with snapshot available. <BR> 
   * The Data Adapter name is configured on the server side through the 
   * "name" attribute of the &lt;data_provider&gt; element, in the "adapters.xml" 
   * file that defines the Adapter Set (a missing attribute configures the 
   * "DEFAULT" name).
   * 
   * @default The default Data Adapter for the Adapter Set,
   * configured as "DEFAULT" on the Server.
   *
   * @lifecycle This method can only be called while the Subscription
   * instance is in its "inactive" state.
   *
   * @throws IllegalStateException if the Subscription is currently 
   * "active".
   * @throws IllegalStateException if the Subscription mode is not "COMMAND".
   *
   * @param dataAdapter the name of the Data Adapter. A null value 
   * is equivalent to the "DEFAULT" name.
   *  
   * @see Subscription#setCommandSecondLevelFields(String[])
   * @see Subscription#setCommandSecondLevelFieldSchema(String)
   */
  public synchronized void setCommandSecondLevelDataAdapter(@Nullable String dataAdapter) {
    this.notAliveCheck();
    this.secondLevelCheck();
    
    this.underDataAdapter = dataAdapter;
    if (log.isDebugEnabled()) {
      log.debug("Second level Data Adapter Set assigned: " + dataAdapter);
    }
  }
  /**
   * Inquiry method that can be used to read the "Field List" specified for second-level 
   * Subscriptions.
   *
   * @lifecycle This method can only be called if the second-level of this Subscription 
   * has been initialized using a "Field List"
   * @throws IllegalStateException if the Subscription was initialized with a "Field Schema" 
   * or was not initialized at all.
   * @throws IllegalStateException if the Subscription mode is not COMMAND
   * @return the list of fields to be subscribed to through the server.
   * @see Subscription#setCommandSecondLevelFields(String[])
   */
  @Nonnull 
  public synchronized String[] getCommandSecondLevelFields() {
    if (this.subFieldDescriptor == null) {
      throw new IllegalStateException(NO_SUB_SCHEMA_NOR_LIST);
    } else if (this.subFieldDescriptor instanceof NameDescriptor) {
      throw new IllegalStateException("The second level of this Subscription was initiated using a field schema, use getCommandSecondLevelFieldSchema instead of using getCommandSecondLevelFields");
    }
    
    return ((ListDescriptor) this.subFieldDescriptor).getOriginal();

  }
  
  /**
   * Setter method that sets the "Field List" to be subscribed to through 
   * Lightstreamer Server for the second-level items. It can only be used on
   * COMMAND Subscriptions. <BR>
   * Any call to this method will override any "Field List" or "Field Schema"
   * previously specified for the second-level. <BR>
   * Calling this method enables the two-level behavior:<BR>
   * in synthesis, each time a new key is received on the COMMAND Subscription, 
   * the key value is treated as an Item name and an underlying Subscription for
   * this Item is created and subscribed to automatically, to feed fields specified
   * by this method. This mono-item Subscription is specified through an "Item List"
   * containing only the Item name received. As a consequence, all the conditions
   * provided for subscriptions through Item Lists have to be satisfied. The item is 
   * subscribed to in "MERGE" mode, with snapshot request and with the same maximum
   * frequency setting as for the first-level items (including the "unfiltered" 
   * case). All other Subscription properties are left as the default. When the 
   * key is deleted by a DELETE command on the first-level Subscription, the 
   * associated second-level Subscription is also unsubscribed from. <BR> 
   * Specifying null as parameter will disable the two-level behavior.
   *       
   * @lifecycle This method can only be called while the Subscription
   * instance is in its "inactive" state.
   * 
   * @throws IllegalArgumentException if any of the field names in the "Field List"
   * contains a space or is empty/null.
   * @throws IllegalStateException if the Subscription is currently 
   * "active".
   * @throws IllegalStateException if the Subscription mode is not "COMMAND".
   * 
   * @param fields An array of Strings containing a list of fields to
   * be subscribed to through the server. <BR>
   * Ensure that no name conflict is generated between first-level and second-level
   * fields. In case of conflict, the second-level field will not be accessible
   * by name, but only by position.
   * 
   * @see Subscription#setCommandSecondLevelFieldSchema(String)
   */
  public synchronized void setCommandSecondLevelFields(@Nullable String[] fields) {
    this.notAliveCheck();
    this.secondLevelCheck();
    
    ListDescriptor.checkFieldNames(fields,"A field");
    
    this.subFieldDescriptor = fields == null ? null : new ListDescriptor(fields);
    
    this.prepareSecondLevel();
    
    debugDescriptor("Second level field list assigned: ", this.subFieldDescriptor);
  }

  /**
   * Inquiry method that can be used to read the "Field Schema" specified for second-level 
   * Subscriptions.
   *
   * @lifecycle This method can only be called if the second-level of this Subscription has 
   * been initialized using a "Field Schema".
   * @throws IllegalStateException if the Subscription was initialized with a "Field List" or 
   * was not initialized at all.
   * @throws IllegalStateException if the Subscription mode is not COMMAND
   * @return the "Field Schema" to be subscribed to through the server.
   * @see Subscription#setCommandSecondLevelFieldSchema(String)
   */
  @Nonnull 
  public synchronized String getCommandSecondLevelFieldSchema() {
    if (this.subFieldDescriptor == null) {
      throw new IllegalStateException(NO_SUB_SCHEMA_NOR_LIST);
    } else if (this.subFieldDescriptor instanceof ListDescriptor) {
      throw new IllegalStateException("The second level of this Subscription was initiated using a field list, use getCommandSecondLevelFields instead of using getCommandSecondLevelFieldSchema");
    }
    
    return ((NameDescriptor) this.subFieldDescriptor).getOriginal();
  }
  
  /**
   * Setter method that sets the "Field Schema" to be subscribed to through 
   * Lightstreamer Server for the second-level items. It can only be used on
   * COMMAND Subscriptions. <BR>
   * Any call to this method will override any "Field List" or "Field Schema"
   * previously specified for the second-level. <BR>
   * Calling this method enables the two-level behavior:<BR>
   * in synthesis, each time a new key is received on the COMMAND Subscription, 
   * the key value is treated as an Item name and an underlying Subscription for
   * this Item is created and subscribed to automatically, to feed fields specified
   * by this method. This mono-item Subscription is specified through an "Item List"
   * containing only the Item name received. As a consequence, all the conditions
   * provided for subscriptions through Item Lists have to be satisfied. The item is 
   * subscribed to in "MERGE" mode, with snapshot request and with the same maximum
   * frequency setting as for the first-level items (including the "unfiltered" 
   * case). All other Subscription properties are left as the default. When the 
   * key is deleted by a DELETE command on the first-level Subscription, the 
   * associated second-level Subscription is also unsubscribed from. <BR>
   * Specify null as parameter will disable the two-level behavior.
   * 
   * @lifecycle This method can only be called while the Subscription
   * instance is in its "inactive" state.
   * 
   * @throws IllegalStateException if the Subscription is currently 
   * "active".
   * @throws IllegalStateException if the Subscription mode is not "COMMAND".
   * 
   * @param schemaName A String to be expanded into a field list by the
   * Metadata Adapter. 
   * 
   * @see Subscription#setCommandSecondLevelFields
   */
  public synchronized void setCommandSecondLevelFieldSchema(@Nullable String schemaName) {
    this.notAliveCheck();
    this.secondLevelCheck();
    
    this.subFieldDescriptor = schemaName == null ? null : new NameDescriptor(schemaName);
    
    this.prepareSecondLevel();
    
    debugDescriptor("Second level field schema assigned: ", this.subFieldDescriptor);
  }
  
  /**
   * Returns the latest value received for the specified item/field pair. <BR>
   * It is suggested to consume real-time data by implementing and adding
   * a proper {@link SubscriptionListener} rather than probing this method. <BR>
   * In case of COMMAND Subscriptions, the value returned by this
   * method may be misleading, as in COMMAND mode all the keys received, being
   * part of the same item, will overwrite each other; for COMMAND Subscriptions,
   * use {@link #getCommandValue} instead. <BR>
   * Note that internal data is cleared when the Subscription is 
   * unsubscribed from.
   *
   * @lifecycle This method can be called at any time; if called 
   * to retrieve a value that has not been received yet, then it will return null. 
   * @throws IllegalArgumentException if an invalid item name or field name is specified.
   * @param itemName an item in the configured "Item List"
   * @param fieldName a item in the configured "Field List"
   * @return the current value for the specified field of the specified item
   * (possibly null), or null if no value has been received yet.
   */
  @Nullable 
  public synchronized String getValue(@Nonnull String itemName, @Nonnull String fieldName) {
    return this.oldValuesByItem.get(this.toItemPos(itemName),this.toFieldPos(fieldName));
  }
  
  
  /**
   * Returns the latest value received for the specified item/field pair. <BR>
   * It is suggested to consume real-time data by implementing and adding
   * a proper {@link SubscriptionListener} rather than probing this method. <BR>
   * In case of COMMAND Subscriptions, the value returned by this
   * method may be misleading, as in COMMAND mode all the keys received, being
   * part of the same item, will overwrite each other; for COMMAND Subscriptions,
   * use {@link #getCommandValue} instead. <BR>
   * Note that internal data is cleared when the Subscription is 
   * unsubscribed from.
   *
   * @lifecycle This method can be called at any time; if called 
   * to retrieve a value that has not been received yet, then it will return null. 
   * @throws IllegalArgumentException if the specified item position or field position is 
   * out of bounds.
   * @param itemPos the 1-based position of an item within the configured "Item Group"
   * or "Item List" 
   * @param fieldPos the 1-based position of a field within the configured "Field Schema"
   * or "Field List"
   * @return the current value for the specified field of the specified item
   * (possibly null), or null if no value has been received yet.
   */
  @Nullable 
  public synchronized String getValue(int itemPos, int fieldPos) {
    this.verifyItemPos(itemPos);
    this.verifyFieldPos(fieldPos, false);
    return this.oldValuesByItem.get(itemPos,fieldPos);
  }
  /**
   * Returns the latest value received for the specified item/field pair. <BR>
   * It is suggested to consume real-time data by implementing and adding
   * a proper {@link SubscriptionListener} rather than probing this method. <BR>
   * In case of COMMAND Subscriptions, the value returned by this
   * method may be misleading, as in COMMAND mode all the keys received, being
   * part of the same item, will overwrite each other; for COMMAND Subscriptions,
   * use {@link #getCommandValue} instead. <BR>
   * Note that internal data is cleared when the Subscription is 
   * unsubscribed from. 
   *
   * @lifecycle This method can be called at any time; if called 
   * to retrieve a value that has not been received yet, then it will return null. 
   * @throws IllegalArgumentException if an invalid item name is specified.
   * @throws IllegalArgumentException if the specified field position is out of bounds.
   * @param itemName an item in the configured "Item List"
   * @param fieldPos the 1-based position of a field within the configured "Field Schema"
   * or "Field List"
   * @return the current value for the specified field of the specified item
   * (possibly null), or null if no value has been received yet.
   */
  @Nullable 
  public synchronized String getValue(@Nonnull String itemName, int fieldPos) {
    this.verifyFieldPos(fieldPos, false);
    return this.oldValuesByItem.get(this.toItemPos(itemName),fieldPos);
  }
  /**
   * Returns the latest value received for the specified item/field pair. <BR>
   * It is suggested to consume real-time data by implementing and adding
   * a proper {@link SubscriptionListener} rather than probing this method. <BR>
   * In case of COMMAND Subscriptions, the value returned by this
   * method may be misleading, as in COMMAND mode all the keys received, being
   * part of the same item, will overwrite each other; for COMMAND Subscriptions,
   * use {@link #getCommandValue} instead. <BR>
   * Note that internal data is cleared when the Subscription is 
   * unsubscribed from. 
   *
   * @lifecycle This method can be called at any time; if called 
   * to retrieve a value that has not been received yet, then it will return null. 
   * @throws IllegalArgumentException if an invalid field name is specified.
   * @throws IllegalArgumentException if the specified item position is out of bounds.
   * @param itemPos the 1-based position of an item within the configured "Item Group"
   * or "Item List"
   * @param fieldName a item in the configured "Field List"
   * @return the current value for the specified field of the specified item
   * (possibly null), or null if no value has been received yet.
   */
  @Nullable 
  public synchronized String getValue(int itemPos, @Nonnull String fieldName) {
    this.verifyItemPos(itemPos);
    return this.oldValuesByItem.get(itemPos,this.toFieldPos(fieldName));
  }
  /**
   * Returns the latest value received for the specified item/key/field combination. 
   * This method can only be used if the Subscription mode is COMMAND. 
   * Subscriptions with two-level behavior
   * are also supported, hence the specified field 
   * (see {@link Subscription#setCommandSecondLevelFields(String[])} and {@link Subscription#setCommandSecondLevelFieldSchema(String)})
   * can be either a first-level or a second-level one. <BR>
   * It is suggested to consume real-time data by implementing and adding a proper 
   * {@link SubscriptionListener} rather than probing this method. <BR>
   * Note that internal data is cleared when the Subscription is unsubscribed from.
   *
   * @param itemName an item in the configured "Item List"
   * @param keyValue the value of a key received on the COMMAND subscription.
   * @param fieldName a item in the configured "Field List"
   * @throws IllegalArgumentException if an invalid item name or field name is specified.
   * @throws IllegalStateException if the Subscription mode is not COMMAND.
   * @return the current value for the specified field of the specified key within the 
   * specified item (possibly null), or null if the specified key has not been added yet 
   * (note that it might have been added and then deleted).
   */
  @Nullable 
  public synchronized String getCommandValue(@Nonnull String itemName, @Nonnull String keyValue, @Nonnull String fieldName) {
    this.commandCheck();
    
    String mapKey = this.toItemPos(itemName) + " " + keyValue;
    return this.oldValuesByKey.get(mapKey,this.toFieldPos(fieldName));
  }
  
  /**
   * Returns the latest value received for the specified item/key/field combination. 
   * This method can only be used if the Subscription mode is COMMAND. 
   * Subscriptions with two-level behavior
   * (see {@link Subscription#setCommandSecondLevelFields(String[])} and {@link Subscription#setCommandSecondLevelFieldSchema(String)})
   * are also supported, hence the specified field 
   * can be either a first-level or a second-level one. <BR>
   * It is suggested to consume real-time data by implementing and adding a proper 
   * {@link SubscriptionListener} rather than probing this method. <BR>
   * Note that internal data is cleared when the Subscription is unsubscribed from.
   *
   * @param itemPos the 1-based position of an item within the configured "Item Group"
   * or "Item List" 
   * @param keyValue the value of a key received on the COMMAND subscription.
   * @param fieldPos the 1-based position of a field within the configured "Field Schema"
   * or "Field List"
   * @throws IllegalArgumentException if the specified item position or field position is 
   * out of bounds.
   * @throws IllegalStateException if the Subscription mode is not COMMAND.
   * @return the current value for the specified field of the specified key within the 
   * specified item (possibly null), or null if the specified key has not been added yet 
   * (note that it might have been added and then deleted).
   */
  @Nullable 
  public synchronized String getCommandValue(int itemPos, @Nonnull String keyValue, int fieldPos) {
    this.commandCheck();
    
    this.verifyItemPos(itemPos);
    this.verifyFieldPos(fieldPos, true);
    
    String mapKey = itemPos + " " + keyValue;
    return this.oldValuesByKey.get(mapKey,fieldPos);

  }
  
  /**
   * Returns the latest value received for the specified item/key/field combination. 
   * This method can only be used if the Subscription mode is COMMAND. 
   * Subscriptions with two-level behavior
   * (see {@link Subscription#setCommandSecondLevelFields(String[])} and {@link Subscription#setCommandSecondLevelFieldSchema(String)})
   * are also supported, hence the specified field 
   * can be either a first-level or a second-level one. <BR>
   * It is suggested to consume real-time data by implementing and adding a proper 
   * {@link SubscriptionListener} rather than probing this method. <BR>
   * Note that internal data is cleared when the Subscription is unsubscribed from.
   *
   * @param itemPos the 1-based position of an item within the configured "Item Group"
   * or "Item List"
   * @param keyValue the value of a key received on the COMMAND subscription.
   * @param fieldName a item in the configured "Field List"
   * @throws IllegalArgumentException if an invalid field name is specified.
   * @throws IllegalArgumentException if the specified item position is out of bounds.
   * @throws IllegalStateException if the Subscription mode is not COMMAND.
   * @return the current value for the specified field of the specified key within the 
   * specified item (possibly null), or null if the specified key has not been added yet 
   * (note that it might have been added and then deleted).
   */
  @Nullable 
  public synchronized String getCommandValue(int itemPos, @Nonnull String keyValue, @Nonnull String fieldName) {
    this.commandCheck();

    this.verifyItemPos(itemPos);
    
    String mapKey = itemPos + " " + keyValue;
    return this.oldValuesByKey.get(mapKey,this.toFieldPos(fieldName));
  }
  
  /**
   * Returns the latest value received for the specified item/key/field combination. 
   * This method can only be used if the Subscription mode is COMMAND. 
   * Subscriptions with two-level behavior
   * (see {@link Subscription#setCommandSecondLevelFields(String[])} and {@link Subscription#setCommandSecondLevelFieldSchema(String)})
   * are also supported, hence the specified field 
   * can be either a first-level or a second-level one. <BR>
   * It is suggested to consume real-time data by implementing and adding a proper 
   * {@link SubscriptionListener} rather than probing this method. <BR>
   * Note that internal data is cleared when the Subscription is unsubscribed from.
   *
   * @param itemName an item in the configured "Item List" 
   * @param keyValue the value of a key received on the COMMAND subscription.
   * @param fieldPos the 1-based position of a field within the configured "Field Schema"
   * or "Field List"
   * @throws IllegalArgumentException if an invalid item name is specified.
   * @throws IllegalArgumentException if the specified field position is out of bounds.
   * @return the current value for the specified field of the specified key within the 
   * specified item (possibly null), or null if the specified key has not been added yet 
   * (note that it might have been added and then deleted).
   */
  @Nullable 
  public synchronized String getCommandValue(@Nonnull String itemName, @Nonnull String keyValue, int fieldPos) {
    this.commandCheck();
    
    this.verifyFieldPos(fieldPos, true);
    
    String mapKey = this.toItemPos(itemName) + " " + keyValue;
    return this.oldValuesByKey.get(mapKey,fieldPos);
  }
  
//////////////////////////Lifecycle  
 
  void notAliveCheck() {
    if (this.isActive()) {
      throw new IllegalStateException(IS_ALIVE);
    }
  }
  
  void isAliveCheck() {
    if (!this.isActive()) {
      throw new IllegalStateException(NOT_ALIVE);
    }
  }
  
  void setActive() {
    this.notAliveCheck();
    if (this.itemDescriptor == null) {
      throw new IllegalArgumentException(NO_ITEMS);
    }
    if (this.fieldDescriptor == null) {
      throw new IllegalArgumentException(NO_FIELDS);
    }
    
    this.isActive = true;
  }
  
  void setInactive() {
    this.isAliveCheck();
    this.isActive = false;
  }
  
  int getSubscriptionId() {
    return this.subscriptionId;
  }
  
  
  

  private boolean is(String what) {
    return this.tablePhaseType.equals(what);
  }
  
  private boolean isNot(String what) {
    return !this.is(what);
  }
  
  private void setPhase(String what) {
    this.tablePhaseType = what;
    this.tablePhase++;
  }
  
  int getPhase() {
    return this.tablePhase;
  }
  
  boolean checkPhase(int phase) {
    return phase == this.tablePhase;
  }
  
  
  
  void onAdd(int subId, SubscriptionManager manager, SessionThread sessionThread) {
    if (this.isNot(OFF)) {
      log.error("Add event already executed");
    }
    this.sessionThread = sessionThread;
    this.subscriptionId  = subId;
    this.manager = manager;
    this.setPhase(WAITING);
    
    if (log.isDebugEnabled()) {
      log.debug("Subscription "+subId+" ready to be sent to server");
    }
  }
  
  void onStart() {
    if (this.isNot(PAUSED)) {
      log.error("Unexpected start while not paused");
    }
    this.setPhase(WAITING);
    
    if (log.isDebugEnabled()) {
      log.debug("Subscription "+this.subscriptionId+" ready to be sent to server");
    }
  }
  
  void onRemove() {
    boolean wasSubscribed = this.is(PUSHING);
    this.setPhase(OFF);
    
    if (wasSubscribed) {
      this.dispatcher.dispatchEvent(new SubscriptionListenerUnsubscriptionEvent());
    }
    
    if (this.behavior.equals(MULTIMETAPUSH)) {
      this.removeSubTables();
    }
    this.cleanData();
    
    if (log.isDebugEnabled()) {
      log.debug("Subscription "+this.subscriptionId+" is now off");
    }
    
  }
    
  void onPause() {
    if (this.is(OFF)) {
      log.error("Unexpected pause");
    }
    
    boolean wasSubscribed = this.is(PUSHING);
    this.setPhase(PAUSED);
    
    if (wasSubscribed) {
      this.dispatcher.dispatchEvent(new SubscriptionListenerUnsubscriptionEvent());
    }
    if (this.behavior.equals(MULTIMETAPUSH)) {
      this.removeSubTables();
    }
    this.cleanData();
    
    if (log.isDebugEnabled()) {
      log.debug("Subscription "+this.subscriptionId+" is now on hold");
    }
    
  }
  
  void onSubscriptionSent() {
    if (this.is(SUBSCRIBING)) {
      //first subscribe failed, try again
      log.debug("Subscription "+this.subscriptionId+" sent to server again");
      return;
    } else if (this.isNot(WAITING)) {
      log.error("Was not expecting the subscription request");
    }
    this.setPhase(SUBSCRIBING);
    
    if (log.isDebugEnabled()) {
      log.debug("Subscription "+this.subscriptionId+" sent to server");
    }
  }
  
  
  void unsupportedCommandWithFieldSchema() {
    //can't handle command stuff if I don't know which is the key and which is the command
    this.setPhase(PAUSED);
    this.dispatcher.dispatchEvent(new SubscriptionListenerSubscriptionErrorEvent(23,"current client/server pair does not support COMMAND subscriptions containing field schema: specify a field list"));
    
    this.manager.remove(this);   
  }
  
  void onSubscriptionAck() {
//      if (this.isNot(SUBSCRIBING)) {
//          log.error("Was not expecting the subscribed event");
//      }
      
      /* this method was extracted from onSubscribed() to stop the retransmissions when a REQOK is received */
      this.setPhase(PUSHING);
  }
  
  void onSubscribed(int commandPos, int keyPos, int items, int fields) {
//    if (this.isNot(SUBSCRIBING)) {
//      log.error("Was not expecting the subscribed event");
//    }
    this.setPhase(PUSHING);
    
    if (this.behavior.equals(MULTIMETAPUSH)) {
      this.fieldDescriptor.setSubDescriptor(this.subFieldDescriptor);
    }
    if (this.fieldDescriptor instanceof NameDescriptor && !this.behavior.equals(SIMPLE)) {
      log.debug("Received position of COMMAND and KEY fields from server");
      
      this.commandCode = commandPos; 
      this.keyCode = keyPos;
    }

    this.itemDescriptor.setSize(items);
    this.fieldDescriptor.setSize(fields);
    this.snapshotByItem = new SnapshotManager[1 + items];
    for (int i = 1; i <= items; i++) {
        this.snapshotByItem[i] = new SnapshotManager(isRequiredSnapshot, mode);
    }
    
    this.dispatcher.dispatchEvent(new SubscriptionListenerSubscriptionEvent());
    
    if (log.isDebugEnabled()) {
      log.debug("Subscription "+this.subscriptionId+" is now pushing");
    }
  }

  void onSubscriptionError(int code, String message) {
    if (this.isNot(SUBSCRIBING)) {
      log.error("Was not expecting the error event");
    }
    this.setPhase(PAUSED);
    
    this.dispatcher.dispatchEvent(new SubscriptionListenerSubscriptionErrorEvent(code,message));
  }
  
  
  boolean isOff() {
    return this.is(OFF);
  }
  
  boolean isWaiting() {
    return this.is(WAITING);
  }
  
  boolean isPaused() {
    return this.is(PAUSED);
  }
    
  //isSubscribed is part of the API
  
  boolean isSubscribing() {
    return this.is(SUBSCRIBING);
  }
  
  
  private boolean checkStatusForUpdate() {
    /*
     * The method returns true when the update sent by the server is acceptable to the client.
     * 
     * An update can reach the client only in these cases:
     * 1) the client deletes a subscription but the server keeps on sending updates because it has not yet received the deletion:
     * the client must ignore the update
     * 2) the connection is in PUSHING state: the client must accept the update.
     */
    if (!this.isActive()) { //NOTE: this blocks!
      //user does not care anymore, skip
      return false; //not active
      //case should be rare as soon after the setInactive call the subscription is removed 
      //from the subscriptions collection
    } else if (this.isNot(PUSHING)){
        assert false;
        return false; //active but not pushing, unexpected case
    }
    
    return true; //active and pushing
  }
  
//////////////////////////data utils
  
  SubscribeRequest generateSubscribeRequest() {
    return new SubscribeRequest(this.subscriptionId, this.mode, this.itemDescriptor, this.fieldDescriptor,
        this.dataAdapter, this.selector, this.isRequiredSnapshot, 
        this.requestedMaxFrequency, this.requestedBufferSize);
  }
  
  ChangeSubscriptionRequest generateFrequencyRequest() {
    return new ChangeSubscriptionRequest(this.subscriptionId,this.requestedMaxFrequency,++this.nextReconfId);
  }
  
  ChangeSubscriptionRequest generateFrequencyRequest(int reconfId) {
    return new ChangeSubscriptionRequest(this.subscriptionId,this.requestedMaxFrequency,reconfId);
  }
  
  private void prepareSecondLevel() {
    if (this.subFieldDescriptor == null) {
      //disable second level
      this.behavior = METAPUSH;
      
    } else {
      //enable second level
      this.behavior = MULTIMETAPUSH;
    }
  }

  private void secondLevelCheck() {
    if (!this.mode.equals(Constants.COMMAND)) {
      throw new IllegalStateException(NO_SECOND_LEVEL);
    }
  }
  
  private void commandCheck() {
    if (!this.mode.equals(Constants.COMMAND)) {
      throw new IllegalStateException(NO_COMMAND);
    }
  }
  
  private void setSchemaReadMetapushFields(Descriptor tmpDescriptor) {
    if (!this.mode.equals(Constants.COMMAND) || tmpDescriptor == null || tmpDescriptor instanceof NameDescriptor ) {
      return;
    }
    
    this.commandCode = tmpDescriptor.getPos("command"); 
    this.keyCode = tmpDescriptor.getPos("key");
    
    if (this.commandCode == -1 || this.keyCode == -1) {
      throw new IllegalArgumentException("A field list for a COMMAND subscription must contain the key and command fields");
    }
  }
  
///////////////////////////push events
  
  void endOfSnapshot(int item) {
    if(!this.checkStatusForUpdate()) {
      return;
    }
    
    String name = this.itemDescriptor.getName(item);
    this.snapshotByItem[item].endOfSnapshot();
    this.dispatcher.dispatchEvent(new SubscriptionListenerEndOfSnapshotEvent(name,item));
  }

  void clearSnapshot(int item) {
    if(!this.checkStatusForUpdate()) {
      return;
    }
    
    String name = this.itemDescriptor.getName(item);
    
    if (this.behavior.equals(METAPUSH)) {
      //delete key-status
      this.oldValuesByKey.clear();
    } else if (this.behavior.equals(MULTIMETAPUSH)) {
      //delete key-status
      this.oldValuesByKey.clear();
      //unsubscribe subtables
      this.removeItemSubTables(item);
      this.onLocalFrequencyChanged();
    }

    this.dispatcher.dispatchEvent(new SubscriptionListenerClearSnapshotEvent(name,item));
  }
  
  void lostUpdates(int item, int lostUpdates) {
    if(!this.checkStatusForUpdate()) {
      return;
    }
    String name = this.itemDescriptor.getName(item);
    this.dispatcher.dispatchEvent(new SubscriptionListenerItemLostUpdatesEvent(name,item,lostUpdates));
  }
  
  void configure(String frequency) {
      if(!this.checkStatusForUpdate()) {
          return;
      }
      if (frequency.equalsIgnoreCase("unlimited")) {
        this.localRealMaxFrequency = FREQUENCY_UNLIMITED;
      } else {
        try {
          this.localRealMaxFrequency = Double.parseDouble(frequency);
        } catch (NumberFormatException nfe) {
          assert(false); // should have been checked by the caller
          // too late to handle the error, just ignore the information
          log.error("Invalid frequency received from the Server for subscription " + this.subscriptionId + ": ignored");
          this.localRealMaxFrequency = FREQUENCY_NULL;
        }
      }
      if (behavior.equals(MULTIMETAPUSH)) {
          this.onLocalFrequencyChanged();
          // may invoke dispatchEvent
      } else {
          this.dispatcher.dispatchEvent(new SubscriptionListenerConfigurationEvent(frequency));
          // if it is a second-level subscription, the listener will take care
          // of calling onLocalFrequencyChanged() on the first level
      }
  }
  
  void onLostUpdates(String relKey,int lostUpdates) {
    if(!this.checkStatusForUpdate()) {
      return;
    }
    this.dispatcher.dispatchEvent(new SubscriptionListenerCommandSecondLevelItemLostUpdatesEvent(lostUpdates,relKey));
  }
  
  void onServerError(int code,String message,String relKey) {
    if(!this.checkStatusForUpdate()) {
      return;
    }
    this.dispatcher.dispatchEvent(new SubscriptionListenerCommandSecondLevelSubscriptionErrorEvent(code,message,relKey));
  }

  
  
  void update(ArrayList<String> args, int item, boolean fromMultison) {
    if(!this.checkStatusForUpdate()) {
      return;
    }
    this.snapshotByItem[item].update();
  
    SortedSet<Integer> changedFields = this.prepareChangedSet(args);
    
    String key = String.valueOf(item);
    if (!this.behavior.equals(SIMPLE)) {
      //handle metapush update
      key = this.organizeMPUpdate(args,item,fromMultison,changedFields);
      //NOTE, args is now modified!
     
      //args enters UNCHANGED by item exits UNCHANGED by key
      //oldValuesByItem is updated with new values
      
    } 
    
    if (this.behavior.equals(MULTIMETAPUSH) && !fromMultison) {
      //2 level push
      //here we handle subscription/unsubscription for the second level
      //(obviously we do not have anything to do if the update is from the second level)
      this.handleMultiTableSubscriptions(item,args); 
    }
    
    
   if(this.behavior.equals(SIMPLE)) {
      this.updateStructure(this.oldValuesByItem,item,args,changedFields);
    } else {
      this.updateStructure(this.oldValuesByKey,key,args,changedFields);
      //organizeMPUpdate has already updated the oldValuesByItem array
    }
    
    
    
    
    String itemName = itemDescriptor.getName(item);
    boolean snapshot = this.snapshotByItem[item].isSnapshot();
    ItemUpdate updateObj = new ItemUpdate(itemName,item,snapshot,args,changedFields,fieldDescriptor); 
    
    this.dispatcher.dispatchEvent(new SubscriptionListenerItemUpdateEvent(updateObj));
    
    if(!this.behavior.equals(SIMPLE)) {
      String command = this.oldValuesByKey.get(key,this.commandCode);
      if (Constants.DELETE.equals(command)) {
        this.oldValuesByKey.delRow(key);
      }
    }
    
  }
  
  
/////////////////data handling  
  
  private void cleanData() {
    //this.subscriptionId = -1;
    //this.manager = null;
    
    this.oldValuesByItem.clear(); 
    this.oldValuesByKey.clear();
    this.snapshotByItem = null;
    
    //resets the schema size
    this.fieldDescriptor.setSize(0);
    this.itemDescriptor.setSize(0);
    
    if (this.behavior.equals(MULTIMETAPUSH)) {
      this.fieldDescriptor.setSubDescriptor(null);
      this.subTables.clear();
    }
    
    if (log.isDebugEnabled()) {
      log.debug("structures reset for subscription " + this.subscriptionId);
    }
  }
 
  
  private SortedSet<Integer> prepareChangedSet(ArrayList<String> args) {
    SortedSet<Integer> changedFields = new TreeSet<Integer>();
    for (int i=0; i<args.size(); i++) {
      if (ProtocolConstants.UNCHANGED != args.get(i)) {
        changedFields.add(i+1);
      }
    }
    return changedFields;
  }
  private <K> void updateStructure(
      ConcurrentMatrix<K, Integer> struct, K key,
      ArrayList<String> args, SortedSet<Integer> changedFields) {

    for (int i=0; i<args.size(); i++) {
      int fieldPos = i+1;
      String value = args.get(i);
      
      if(ProtocolConstants.UNCHANGED != value) {
        struct.insert(value, key, fieldPos);
      } else {
        String oldValue = struct.get(key, fieldPos);
        args.set(i, oldValue);
      }
    }
    
  }
  
  private String organizeMPUpdate(ArrayList<String> args, int item,
      boolean fromMultison, SortedSet<Integer> changedFields) {
    
    String extendedKey;
    
    int numFields = args.size();
    if (this.commandCode > numFields || this.keyCode > numFields) {
      log.error("key and/or command position not correctly configured");
      return null;
    }
    
    //we still have the server UNCHANGED here, so we need to evaluate the correct value for the key
    String currentKey = args.get(this.keyCode-1);
    if (ProtocolConstants.UNCHANGED == currentKey) {
      //key is unchyanged, get the old value
      extendedKey = item+" "+this.oldValuesByItem.get(item,this.keyCode);
    } else {

      extendedKey = item+" "+currentKey;
    }
    
    
    //replace unchanged by item with unchanged by key and prepare the old
    //by item for the next time
    //makes only sense for COMMAND update, updates from second level are
    //already organized by key
    
    if (!fromMultison) {
      changedFields.clear();
      
      for (int i=0; i<args.size(); i++) {
        String current = args.get(i);
        int fieldPos = i+1;
        String oldByItem =  this.oldValuesByItem.get(item,fieldPos);
          
        if (ProtocolConstants.UNCHANGED == current) {
          //unchanged from server, replace with old by item
          current = oldByItem;
          args.set(i, oldByItem);
        } else {
          //changed from server, put it on the old by item
          this.oldValuesByItem.insert(current,item,fieldPos);
        }
        
        String oldByKey =  this.oldValuesByKey.get(extendedKey,fieldPos);
        if ((oldByKey == null && current == null) || (oldByKey != null && oldByKey.equals(current))) {
          //i.e.: if (oldByKey == current)
          //  it means that old and new by key are equals, thus the value is UNCHANGED
          args.set(i, ProtocolConstants.UNCHANGED);
        } else {
          //or else 
          changedFields.add(fieldPos);
        }
        
      }
      
      if (this.behavior.equals(MULTIMETAPUSH)) {
        int newL = this.fieldDescriptor.getFullSize();
        if(newL > args.size()) {
          //I got an update on the first level, 
          //fill the second level fields with unchanged values
         
          for (int i = args.size(); i < newL; i++) {
            args.add(ProtocolConstants.UNCHANGED);
          }
        }
      }
      
    } else {
      
      //update from the second level, the update (args) is already long enough for both levels
      
      //key is not changed for sure
      args.set(this.keyCode-1, ProtocolConstants.UNCHANGED);
      changedFields.remove(this.keyCode);
      
      //command is probably not changed 
      String updateCommand = args.get(this.commandCode-1);
      String prevCommand = this.oldValuesByKey.get(extendedKey,this.commandCode);
      if (updateCommand.equals(prevCommand)) { //NOTE: update can't be null
        args.set(this.commandCode-1, ProtocolConstants.UNCHANGED);
        changedFields.remove(this.commandCode);
      } else {
        changedFields.add(this.commandCode);
      }
      
      //other 1st level fiedls are already UNCHANGED
      
    }
    return extendedKey;
  }
  
//////////////////second level handling  

  private void handleMultiTableSubscriptions(int item, ArrayList<String> args) {
    // subscription/unsubscription of second level subscriptions 
   
    String key = args.get(this.keyCode-1);
    if (key == ProtocolConstants.UNCHANGED) {
      key = this.oldValuesByItem.get(item,this.keyCode);
    }
    
    String itemCommand = args.get(this.commandCode-1);
     
    boolean subTableExists =  this.hasSubTable(item,key);
    if (Constants.DELETE.equals(itemCommand)) {
      if (subTableExists) {
        this.removeSubTable(item,key,CLEAN);
        this.onLocalFrequencyChanged();
      }
    } else if (!subTableExists) {
      this.addSubTable(item,key);
      // this.onLocalFrequencyChanged(); (useless)
    }
  }
  
  private void onLocalFrequencyChanged() {
    assert behavior.equals(MULTIMETAPUSH);
    assert ! isSubTable();
    double prevRealMaxFrequency = aggregatedRealMaxFrequency;

    aggregatedRealMaxFrequency = localRealMaxFrequency;
    this.subTables.forEachElement(new Matrix.ElementCallback<Integer, String, Subscription>() {
      @Override
      public boolean onElement(Subscription value, Integer item, String key) {
        if (isHigherFrequency(value.localRealMaxFrequency, aggregatedRealMaxFrequency)) {
          aggregatedRealMaxFrequency = value.localRealMaxFrequency;
        }
        return false;
      }
      private boolean isHigherFrequency(double fNew, double fOld) {
        if (fOld == FREQUENCY_UNLIMITED || fNew == FREQUENCY_NULL) {
          return false;
        } else if (fNew == FREQUENCY_UNLIMITED || fOld == FREQUENCY_NULL) {
          return true;
        } else {
          return fNew > fOld;
        }
      }
    });

    if (aggregatedRealMaxFrequency != prevRealMaxFrequency) {
      String frequency;
      if (aggregatedRealMaxFrequency == FREQUENCY_UNLIMITED) {
        frequency = "unlimited";
      } else if (aggregatedRealMaxFrequency == FREQUENCY_NULL) {
        frequency = null;
      } else {
        frequency = String.valueOf(aggregatedRealMaxFrequency);
      }
      this.dispatcher.dispatchEvent(new SubscriptionListenerConfigurationEvent(frequency));
    }
  }
  
  // package-protected instead of private to enable special testing
  void addSubTable(int item, String key) {
    
    Subscription secondLevelSubscription = new Subscription(this.subMode);
    secondLevelSubscription.makeSubTable();
    
    try {
      secondLevelSubscription.setItems(new String[]{key});
      this.subTables.insert(secondLevelSubscription, item, key);
    } catch(IllegalArgumentException e) {
      log.error("Subscription error", e);
      onServerError(14, INVALID_SECOND_LEVEL_KEY, key);
      return;
    }
    
    if (this.subFieldDescriptor instanceof ListDescriptor) {
      secondLevelSubscription.setFields(((ListDescriptor) subFieldDescriptor).getOriginal());
    } else {
      secondLevelSubscription.setFieldSchema(((NameDescriptor) subFieldDescriptor).getOriginal());
    }
    
    secondLevelSubscription.setDataAdapter(this.underDataAdapter);
    secondLevelSubscription.setRequestedSnapshot("yes");
    secondLevelSubscription.requestedMaxFrequency = this.requestedMaxFrequency;
    
    SubscriptionListener subListener = new SecondLevelSubscriptionListener(item,key);
    secondLevelSubscription.addListener(subListener);
    
    secondLevelSubscription.setActive();
    this.manager.doAdd(secondLevelSubscription);
  
  }
  
  private void makeSubTable() {
    this.subTableFlag = true;
  }
  boolean isSubTable() {
    //do not abuse
    return this.subTableFlag;
  }
  private boolean hasSubTable(int item, String key) {
    return this.subTables.get(item,key) != null;
  }

  private void removeSubTable(int item, String key, boolean clean) {
    Subscription secondLevelSubscription = this.subTables.get(item,key);
    secondLevelSubscription.setInactive();
    this.manager.doRemove(secondLevelSubscription);
    if (clean) {
      this.subTables.del(item,key);
    }
  }

  private void removeItemSubTables(int item) {
    this.subTables.forEachElementInRow(item, new Matrix.ElementCallback<Integer, String, Subscription>() {
      @Override
      public boolean onElement(Subscription value, Integer item, String key) {
        removeSubTable(item,key,DONT_CLEAN);
        return true;
      }
    });
  }
  
  private void removeSubTables() {
    this.subTables.forEachElement(new Matrix.ElementCallback<Integer, String, Subscription>() {
      @Override
      public boolean onElement(Subscription value, Integer item, String key) {
        removeSubTable(item,key,DONT_CLEAN);
        return true;
      }
    });
  }
  
  private void setSecondLevelSchemaSize(int size) {
    this.subFieldDescriptor.setSize(size);
  }
  
  ////////////////////////helpers
  private void debugDescriptor(String debugString, Descriptor desc) {
    if (log.isDebugEnabled()) {
      String s = desc != null? desc.getComposedString() : "<null>";
      log.debug(debugString + s);
    }
  }
  
  private int getFullSchemaSize() {
    return this.fieldDescriptor.getFullSize();
  }
  
  private int getMainSchemaSize() {
    return this.fieldDescriptor.getSize();
  }
  
  
  private Integer toFieldPos(String fieldName) {
    int fieldPos = this.fieldDescriptor.getPos(fieldName);
    if (fieldPos == -1) {
      throw new IllegalArgumentException("the specified field does not exist");
    }
    return fieldPos;
  }
  
  private void verifyFieldPos(int fieldPos,boolean full) {
    if (fieldPos <= 0 || fieldPos > (full ? this.fieldDescriptor.getFullSize() : this.fieldDescriptor.getSize())) {
      throw new IllegalArgumentException("the specified field position is out of bounds");
    }
  }
  
  private void verifyItemPos(int itemPos) {
    if (itemPos <= 0 || itemPos > this.itemDescriptor.getSize()) {
      throw new IllegalArgumentException("the specified item position is out of bounds");
    }
  }

  private Integer toItemPos(String itemName) {
    int itemPos = this.itemDescriptor.getPos(itemName);
    if (itemPos == -1) {
      throw new IllegalArgumentException("the specified item does not exist");
    }
    return itemPos;
  }
 

  
  
  private class SecondLevelSubscriptionListener implements SubscriptionListener {


    private int itemReference;
    private String relKey;

    public SecondLevelSubscriptionListener(int item, String key) {
      this.itemReference = item;
      this.relKey = key;
    }

    @Override
    public void onClearSnapshot(@Nullable String itemName, int itemPos) {
      // not expected, as MERGE mode is implied here
    }

    @Override
    public void onCommandSecondLevelItemLostUpdates(int lostUpdates, String key) {
      // can't happen
    }

    @Override
    public void onCommandSecondLevelSubscriptionError(int code, @Nullable String message,
        String key) {
      // can't happen
    }

    @Override
    public void onEndOfSnapshot(@Nullable String itemName, int itemPos) {
      // nothing to do
    }

    @Override
    public void onItemLostUpdates(@Nullable String itemName, int itemPos, int lostUpdates) {
      if (!this.shouldDispatch()) {
        return;
      }
    
      onLostUpdates(this.relKey,lostUpdates);
    }

    @Override
    public void onItemUpdate(ItemUpdate itemUpdate) {
      if (!this.shouldDispatch()) {
        return;
      }
      
      
      setSecondLevelSchemaSize(itemUpdate.getFieldsCount());
      
      ArrayList<String> args = this.convertMultiSonUpdate(itemUpdate);

      //once the update args are converted we pass them to the main table
      update(args,this.itemReference,true);
      
    }

    @Override
    public void onListenEnd(Subscription subscription) {
      // don't care
    }

    @Override
    public void onListenStart(Subscription subscription) {
      // don't care
      
    }

    @Override
    public void onSubscription() {
      // nothing to do
    }

    @Override
    public void onSubscriptionError(int code, @Nullable String message) {
      if (!this.shouldDispatch()) {
        return;
      }
      
      onServerError(code,message,this.relKey);
    }

    @Override
    public void onUnsubscription() {
      // nothing to do
    }
    
    private boolean shouldDispatch() {
      return hasSubTable(this.itemReference,this.relKey);
    }
    
    
    
    private ArrayList<String> convertMultiSonUpdate(ItemUpdate itemUpdate) {

      int y = 1;
      int newLen = getFullSchemaSize(); //the combined length of the schemas
      ArrayList<String> newArgs = new ArrayList<String>(newLen);
      for (int i=0; i<newLen; i++) {
        if (i == keyCode-1) {
          //item is our key
          newArgs.add(this.relKey);
        } else if(i == commandCode-1) {
          //command must be an UPDATE
          newArgs.add(Constants.UPDATE);
        } else if (i < getMainSchemaSize()) {
          //other fields from the first level are unchanged
          newArgs.add(ProtocolConstants.UNCHANGED);
        } else {
          
          if (itemUpdate.isValueChanged(y)) {
            //changed fields from the second level
            newArgs.add(itemUpdate.getValue(y));
          } else {
            newArgs.add(ProtocolConstants.UNCHANGED);
          }
          
          y++;
          
        }
      }
      
      return newArgs;
      
    }

    @Override
    public void onRealMaxFrequency(@Nullable String frequency) {
      // the caller has already updated localRealMaxFrequency on the second-level object
      onLocalFrequencyChanged();
        // this invokes the first-level object
    }
    
  }
  
  @Nonnull
  @SuppressWarnings("null")
  private String toLowerCase(@Nonnull String s) {
      return s.toLowerCase();
  }
  
  /**
   * Detects whether the current update is a snapshot according to the rules in the following table.
   * <pre>
+--------------------+-------+----------+----------+---------+---------+-------+-------+-------+
|                    | r1    | r2       | r3       | r4      | r5      | r6    | r7    | r8    |
+--------------------+-------+----------+----------+---------+---------+-------+-------+-------+
| snapshot requested | false | true     | true     | true    | true    | true  | true  | true  |
+--------------------+-------+----------+----------+---------+---------+-------+-------+-------+
| mode               | -     | DISTINCT | DISTINCT | COMMAND | COMMAND | MERGE | MERGE | RAW   |
+--------------------+-------+----------+----------+---------+---------+-------+-------+-------+
| first update       | -     | -        | -        | -       | -       | false | true  | -     |
+--------------------+-------+----------+----------+---------+---------+-------+-------+-------+
| EOS received       | -     | false    | true     | false   | true    | -     | -     | -     |
+--------------------+-------+----------+----------+---------+---------+-------+-------+-------+
| isSnapshot()       | false | true     | false    | true    | false   | false | true  | error |
+--------------------+-------+----------+----------+---------+---------+-------+-------+-------+
   * </pre>
   */
  private static class SnapshotManager {
      
      private boolean firstUpdate = true;
      private boolean eosReceived = false;
      private SnapshotManagerState state = SnapshotManagerState.NO_UPDATE_RECEIVED;
      
      private final String _isRequiredSnapshot;
      private final String _mode;
      
      SnapshotManager(String isRequiredSnapshot, String mode) {
          this._isRequiredSnapshot = isRequiredSnapshot;
          this._mode = mode;
      }
      
      /**
       * Notifies the manager that a new update is available.
       */
      void update() {
          if (state == SnapshotManagerState.NO_UPDATE_RECEIVED) {
              state = SnapshotManagerState.ONE_UPDATE_RECEIVED;
              
          } else if (state == SnapshotManagerState.ONE_UPDATE_RECEIVED) {
              state = SnapshotManagerState.MORE_THAN_ONE_UPDATE_RECEIVED;
              firstUpdate = false;
          }
      }
      
      /**
       * Notifies the manager that the message EOS has arrived.
       */
      void endOfSnapshot() {
          eosReceived = true;
      }
      
      /**
       * Returns true if the user has requested the snapshot.
       */
      boolean snapshotRequested() {
          return _isRequiredSnapshot != null && ! _isRequiredSnapshot.equals("no");
      }
      
      /**
       * Returns true if the current update is a snapshot.
       */
      boolean isSnapshot() {
          if (! snapshotRequested()) {
              // r1
              return false;
              
          } else if (Constants.MERGE.equals(_mode)) {
              // r6, r7
              return firstUpdate;
              
          } else if (Constants.COMMAND.equals(_mode) || Constants.DISTINCT.equals(_mode)) {
              // r2, r3, r4, r5
              return ! eosReceived;
              
          } else {
              // r8
              // should never happen
              assert Constants.RAW.equals(_mode);
              return false;
          }
      }
  }
  
  /**
   * Control states of {@link SnapshotManager}.
   */
  private enum SnapshotManagerState { 
      NO_UPDATE_RECEIVED,
      ONE_UPDATE_RECEIVED,
      MORE_THAN_ONE_UPDATE_RECEIVED
  }
}

