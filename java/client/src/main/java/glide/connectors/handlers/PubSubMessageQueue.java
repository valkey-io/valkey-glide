/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.connectors.handlers;

import glide.api.models.PubSubMessage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An asynchronous FIFO message queue for {@link PubSubMessage} backed by {@link
 * ConcurrentLinkedDeque}.
 */
public class PubSubMessageQueue {
    // fields are protected to ease testing
    /** The queue itself. */
    protected final ConcurrentLinkedDeque<PubSubMessage> messageQueue = new ConcurrentLinkedDeque<>();

    /**
     * A promise for the first incoming message. Returned to a user, if message queried in async
     * manner, but nothing received yet.
     */
    protected CompletableFuture<PubSubMessage> firstMessagePromise = new CompletableFuture<>();

    /** A flag whether a user already got a {@link #firstMessagePromise}. */
    private final AtomicBoolean firstMessagePromiseRequested = new AtomicBoolean(false);

    /** A private object used to synchronize {@link #push} and {@link #popAsync}. */
    private final Object lock = new Object();

    // TODO Rework to remove or reduce `synchronized` blocks. If remove it now, some messages get
    // reordered.

    /** Store a new message. */
    public void push(PubSubMessage message) {
        synchronized (lock) {
            if (firstMessagePromiseRequested.getAndSet(false)) {
                firstMessagePromise.complete(message);
                firstMessagePromise = new CompletableFuture<>();
                return;
            }

            messageQueue.addLast(message);
        }
    }

    /** Get a promise for a next message. */
    public synchronized CompletableFuture<PubSubMessage> popAsync() {
        synchronized (lock) {
            PubSubMessage message = messageQueue.poll();
            if (message == null) {
                // this makes first incoming message to be delivered into `firstMessagePromise` instead of
                // `messageQueue`
                firstMessagePromiseRequested.set(true);
                return firstMessagePromise;
            }
            var future = new CompletableFuture<PubSubMessage>();
            future.complete(message);
            return future;
        }
    }

    /** Get a new message or null if nothing stored so far. */
    public PubSubMessage popSync() {
        return messageQueue.poll();
    }
}
