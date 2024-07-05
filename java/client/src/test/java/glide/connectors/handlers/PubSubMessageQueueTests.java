/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.connectors.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.models.PubSubMessage;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30) // sec
public class PubSubMessageQueueTests {

    private void checkFutureStatus(CompletableFuture<PubSubMessage> future, boolean shouldBeDone) {
        assertEquals(shouldBeDone, future.isDone());
        assertFalse(future.isCancelled());
        assertFalse(future.isCompletedExceptionally());
    }

    @Test
    @SneakyThrows
    public void async_read_messages_then_add() {
        var queue = new PubSubMessageQueue();

        // read async - receiving promises
        var promise1 = queue.popAsync();
        var promise2 = queue.popAsync();

        // async reading from empty queue returns the same future for the same message
        assertSame(promise1, promise2);
        checkFutureStatus(promise1, false);
        assertTrue(queue.messageQueue.isEmpty());

        // now - add
        var msg1 = new PubSubMessage("one", "one");
        var msg2 = new PubSubMessage("two", "two");
        var msg3 = new PubSubMessage("three", "three");
        queue.push(msg1);
        queue.push(msg2);
        queue.push(msg3);

        // promises should get resolved automagically
        checkFutureStatus(promise1, true);
        assertSame(msg1, promise1.get());
        // `firstMessagePromise` is a new uncompleted future
        checkFutureStatus(queue.firstMessagePromise, false);
        // and `msg1` isn't stored in the Q
        assertEquals(2, queue.messageQueue.size());
        assertSame(msg2, queue.messageQueue.pop());
        assertSame(msg3, queue.messageQueue.pop());
        // now MQ should be empty
        assertTrue(queue.messageQueue.isEmpty());
    }

    @Test
    @SneakyThrows
    public void sync_read_messages_then_add() {
        var queue = new PubSubMessageQueue();

        // read async - receiving nulls
        assertNull(queue.popSync());
        assertNull(queue.popSync());

        // `firstMessagePromise` remains unset and unused
        checkFutureStatus(queue.firstMessagePromise, false);
        // and Q is empty
        assertTrue(queue.messageQueue.isEmpty());

        // now - add
        var msg1 = new PubSubMessage("one", "one");
        var msg2 = new PubSubMessage("two", "two");
        var msg3 = new PubSubMessage("three", "three");
        queue.push(msg1);
        queue.push(msg2);
        queue.push(msg3);

        // `firstMessagePromise` remains unset and unused
        checkFutureStatus(queue.firstMessagePromise, false);
        // all 3 messages are stored in the Q
        assertEquals(3, queue.messageQueue.size());

        // reading them
        assertSame(msg1, queue.popSync());
        assertSame(msg2, queue.popSync());
        assertSame(msg3, queue.popSync());
    }

    @Test
    @SneakyThrows
    public void add_messages_then_read() {
        var queue = new PubSubMessageQueue();

        var msg1 = new PubSubMessage("one", "one");
        var msg2 = new PubSubMessage("two", "two");
        var msg3 = new PubSubMessage("three", "three");
        var msg4 = new PubSubMessage("four", "four");
        queue.push(msg1);
        queue.push(msg2);
        queue.push(msg3);
        queue.push(msg4);

        // `firstMessagePromise` remains unset and unused
        checkFutureStatus(queue.firstMessagePromise, false);
        // all messages are stored in the Q
        assertEquals(4, queue.messageQueue.size());

        // now - read one async
        assertSame(msg1, queue.popAsync().get());
        // `firstMessagePromise` remains unset and unused
        checkFutureStatus(queue.firstMessagePromise, false);
        // Q stores remaining 3 messages
        assertEquals(3, queue.messageQueue.size());

        // read sync
        assertSame(msg2, queue.popSync());
        checkFutureStatus(queue.firstMessagePromise, false);
        assertEquals(2, queue.messageQueue.size());

        // keep reading
        // get a future for the next message
        var future = queue.popAsync();
        checkFutureStatus(future, true);
        checkFutureStatus(queue.firstMessagePromise, false);
        assertEquals(1, queue.messageQueue.size());
        // then read sync
        assertSame(msg4, queue.popSync());
        // nothing remains in the Q
        assertEquals(0, queue.messageQueue.size());
        // message 3 isn't lost - it is stored in `future`
        assertSame(msg3, future.get());
    }

