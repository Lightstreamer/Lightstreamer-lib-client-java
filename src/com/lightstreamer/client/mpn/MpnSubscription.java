package com.lightstreamer.client.mpn;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import com.lightstreamer.client.ConnectionDetails;
import com.lightstreamer.client.Constants;
import com.lightstreamer.client.LightstreamerClient;
import com.lightstreamer.client.Subscription;
import com.lightstreamer.client.events.EventsThread;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;
import com.lightstreamer.util.Assertions;
import com.lightstreamer.util.Descriptor;
import com.lightstreamer.util.ListDescriptor;
import com.lightstreamer.util.ListenerHolder;
import com.lightstreamer.util.LsUtils;
import com.lightstreamer.util.Visitor;

/**
 * Class representing a Mobile Push Notifications (MPN) subscription to be submitted to the MPN Module of a Lightstreamer Server.<BR>
 * It contains subscription details and the listener needed to monitor its status. Real-time data is routed via native push notifications.<BR>
 * In order to successfully subscribe an MPN subscription, first an MpnDevice must be created and registered on the LightstreamerClient with
 * {@link LightstreamerClient#registerForMpn(MpnDeviceInterface)}.<BR>
 * After creation, an MpnSubscription object is in the "inactive" state. When an MpnSubscription object is subscribed to on an LightstreamerClient
 * object, through the {@link LightstreamerClient#subscribe(MpnSubscription, boolean)} method, its state switches to "active". This means that the subscription request
 * is being sent to the Lightstreamer Server. Once the server accepted the request, it begins to send real-time events via native push notifications and
 * the MpnSubscription object switches to the "subscribed" state.<BR>
 * If a trigger expression is set, the MPN subscription does not send any push notifications until the expression evaluates to true. When this happens,
 * the MPN subscription switches to "triggered" state and a single push notification is sent. Once triggered, no other push notifications are sent.<BR>
 * When an MpnSubscription is subscribed on the server, it acquires a permanent subscription ID that the server later uses to identify the same
 * MPN subscription on subsequent sessions.<BR>
 * An MpnSubscription can be configured to use either an Item Group or an Item List to specify the items to be subscribed to, and using either a Field Schema
 * or Field List to specify the fields. The same rules that apply to {@link Subscription} apply to MpnSubscription.<BR>
 * An MpnSubscription object can also be provided by the client to represent a pre-existing MPN subscription on the server. In fact, differently than real-time
 * subscriptions, MPN subscriptions are persisted on the server's MPN Module database and survive the session they were created on.<BR>
 * MPN subscriptions are associated with the MPN device, and after the device has been registered the client retrieves pre-existing MPN subscriptions from the
 * server's database and exposes them with the {@link LightstreamerClient#getMpnSubscriptions(String)} method.
 */
public class MpnSubscription {
    
    private static final Logger log = LogManager.getLogger(Constants.MPN_LOG);

    /* 
     * =======================
     * user-defined attributes
     * ========================
     * 
     * NB they are volatile because they can be written by an user thread and read by the session thread, or vice versa
     */
    private volatile String mode;
    private volatile String items;
    private volatile String fields;
    private volatile String format;
    private volatile String triggerExpression;
    private volatile String dataAdapter;
    private volatile int requestedBufferSize = -1;
    private volatile double requestedMaxFrequency = -2;
    private volatile long statusTimestamp = 0;
    /* 
     * =======================
     * internal attributes 
     * =======================
     * 
     * NB they are mainly manipulated by the session thread, but they may be indirectly read by an user thread
     */
    private volatile String PN_subscriptionId;
    volatile boolean coalescing = false; // written by MpnManager
    /**
     * As a general rule, the subscription is wholly initialized when the
     * MpnManager sets the subscription fields with the values coming from the
     * subscription internal adapter.
     * <p>
     * For example suppose that a subscription is created as a copy of another active subscription.
     * It is reasonable that the copy starts in the state INACTIVE (*), but, since the original subscription
     * is already in state SUBSCRIBED, the updates coming from the internal adapter don't mark the field
     * status as modified. However this flag remembers that the copy is not fully initialized so 
     * the status is set notwithstanding the internal adapter incoherent information.
     * <p>
     * (*) Starting as INACTIVE, when the state becomes SUBSCRIBED, we can fire the subscription triggers. 
     */
    volatile boolean needsInitialization = true; // written by MpnManager
    
    final SubscriptionEventManager eventManager = new SubscriptionEventManager(); // read by MpnManager
    private final EventsThread eventThread = EventsThread.instance;    
    private final PropertyHolder properties = new PropertyHolder(eventThread);
    private final StateMachine machine = new StateMachine();
    
    /*
     * Public API
     */

    /**
     * Creates an object to be used to describe an MPN subscription that is going to be subscribed to through the MPN Module of Lightstreamer Server.<BR>
     * The object can be supplied to {@link LightstreamerClient#subscribe(MpnSubscription, boolean)} in order to bring the MPN subscription to "active" state.<BR>
     * Note that all of the methods used to describe the subscription to the server can only be called while the instance is in the "inactive" state.
     *
     * @param subscriptionMode The subscription mode for the items, required by Lightstreamer Server. Permitted values are:<ul>
     * <li><code>MERGE</code></li>
     * <li><code>DISTINCT</code></li>
     * </ul>
     * @param items An array of items to be subscribed to through Lightstreamer Server. It is also possible specify the "Item List" or
     * "Item Group" later through {@link #setItems(String[])} and {@link #setItemGroup(String)}.
     * @param fields An array of fields for the items to be subscribed to through Lightstreamer Server. It is also possible to specify the "Field List" or
     * "Field Schema" later through {@link #setFields(String[])} and {@link #setFieldSchema(String)}.
     * @throws IllegalArgumentException If no or invalid subscription mode is passed.
     * @throws IllegalArgumentException If either the items or the fields array is left null.
     * @throws IllegalArgumentException If the specified "Item List" or "Field List" is not valid; see {@link #setItems(String[])} and {@link #setFields(String[])} for details.
     */
    public MpnSubscription(@Nonnull String subscriptionMode, @Nonnull String[] items, @Nonnull String[] fields) {
       init(subscriptionMode, items, fields);
    }

