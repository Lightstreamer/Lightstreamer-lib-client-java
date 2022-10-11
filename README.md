# Lightstreamer Client SDK

Lightstreamer Client SDK enables any JavaSE/Android application to communicate bidirectionally with a **Lightstreamer Server**. The API allows to subscribe to real-time data pushed by the server and to send any message to the server.

The library offers automatic recovery from connection failures, automatic selection of the best available transport, and full decoupling of subscription and connection operations. It is responsible of forwarding the subscriptions to the Server and re-forwarding all the subscriptions whenever the connection is broken and then reopened.

## Android support

The Android variant is available in two versions: "compact" and "full". The "compact" version leans on the basic JDK's HTTP implementation and has a smaller footprint at the cost of a few limitations. The most important limitation is that WebSockets are not available in the "compact" version. 

The Android library also offers support for mobile push notifications (MPN). While real-time subscriptions deliver their updates via the client connection, MPN subscriptions deliver their updates via push notifications, even when the application is offline. They are handled by a special module of the Server, the MPN Module, that keeps them active at all times and continues pushing with no need for a client connection.

## Installation

Lightstreamer JavaSE SDK requires Java version 8 or later.
 
To add a dependency using Maven, use the following:

```xml
<dependency>
  <groupId>com.lightstreamer</groupId>
  <artifactId>ls-javase-client</artifactId>
  <version>4.3.8</version>
</dependency>
```

To add a dependency using Gradle:

```gradle
dependencies {
  implementation("com.lightstreamer:ls-javase-client:4.3.8")
}
```

Lightstreamer Android SDK requires Android 6.0 (API level 23) or greater.

Note that the following examples refer to the normal, "full" library; for the limited, "compact" library, just replace 
*ls-android-client* with *ls-android-client-compact* in the instructions below.

To add a dependency using Maven, use the following:

```xml
<dependency>
  <groupId>com.lightstreamer</groupId>
  <artifactId>ls-android-client</artifactId>
  <version>4.2.6</version>
</dependency>
```

To add a dependency using Gradle:

```gradle
dependencies {
  implementation("com.lightstreamer:ls-android-client:4.2.6")
}
```

## Quickstart

