package com.lightstreamer.client.transport.providers.netty.pool;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

import java.util.Deque;

import com.lightstreamer.client.Constants;
import com.lightstreamer.client.transport.providers.netty.pool.ChannelUpgradeFuture.ChannelUpgradeFutureListener;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.pool.ChannelHealthChecker;
import io.netty.channel.pool.ChannelPool;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.channel.pool.SimpleChannelPool;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.ThrowableUtil;

/**
 * Simple {@link ChannelPool} implementation which will acquire a channel from the parent pool if someone tries to acquire
 * a {@link Channel} but none is in the pool at the moment. No limit on the maximal concurrent {@link Channel}s is enforced.
 *
 * This implementation uses LIFO order for {@link Channel}s in the {@link ChannelPool}.
 * <p>
 * <b>NB</b> The code was adapted from {@link SimpleChannelPool}.
 */
public abstract class ChildChannelPool implements ChannelPool {
    protected static final Logger log = LogManager.getLogger(Constants.NETTY_POOL_LOG);
    protected static final Logger logStream = LogManager.getLogger(Constants.TRANSPORT_LOG);
    
    private static final AttributeKey<ChildChannelPool> POOL_KEY = AttributeKey.newInstance("myChannelPool");
    private static final IllegalStateException FULL_EXCEPTION = ThrowableUtil.unknownStackTrace(
            new IllegalStateException("ChannelPool full"), ChildChannelPool.class, "releaseAndOffer(...)");
    private static final IllegalStateException UNHEALTHY_NON_OFFERED_TO_POOL = ThrowableUtil.unknownStackTrace(
            new IllegalStateException("Channel is unhealthy not offering it back to pool"),
            ChildChannelPool.class, "releaseAndOffer(...)");

    private final Deque<Channel> deque = PlatformDependent.newConcurrentDeque();
    private final ChannelPoolHandler handler;
    private final ChannelHealthChecker healthCheck;
    private final Bootstrap bootstrap;
    private final boolean releaseHealthCheck;
    private final ChannelPool parentPool;

    /**
     * Creates a new channel pool.
     * 
     * @param bootstrap the {@link Bootstrap} used to create promises
     * @param parentPool the parent pool providing the channels to the child
     * @param handler the {@link ChannelPoolHandler} that will be notified for the different pool actions
     */
    public ChildChannelPool(Bootstrap bootstrap, ChannelPool parentPool, final ChannelPoolHandler handler) {
        this.handler = checkNotNull(handler, "handler");
        this.healthCheck = ChannelHealthChecker.ACTIVE;
        this.releaseHealthCheck = true;
        this.parentPool = checkNotNull(parentPool, "parentPool");
        this.bootstrap = checkNotNull(bootstrap, "bootstrap");
    }

    @Override
    public final Future<Channel> acquire() {
        return acquire(bootstrap.config().group().next().<Channel>newPromise());
    }

    @Override
    public Future<Channel> acquire(final Promise<Channel> promise) {
        checkNotNull(promise, "promise");
        return acquireHealthyFromPoolOrNew(promise);
    }

    /**
     * Tries to retrieve healthy channel from the pool if any or creates a new channel otherwise.
     * @param promise the promise to provide acquire result.
     * @return future for acquiring a channel.
     */
    private Future<Channel> acquireHealthyFromPoolOrNew(final Promise<Channel> promise) {
        try {
            final Channel ch = pollChannel();
            if (ch == null) {
                // No Channel left in the pool bootstrap a new Channel
                Bootstrap bs = bootstrap.clone();
                bs.attr(POOL_KEY, this);
                Future<Channel> fc = parentPool.acquire();
                ChannelUpgradeFuture f = connectChannel(fc);
                if (f.isDone()) {
                    notifyConnect(f, promise);
                } else {
                    f.addListener(new ChannelUpgradeFuture.ChannelUpgradeFutureListener() {
                        @Override
                        public void operationComplete(ChannelUpgradeFuture future) {
                            notifyConnect(future, promise);
                        }
                    });
                }
                return promise;
            }
            EventLoop loop = ch.eventLoop();
            if (loop.inEventLoop()) {
                doHealthCheck(ch, promise);
            } else {
                loop.execute(new Runnable() {
                    @Override
                    public void run() {
                        doHealthCheck(ch, promise);
                    }
                });
            }
        } catch (Throwable cause) {
            promise.tryFailure(cause);
        }
        return promise;
    }

    private void notifyConnect(ChannelUpgradeFuture future, Promise<Channel> promise) {
        if (future.isSuccess()) {
            try {
                Channel channel = future.channel();
                channel.attr(POOL_KEY).set(this);
                handler.channelCreated(channel);
                if (!promise.trySuccess(channel)) {
                    // Promise was completed in the meantime (like cancelled), just release the channel again
                    release(channel);
                }
                
            } catch (Exception e) {
                promise.tryFailure(e); // the exception may be thrown by channelCreated
            }
        } else {
            promise.tryFailure(future.cause());
        }
    }

    private void doHealthCheck(final Channel ch, final Promise<Channel> promise) {
        assert ch.eventLoop().inEventLoop();

        Future<Boolean> f = healthCheck.isHealthy(ch);
        if (f.isDone()) {
            notifyHealthCheck(f, ch, promise);
        } else {
            f.addListener(new FutureListener<Boolean>() {
                @Override
                public void operationComplete(Future<Boolean> future) throws Exception {
                    notifyHealthCheck(future, ch, promise);
                }
            });
        }
    }

