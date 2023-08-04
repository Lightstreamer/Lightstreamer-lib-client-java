# Lightstreamer SDK for Android Clients Changelog


## 4.2.8 build 186

<i>Compatible with Lightstreamer Server since 7.1.</i><br/>
<i>Compatible with code developed with the previous version.</i><br/>
<i>Released on 4 Aug 2023</i>

Fixed the pom.xml, that still referenced Netty 4.1.46 instead of Netty 4.1.52.


## 4.2.7 build 185

<i>Compatible with Lightstreamer Server since 7.1.</i><br/>
<i>Compatible with code developed with the previous version.</i><br/>
<i>Released on 3 Aug 2023</i>

Updated Netty to version 4.1.52 to fix the exception `io.netty.handler.codec.UnsupportedMessageTypeException: io.netty.handler.codec.http.websocketx.TextWebSocketFrame (expected: io.netty.buffer.ByteBuf)`. This exception could occur when a secure WebSocket connection had to pass through an HTTP proxy. The bug did not affect the other transports.


## 4.2.6 build 184

<i>Compatible with Lightstreamer Server since 7.1.</i><br/>
<i>Compatible with code developed with the previous version.</i><br/>
<i>Released on 11 Oct 2022</i>

Fixed a bug about message sending that, upon a message retransmission, could have caused the client to trigger ClientListener.onServerError with error code 32 or 33.

<!--10/10/2022-->
Fixed a bug that, upon a session recovery, could have caused a memory leak.


## 4.2.5 build 183

<i>Compatible with Lightstreamer Server since 7.1.</i><br/>
<i>Compatible with code developed with the previous version.</i><br/>
<i>Released on 29 Mar 2021</i>

Fixed a bug on Websocket transport that, upon a connection interruption, could have caused the client to trigger the listener ClientListener.onServerError with error code 61. The bug didn't affect the so-called "compact" version.


## 4.2.4 build 181

<i>Compatible with Lightstreamer Server since 7.1.</i><br/>
<i>Compatible with code developed with the previous version.</i><br/>
<i>Released on 11 Dec 2020</i>

<!---id=3168--->
Fixed a bug introduced in version 4.0.2 and affecting the ItemUpdate.isSnapshot method. In case of a subscription of multiple items with a single Subscription object, the method returned true only for the first snapshot received. After that, the method returned false even when the updates were indeed snapshots.


## 4.2.3 build 180

<i>Compatible with Lightstreamer Server since 7.1.</i><br>
<i>Compatible with code developed with the previous version.</i><br>
<i>Released on 8 Oct 2020</i>

<!---id=3153--->Discontinued a notification to the Server of the termination of a HTTP streaming session.
The notification could help the Server to detect closed connections in some cases, but in other cases it
could give rise to bursts of new connections.

<!---id=3159--->Fixed a bug on connection reuse that, under particular conditions, could have caused some
connection attempts to fail. The bug didn't affect the so-called "compact" version.


## 4.2.2 build 179

<i>Compatible with Lightstreamer Server since 7.1.</i><br>
<i>Compatible with code developed with the previous version.</i><br>
<i>Released on 17 Aug 2020</i>