    /**
     * Creates an object to be used to describe an MPN subscription that is going to be subscribed to through the MPN Module of Lightstreamer Server.<BR>
     * The object can be supplied to {@link LightstreamerClient#subscribe(MpnSubscription, boolean)} in order to bring the MPN subscription to "active" state.<BR>
     * Note that all of the methods used to describe the subscription to the server can only be called while the instance is in the "inactive" state.
     * 
     * @param subscriptionMode The subscription mode for the items, required by Lightstreamer Server. Permitted values are:<ul>
     * <li><code>MERGE</code></li>
     * <li><code>DISTINCT</code></li>
     * </ul>
     * @param item The item name to be subscribed to through Lightstreamer Server.
     * @param fields An array of fields for the items to be subscribed to through Lightstreamer Server. It is also possible to specify the "Field List" or
     * "Field Schema" later through {@link #setFields(String[])} and {@link #setFieldSchema(String)}.
     * @throws IllegalArgumentException If no or invalid subscription mode is passed.
     * @throws IllegalArgumentException If either the item or the fields array is left null.
     * @throws IllegalArgumentException If the specified "Field List" is not valid; see {@link #setFields(String[])} for details.
     */
    public MpnSubscription(@Nonnull String subscriptionMode, @Nonnull String item, @Nonnull String[] fields) {
        this(subscriptionMode, new String[] { item }, fields);
    }

    /**
     * Creates an object to be used to describe an MPN subscription that is going to be subscribed to through the MPN Module of Lightstreamer Server.<BR>
     * The object can be supplied to {@link LightstreamerClient#subscribe(MpnSubscription, boolean)} in order to bring the MPN subscription to "active" state.<BR>
     * Note that all of the methods used to describe the subscription to the server can only be called while the instance is in the "inactive" state.
     * 
     * @param subscriptionMode The subscription mode for the items, required by Lightstreamer Server. Permitted values are:<ul>
     * <li><code>MERGE</code></li>
     * <li><code>DISTINCT</code></li>
     * </ul>
     * @throws IllegalArgumentException If no or invalid subscription mode is passed.
     */
    public MpnSubscription(@Nonnull String subscriptionMode) {
        init(subscriptionMode, null, null);
    }

    /**
     * Creates an MpnSubscription object copying subscription mode, items, fields and data adapter from the specified real-time subscription.<BR>
     * The object can be supplied to {@link LightstreamerClient#subscribe(MpnSubscription, boolean)} in order to bring the MPN subscription to "active" state.<BR>
     * Note that all of the methods used to describe the subscription to the server can only be called while the instance is in the "inactive" state.
     * 
     * @param copyFrom The Subscription object to copy properties from.
     */
    public MpnSubscription(@Nonnull Subscription copyFrom) {
        this.mode = copyFrom.getMode();
        Descriptor itemsCopy = com.lightstreamer.client.Internals.getItemDescriptor(copyFrom);
        if (itemsCopy != null) {
            this.items = itemsCopy.getComposedString();
        }
        Descriptor fieldsCopy = com.lightstreamer.client.Internals.getFieldDescriptor(copyFrom);
        if (fieldsCopy != null) {
            this.fields = fieldsCopy.getComposedString();
        }
        this.dataAdapter = copyFrom.getDataAdapter();
    }

    /**
     * Creates an MpnSubscription object copying all properties (including the subscription ID) from the specified MPN subscription.<BR>
     * The created MpnSubscription is a copy of the original MpnSubscription object and represents the same MPN subscription, since their subscription ID is
     * the same. When the object is supplied to {@link LightstreamerClient#subscribe(MpnSubscription, boolean)} in order to bring it to "active" state, the MPN subscription
     * is modified: any property changed in this MpnSubscription replaces the corresponding value of the MPN subscription on the server.<BR>
     * Note that all of the methods used to describe the subscription to the server can only be called while the instance is in the "inactive" state.
     * 
     * @param copyFrom The MpnSubscription object to copy properties from.
     */
    public MpnSubscription(@Nonnull MpnSubscription copyFrom) {
        this.mode = copyFrom.mode;
        this.items = copyFrom.items;
        this.fields = copyFrom.fields;
        this.format = copyFrom.format;
        this.triggerExpression = copyFrom.triggerExpression;
        this.dataAdapter = copyFrom.dataAdapter;
        this.PN_subscriptionId = copyFrom.PN_subscriptionId;
        this.requestedBufferSize = copyFrom.requestedBufferSize;
        this.requestedMaxFrequency = copyFrom.requestedMaxFrequency;
    }
    
    private void init(String mode, String[] items, String[] fields) {
        if (!("MERGE".equals(mode) || "DISTINCT".equals(mode))) {
            throw new IllegalArgumentException("Only MERGE and DISTINCT modes are allowed for MPN subscriptions");
        }
        this.mode = mode;
        setItems(items);
        setFields(fields);
    }

    /**
     * Adds a listener that will receive events from the MpnSubscription instance.<BR>
     * The same listener can be added to several different MpnSubscription instances.<BR>
     * 
     * @lifecycle A listener can be added at any time. A call to add a listener already present will be ignored.
     * 
     * @param listener An object that will receive the events as documented in the {@link MpnSubscriptionListener}  interface.
     * 
     * @see #removeListener(MpnSubscriptionListener)
     */
    public void addListener(@Nonnull final MpnSubscriptionListener listener) {
        properties.addListener(listener, new Visitor<MpnSubscriptionListener>() {
            @Override
            public void visit(MpnSubscriptionListener listener) {
                listener.onListenStart(MpnSubscription.this);
            }
        });
    }

    /**
     * Removes a listener from the MpnSubscription instance so that it will not receive events anymore.
     * 
     * @lifecycle A listener can be removed at any time.
     * 
     * @param listener The listener to be removed.
     * 
     * @see #addListener(MpnSubscriptionListener)
     */
    public void removeListener(@Nonnull final MpnSubscriptionListener listener) {
        properties.removeListener(listener, new Visitor<MpnSubscriptionListener>() {
            @Override
            public void visit(MpnSubscriptionListener listener) {
                listener.onListenEnd(MpnSubscription.this);
            }
        });
    }

    /**
     * Returns the list containing the {@link MpnSubscriptionListener} instances that were added to this MpnSubscription.
     * 
     * @return a list containing the listeners that were added to this subscription.
     *
     * @see #addListener(MpnSubscriptionListener)
     */
    @Nonnull
    public List<MpnSubscriptionListener> getListeners() {
        return properties.getListeners();
    }
    
    /**
     * Returns the JSON structure to be used as the format of push notifications.<BR>
     * This JSON structure is sent by the server to the push notification service provider (i.e. Google's FCM), hence it must follow
     * its specifications.<BR>
     * 
     * @return the JSON structure to be used as the format of push notifications.
     * 
     * @see #setNotificationFormat(String)
     */
    @Nullable
    public String getNotificationFormat() {
        return format;
    }