To connect to a Lightstreamer Server, a [LightstreamerClient](https://lightstreamer.com/api/ls-javase-client/latest/com/lightstreamer/client/LightstreamerClient.html) object has to be created, configured, and instructed to connect to the Lightstreamer Server. 
A minimal version of the code that creates a LightstreamerClient and connects to the Lightstreamer Server on *https://push.lightstreamer.com* will look like this:

```java
LightstreamerClient client = new LightstreamerClient("https://push.lightstreamer.com/","DEMO");
client.connect();
```

For each subscription to be subscribed to a Lightstreamer Server a [Subscription](https://lightstreamer.com/api/ls-javase-client/latest/com/lightstreamer/client/Subscription.html) instance is needed.
A simple Subscription containing three items and two fields to be subscribed in *MERGE* mode is easily created (see [Lightstreamer General Concepts](https://www.lightstreamer.com/docs/ls-server/latest/General%20Concepts.pdf)):

```java
String[] items = { "item1","item2","item3" };
String[] fields = { "stock_name","last_price" };
Subscription sub = new Subscription("MERGE",items,fields);
sub.setDataAdapter("QUOTE_ADAPTER");
sub.setRequestedSnapshot("yes");
client.subscribe(sub);
```

Before sending the subscription to the server, usually at least one [SubscriptionListener](https://lightstreamer.com/api/ls-javase-client/latest/com/lightstreamer/client/SubscriptionListener.html) is attached to the Subscription instance in order to consume the real-time updates. The following code shows the values of the fields *stock_name* and *last_price* each time a new update is received for the subscription:

```java
sub.addListener(new SubscriptionListener() {
    public void onItemUpdate(ItemUpdate obj) {
    	System.out.println(obj.getValue("stock_name") + ": " + obj.getValue("last_price"));
    }
    // other methods...
});
```

## Mobile Push Notifications Quickstart

Mobile Push Notifications (MPN) are based on [Google's Firebase Cloud Messaging technology](https://firebase.google.com/docs/cloud-messaging).

Before you can use MPN services, you need to
- create a Firebase project to connect to your Android app (read carefully the Firebase documentation about [Set up a Firebase Cloud Messaging client app on Android](https://firebase.google.com/docs/cloud-messaging/android/client));
- configure the Lightstreamer MPN module (read carefully the section *5 Mobile and Web Push Notifications* in the [General Concepts guide](https://lightstreamer.com/docs/ls-server/7.1.1/General%20Concepts.pdf)).

After you have a Firebase project, you can create a [MPN device](https://lightstreamer.com/api/ls-android-client/latest/com/lightstreamer/client/mpn/android/MpnDevice.html), which represents a specific app running on a specific mobile device.

```java
FirebaseInstanceId.getInstance().getInstanceId().addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
    @Override
    public void onComplete(final Task<InstanceIdResult> task) {
        if (task.isSuccessful()) {
            MpnDevice device = new MpnDevice(context, task.getResult().getToken());
            client.registerForMpn(device);
        }
    }
});
```

To receive notifications, you need to subscribe to a [MPN subscription](https://lightstreamer.com/api/ls-android-client/latest/com/lightstreamer/client/mpn/MpnSubscription.html): it contains subscription details and the listener needed to monitor its status. Real-time data is routed via native push notifications.

```java
String[] items = { "item1","item2","item3" };
String[] fields = { "stock_name","last_price" };
MpnSubscription sub = new MpnSubscription("MERGE",items,fields);
Map<String, String> data= new HashMap<String, String>();
data.put("stock_name", "${stock_name}");
data.put("last_price", "${last_price}");
data.put("time", "${time}");
data.put("item", stockSubscription.getItems()[0]);
String format = new MpnBuilder().data(data).build();
sub.setNotificationFormat(format);
sub.setTriggerExpression("Double.parseDouble($[2])>45.0");
client.subscribe(sub, true);
```

The notification format lets you specify how to format the notification message. It can contain a special syntax that lets you compose the message with the content of the subscription updates (see §5.4.1 of the [General Concepts guide](https://lightstreamer.com/docs/ls-server/7.1.1/General%20Concepts.pdf) ).

The optional  trigger expression  lets you specify  when to send  the notification message: it is a boolean expression, in Java language, that when evaluates to true triggers the sending of the notification (see §5.4.2 of the [General Concepts guide](https://lightstreamer.com/docs/ls-server/7.1.1/General%20Concepts.pdf)). If not specified, a notification is sent each time the Data Adapter produces an update.

Finally, you need to configure a service that extends [FirebaseMessagingService](https://firebase.google.com/docs/reference/android/com/google/firebase/messaging/FirebaseMessagingService) in order to receive foreground/background notifications.
The steps are described in the Firebase documentation about [Receive messages in an Android app](https://firebase.google.com/docs/cloud-messaging/android/receive).
As an example, you can see the class [MyFirebaseMessagingService](https://github.com/Lightstreamer/Lightstreamer-example-MPNStockList-client-android/blob/215951071175063d36b134c0222cefe3416fb58b/app/src/main/java/com/lightstreamer/demo/android/fcm/MyFirebaseMessagingService.java) in the [Lightstreamer MPN StockList demo](https://github.com/Lightstreamer/Lightstreamer-example-MPNStockList-client-android).

## Logging

To enable the internal client logger, create an instance of [LoggerProvider](https://lightstreamer.com/api/ls-log-adapter-java/1.0.2/com/lightstreamer/log/LoggerProvider.html) and set it as the default provider of [LightstreamerClient](https://lightstreamer.com/api/ls-javase-client/latest/com/lightstreamer/client/LightstreamerClient.html).

### JavaSE

```java
SystemOutLogProvider loggerProvider = new SystemOutLogProvider();
LightstreamerClient.setLoggerProvider(loggerProvider);
```

Add also these dependencies to your project:

```gradle
dependencies {
  implementation("com.lightstreamer:ls-log-adapter-java:1.0.2")
  implementation("com.lightstreamer:java-system-out-log:1.0.2")
}
```

### Android

```java
AndroidLogProvider loggerProvider = new AndroidLogProvider();
LightstreamerClient.setLoggerProvider(loggerProvider);
```

Add also these dependencies to your project:

```gradle
dependencies {
  implementation("com.lightstreamer:ls-log-adapter-java:1.0.2")
  implementation("com.lightstreamer:android-log-wrapper:1.0.1")
}
```

## Building

### JavaSE client

To build the Lightstreamer JavaSE client, ensure that you have the JDK version 8 or higher.
Then, run the Gradle `build` task:

```sh
$ ./gradlew :ls-javase-client:build
```

After that, you can find all generated artifacts (library, javadocs, and source code) under the folder `javase-lib/build`.

### Android client

To build the Lightstreamer Android client you need:
- JDK version 8 or higher
- The *Android command line tools*.

To install the Android command line tools, follow these steps:
- [Download](https://developer.android.com/studio#command-tools) the tools package and extract it into `<your_android_sdk_root>/cmdline-tools` folder,
where `<your_android_sdk_root>` is a folder in your system.
- Export the `ANDROID_SDK_ROOT` environment variable:
  ```sh
  $ export ANDROID_SDK_ROOT=<your_android_sdk_root>
  ```
- From `<your_android_sdk_root>/cmdline-tools/tools/bin`, run the following command and accept all offered SDK package licenses:
  ```sh
  $ ./sdkmanager --licenses
  ```
  
Then, run the following commands:

```sh
$ ./gradlew :ls-android-client:preprocess
```

```sh
$ ./gradlew :ls-android-client:build
```

After that, you can find all generated artifacts (library, javadocs, and source code) under the folder `android-lib/build`.

To build the Lightstreamer Android compact client, run the commands above substituting `ls-android-client` with `ls-android-client-compact`. The generated files are under the folder `android-compact-lib/build`.

## Compatibility

- **JavaSE** library: compatible with Lightstreamer Server since version 7.0.

- **Android** library: compatible with Lightstreamer Server since version 7.1.

## Documentation

- [Live demos](https://demos.lightstreamer.com/?p=lightstreamer&t=client&lclient=java_client&sclientjava_client=android&sclientjava_client=javase)

- [JavaSE API Reference](https://lightstreamer.com/api/ls-javase-client/latest/)

- [Android API Reference](https://www.lightstreamer.com/api/ls-android-client/latest/)

- [Lightstreamer General Concepts](https://lightstreamer.com/docs/ls-server/7.1.1/General%20Concepts.pdf)

## Support

For questions and support please use the [Official Forum](https://forums.lightstreamer.com/). The issue list of this page is **exclusively** for bug reports and feature requests.

## License

[Apache 2.0](https://opensource.org/licenses/Apache-2.0)
