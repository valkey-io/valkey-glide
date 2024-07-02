/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.connectors.handlers;

import glide.api.models.PubSubMessage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Future;

/** A FIFO message queue for {@link PubSubMessage} backed by {@link ConcurrentLinkedDeque}. */
public class PubSubMessageQueue {
    /** The queue itself. */
    private final ConcurrentLinkedDeque<PubSubMessage> mq = new ConcurrentLinkedDeque<>();

    /**
     * The head of the queue stored aside as a {@link Future}. Stores a promise to the first message.
     */
    private CompletableFuture<PubSubMessage> head = new CompletableFuture<>();

    /** An object to synchronize threads. */
    private final Object lock = 42;

    /** State of the queue. */
    private enum HeadState {
        // `head` is an empty CF which was never given to a user
        UNSET_UNREAD,
        // `head` is a non-empty CF, which was never given to a user
        SET_UNREAD,
        // `head` is unset, but was given to a user
        UNSET_READ,
        // `head` was set, and given to a user
        SET_READ,
    }

    private HeadState state = HeadState.UNSET_UNREAD;

    /** Store a new message. */
    public void push(PubSubMessage message) {
        synchronized (lock) {
            switch (state) {
                case UNSET_UNREAD:
                    head.complete(message);
                    state = HeadState.SET_UNREAD;
                    break;
                case SET_UNREAD:
                    mq.push(message);
                    break;
                case SET_READ:
                case UNSET_READ:
                    head.complete(message);
                    head = new CompletableFuture<>();
                    state = HeadState.SET_UNREAD;
                    break;
            }
        }
    }

    /** Get a promise for the next message. */
    public CompletableFuture<PubSubMessage> pop() {
        synchronized (lock) {
            CompletableFuture<PubSubMessage> result = head;
            switch (state) {
                case UNSET_UNREAD:
                case SET_READ:
                    state = HeadState.UNSET_READ;
                    break;
                case SET_UNREAD:
                    head = new CompletableFuture<>();
                    if (mq.isEmpty()) {
                        state = HeadState.SET_READ;
                        break;
                    }
                    head.complete(mq.pop());
                    // no state change
                    break;
                case UNSET_READ:
                    // no state change
                    break;
            }
            return result;
        }
    }
}