    /**
     * Sets the JSON structure to be used as the format of push notifications.<BR>
     * This JSON structure is sent by the server to the push notification service provider (i.e. Google's FCM), hence it must follow
     * its specifications.<BR>
     * The JSON structure may contain named arguments with the format <code>${field}</code>, or indexed arguments with the format <code>$[1]</code>. These arguments are 
     * replaced by the server with the value of corresponding subscription fields before the push notification is sent.<BR>
     * For instance, if the subscription contains fields "stock_name" and "last_price", the notification format could be something like this:<ul>
     * <li><code>{ "android" : { "notification" : { "body" : "Stock ${stock_name} is now valued ${last_price}" } } }</code></li>
     * </ul>
     * Named arguments are available if the Metadata Adapter is a subclass of LiteralBasedProvider or provides equivalent functionality, otherwise only
     * indexed arguments may be used. In both cases common metadata rules apply: field names and indexes are checked against the Metadata Adapter, hence
     * they must be consistent with the schema and group specified.<BR>
     * A special server-managed argument may also be used:<ul>
     * <li><code>${LS_MPN_subscription_ID}</code>: the ID of the MPN subscription generating the push notification.
     * </ul>
     * The {@link com.lightstreamer.client.mpn.util.MpnBuilder} object provides methods to build an appropriate JSON structure from its defining fields.<BR>
     * Note: if the MpnSubscription has been created by the client, such as when obtained through {@link LightstreamerClient#getMpnSubscriptions(String)},
     * named arguments are always mapped to its corresponding indexed argument, even if originally the notification format used a named argument.<BR>
     * Note: the content of this property may be subject to length restrictions (See the "General Concepts" document for more information).
     * 
     * @lifecycle This method can only be called while the MpnSubscription instance is in its "inactive" state.
     * 
     * @notification A change to this setting will be notified through a call to {@link MpnSubscriptionListener#onPropertyChanged(String)}
     * with argument <code>notification_format</code> on any {@link MpnSubscriptionListener} listening to the related MpnSubscription.
     * 
     * @param format the JSON structure to be used as the format of push notifications.
     * @throws IllegalStateException if the MpnSubscription is currently "active".
     * 
     * @see com.lightstreamer.client.mpn.util.MpnBuilder
     */
    public void setNotificationFormat(@Nonnull String format) {
        throwErrorIfActive();
        this.format = format;
    }

    /**
     * Returns the boolean expression that is evaluated against each update and acts as a trigger to deliver the push notification.
     * 
     * @return the boolean expression that acts as a trigger to deliver the push notification.
     * 
     * @see #setTriggerExpression(String)
     */
    @Nullable
    public String getTriggerExpression() {
        return triggerExpression;
    }

    /**
     * Sets the boolean expression that will be evaluated against each update and will act as a trigger to deliver the push notification.<BR>
     * If a trigger expression is set, the MPN subscription does not send any push notifications until the expression evaluates to true. When this happens,
     * the MPN subscription "triggers" and a single push notification is sent. Once triggered, no other push notifications are sent. In other words, with a trigger
     * expression set, the MPN subscription sends *at most one* push notification.<BR>
     * The expression must be in Java syntax and can contain named arguments with the format <code>${field}</code>, or indexed arguments with the format <code>$[1]</code>.
     * The same rules that apply to {@link #setNotificationFormat(String)} apply also to the trigger expression. The expression is verified and evaluated on the server.<BR>
     * Named and indexed arguments are replaced by the server with the value of corresponding subscription fields before the expression is evaluated. They are
     * represented as String variables, and as such appropriate type conversion must be considered. E.g.<ul>
     * <li><code>Double.parseDouble(${last_price}) &gt; 500.0</code></li>
     * </ul>
     * Argument variables are named with the prefix <code>LS_MPN_field</code> followed by an index. Thus, variable names like <code>LS_MPN_field1</code> should be considered
     * reserved and their use avoided in the expression.<BR>
     * Consider potential impact on server performance when writing trigger expressions. Since Java code may use classes and methods of the JDK, a badly written
     * trigger may cause CPU hogging or memory exhaustion. For this reason, a server-side filter may be applied to refuse poorly written (or even
     * maliciously crafted) trigger expressions. See the "General Concepts" document for more information.<BR>
     * Note: if the MpnSubscription has been created by the client, such as when obtained through {@link LightstreamerClient#getMpnSubscriptions(String)},
     * named arguments are always mapped to its corresponding indexed argument, even if originally the trigger expression used a named argument.<BR>
     * Note: the content of this property may be subject to length restrictions (See the "General Concepts" document for more information).
     * 
     * @lifecycle This method can only be called while the MpnSubscription instance is in its "inactive" state.
     * 
     * @notification A change to this setting will be notified through a call to {@link MpnSubscriptionListener#onPropertyChanged(String)}
     * with argument <code>trigger</code> on any {@link MpnSubscriptionListener} listening to the related MpnSubscription.
     * 
     * @param expr the boolean expression that acts as a trigger to deliver the push notification.
     * @throws IllegalStateException if the MpnSubscription is currently "active".
     * 
     * @see #isTriggered()
     */
    public void setTriggerExpression(@Nonnull String expr) {
        throwErrorIfActive();
        triggerExpression = expr;
    }

    /**
     * Checks if the MpnSubscription is currently "active" or not.<BR>
     * Most of the MpnSubscription properties cannot be modified if an MpnSubscription is "active".<BR>
     * The status of an MpnSubscription is changed to "active" through the {@link LightstreamerClient#subscribe(MpnSubscription, boolean)} method and back to "inactive"
     * through the {@link LightstreamerClient#unsubscribe(MpnSubscription)} and {@link LightstreamerClient#unsubscribeMpnSubscriptions(String)} ones.
     * 
     * @lifecycle This method can be called at any time.
     * 
     * @return true if the MpnSubscription is currently "active", false otherwise.
     * 
     * @see #getStatus()
     * @see LightstreamerClient#subscribe(MpnSubscription, boolean)
     * @see LightstreamerClient#unsubscribe(MpnSubscription)
     * @see LightstreamerClient#unsubscribeMpnSubscriptions(String)
     */
    public boolean isActive() {
        return machine.isActive();
    }

    /**
     * Checks if the MpnSubscription is currently subscribed to through the server or not.<BR>
     * This flag is switched to true by server sent subscription events, and back to false in case of client disconnection,
     * {@link LightstreamerClient#unsubscribe(MpnSubscription)} or {@link LightstreamerClient#unsubscribeMpnSubscriptions(String)} calls, and server sent 
     * unsubscription events.
     * 
     * @lifecycle This method can be called at any time.
     * 
     * @return true if the MpnSubscription has been successfully subscribed on the server, false otherwise.
     * 
     * @see #getStatus()
     * @see LightstreamerClient#unsubscribe(MpnSubscription)
     * @see LightstreamerClient#unsubscribeMpnSubscriptions(String)
     */
    public boolean isSubscribed() {
        return machine.isSubscribed();
    }

