package com.lightstreamer.client.transport.providers.netty.pool;

import io.netty.channel.Channel;

/**
 * The result of an asynchronous I/O operation of upgrading of a channel.
 * 
 * 
 * @since August 2017
 */
public interface ChannelUpgradeFuture {

    /**
     * Listens to the result of a {@link ChannelUpgradeFuture}.
     */
    interface ChannelUpgradeFutureListener {
        void operationComplete(ChannelUpgradeFuture future);
    }

    /**
     * Returns {@code true} if this task completed.
     */
    boolean isDone();

    /**
     * Returns {@code true} if and only if the I/O operation was completed
     * successfully.
     */
    boolean isSuccess();

    /**
     * Sets the specified listener to this future.  The
     * specified listener is notified when this future is
     * {@linkplain #isDone() done}.  If this future is already
     * completed, the specified listener is notified immediately.
     */
    void addListener(ChannelUpgradeFuture.ChannelUpgradeFutureListener fl);

    /**
     * Returns a channel where the I/O operation associated with this
     * future takes place.
     */
    Channel channel();

    /**
     * Returns the cause of the failed I/O operation if the I/O operation has
     * failed.
     */
    Throwable cause();

}