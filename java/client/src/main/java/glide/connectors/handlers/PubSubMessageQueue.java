/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.connectors.handlers;

import glide.api.models.PubSubMessage;
import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/** A FIFO message queue for {@link PubSubMessage}. */
public class PubSubMessageQueue {
    /** The queue itself. */
    private final LinkedList<PubSubMessage> messageQueue = new LinkedList<>();

    /**
     * The head of the queue stored aside as a {@link Future}. Stores a promise to the first message.
     */
    private CompletableFuture<PubSubMessage> head = new CompletableFuture<>();

    /** An object to synchronize threads. */
    private final Object lock = new Object();

    /** State of the queue. */
    private enum HeadState {
        // `head` is a new CF, which was never given to a user
        UNSET_UNREAD,
        // `head` is a non-empty CF, which was never given to a user
        SET_UNREAD,
        // `head` is unset, but was given to a user
        UNSET_READ,
    }

    private HeadState state = HeadState.UNSET_UNREAD;

    /** Store a new message. */
    public void push(PubSubMessage message) {
        synchronized (lock) {
            switch (state) {
                case SET_UNREAD:
                    messageQueue.push(message);
                    break;
                case UNSET_UNREAD:
                    head.complete(message);
                    state = HeadState.SET_UNREAD;
                    break;
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
                    state = HeadState.UNSET_READ;
                    break;
                case SET_UNREAD:
                    head = new CompletableFuture<>();
                    if (messageQueue.isEmpty()) {
                        state = HeadState.UNSET_UNREAD;
                        break;
                    }
                    head.complete(messageQueue.pop());
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