    /**
     * Checks if the MpnSubscription is currently triggered or not.<BR>
     * This flag is switched to true when a trigger expression has been set and it evaluated to true at least once. For this to happen, the subscription
     * must already be in "active" and "subscribed" states. It is switched back to false if the subscription is modified with a
     * {@link LightstreamerClient#subscribe(MpnSubscription, boolean)} call on a copy of it, deleted with {@link LightstreamerClient#unsubscribe(MpnSubscription)} or
     * {@link LightstreamerClient#unsubscribeMpnSubscriptions(String)} calls, and server sent subscription events.
     * 
     * @lifecycle This method can be called at any time.
     * 
     * @return true if the MpnSubscription's trigger expression has been evaluated to true at least once, false otherwise.
     * 
     * @see #getStatus()
     * @see LightstreamerClient#subscribe(MpnSubscription, boolean)
     * @see LightstreamerClient#unsubscribe(MpnSubscription)
     * @see LightstreamerClient#unsubscribeMpnSubscriptions(String)
     */
    public boolean isTriggered() {
        return machine.isTriggered();
    }

    /**
     * The status of the subscription.<BR>
     * The status can be:<ul>
     * <li><code>UNKNOWN</code>: when the MPN subscription has just been created or deleted (i.e. unsubscribed). In this status {@link #isActive()}, {@link #isSubscribed} 
     * and {@link #isTriggered()} are all false.</li>
     * <li><code>ACTIVE</code>: when the MPN susbcription has been submitted to the server, but no confirm has been received yet. In this status {@link #isActive()} is true, 
     * {@link #isSubscribed} and {@link #isTriggered()} are false.</li>
     * <li><code>SUBSCRIBED</code>: when the MPN subscription has been successfully subscribed on the server. If a trigger expression is set, it has not been
     * evaluated to true yet. In this status {@link #isActive()} and {@link #isSubscribed} are true, {@link #isTriggered()} is false.</li>
     * <li><code>TRIGGERED</code>: when the MPN subscription has a trigger expression set, has been successfully subscribed on the server and
     * the trigger expression has been evaluated to true at least once. In this status {@link #isActive()}, {@link #isSubscribed} and {@link #isTriggered()} are all true.</li>
     * </ul>
     * 
     * @lifecycle This method can be called at any time.
     * 
     * @return the status of the subscription.
     * 
     * @see #isActive()
     * @see #isSubscribed()
     * @see #isTriggered()
     */
    @Nonnull
    public String getStatus() {
        return machine.status();
    }

    /**
     * The server-side timestamp of the subscription status.
     * 
     * @lifecycle This method can be called at any time.
     * 
     * @notification A change to this setting will be notified through a call to {@link MpnSubscriptionListener#onPropertyChanged(String)}
     * with argument <code>status_timestamp</code> on any {@link MpnSubscriptionListener} listening to the related MpnSubscription.
     * 
     * @return The server-side timestamp of the subscription status, expressed as a Java time.
     * 
     * @see #getStatus()
     */
    public long getStatusTimestamp() {
        return statusTimestamp;
    }

    /**
     * Setter method that sets the "Item List" to be subscribed to through 
     * Lightstreamer Server. <BR>
     * Any call to this method will override any "Item List" or "Item Group"
     * previously specified.
     * 
     * @lifecycle This method can only be called while the MpnSubscription
     * instance is in its "inactive" state.
     * 
     * @notification A change to this setting will be notified through a call to {@link MpnSubscriptionListener#onPropertyChanged(String)}
     * with argument <code>group</code> on any {@link MpnSubscriptionListener} listening to the related MpnSubscription.
     * 
     * @param items an array of items to be subscribed to through the server. 
     * @throws IllegalArgumentException if any of the item names in the "Item List"
     * contains a space or is a number or is empty/null.
     * @throws IllegalStateException if the MpnSubscription is currently 
     * "active".
     */
    public void setItems(@Nullable String[] items) {
        throwErrorIfActive();
        ListDescriptor.checkItemNames(items, "An item");
        this.items = LsUtils.join(items, ' ');
    }

    /**
     * Inquiry method that can be used to read the "Item List" specified for this MpnSubscription.<BR> 
     * Note that if the single-item-constructor was used, this method will return an array 
     * of length 1 containing such item.<BR>
     * Note: if the MpnSubscription has been created by the client, such as when obtained through {@link LightstreamerClient#getMpnSubscriptions(String)},
     * items are always expressed with an "Item Group"", even if originally the MPN subscription used an "Item List".
     *
     * @lifecycle This method can only be called if the MpnSubscription has been initialized 
     * with an "Item List".

     * @return the "Item List" to be subscribed to through the server.
     * 
     * @throws IllegalStateException if the MpnSubscription was not initialized.
     */
    @Nonnull
    public String[] getItems() {
        if (items == null) {
            throw new IllegalStateException("The  item list/item group of this Subscription was not initiated");
        }
        return LsUtils.split(items, ' ');
    }

    /**
     * Setter method that sets the "Item Group" to be subscribed to through 
     * Lightstreamer Server. <BR>
     * Any call to this method will override any "Item List" or "Item Group"
     * previously specified.
     * 
     * @lifecycle This method can only be called while the MpnSubscription
     * instance is in its "inactive" state.
     * 
     * @notification A change to this setting will be notified through a call to {@link MpnSubscriptionListener#onPropertyChanged(String)}
     * with argument <code>group</code> on any {@link MpnSubscriptionListener} listening to the related MpnSubscription.
     * 
     * @param groupName A String to be expanded into an item list by the
     * Metadata Adapter. 
     * @throws IllegalStateException if the MpnSubscription is currently 
     * "active".
     */
    public void setItemGroup(@Nonnull String groupName) {
        throwErrorIfActive();
        items = groupName;
    }

    /**
     * Inquiry method that can be used to read the item group specified for this MpnSubscription.<BR>
     * Note: if the MpnSubscription has been created by the client, such as when obtained through {@link LightstreamerClient#getMpnSubscriptions(String)},
     * items are always expressed with an "Item Group"", even if originally the MPN subscription used an "Item List".
     *
     * @lifecycle This method can only be called if the MpnSubscription has been initialized
     * using an "Item Group"
     * 
     * @return the "Item Group" to be subscribed to through the server.
     * 
     * @throws IllegalStateException if the MpnSubscription was not initialized.
     */
    @Nonnull
    public String getItemGroup() {
        if (items == null) {
            throw new IllegalStateException("The  item list/item group of this Subscription was not initiated");
        }
        return items;
    }