Made the library available on [Maven Central Repository](https://mvnrepository.com/artifact/com.lightstreamer/ls-android-client). 
The "compact" variant is also on [Maven Central Repository](https://mvnrepository.com/artifact/com.lightstreamer/ls-android-client-compact).
The new location will replace the old [Lightstreamer internal repository](https://www.lightstreamer.com/repo/maven).
Moreover, the library is now open source, available on GitHub at the
address [https://github.com/Lightstreamer/Lightstreamer-lib-client-java](https://github.com/Lightstreamer/Lightstreamer-lib-client-java).

Updated the required version of the third-party Netty library.


## 4.2.1 build 167

<i>Compatible with Lightstreamer Server since 7.1.</i><br>
<i>Compatible with code developed with the previous version.</i><br>
<i>Released on 11 Dec 2019</i>

<!---id=3100--->Revised the policy of reconnection attempts to reduce the attempt frequency
in case of repeated failure of the first bind request, which could be due to issues
in accessing the "control link" (when configured).


## 4.2.0 build 166

<i>Compatible with Lightstreamer Server since 7.1.</i><br>
<i>May not be compatible with code developed with the previous version;
see compatibility notes below.</i><br>
<i>Released on 22 November 2019</i>

Introduced changes to MPN features to comply with new FCM server libraries now
in use on Lightstreamer Server version 7.1. <b>COMPATIBILITY NOTE:</b> <i>   This breaks the compatibility with Server version 7.0. However, if MPN support
is not used, compatibility with Server version 7.0 is still ensured.</i><br/>
In particular:<ul>
<li>Removed the <code>MpnDevice.create</code> factory method that obtained
 the device token automatically. Now the token must be obtained manually
 and passed as an argument to the <code>MpnDevice</code> constructor.
 <b>COMPATIBILITY NOTE:</b> <i>Existing applications
 that were using the <code>MpnDevice.create</code> method must be ported,
 replacing the call with the equivalent code that makes direct use of
 Firebase APIs.</i></li>
<li>Changed the JSON structure required by <code>setNotificationFormat</code> in
 <code>MpnSubscription</code> and modified <code>MpnBuilder</code> to produce
 the correct format. <b>COMPATIBILITY NOTE:</b> <i>       Existing applications that don't rely on <code>MpnBuilder</code> may not receive
 notifications with the expected format and should be ported.</i></li>
</ul>
See paragraph 5.7 of the General Concepts document for information on the
GCM to FCM transition and how to update.

<!---id=3008--->Fixed a bug causing ConnectionDetails.getSessionId() to return null
notwithstanding the connection status was CONNECTED:STREAM-SENSING.

<!---id=3034--->Fixed a bug affecting the various callbacks that report custom error codes
and error messages received from Lightstreamer Server (originated by the Metadata Adapter).
The message reported could have been in a percent-encoded form. 

<!---id=3031--->By-passed the "retry delay" setting when recovering from a closed session.
This may speedup the recovery process.

Clarified in the docs the role of the delayTimeout in sendMessage.<br/>
Fixed typos in the docs of MPN-related methods.

<!---id=3076--->Fixed a bug which caused an exception when the Server sent an invalid item name in two-level subscriptions
instead of triggering the listener SubscriptionListener.onCommandSecondLevelSubscriptionError
with the error code 14 (Invalid second-level item name).

<!---id=3089--->Fixed a bug affecting polling sessions, which, upon a connection issue,
could have caused the session to be interrupted by issuing onServerError,
instead of being automatically recovered.

<!---id=3091--->Improved connection management. Now a connection is closed as soon as
it is discarded (for example because the connect timeout has expired).

Dropped support for API level lower than 23. Now Android 6 or later is required.
<b>COMPATIBILITY NOTE:</b> <i>Existing applications with
wider Android support requirements cannot be upgraded.</i>

Incremented the minor version number.
<b>COMPATIBILITY NOTE:</b> <i>If running Lightstreamer Server with a license
of "file" type which enables Android Client SDK up to version 4.1 or earlier, clients based
on this new version will not be accepted by the Server: a license upgrade will be needed.</i>


## 4.1.1 build 149.2

<i>Compatible with Lightstreamer Server since 7.0.</i><br>
<i>Compatible with code developed with the previous version.</i><br>
<i>Released on 4 April 2019</i>

Fixed a bug in the recently revised policy of reconnection attempts upon failed
or unresponsive requests. In case of multiple failed attempts on unresponsive connections
the retry delay was increased dynamically, but was not restored to the configured
value after a successful connection. As a consequence, after a server or network
unavailability lasting for a couple of minutes, further cases of server or network
unavailability would be recovered in about one minute, even if much shorter.


## 4.1.0 build 149.1

<i>Compatible with Lightstreamer Server since 7.0.</i><br>
<i>Compatible with code developed with the previous version.</i><br>
<i>Released on 14 March 2019</i>

Wholly revised the policy of reconnection attempts upon failed or unresponsive requests.
Now the only property related with this policy is "RetryDelay", which now affects both
(1) the minimum time to wait before trying a new connection to the Server in case the previous one failed 
for any reason and (2) the maximum time to wait for a response to a request before dropping the connection 
and trying with a different approach.<br/>
Previously, point (2) was related with the "ConnectTimeout" and "CurrentConnectTimeout" properties.
Now, in case of multiple failed attempts on unresponsive connections (i.e. while in CONNECTING state),
the timeout used may still be increased dynamically and can be inspected through getCurrentConnectTimeout,
but this behavior is no longer configurable.
<b>COMPATIBILITY NOTE:</b> <i>Existing code that tries to take control of the
connection timeouts will no longer be obeyed, but we assume that the new policy will bring
an overall improvement. Note that, when in CONNECTING state, the current timeout can be restored
by issuing disconnect() and then connect().</i><br/>
As a result of the change, methods setConnectTimeout, getConnectTimeout and setCurrentConnectTimeout 
of ConnectionOptions have been deprecated, as the setters have no effect and the getter
is now equivalent to getRetryDelay.<br/>
Also changed the default value of the "RetryDelay" property from 2 seconds to 4 seconds.

Modified the implementation of connect() when issued while the state is either
DISCONNECTED:WILL-RETRY or DISCONNECTED:TRYING-RECOVERY. The call will no longer interrupt
the pending reconnection attempt, but it will be ignored, to lean on the current attempt.
Note that a pending reconnection attempt can still be interrupted by issuing disconnect() first.<br/>
Modified in a similar way the implementation of setForcedTransport(); when issued
while the state is either DISCONNECTED:WILL-RETRY or DISCONNECTED:TRYING-RECOVERY,
the call will no longer interrupt the pending reconnection attempt, but it will apply
to the outcome of that connection attempt.

Fixed a bug triggered by a call to connect() or setForcedTransport() issued while the Client
was attempting the recovery of the current session. This caused the recovery to fail,
but, then, the library might not reissue the current subscriptions on the newly created session.

Fixed a bug that could have caused session recovery to fail if preceded by a previous
successful session recovery on the same session by more than a few seconds.

Changed the default value of the "EarlyWSOpenEnabled" property from true to false 
(see ConnectionOptions.setEarlyWSOpenEnabled). This removes a potential incompatibility
with cookie-based Load Balancers, at the expense of a possible slight delay in session startup.

Changed the default value of the "SlowingEnabled" property from true to false 
(see ConnectionOptions.setSlowingEnabled).

Incremented the minor version number.
<b>COMPATIBILITY NOTE:</b> <i>If running Lightstreamer Server with a license
of "file" type which enables Android Client SDK up to version 4.0 or earlier, clients based
on this new version will not be accepted by the Server: a license upgrade will be needed.</i>


## 4.0.12 build 149

<i>Compatible with Lightstreamer Server since 7.0.</i><br>
<i>Compatible with code developed with the previous version.</i><br>
<i>Released on 19 November 2018</i>

Fixed a very rare race condition that could lead the Client to stop trying to reconnect
to the Server after a network disconnection or a server restart.


## 4.0.11 build build 148

<i>Compatible with Lightstreamer Server since 7.0.</i><br>
<i>Compatible with code developed with the previous version.</i><br>
<i>Released on 23 October 2018</i>

Fixed a bug causing a fatal error when the Client recreated a session because of a network error
and there were active MPN subscriptions.


## 4.0.10 build build 147

<i>Compatible with Lightstreamer Server since 7.0.</i><br>
<i>Compatible with code developed with the previous version.</i><br>
<i>Released on 15 October 2018</i>

Fixed a bug causing the Client to ignore the SessionRecoveryTimeout when in state STALLED.

Fixed a bug causing the closing of the session when the user made a copy of a triggered subscription
(with the constructor MpnSubscription(MpnSubscription)) and then subscribed the copy.


## 4.0.9 build 142

<i>Compatible with Lightstreamer Server since 7.0.</i><br>
<i>Compatible with code developed with the previous version.</i><br>
<i>Released on 28 May 2018</i>

Introduced a maximum time on attempts to recover the current session, after which
a new session will be opened. The default is 15 seconds, but it can be customized
with the newly added "SessionRecoveryTimeout" property in ConnectionOptions.
This fixes a potential case of permanently unsuccessful recovery, if the
&lt;control_link_address&gt; setting were leveraged in a Server cluster and a Server
instance happened to leave a cluster and were not automatically restarted.

Fixed a bug in the recently introduced session recovery mechanism triggered
by the use of the &lt;control_link_address&gt; setting on Lightstreamer Server,
which could have caused feasible recovery attempts to fail.

Fixed a bug in the Stream-Sense mechanism, which was unable to recover to HTTP
streaming in some specific cases of unavailability of a websocket connection.


## 4.0.8 build 141

<i>Compatible with Lightstreamer Server since 7.0.</i><br>
<i>Compatible with code developed with the previous version.</i><br>
<i>Released on 18 April 2018</i>

Fixed a compatibility issue with Android API level 21, which may have caused
TLS connections to fail during the initial handshake. This only affected the
"full" version of the library.

Updated the included version of the third-party Netty library. This only regards
the "full" version of the library.


## 4.0.7 build 139

<i>Compatible with Lightstreamer Server since 7.0.</i><br>
<i>May not be compatible with code developed with the previous version;
see compatibility notes below.</i><br>
<i>Released on 13 April 2018</i>

Fixed a bug, introduced in version 4.0.0, affecting the interruption of a session
on a WebSocket (available in the "full" version of the library).
In case of connectivity issues (or cluster affinity issues) the session might
have remained open in the background, causing a resource waste.

Modified the constructor of the MpnDevice class, by removing the "senderId" argument,
which was not actually used. <b>COMPATIBILITY NOTE:</b> <i>   Existing code leveraging the MPN support and using this constructor (instead
of the available factory method) needs to be aligned and recompiled.</i>

Discontinued token reuse upon MPN device registration: now a new token is always requested.
Previously, the existing token was reused if the application package version had not changed.
This better conforms to Android FCM library best usage practices.


## 4.0.6 build 137

<i>Compatible with Lightstreamer Server since 7.0.</i><br>
<i>Compatible with code developed with the previous version.</i><br>
<i>Released on 9 Apr 2018</i>

Fixed a bug in the recently introduced session recovery mechanism, by which,
a sendMessage request issued while a recovery operation was in place,
could have never been notified to the listener until the end of the session
(at which point an "abort" notification would have been issued to the listener),
even in case the recovery was successful.

Fixed possible cases of incorrect cookie handling in old versions of Android
(api level 23 and earlier).


## 4.0.5 build 134

<i>Compatible with Lightstreamer Server since 7.0.</i><br>
<i>Compatible with code developed with the previous version.</i><br>
<i>Released on 6 Apr 2018</i>

Fixed a compatibility issue in the previously added debug log.


## 4.0.4 build 133

<i>Compatible with Lightstreamer Server since 7.0.</i><br>
<i>Compatible with code developed with the previous version.</i><br>
<i>Released on 5 Apr 2018</i>

Improved internal debug log.


## 4.0.3 build 132

<i>Compatible with Lightstreamer Server since 7.0.</i><br>
<i>Compatible with code developed with the previous version.</i><br>
<i>Released on 26 Mar 2018</i>

Fixed a bug which, upon particular kinds of network issues and when the "early websocket open"
feature was enabled, could have caused the Client to abort session establishment or recovery
attempts with no notification to the application.


## 4.0.2 build 131

<i>Compatible with Lightstreamer Server since 7.0 b2.</i><br>
<i>Compatible with code developed with the previous version.</i><br>
<i>Released on 27 Feb 2018</i>

Fixed a bug, introduced in version 3.0, which could have caused the method
ItemUpdate.isSnapshot to behave wrongly on subscriptions in which the snapshot
was not requested.


## 4.0.1 build 129

<i>Compatible with Lightstreamer Server since 7.0 b2.</i><br>
<i>May not be compatible with code developed with the previous version;
see compatibility notes below.</i><br>
<i>Released on 21 Feb 2018</i>

Added the error code 21 in onServerError, that can be received upon some failed
requests, to inform that not only the current session was not found but it is also likely
that the request was routed to the wrong Server instance. Previously, in the same cases,
the SDK library would not invoke onServerError and would open a new session instead.
<b>COMPATIBILITY NOTE:</b> <i>If using an existing application, you should
check how it would handle the new (and unexpected) error code. A reconnection attempt would
ensure the previous behavior, although this is no longer the suggested action.</i>

Modified the default value of the "RetryDelay" property from 5000 to 2000 ms.
This should help recovering from network outages of a few seconds, typical, for
instance, of wifi/mobile network switches.

Extended the recovery mechanism to stalled sessions. Now, when the ReconnectTimeout
expires, an attempt to recover the current session will be performed first.

Fixed a bug introduced with version 3.0.0 preventing the connection when the Server
configuration specifies a control-link address.

Fixed a rare race condition which could have caused the delay of subscription
requests issued on a websocket session startup due to a wrong request to the Server.

Fixed a bug which, in a slow client scenario, could have caused the interruption
of a polling session due to a wrong request to the Server.

Fixed a race condition, mostly possible in an overloaded client scenario, which
could have caused subscription or sendMessage requests to be delayed.

Fixed a harmless bug in the reverse heartbeat mechanism  which, upon session startup,
could have caused a NullPointerException to be logged on the console.

Fixed the instructions provided in the documentation of setNotificationFormat
in the MpnSubscription class.

Extended the scope of error code 41 on the various MPN requests to all issues related
with resource unavailability in the Server preventing the operation. Previously,
such issues could have caused the whole session to be interrupted.

Added clarifications on licensing matters in the docs.


## 4.0.0 build 127

<i>Compatible with Lightstreamer Server since 7.0 b2.</i><br>
<i>May not be compatible with code developed with the previous version;
see compatibility notes below.</i><br>
<i>Released on 20 Dec 2017</i>

Introduced the support for Mobile Push Notifications. It consists in
new methods in the LightstreamerClient class together with new dedicated
classes under the "mpn" subpackage. See the API documentation for details.<br/>
As a consequence, new dependencies from third-party libraries have been added.<br/>
An MPN subscription is backed by a real-time subscription, from which it may take
any field value. Unlike the usual real-time subscriptions, MPN subscriptions are persistent:
they survive the session and are identified by a permanent, global, unique key provided
by the Server at time of activation.<br/>
The notifications are managed by third-party services supported by the Server,
which determine the notification characteristics and the supported devices.

Restricted the compatibility constraint to Android 4 or later.
<b>COMPATIBILITY NOTE:</b> <i>Existing applications can keep
running on earlier Android versions as long they don't leverage the MPN support.</i>

Added automatic recovery of sessions upon unexpected socket interruption during
streaming or long polling. Now the library will perform an attempt to resume
the session from the interruption point. The attempt may or may not succeed,
also depending on the Server configuration of the recovery capability.<br/>
As a consequence, introduced a new status, namely DISCONNECTED:TRYING-RECOVERY,
to inform the application when a recovery attempt is being performed; hence,
onStatusChange and getStatus can provide the new status.
<b>COMPATIBILITY NOTE:</b> <i>Existing code that uses the status
names received via onStatusChange or getStatus may have to be aligned.</i>

Extended the reverse heartbeat mechanism, governed by the "ReverseHeartbeatInterval"
property. Now, it will also allow the Server to detect when a client has abandoned
a session although the socket remains open.<br/>
Fixed the reverse heartbeat mechanism, which didn't work at all. Since version 3.0.0,
setting a value for the "ReverseHeartbeatInterval" property could have even caused
the connection interruption.

Addressed an issue in the "compact" version of the library, which, in particular
circumstances (more likely to occur in long polling), could have caused the silent
loss of events on the session. Look for "sun.net.http.retryPost" in the Javadoc
overview for details.

Added the new Server error code 71 to onServerError and clarified the difference
with error code 60.

Fixed the documentation of getServerSocketName, whose behavior has slightly
changed since version 3.0.0.

Fixed the documentation of the "ContentLength", "KeepaliveInterval", and
"ReverseHeartbeatInterval" properties of ConnectionOptions, to clarify that
a zero value is not allowed in the first and it is allowed in the others.

Fixed the javadocs, by removing some class member variables that were not
supposed to be documented.

Aligned the documentation to comply with current licensing policies.


## 3.1.7 build 103.10

<i>Compatible with Lightstreamer Server since 6.1.</i><br>
<i>Compatible with code developed with the previous version</i><br>
<i>Released on 6 Dec 2017</i>

Added the method LightstreamerClient.setTrustManagerFactory,
which provides a mean to control the way TLS certificates are evaluated,
with the possibility to accept untrusted ones.


## 3.1.6 build 103.9

<i>Compatible with Lightstreamer Server since 6.1.</i><br>
<i>Compatible with code developed with the previous version</i><br>
<i>Released on 24 Nov 2017</i>

Fixed a bug that, when under certain circumstances the Server is unreachable,
caused the Client to enter in a tight reconnection loop ignoring the parameter ConnectionOptions.setRetryDelay.


## 3.1.5 build 103.8

<i>Compatible with Lightstreamer Server since 6.1.</i><br>
<i>Compatible with code developed with the previous version</i><br>
<i>Released on 23 Nov 2017</i>

Addressed compatibility issue with Android 8 affecting SSL/TLS WebSocket connections
and causing a tight loop of connection attempts.

Fixed an error in the determination of the LIB_VERSION constant of the
LightstreamerClient class in the "full" (i.e. non "compact") version;
this version description can also be reported by the Server.


## 3.1.4 build 103.2

<i>Compatible with Lightstreamer Server since 6.1.</i><br>
<i>Compatible with code developed with the previous version</i><br>
<i>Released on 26 Sep 2017</i>

Fixed a bug, introduced in version 3, affecting the case of session establishment
refusal by the Metadata Adapter (through a CreditsException) and the case of forced
destroy (via external requests), when a negative custom error code was supplied.
The subsequent invocation to onServerError would carry code 61 (internal error)
instead of the specified custom code.


## 3.1.3 build 103.1

<i>Compatible with Lightstreamer Server since 6.1.</i><br>
<i>May not be compatible with code developed with the previous version;
see compatibility notes below.</i><br>
<i>Released on 6 Jul 2017</i>

Fixed the reconnection algorithm, which, in the "full" Android client, could have caused a tight reconnection loop
in particular conditions when the Server is not available.

Added the null annotations (according to JSR 305) in the class files of public
classes, to better support library use with Kotlin and any other language which
leverages JSR 305.
<b>COMPATIBILITY NOTE:</b> <i>Existing code written in Kotlin
and similar languages may no longer compile and should be aligned with the new
method signatures. No issues are expected for existing Java code.</i>


## 3.1.1 build 100

<i>Compatible with Lightstreamer Server since 6.1.</i><br>
<i>Compatible with code developed with the previous version.</i><br>
<i>Released on 29 May 2017</i>

Fixed a bug introduced in the previous release, as unnecessary encoding
of characters in the requests was saved. The bug caused any '+' characters
in the requests (for instance, in group/schema names and in client messages)
to be handled incorrectly.

Fixed a bug which caused polling on WebSocket to fail. Note that this feature
is available, but very rarely used.


## 3.1.0 build 99

<i>Compatible with Lightstreamer Server since 6.1.</i><br>
<i>Compatible with code developed with the previous version.</i><br>
<i>Released on 25 May 2017</i>

Fixed the support for proxies, which could have caused connections to fail in case
a public DNS were not available from the client host.

Fixed a bug which affected any unsubscription request issued so early
that the corresponding subscription request had not been completed yet.
Such unsubscription could have caused onSubscriptionError with code 19
and the item would have been left subscribed to by the session on the Server,
yet considered not subscribed to by the library.

Revised the support for cookies:
<ul>
 <li>Fixed cookie handling in the "compact" version of the library, whereby
  some cookie settings received from Lightstreamer Server could have been
  ignored due to a case sensitive test of the "Set-Cookie" header name.</li>
 <li>Fixed a missing case in cookie handling, concerning the propagation
  of cookies received from Lightstreamer Server upon a WebSocket handshake.
  Note that the case in which new cookies are received in this way
  is unlikely to occur.
  This issue did not affect the "compact" version of the library.</li>
 <li>Regarding cookies received from the Server upon normal HTTP requests,
  fixed the handling of the case in which multiple cookie settings
  are received, as only one of them was considered and the other ones
  were ignored.
  This issue did not affect the "compact" version of the library.</li>
 <li>Enforced cookie handling regardless that a default java.net.CookieHandler
  has been set. If not set at the beginning, a local cookie store will
  be setup. Note that, in this case, cookies will not be automatically
  shared with other connections performed by the application.</li>
 <li>Clarified in the docs that, in the "full" (i.e. non "compact") version
  of the library, the default java.net.CookieHandler is supported only
  if it is of type java.net.CookieManager.</li>
 <li>Added static methods addCookies and getCookies in LightstreamerClient,
  to allow for cookie sharing with the rest of the application when a local
  cookie store is being used.</li>
 <li>Removed the support for the "Set-Cookie2" HTTP header, whose specification
  has been deprecated and the use discontinued.</li>
</ul>

Improved the efficiency by avoiding unnecessary encoding of characters in the
requests (for instance, in group/schema names and in client messages).
In particular, non-ascii characters are no longer encoded.

Introduced a configuration property to choose whether multiple instances
of LightstreamerClient should use dedicated threads for their internal operations
instead of sharing a single one. See the Javadoc comment for the LightstreamerClient
class for details.

Fixed spurious entries in the Javadoc of the Subscription class.


## 3.0.1 build 98

<i>Compatible with Lightstreamer Server since 6.1.</i><br>
<i>Compatible with code developed with the previous version.</i><br>
<i>Released on 2 Feb 2017</i>

Fixed a bug in TLS management for the newly introduced support of WebSockets,
whereby, when the EarlyWSOpenEnabled property was true, the WebSocket
connections was open without performing the TLS certificate check.
However, in case of an untrusted certificate, still no request could
be sent to the Server and no session could be opened. Obviously, the bug
did not affect the "compact" version of the library.

Improved the management of retries upon unsuccessful control requests.


## 3.0.0 build 97

<i>Compatible with Lightstreamer Server since 6.1.</i><br>
<i>May not be compatible with code developed with the previous version;
see compatibility notes below.</i><br>
<i>Released on 20 Jan 2017</i>

Introduced the use of WebSockets both for streaming and for subscription
and client message requests, which brings an overall performance improvement.<br/>
As a consequence, setForcedTransport now also supports the "WS", "WS-STREAMING",
and "WS-POLLING" values and the predisposed setEarlyWSOpenEnabled method
is now effective.<br/>
Since the use of WebSockets requires a larger memory footprint, an alternative
"compact" version of the library, which gives up WebSocket support, is also
available via Maven with name ls-android-client-compact.

Replaced the "maxBandwidth" property of the ConnectionOptions bean with two
distinct properties: "requestedMaxBandwidth" and the read-only "realMaxBandwidth",
so that the setting is made with the former, while the value applied by
the Server is only reported by the latter, now including changes during session
life. The extension affects the getter and setter names and also the invocations
of onPropertyChange on the ClientListener (see the docs for details).
<b>COMPATIBILITY NOTE:</b> <i>Custom code using "maxBandwidth"
in any of the mentioned forms has to be ported and recompiled. If the property
is not used in any form, existing compiled code can still run against the new
library.</i>

Introduced a new callback, "onRealMaxFrequency", to the SubscriptionListener,
to report the frequency constraint on the subscription as determined by the
Server and their changes during subscription life. See the docs for details
and special cases.
<b>COMPATIBILITY NOTE:</b> <i>Custom code has to be ported,
by implementing the new method, and recompiled. Existing compiled code should
still run against the new library: invocations to onRealMaxFrequency to the
custom listener would cause an exception that would be caught internally.</i>

Introduced a new property, "clientIp", in the ConnectionDetails bean;
it is a read-only property with the related getter and keyword for
onPropertyChange (see the docs for details).

Completed the implementation of methods whose implementation was only partial.
This regards:
<ul>
  <li>setFieldSchema in Subscription now also working for COMMAND mode subscriptions;</li>
  <li>setSlowingEnabled in ConnectionOptions now working if true is supplied;</li>
  <li>onClearSnapshot in the SubscriptionListener now invoked as expected.</li>
</ul>

Removed a restriction on field names that can be supplied to a Subscription object
within a "field list"; names made by numbers are now allowed.
Obviously, the final validation on field names is made by the Metadata Adapter.

Fixed a bug on the implementation of disconnect(), whereby an ongoing loop
of connection attempt was not interrupted in case of Server down or wrong address.

Revised the sendMessage implementation in the HTTP case, to limit recovery actions
when messages are not to be ordered and a listener is not provided.<br/>
Revised sendMessage to accept 0 as a legal value for the "delayTimeout" argument;
negative values will now be accepted to mean that the Server default timeout is to be used.
<b>COMPATIBILITY NOTE:</b> <i>Existing code using the 5-argument
version of sendMessage and supplying 0 as "delayTimeout" must be modified to use
-1 instead. Invocations to the 1-argument version don't have to be modified.</i>

Added new error codes 66 and 68 to onServerError, onSubscriptionError, and
onSecondLevelSubscriptionError, to report server-side issues; previously,
upon such problems, the connection was just interrupted.<br/>
Added missing error code 60 to onServerError documentation; this error
reports server-side licensing limitations.<br/>
Removed error code 20 from onSubscriptionError and onSecondLevelSubscriptionError
documentation; when a subscription request cannot find the session,
the session is just closed and recovered immediately.<br/>
Revised the documentation of the possible error codes.

Slightly delayed the availability of the "serverSocketName" property of the
ConnectionDetails bean, which was already valued upon session start.
<b>COMPATIBILITY NOTE:</b> <i>Custom code using
getServerSocketName right after a session start, should ensure that
onPropertyChange for "serverSocketName" gets invoked first.</i>

Added the support for non standard unicode names, if supplied as hostnames
in Lightstreamer Server's &lt;control_link_address&gt; configuration element.

Removed useless requests to the Server for bandwidth change when the Server
is not configured for bandwidth management.

Improved the management of setHttpExtraHeadersOnSessionCreationOnly, when true.
Previously, the extra headers (supplied with setHttpExtraHeaders) were still
sent, redundantly, on control requests.

Revised the default setting for the "ContentLength" property of ConnectionOptions,
to allow the library to set it to the best value.

Clarified in the documentation the meaning of null in setRequestedMaxFrequency
and setRequestedBufferSize. Extended setRequestedMaxFrequency to allow the setting
also when the subscription is "active" and the current value is null.

Improved the documentation of various methods in regard to the special case
of two-level subscriptions.<br/>
Detailed the documentation of the various
property getters and setters in regard to the possible values.

Improved the Javadocs, by shortening the concise descriptions of some classes
and methods.


## 2.0.4 build 94.1

<i>Compatible with Lightstreamer Server since 6.0.1.</i><br>
<i>Compatible with code developed with the previous version.</i><br>
<i>Released on 23 Nov 2016</i>

Fixed a bug, regarding only subscriptions in COMMAND mode,
which caused unchanged fields to be redundantly indicated as changed.
The bug didn't affect the returned data values.


## 2.0.3 build 94

<i>Compatible with Lightstreamer Server since 6.0.1.</i><br>
<i>Compatible with code developed with the previous version.</i><br>
<i>Released on 14 Nov 2016</i>

Fixed a bug which could have caused onSubscriptionError and
onCommandSecondLevelSubscriptionError to report wrong error codes,
that is, codes different from the documented ones.

Fixed the documentation of onClearSnapshot, which is only predisposed.


## 2.0.2 build 92

<i>Compatible with Lightstreamer Server since 6.0.1.</i><br>
<i>Compatible with code developed with the previous version.</i><br>
<i>Released on 30 Aug 2016</i>

Added meta-information on method argument names for interface classes,
so that developer GUIs can take advantage of them.


## 2.0.1 build 88

<i>Compatible with Lightstreamer Server since 6.0.1.</i><br>
<i>Compatible with code developed with the previous version.</i><br>
<i>Released on 22 Jul 2016</i>

Clarified the documentation in regard to a few API methods that still have
a partial implementation or are just predisposed and not implemented yet.


## 2.0.0 build 87

<i>Compatible with Lightstreamer Server since 6.0.1.</i><br>
<i>May not be compatible with code developed with the previous version;
see compatibility notes below.</i><br>
<i>Released on 10 May 2016</i>

Changed the names of some properties in the ConnectionOptions bean. To resume:
<ul>
 <li>keepaliveMillis has become keepaliveInterval</li>
 <li>idleMillis has become idleTimeout</li>
 <li>pollingMillis has become pollingInterval</li>
 <li>reverseHeartbeatMillis has become reverseHeartbeatInterval</li>
</ul>
This affects the getter and setter names and also the invocations of
onPropertyChange on the ClientListener. <b>COMPATIBILITY NOTE:</b> <i>   Custom code using any of the mentioned properties has to be ported and any
related binaries have to be recompiled.</i>

Fixed potential NullPointerException which could be thrown when setting or updating
fields and/or items list if logging is enabled at DEBUG level.


Changed the type and behavior of the getConnectTimeout/setConnectTimeout. This setting is now
represented as a String in order to accept the "auto" value. If "auto" is specified the value used
internally will be chosen (and possibly changed overtime) by the library itself. Note that "auto" is
also the new default value.
To check and or modify the current value, a new CurrentConnectTimeout property,
with its getter/setter pair in ConnectionOptions, is exposed.
<b>COMPATIBILITY NOTE:</b> <i>if the setConnectTimeout method is called
by the client code, the given parameter must be modified to be a String. If the getConnectTimeout
method is called by the client code its receiving variable must be converted to a String; moreover
it is likely that getConnectTimeout calls should be replaced by getCurrentConnectTimeout ones.</i><br/>
See the docs for further details.


Prevented reconnection attempts upon wrong answers from the Server.

Fixed an error in the log of the keepalive interval setting.

Fixed the documentation of onServerError and onStatusChange, to specify that onServerError
is always preceded, not followed, by onStatusChange with DISCONNECTED.

Revised javadoc formatting style and fixed various typos in the javadocs.


## 2.0 a2 build 74

<i>Compatible with Lightstreamer Server since 6.0.1.</i><br>
<i>May not be compatible with code developed with the previous version;
see compatibility notes below.</i><br>

Resorted to an external package for the log support.
<b>COMPATIBILITY NOTE:</b> <i>Custom code using the LoggerProvider
interface should be revised, based on the new documentation; see setLoggerProvider.</i>

Improved the handling of long polling.

Added missing documentation of setProxy in ConnectionOptions.


## 2.0 a1 build 66

<i>Compatible with Lightstreamer Server since 6.0.1.</i><br>
<i>Made available as a prerelease on 16 Jul 2015</i>

Introduced as an improved alternative to the SDK for Android Clients.
The interface offered is completely different, and it is very similar
to the one currently exposed by the SDK for JavaScript Clients.<br/>
see "sdk_client_android_alpha".