    @Test
    @SneakyThrows
    public void getting_messages_reordered_on_concurrent_async_and_sync_read() {
        var queue = new PubSubMessageQueue();
        var msg1 = new PubSubMessage("one", "one");
        var msg2 = new PubSubMessage("two", "two");
        queue.push(msg1);
        queue.push(msg2);

        var readMessages = new ArrayList<PubSubMessage>(2);

        // assuming thread 1 started async read
        var future = queue.popAsync();
        // and got raced by thread 2 which reads sync
        var msg = queue.popSync();
        readMessages.add(msg);
        // then thread 1 continues
        msg = future.get();
        readMessages.add(msg);

        // messages get reordered since stored into a single collection (even if is a concurrent one)
        assertEquals(List.of(msg2, msg1), readMessages);

        // another example

        // reading async before anything added to the queue
        future = queue.popAsync();
        // queue gets 2 messages
        queue.push(msg1);
        queue.push(msg2);
        // but inside the queue only one is stored
        assertEquals(1, queue.messageQueue.size());
        // then if we read sync, we receive only second one
        assertSame(msg2, queue.popSync());
        // future gets resolved by the first message
        assertSame(msg1, future.get());
    }

    // Not merging `concurrent_write_async_read` and `concurrent_write_sync_read`, because
    // concurrent sync and async read may reorder messages

    @Test
    @SneakyThrows
    public void concurrent_write_async_read() {
        var queue = new PubSubMessageQueue();
        var numMessages = 1000; // test takes ~0.5 sec
        // collections aren't concurrent, since we have only 1 reader and 1 writer so far
        var expected = new LinkedList<PubSubMessage>();
        var actual = new LinkedList<PubSubMessage>();
        var rand = new Random();
        for (int i = 0; i < numMessages; i++) {
            expected.add(new PubSubMessage(i + " " + UUID.randomUUID(), UUID.randomUUID().toString()));
        }

        Runnable writer =
                () -> {
                    for (var message : expected) {
                        queue.push(message);
                        try {
                            Thread.sleep(rand.nextInt(2));
                        } catch (InterruptedException ignored) {
                        }
                    }
                };
        Runnable reader =
                () -> {
                    PubSubMessage message = null;
                    do {
                        try {
                            message = queue.popAsync().get(15, TimeUnit.MILLISECONDS);
                            actual.add(message);
                            Thread.sleep(rand.nextInt(4));
                        } catch (TimeoutException ignored) {
                            // all messages read
                            break;
                        } catch (Exception ignored) {
                        }
                    } while (message != null);
                };

        // start reader and writer and wait for finish
        CompletableFuture.allOf(CompletableFuture.runAsync(writer), CompletableFuture.runAsync(reader))
                .get();

        // this verifies message order
        assertEquals(expected, actual);
    }

    @Test
    @SneakyThrows
    public void concurrent_write_sync_read() {
        var queue = new PubSubMessageQueue();
        var numMessages = 1000; // test takes ~0.5 sec
        // collections aren't concurrent, since we have only 1 reader and 1 writer so far
        var expected = new LinkedList<PubSubMessage>();
        var actual = new LinkedList<PubSubMessage>();
        var rand = new Random();
        for (int i = 0; i < numMessages; i++) {
            expected.add(new PubSubMessage(i + " " + UUID.randomUUID(), UUID.randomUUID().toString()));
        }

        Runnable writer =
                () -> {
                    for (var message : expected) {
                        queue.push(message);
                        try {
                            Thread.sleep(rand.nextInt(2));
                        } catch (InterruptedException ignored) {
                        }
                    }
                };
        Runnable reader =
                () -> {
                    do {
                        try {
                            var message = queue.popSync();
                            if (message != null) {
                                actual.add(message);
                            }
                            Thread.sleep(rand.nextInt(4));
                        } catch (InterruptedException ignored) {
                        }
                    } while (actual.size() < expected.size());
                };
        // start reader and writer and wait for finish
        CompletableFuture.allOf(CompletableFuture.runAsync(writer), CompletableFuture.runAsync(reader))
                .get();

        // this verifies message order
        assertEquals(expected, actual);
    }
}
