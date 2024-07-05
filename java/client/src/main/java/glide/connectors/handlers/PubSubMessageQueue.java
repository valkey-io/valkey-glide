/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.connectors.handlers;

import glide.api.models.PubSubMessage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/** A FIFO message queue for {@link PubSubMessage}. */
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
    private final AtomicBoolean headPromiseRequested = new AtomicBoolean(false);

    // TODO Rework to remove or reduce `synchronized` blocks. If remove it now, some messages get
    // reordered.

    /** Store a new message. */
    public synchronized void push(PubSubMessage message) {
        if (headPromiseRequested.getAndSet(false)) {
            firstMessagePromise.complete(message);
            firstMessagePromise = new CompletableFuture<>();
            return;
        }

        messageQueue.addLast(message);
    }

    /** Get a promise for a next message. */
    public synchronized CompletableFuture<PubSubMessage> popAsync() {
        PubSubMessage message;
        if ((message = messageQueue.poll()) != null) {
            var future = new CompletableFuture<PubSubMessage>();
            future.complete(message);
            return future;
        }

        headPromiseRequested.set(true);
        return firstMessagePromise;
    }

    /** Get a new message or null if nothing stored so far. */
    public PubSubMessage popSync() {
        return messageQueue.poll();
    }
}