    /**
     * Setter method that sets the "Field List" to be subscribed to through 
     * Lightstreamer Server. <BR>
     * Any call to this method will override any "Field List" or "Field Schema"
     * previously specified.
     * 
     * @lifecycle This method can only be called while the MpnSubscription
     * instance is in its "inactive" state.
     * 
     * @notification A change to this setting will be notified through a call to {@link MpnSubscriptionListener#onPropertyChanged(String)}
     * with argument <code>schema</code> on any {@link MpnSubscriptionListener} listening to the related MpnSubscription.
     * 
     * @param fields an array of fields to be subscribed to through the server. 
     * @throws IllegalArgumentException if any of the field names in the list
     * contains a space or is empty/null.
     * @throws IllegalStateException if the MpnSubscription is currently 
     * "active".
     */
    public void setFields(@Nullable String[] fields) {
        throwErrorIfActive();
        ListDescriptor.checkFieldNames(fields, "A field");
        this.fields = LsUtils.join(fields, ' ');
    }

    /**
     * Inquiry method that can be used to read the "Field List" specified for this MpnSubscription.<BR>
     * Note: if the MpnSubscription has been created by the client, such as when obtained through {@link LightstreamerClient#getMpnSubscriptions(String)},
     * fields are always expressed with a "Field Schema"", even if originally the MPN subscription used a "Field List".
     *
     * @lifecycle  This method can only be called if the MpnSubscription has been initialized 
     * using a "Field List".
     * 
     * @return the "Field List" to be subscribed to through the server.
     * 
     * @throws IllegalStateException if the MpnSubscription was not initialized.
     */
    @Nonnull
    public String[] getFields() {
        if (fields == null) {
            throw new IllegalStateException("The field list/field schema of this Subscription was not initiated");
        }
        return LsUtils.split(fields, ' ');
    }

    /**
     * Setter method that sets the "Field Schema" to be subscribed to through 
     * Lightstreamer Server. <BR>
     * Any call to this method will override any "Field List" or "Field Schema"
     * previously specified.
     * 
     * @lifecycle This method can only be called while the MpnSubscription
     * instance is in its "inactive" state.
     * 
     * @notification A change to this setting will be notified through a call to {@link MpnSubscriptionListener#onPropertyChanged(String)}
     * with argument <code>schema</code> on any {@link MpnSubscriptionListener} listening to the related MpnSubscription.
     * 
     * @param schemaName A String to be expanded into a field list by the
     * Metadata Adapter. 
     * 
     * @throws IllegalStateException if the MpnSubscription is currently 
     * "active".
     */
    public void setFieldSchema(@Nonnull String schemaName) {
        throwErrorIfActive();
        fields = schemaName;
    }

    /**
     * Inquiry method that can be used to read the field schema specified for this MpnSubscription.<BR>
     * Note: if the MpnSubscription has been created by the client, such as when obtained through {@link LightstreamerClient#getMpnSubscriptions(String)},
     * fields are always expressed with a "Field Schema"", even if originally the MPN subscription used a "Field List".
     *
     * @lifecycle This method can only be called if the MpnSubscription has been initialized 
     * using a "Field Schema"
     * 
     * @return the "Field Schema" to be subscribed to through the server.

     * @throws IllegalStateException if the MpnSubscription was not initialized.
     */
    @Nonnull
    public String getFieldSchema() {
        if (fields == null) {
            throw new IllegalStateException("The field list/field schema of this Subscription was not initiated");
        }
        return fields;
    }

    /**
     * Setter method that sets the name of the Data Adapter
     * (within the Adapter Set used by the current session)
     * that supplies all the items for this MpnSubscription. <BR>
     * The Data Adapter name is configured on the server side through
     * the "name" attribute of the "data_provider" element, in the
     * "adapters.xml" file that defines the Adapter Set (a missing attribute
     * configures the "DEFAULT" name). <BR>
     * Note that if more than one Data Adapter is needed to supply all the
     * items in a set of items, then it is not possible to group all the
     * items of the set in a single MpnSubscription. Multiple MpnSubscriptions
     * have to be defined.
     *
     * @default The default Data Adapter for the Adapter Set,
     * configured as "DEFAULT" on the Server.
     *
     * @lifecycle This method can only be called while the MpnSubscription
     * instance is in its "inactive" state.
     * 
     * @notification A change to this setting will be notified through a call to {@link MpnSubscriptionListener#onPropertyChanged(String)}
     * with argument <code>adapter</code> on any {@link MpnSubscriptionListener} listening to the related MpnSubscription.
     * 
     * @param dataAdapter the name of the Data Adapter. A null value 
     * is equivalent to the "DEFAULT" name.
     * @throws IllegalStateException if the Subscription is currently 
     * "active".
     *
     * @see ConnectionDetails#setAdapterSet(String)
     */
    public void setDataAdapter(@Nullable String dataAdapter) {
        throwErrorIfActive();
        this.dataAdapter = dataAdapter;
    }

    /**
     * Inquiry method that can be used to read the name of the Data Adapter specified for this 
     * MpnSubscription through {@link #setDataAdapter(String)}.
     * 
     * @lifecycle This method can be called at any time.
     * 
     * @return the name of the Data Adapter; returns null if no name has been configured, 
     * so that the "DEFAULT" Adapter Set is used.
     */
    @Nullable
    public String getDataAdapter() {
        return dataAdapter;
    }
    
    /**
     * Setter method that sets the length to be requested to Lightstreamer
     * Server for the internal queuing buffers for the items in the MpnSubscription.<BR>
     * A Queuing buffer is used by the Server to accumulate a burst
     * of updates for an item, so that they can all be sent to the client,
     * despite of bandwidth or frequency limits.<BR>
     * Note that the Server may pose an upper limit on the size of its internal buffers.
     *
     * @default null, meaning to lean on the Server default based on the subscription
     * mode. This means that the buffer size will be 1 for MERGE 
     * subscriptions and "unlimited" for DISTINCT subscriptions. See 
     * the "General Concepts" document for further details.
     *
     * @lifecycle This method can only be called while the MpnSubscription
     * instance is in its "inactive" state.
     * 
     * @notification A change to this setting will be notified through a call to {@link MpnSubscriptionListener#onPropertyChanged(String)}
     * with argument <code>requested_buffer_size</code> on any {@link MpnSubscriptionListener} listening to the related MpnSubscription.
     * 
     * @param size  An integer number, representing the length of the internal queuing buffers
     * to be used in the Server. If the string "unlimited" is supplied, then no buffer
     * size limit is requested (the check is case insensitive). It is also possible
     * to supply a null value to stick to the Server default (which currently
     * depends on the subscription mode).
     * @throws IllegalStateException if the MpnSubscription is currently 
     * "active".
     * @throws IllegalArgumentException if the specified value is not
     * null nor "unlimited" nor a valid positive integer number.
     *
     * @see #setRequestedMaxFrequency(String)
     */
    public void setRequestedBufferSize(@Nullable String size) {
        throwErrorIfActive();
        if (size == null) {
            requestedBufferSize = -1;
        } else if (size.equalsIgnoreCase("unlimited")) {
            requestedBufferSize = 0;
        } else {
            try {
                int buffSize = Integer.parseInt(size);
                if (buffSize <= 0) {
                    throw new IllegalArgumentException("The given value is not valid for this setting; use null, 'unlimited' or a positive integer instead");
                }
                requestedBufferSize = buffSize;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("The given value is not valid for this setting; use null, 'unlimited' or a positive integer instead");
            }
        }
    }
    
