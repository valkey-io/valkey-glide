/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.connectors.handlers;

import static glide.api.models.GlideString.gs;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import glide.api.logging.Logger;
import glide.api.models.GlideString;
import glide.api.models.PubSubMessage;
import glide.api.models.configuration.BaseSubscriptionConfiguration.MessageCallback;
import glide.api.models.exceptions.GlideException;
import glide.managers.BaseResponseResolver;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import response.ResponseOuterClass.Response;

/** Handler for incoming push messages (subscriptions). */
@Getter
@RequiredArgsConstructor
public class MessageHandler {

    /** A wrapper for exceptions thrown from {@link MessageCallback} implementations. */
    static class MessageCallbackException extends Exception {
        private MessageCallbackException(Exception cause) {
            super(cause);
        }

        @Override
        public synchronized Exception getCause() {
            // Overridden to restrict the return type to Exception rather than Throwable.
            return (Exception) super.getCause();
        }
    }

    // TODO maybe store `BaseSubscriptionConfiguration` as is?
    /**
     * A user callback to call for every incoming message, if given. If missing, messages are pushed
     * into the {@link #queue}.
     */
    private final Optional<MessageCallback> callback;

    /** An arbitrary user object to be passed to callback. */
    private final Optional<Object> context;

    /** Helper which extracts data from received {@link Response}s from GLIDE. */
    private final BaseResponseResolver responseResolver;

    /** A message queue wrapper. */
    @Getter(
            onMethod_ = {
                @SuppressFBWarnings(
                        value = "EI_EXPOSE_REP",
                        justification = "Queue is intentionally shared for asynchronous message consumption")
            })
    private final PubSubMessageQueue queue = new PubSubMessageQueue();

    /** Process a push (PUBSUB) message received as a part of {@link Response} from GLIDE. */
    void handle(Response response) throws MessageCallbackException {
        Object data = responseResolver.apply(response);
        if (!(data instanceof Map)) {
            Logger.log(
                    Logger.Level.WARN,
                    "invalid push",
                    "Received invalid push: empty or in incorrect format.");
            throw new GlideException("Received invalid push: empty or in incorrect format.");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> push = (Map<String, Object>) data;
        PushKind pushType = Enum.valueOf(PushKind.class, push.get("kind").toString());
        // The objects in values will actually be byte[].
        Object[] values = (Object[]) push.get("values");

        switch (pushType) {
            case Disconnection:
                Logger.log(
                        Logger.Level.WARN,
                        "disconnect notification",
                        "Transport disconnected, messages might be lost");
                break;
            case PMessage:
                handle(
                        new PubSubMessage(
                                gs((byte[]) values[2]), gs((byte[]) values[1]), gs((byte[]) values[0])));
                return;
            case Message:
            case SMessage:
                handle(new PubSubMessage(gs((byte[]) values[1]), gs((byte[]) values[0])));
                return;
            case Subscribe:
            case PSubscribe:
            case SSubscribe:
            case Unsubscribe:
            case PUnsubscribe:
            case SUnsubscribe:
                // ignore for now
                Logger.log(
                        Logger.Level.INFO,
                        "subscribe/unsubscribe notification",
                        () ->
                                String.format(
                                        "Received push notification of type '%s': %s",
                                        pushType,
                                        Arrays.stream(values)
                                                .map(v -> GlideString.of(v).toString())
                                                .collect(Collectors.joining(" "))));
                break;
            default:
                Logger.log(
                        Logger.Level.WARN,
                        "unknown notification",
                        () -> String.format("Unknown notification message: '%s'", pushType));
        }
    }

    /** Process a {@link PubSubMessage} received. */
    private void handle(PubSubMessage message) throws MessageCallbackException {
        if (callback.isPresent()) {
            try {
                callback.get().accept(message, context.orElse(null));
            } catch (Exception callbackException) {
                throw new MessageCallbackException(callbackException);
            }
            // Note: Error subclasses are uncaught and will just propagate.
        } else {
            queue.push(message);
        }
    }

    /** Push type enum copy-pasted 1:1 from `redis-rs`. */
    enum PushKind {
        /// `Disconnection` is sent from the **library** when connection is closed.
        Disconnection,
        /// Other kind to catch future kinds.
        Other,
        /// `invalidate` is received when a key is changed/deleted.
        Invalidate,
        /// `message` is received when pubsub message published by another client.
        Message,
        /// `pmessage` is received when pubsub message published by another client and client subscribed
        // to topic via pattern.
        PMessage,
        /// `smessage` is received when pubsub message published by another client and client subscribed
        // to it with sharding.
        SMessage,
        /// `unsubscribe` is received when client unsubscribed from a channel.
        Unsubscribe,
        /// `punsubscribe` is received when client unsubscribed from a pattern.
        PUnsubscribe,
        /// `sunsubscribe` is received when client unsubscribed from a shard channel.
        SUnsubscribe,
        /// `subscribe` is received when client subscribed to a channel.
        Subscribe,
        /// `psubscribe` is received when client subscribed to a pattern.
        PSubscribe,
        /// `ssubscribe` is received when client subscribed to a shard channel.
        SSubscribe,
    }

    /**
     * An asynchronous FIFO message queue for {@link PubSubMessage} backed by {@link
     * ConcurrentLinkedDeque}.
     */
    public static class PubSubMessageQueue {
        /** The queue itself. */
        final ConcurrentLinkedDeque<PubSubMessage> messageQueue = new ConcurrentLinkedDeque<>();

        /**
         * A promise for the first incoming message. Returned to a user, if message queried in async
         * manner, but nothing received yet.
         */
        CompletableFuture<PubSubMessage> firstMessagePromise = new CompletableFuture<>();

        /** A flag whether a user already got a {@link #firstMessagePromise}. */
        private boolean firstMessagePromiseRequested = false;

        /** A private object used to synchronize {@link #push} and {@link #popAsync}. */
        private final Object lock = new Object();

        // TODO Rework to remove or reduce `synchronized` blocks.
        //  If remove it now, some messages get reordered.

        /** Store a new message. */
        public void push(PubSubMessage message) {
            synchronized (lock) {
                if (firstMessagePromiseRequested) {
                    firstMessagePromiseRequested = false;
                    firstMessagePromise.complete(message);
                    firstMessagePromise = new CompletableFuture<>();
                    return;
                }

                messageQueue.addLast(message);
            }
        }

        /** Get a promise for a next message. */
        @SuppressFBWarnings(
                value = "EI_EXPOSE_REP",
                justification = "Future represents pending queue state and must be shared with caller")
        public CompletableFuture<PubSubMessage> popAsync() {
            synchronized (lock) {
                PubSubMessage message = messageQueue.poll();
                if (message == null) {
                    // this makes first incoming message to be delivered into `firstMessagePromise` instead of
                    // `messageQueue`
                    firstMessagePromiseRequested = true;
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
}
