package com.lightstreamer.client.transport.providers.netty;

import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.ThreadSafe;

import com.lightstreamer.client.Constants;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;

import io.netty.channel.Channel;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.util.AttributeKey;

/**
 * A channel pool handler which closes a socket when the socket it is idle for a while.
 * <p>
 * The strategy of automatic closing of the idle sockets rests on the following assumptions:
 * <ol>
 * <li>each channel has its own instance of {@link IdleStateTimer} as a channel attribute with key {@link #IDLE_KEY}</li>
 * <li>a timer is started when a channel is released to the pool (see {@link IdleStateTimer#setIdle()})</li>
 * <li>the timer is stopped when the channel is created/acquired (see {@link IdleStateTimer#setActive()})</li>
 * </ol>
 * 
 * 
 * @since September 2017
 */
public class BaseChannelPoolHandler implements ChannelPoolHandler {
    
    private static final Logger log = LogManager.getLogger(Constants.NETTY_POOL_LOG);
    
    /**
     * Name of the attribute pointing to the idle state handler of a channel.
     */
    private static final AttributeKey<IdleStateTimer> IDLE_KEY = AttributeKey.newInstance("idleStateTimer");
    
    /**
     * Number of nanoseconds after which an idle socket is closed.
     */
    private static final long closeSocketTimeoutNs;
    
    static {
        /*
         * If the system variable "com.lightstreamer.socket.pooling" is set to false, the sockets are closed
         * immediately after they are released to the pool.
         */
        if ("false".equals(System.getProperty("com.lightstreamer.socket.pooling"))) {
            log.warn("Socket pooling is disabled");
            closeSocketTimeoutNs = 0;
        } else {
            closeSocketTimeoutNs = Constants.CLOSE_SOCKET_TIMEOUT_MILLIS * 1_000_000L;
        }
    }
    
    @Override
    public void channelReleased(Channel ch) throws Exception {
        ch.attr(IDLE_KEY).get().setIdle();
    }

    @Override
    public void channelAcquired(Channel ch) throws Exception {
        ch.attr(IDLE_KEY).get().setActive();
    }

    @Override
    public void channelCreated(Channel ch) throws Exception {
        if (! ch.hasAttr(IDLE_KEY)) {
            ch.attr(IDLE_KEY).set(new IdleStateTimer(ch));
        }
        ch.attr(IDLE_KEY).get().setActive();
    }
    
    /**
     * Timer closing idle channels.
     */
    @ThreadSafe
    private static class IdleStateTimer implements Runnable {

        private boolean idle;
        private long lastIdleTimeNs;
        private final Channel ch;
        
        public IdleStateTimer(Channel ch) {
            this.ch = ch;
        }

        /**
         * Sets the channel as active to disable the automatic closing.
         */
        public synchronized void setActive() {
            idle = false;
        }
        
        /**
         * Sets the channel as idle. If the channel stays idle longer than {@link Constants#CLOSE_SOCKET_TIMEOUT_MILLIS},
         * the channel is closed.
         */
        public synchronized void setIdle() {
            idle = true;
            lastIdleTimeNs = System.nanoTime();
            // the scheduler runs the method run() below
            if (closeSocketTimeoutNs > 0) {
                ch.eventLoop().schedule(this, closeSocketTimeoutNs, TimeUnit.NANOSECONDS);
                
            } else {
                /* socket pooling is disabled */
                ch.close();
                if (log.isDebugEnabled()) {                                                
                    log.debug("Channel closed [" + ch.id() + "]");
                }
            }
        }
        
        @Override
        public synchronized void run() {
            long elapsedNs = System.nanoTime() - lastIdleTimeNs;
            if (idle && elapsedNs >= closeSocketTimeoutNs) {
                ch.close();
                if (log.isDebugEnabled()) {                                                
                    log.debug("Channel closed [" + ch.id() + "]");
                }
                
            } else {
                if (log.isDebugEnabled()) {                
                    log.debug("Postpone close [" + ch.id() + "] idle=" + idle + " elapsed=" + elapsedNs);
                }
            }
        }
    } // IdleStateTimer
}