    /**
     * Inquiry method that can be used to read the buffer size, configured though
     * {@link #setRequestedBufferSize}, to be requested to the Server for 
     * this MpnSubscription.
     * 
     * @lifecycle This method can be called at any time.
     * 
     * @return  An integer number, representing the buffer size to be requested to the server,
     * or the string "unlimited", or null.
     */
    @Nullable
    public String getRequestedBufferSize() {
        if (requestedBufferSize == -1) {
            return null;
        } else if (requestedBufferSize == 0) {
            return "unlimited";
        } else {
            return "" + requestedBufferSize;
        }
    }
    
    /**
     * Setter method that sets the maximum update frequency to be requested to
     * Lightstreamer Server for all the items in the MpnSubscription.<BR>
     * Note that frequency limits on the items can also be set on the
     * server side and this request can only be issued in order to further
     * reduce the frequency, not to rise it beyond these limits.
     *
     * @general_edition_note A further global frequency limit could also be imposed by the Server,
     * depending on Edition and License Type.
     * To know what features are enabled by your license, please see the License tab of the
     * Monitoring Dashboard (by default, available at /dashboard).
     *
     * @default null, meaning to lean on the Server default based on the subscription
     * mode. This consists, for all modes, in not applying any frequency 
     * limit to the subscription (the same as "unlimited"); see the "General Concepts"
     * document for further details.
     *
     * @lifecycle This method can only be called while the MpnSubscription
     * instance is in its "inactive" state.
     * 
     * @notification A change to this setting will be notified through a call to {@link MpnSubscriptionListener#onPropertyChanged(String)}
     * with argument <code>requested_max_frequency</code> on any {@link MpnSubscriptionListener} listening to the related MpnSubscription.
     * 
     * @param freq  A decimal number, representing the maximum update frequency (expressed in updates
     * per second) for each item in the Subscription; for instance, with a setting
     * of 0.5, for each single item, no more than one update every 2 seconds
     * will be received. If the string "unlimited" is supplied, then no frequency
     * limit is requested. It is also possible to supply the null value to stick 
     * to the Server default (which currently corresponds to "unlimited").
     * The check for the string constants is case insensitive.
     * @throws IllegalStateException if the MpnSubscription is currently 
     * "active".
     * @throws IllegalArgumentException if the specified value is not
     * null nor the special "unlimited" value nor a valid positive number.
     */
    public void setRequestedMaxFrequency(@Nullable String freq) {
        throwErrorIfActive();
        if (freq == null) {
            requestedMaxFrequency = -2;
        } else if (freq.equalsIgnoreCase("unlimited")) {
            requestedMaxFrequency = 0;
        } else {
            try {
                double maxFreq = Double.parseDouble(freq);
                if (maxFreq <= 0) {
                    throw new IllegalArgumentException("The given value is not valid for this setting; use null, 'unlimited' or a positive number instead");
                }
                requestedMaxFrequency = maxFreq;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("The given value is not valid for this setting; use null, 'unlimited' or a positive number instead");
            }
        }
    }
    
    /**
     * Inquiry method that can be used to read the max frequency, configured
     * through {@link #setRequestedMaxFrequency(String)}, to be requested to the 
     * Server for this MpnSubscription.
     * 
     * @lifecycle This method can be called at any time.
     * 
     * @return  A decimal number, representing the max frequency to be requested to the server
     * (expressed in updates per second), or the string "unlimited", or null.
     */
    @Nullable
    public String getRequestedMaxFrequency() {
        if (requestedMaxFrequency == -2) {
            return null;
        } else if (requestedMaxFrequency == 0) {
            return "unlimited";
        } else {
            return "" + requestedMaxFrequency;
        }
    }
    
    /**
     * Inquiry method that can be used to read the mode specified for this
     * MpnSubscription.
     * 
     * @lifecycle This method can be called at any time.
     * 
     * @return the MpnSubscription mode specified in the constructor.
     */
    @Nonnull
    public String getMode() {
        return mode;
    }
    