    private void notifyHealthCheck(Future<Boolean> future, Channel ch, Promise<Channel> promise) {
        assert ch.eventLoop().inEventLoop();

        if (future.isSuccess()) {
            if (future.getNow()) {
                try {
                    ch.attr(POOL_KEY).set(this);
                    handler.channelAcquired(ch);
                    promise.setSuccess(ch);
                } catch (Throwable cause) {
                    closeAndFail(ch, cause, promise);
                }
            } else {
                closeChannel(ch);
                acquireHealthyFromPoolOrNew(promise);
            }
        } else {
            closeChannel(ch);
            acquireHealthyFromPoolOrNew(promise);
        }
    }

    /**
     * Upgrades a channel taken from the parent pool.
     */
    protected abstract ChannelUpgradeFuture connectChannel(Future<Channel> fc);

    @Override
    public final Future<Void> release(Channel channel) {
        return release(channel, channel.eventLoop().<Void>newPromise());
    }

    @Override
    public Future<Void> release(final Channel channel, final Promise<Void> promise) {
        checkNotNull(channel, "channel");
        checkNotNull(promise, "promise");
        try {
            EventLoop loop = channel.eventLoop();
            if (loop.inEventLoop()) {
                doReleaseChannel(channel, promise);
            } else {
                loop.execute(new Runnable() {
                    @Override
                    public void run() {
                        doReleaseChannel(channel, promise);
                    }
                });
            }
        } catch (Throwable cause) {
            closeAndFail(channel, cause, promise);
        }
        return promise;
    }

    private void doReleaseChannel(Channel channel, Promise<Void> promise) {
        assert channel.eventLoop().inEventLoop();
        // Remove the POOL_KEY attribute from the Channel and check if it was acquired from this pool, if not fail.
        if (channel.attr(POOL_KEY).getAndSet(null) != this) {
            closeAndFail(channel,
                         // Better include a stracktrace here as this is an user error.
                         new IllegalArgumentException(
                                 "Channel " + channel + " was not acquired from this ChannelPool"),
                         promise);
        } else {
            try {
                if (releaseHealthCheck) {
                    doHealthCheckOnRelease(channel, promise);
                } else {
                    releaseAndOffer(channel, promise);
                }
            } catch (Throwable cause) {
                closeAndFail(channel, cause, promise);
            }
        }
    }

    private void doHealthCheckOnRelease(final Channel channel, final Promise<Void> promise) throws Exception {
        final Future<Boolean> f = healthCheck.isHealthy(channel);
        if (f.isDone()) {
            releaseAndOfferIfHealthy(channel, promise, f);
        } else {
            f.addListener(new FutureListener<Boolean>() {
                @Override
                public void operationComplete(Future<Boolean> future) throws Exception {
                    releaseAndOfferIfHealthy(channel, promise, f);
                }
            });
        }
    }

    /**
     * Adds the channel back to the pool only if the channel is healty.
     * @param channel the channel to put back to the pool
     * @param promise offer operation promise.
     * @param future the future that contains information fif channel is healthy or not.
     * @throws Exception in case when failed to notify handler about release operation.
     */
    private void releaseAndOfferIfHealthy(Channel channel, Promise<Void> promise, Future<Boolean> future)
            throws Exception {
        if (future.getNow()) { //channel turns out to be healthy, offering and releasing it.
            releaseAndOffer(channel, promise);
        } else { //channel ont healthy, just releasing it.
            handler.channelReleased(channel);
            closeAndFail(channel, UNHEALTHY_NON_OFFERED_TO_POOL, promise);
        }
    }

    private void releaseAndOffer(Channel channel, Promise<Void> promise) throws Exception {
        if (offerChannel(channel)) {
            handler.channelReleased(channel);
            promise.setSuccess(null);
        } else {
            closeAndFail(channel, FULL_EXCEPTION, promise);
        }
    }

    private static void closeChannel(Channel channel) {
        channel.attr(POOL_KEY).getAndSet(null);
        channel.close();
    }

    private static void closeAndFail(Channel channel, Throwable cause, Promise<?> promise) {
        closeChannel(channel);
        promise.tryFailure(cause);
    }

    /**
     * Poll a {@link Channel} out of the internal storage to reuse it. This will return {@code null} if no
     * {@link Channel} is ready to be reused.
     *
     * Sub-classes may override {@link #pollChannel()} and {@link #offerChannel(Channel)}. Be aware that
     * implementations of these methods needs to be thread-safe!
     */
    protected Channel pollChannel() {
        return deque.pollLast();
    }

    /**
     * Offer a {@link Channel} back to the internal storage. This will return {@code true} if the {@link Channel}
     * could be added, {@code false} otherwise.
     *
     * Sub-classes may override {@link #pollChannel()} and {@link #offerChannel(Channel)}. Be aware that
     * implementations of these methods needs to be thread-safe!
     */
    protected boolean offerChannel(Channel channel) {
        return deque.offer(channel);
    }

    @Override
    public void close() {
        for (;;) {
            Channel channel = pollChannel();
            if (channel == null) {
                break;
            }
            channel.close();
        }
    }
}

