package com.lightstreamer.client.transport.providers.netty;

import com.lightstreamer.client.LightstreamerClient;
import com.lightstreamer.util.threads.ThreadShutdownHook;

/**
 * Releases the resources acquired by Netty library. It is used by {@link LightstreamerClient#disconnectFuture()}.
 * 
 * 
 * @since September 2017
 */
public class NettyShutdownHook implements ThreadShutdownHook {

    @Override
    public void onShutdown() {
        SingletonFactory.instance.close();
    }
}