    /**
     * The server-side unique persistent ID of the MPN subscription.<BR>
     * The ID is available only after the MPN subscription has been successfully subscribed on the server. I.e. when its status is <code>SUBSCRIBED</code> or
     * <code>TRIGGERED</code>.<BR>
     * Note: more than one MpnSubscription may exists at any given time referring to the same MPN subscription, and thus with the same subscription ID.
     * For instace, copying an MpnSubscription with the copy initializer creates a second MpnSubscription instance with the same subscription ID. Also,
     * the <code>coalescing</code> flag of {@link LightstreamerClient#subscribe(MpnSubscription, boolean)} may cause the assignment of a pre-existing MPN subscription ID 
     * to the new subscription.<BR>
     * Two MpnSubscription objects with the same subscription ID always represent the same server-side MPN subscription. It is the client's duty to keep the status
     * and properties of these objects up to date and aligned.
     * 
     * @lifecycle This method can be called at any time.
     * 
     * @return the MPN subscription ID.
     */
    @Nullable
    public String getSubscriptionId() {
        return PN_subscriptionId;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((PN_subscriptionId == null) ? 0 : PN_subscriptionId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MpnSubscription other = (MpnSubscription) obj;
        if (PN_subscriptionId == null) {
            if (other.PN_subscriptionId != null)
                return false;
        } else if (!PN_subscriptionId.equals(other.PN_subscriptionId))
            return false;
        return true;
    }

    private void throwErrorIfActive() throws IllegalStateException {
        if (machine.isActive()) {
            throw new IllegalStateException(
                    "Cannot modify an active subscription, please unsubscribe before applying any change");
        }
    }
    
    /*
     * Support methods.
     * 
     * NB
     * The right way to interact with a subscription is by means of SubscriptionEventManager.
     * However the class LightstreamerClient calls directly these methods to avoid 
     * to be scheduled on SessionThread.
     */
    
    void onSubscribe() {
        machine.subscribe();
    }

    void onUnsubscribe() {
        machine.unsubscribe();
    }

    String getItemsDescriptor() {
        return items;
    }

    String getFieldsDescriptor() {
        return fields;
    }

    void setSubscriptionId(String id) {
        assert Assertions.isSessionThread();
        this.PN_subscriptionId = id;
    }
    
    void throwErrorIfInactiveOrUnsubscribing() {
        if (machine.state == State.INACTIVE || machine.state == State.UNSUBSCRIBING) {
            // this is the same behavior of removing a Subscription: the error is directed to the user
            throw new IllegalStateException("MpnSubscription is not active");
        }
    }
    
    /*
     * Support classes
     */
    
    /**
     * API exposed to {@link MpnManager}.
     * Classes of the MPN package wanting to interact with this subscription should pass from here.
     */
    class SubscriptionEventManager {
        
        /**
         * Called when MPNOK is received.
         */
        void onSubscribeOK(String subId) {
            setSubscriptionId(subId);
        }
        
        /**
         * Called when the MPN internal adapter sends a new status. 
         */
        void onStatusChange(String status, long timestamp) {
            statusTimestamp = timestamp;
            switch (status) {
            case "ACTIVE":
                machine.activate();
                break;
            case "TRIGGERED":
                machine.trigger();
                break;
            default:
                throw new AssertionError("Unknown status: " + status);
            }
        }
        
        /**
         * Called when REQERR is received as subscription response.
         */
        void onSubscribeError(int code, String message) {
            machine.onSubscribeError(code, message);
        }
        
        /**
         * Called when REQERR is received as unsubscription response.
         */
        void onUnsubscribeError(int code, String message) {
            machine.onUnsubscribeError(code, message);
        }

        /**
         * Called when the MPN internal adapter publishes a new server subscription 
         * (i.e. not created by the user by means of a subscription request).
         */
        void simulateSubscribe(String startStatus, long timestamp) {
            statusTimestamp = timestamp;
            if ("ACTIVE".equals(startStatus)) {
                machine.onAddSubscribed();
            } else {
                assert "TRIGGERED".equals(startStatus);
                machine.onAddTriggered();
            }
        }

        /**
         * Called when the MPN internal adapter deletes an item.
         */
        void simulateUnsubscribe() {
            machine.onDelete();
        }
        
        /**
         * Called when an unsubscription cancels a subscription not already sent over the network.
         */
        void cancelSubscription() {
            machine.cancelSubscription();
        }

        /*
         * Internal setters
         */

        void onChangeMode(String _mode) {
            if (LsUtils.notEquals(mode, _mode)) {
                mode = _mode;
                onPropertyChange("mode");                
            }
        }

        void onChangeGroup(String group) {
            if (LsUtils.notEquals(items, group)) {
                items = group;
                onPropertyChange("group");
            }
        }

        void onChangeSchema(String schema) {
            if (LsUtils.notEquals(fields, schema)) {
                fields = schema;
                onPropertyChange("schema");
            }
        }

        void onChangeFormat(String _format) {
            if (LsUtils.notEquals(format, _format)) {
                format = _format;
                onPropertyChange("notification_format");                
            }
        }

        void onChangeTrigger(String trigger) {
            if (LsUtils.notEquals(triggerExpression, trigger)) {
                triggerExpression = trigger;
                onPropertyChange("trigger");                
            }
        }

        void onChangeAdapter(String _adapter) {
            if (LsUtils.notEquals(dataAdapter, _adapter)) {
                dataAdapter = _adapter;                
                onPropertyChange("adapter");
            }
        }
        
        void onChangeRequestedBufferSize(String size) {
            int newSize;
            if (size == null) {
                newSize = -1;
            } else if (size.equalsIgnoreCase("unlimited")) {
                newSize = 0;
            } else {
                newSize = Integer.parseInt(size);
            }
            if (requestedBufferSize != newSize) {
                requestedBufferSize = newSize;
                onPropertyChange("requested_buffer_size");
            }
        }
        
        void onChangeRequestedMaxFrequency(String freq) {
            double newFreq;
            if (freq == null) {
                newFreq = -2;
            } else if (freq.equalsIgnoreCase("unfiltered")) {
                newFreq = -1;
            } else if (freq.equalsIgnoreCase("unlimited")) {
                newFreq = 0;
            } else {
                newFreq = Double.parseDouble(freq);
            }
            if (requestedMaxFrequency != newFreq) {
                requestedMaxFrequency = newFreq;
                onPropertyChange("requested_max_frequency");
            }
        }

        void onChangeTimestamp(long ts) {
            if (statusTimestamp != ts) {
                statusTimestamp = ts;
                onPropertyChange("status_timestamp");                
            }
        }
        
        private void onPropertyChange(final String property) {
            properties.forEachListener(new Visitor<MpnSubscriptionListener>() {
                @Override
                public void visit(MpnSubscriptionListener listener) {
                    listener.onPropertyChanged(property);
                }
            });
        }
    } // SubscriptionEventManager

    /**
     * Class holding the properties of a device.
     * <p>
     * <b>NB</b>
     * Since the properties are set by the Session Thread but they can be read by the user,
     * methods must be synchronized.
     */
    @ThreadSafe
    private static class PropertyHolder extends ListenerHolder<MpnSubscriptionListener> {

        public PropertyHolder(EventsThread eventThread) {
            super(eventThread);
        }
    } // PropertyHolder
    
    /**
     * Control states of {@link StateMachine}.
     * <p>
     * State properties (NB unsubscribing states has been merged):
     * 
     * <div>
     * <img src="{@docRoot}/../docs/mpn/mpn-subscription-statuses.png">
     * </div>
     */
    private enum State {
        INACTIVE(false, false, false, "UNKNOWN"),
        SUBSCRIBING(true, false, false, "ACTIVE"),
        SUBSCRIBED(true, true, false, "SUBSCRIBED"),
        TRIGGERED(true, true, true, "TRIGGERED"),
        UNSUBSCRIBING(true, false, false, "UNKNOWN");
        
        final boolean isActive;
        final boolean isSubscribed;
        final boolean isTriggered;
        @Nonnull final String status;
        
        State(boolean isActive, boolean isSubscribed, boolean isTriggered, @Nonnull String status) {
            this.isActive = isActive;
            this.isSubscribed = isSubscribed;
            this.isTriggered = isTriggered;
            this.status = status;
        }
    } // State
    
    /**
     * Subscription state diagram:
     * 
     * <div>
     * <img src="{@docRoot}/../docs/mpn/mpn-subscription.png">
     * </div>
     */
    @ThreadSafe
    private class StateMachine {
        private State state = State.INACTIVE;
        
        synchronized boolean isActive() {
            return state.isActive;
        }
        
        synchronized boolean isSubscribed() {
            return state.isSubscribed;
        }
        
        synchronized boolean isTriggered() {
            return state.isTriggered;
        }
        
        synchronized @Nonnull String status() {
            return state.status;
        }
        
        /**
         * User wants to subscribe.
         */
        synchronized void subscribe() {
            switch (state) {
            case INACTIVE:
                next(State.SUBSCRIBING, "subscribe");
                break;
                
            default:
                throwError("subscribe");
            }
        }
        
        /**
         * User wants to unsubscribe.
         */
        synchronized void unsubscribe() {
            switch (state) {
            case SUBSCRIBING:
            case SUBSCRIBED:
            case TRIGGERED:
                next(State.UNSUBSCRIBING, "unsubscribe");
                break;
                
            default:
                throwError("unsubscribe");
            }
        }
        
        /**
         * Called when an unsubscription cancels a subscription not already sent over the network.
         */
        synchronized void cancelSubscription() {
            switch (state) {
            case UNSUBSCRIBING:
                next(State.INACTIVE, "cancelSubscription");
                fireOnUnsubscription();
                break;
                
            default:
                throwError("cancelSubscription");
            }
        }
        
        /**
         * MPN internal adapter sends ACTIVE.
         */
        synchronized void activate() {
            switch (state) {
            case SUBSCRIBING:
            case TRIGGERED:
                next(State.SUBSCRIBED, "activate");
                fireOnSubscription();
                break;
                
            case UNSUBSCRIBING:
                next(State.UNSUBSCRIBING, "activate");
                fireOnSubscription();
                break;
                
            case SUBSCRIBED:
                // two activations in a row can happen if the client creates a new session after a session error:
                // the first activation happens in the first session, while the second activation happens in
                // the second session
                break;
                
            default:
                throwError("activate");
            }
        }
        
        /**
         * MPN internal adapter sends TRIGGERED.
         */
        synchronized void trigger() {
            switch (state) {
            case SUBSCRIBING:
            case SUBSCRIBED:
                next(State.TRIGGERED, "trigger");
                fireOnTriggered();
                break;
                
            case UNSUBSCRIBING:
                next(State.UNSUBSCRIBING, "trigger");
                fireOnSubscription();
                break;
                
            case TRIGGERED:
                // two triggers in a row can happen if the client creates a new session after a session error
                // the first trigger happens in the first session, while the second trigger happens in
                // the second session
                break;
                
            default:
                throwError("trigger");
            }
        }
        
        /**
         * MPN internal adapter publishes one new server subscription starting as subscribed.
         */
        synchronized void onAddSubscribed() {
            if (state != State.INACTIVE) {
                throwError("onAddAsSubscribed");
            }
            next(State.SUBSCRIBED, "onAddAsSubscribed");
            fireOnSubscription();
        }
        
        /**
         * MPN internal adapter publishes one new server subscription starting as triggered.
         */
        synchronized void onAddTriggered() {
            if (state != State.INACTIVE) {
                throwError("onAddAsTriggered");
            }
            next(State.TRIGGERED, "onAddAsTriggered");
            fireOnTriggered();
        }
        
        /**
         * MPN internal adapter deletes the server subscription.
         */
        synchronized void onDelete() {
            switch (state) {
            case SUBSCRIBED:
            case TRIGGERED:
            case UNSUBSCRIBING:
                fireOnUnsubscription();
                next(State.INACTIVE, "onDelete");
                break;
                
            default:
                throwError("onDelete");
            }
        }
        
        /**
         * Subscription request receives REQERR.
         */
        synchronized void onSubscribeError(int code, String message) {
            switch (state) {
            case SUBSCRIBING:
            case UNSUBSCRIBING:
                fireOnSubscriptionError(code, message);
                next(State.INACTIVE, "onREQERR");
                break;
                
            default:
                throwError("onREQERR");
            }
        }
        
        /**
         * Unsubscription request receives REQERR.
         */
        synchronized void onUnsubscribeError(int code, String message) {
            switch (state) {
            case UNSUBSCRIBING:
                fireOnUnsubscriptionError(code, message);
                next(State.INACTIVE, "onREQERR");
                break;
                
            default:
                throwError("onREQERR");
            }
        }
        
        private synchronized void next(State next, String event) {
            if (log.isDebugEnabled()) {
                log.debug("MpnSubscription state change (" + PN_subscriptionId + ") on '" + event + "': " + state.name() + " -> " + next.name());
            }
            State oldState = state;
            state = next;
            if (!oldState.status.equals(state.status)) {
                properties.forEachListener(new Visitor<MpnSubscriptionListener>() {
                    @Override
                    public void visit(MpnSubscriptionListener listener) {
                        listener.onStatusChanged(state.status, statusTimestamp);
                    }
                });
            }
        }
        
        private synchronized void fireOnSubscription() {
            properties.forEachListener(new Visitor<MpnSubscriptionListener>() {
                @Override
                public void visit(MpnSubscriptionListener listener) {
                    listener.onSubscription();
                }
            });
        }

        private synchronized void fireOnUnsubscription() {
            properties.forEachListener(new Visitor<MpnSubscriptionListener>() {
                @Override
                public void visit(MpnSubscriptionListener listener) {
                    listener.onUnsubscription();
                }
            });
        }

        private synchronized void fireOnSubscriptionError(final int code, final String message) {
            properties.forEachListener(new Visitor<MpnSubscriptionListener>() {
                @Override
                public void visit(MpnSubscriptionListener listener) {
                    listener.onSubscriptionError(code, message);
                }
            });
        }
        
        private synchronized void fireOnUnsubscriptionError(final int code, final String message) {
            properties.forEachListener(new Visitor<MpnSubscriptionListener>() {
                @Override
                public void visit(MpnSubscriptionListener listener) {
                    listener.onUnsubscriptionError(code, message);
                }
            });
        }

        private synchronized void fireOnTriggered() {
            properties.forEachListener(new Visitor<MpnSubscriptionListener>() {
                @Override
                public void visit(MpnSubscriptionListener listener) {
                    listener.onTriggered();
                }
            });
        }
        
        private synchronized void throwError(String event) {
            throw new AssertionError("Unexpected event '" + event + "' in state " + state.name() + " (" + PN_subscriptionId + ")");
        }
    } // StateMachine
}
