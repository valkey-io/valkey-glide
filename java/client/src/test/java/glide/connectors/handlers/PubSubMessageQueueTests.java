/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.connectors.handlers;

import static glide.api.models.GlideString.gs;
import static org.junit.jupiter.api.Assertions.*;

import glide.api.models.PubSubMessage;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
        var queue = new MessageHandler.PubSubMessageQueue();

        var promise1 = queue.popAsync();
        var promise2 = queue.popAsync();
        assertSame(promise1, promise2);
        checkFutureStatus(promise1, false);
        assertTrue(queue.messageQueue.isEmpty());

        var msg1 = new PubSubMessage(gs("one"), gs("one"));
        var msg2 = new PubSubMessage(gs("two"), gs("two"));
        var msg3 = new PubSubMessage(gs("three"), gs("three"));
        queue.push(msg1);
        queue.push(msg2);
        queue.push(msg3);

        checkFutureStatus(promise1, true);
        assertSame(msg1, promise1.get());
        checkFutureStatus(queue.firstMessagePromise, false);
        assertEquals(2, queue.messageQueue.size());
        assertSame(msg2, queue.messageQueue.pop());
        assertSame(msg3, queue.messageQueue.pop());
        assertTrue(queue.messageQueue.isEmpty());
    }

    @Test
    @SneakyThrows
    public void sync_read_messages_then_add() {
        var queue = new MessageHandler.PubSubMessageQueue();

        assertNull(queue.popSync());
        assertNull(queue.popSync());

        checkFutureStatus(queue.firstMessagePromise, false);
        assertTrue(queue.messageQueue.isEmpty());

        var msg1 = new PubSubMessage(gs("one"), gs("one"));
        var msg2 = new PubSubMessage(gs("two"), gs("two"));
        var msg3 = new PubSubMessage(gs("three"), gs("three"));
        queue.push(msg1);
        queue.push(msg2);
        queue.push(msg3);

        checkFutureStatus(queue.firstMessagePromise, false);
        assertEquals(3, queue.messageQueue.size());

        assertSame(msg1, queue.popSync());
        assertSame(msg2, queue.popSync());
        assertSame(msg3, queue.popSync());
    }

    @Test
    @SneakyThrows
    public void add_messages_then_read() {
        var queue = new MessageHandler.PubSubMessageQueue();

        var msg1 = new PubSubMessage(gs("one"), gs("one"));
        var msg2 = new PubSubMessage(gs("two"), gs("two"));
        var msg3 = new PubSubMessage(gs("three"), gs("three"));
        var msg4 = new PubSubMessage(gs("four"), gs("four"));
        queue.push(msg1);
        queue.push(msg2);
        queue.push(msg3);
        queue.push(msg4);

        checkFutureStatus(queue.firstMessagePromise, false);
        assertEquals(4, queue.messageQueue.size());

        assertSame(msg1, queue.popAsync().get());
        checkFutureStatus(queue.firstMessagePromise, false);
        assertEquals(3, queue.messageQueue.size());

        assertSame(msg2, queue.popSync());
        checkFutureStatus(queue.firstMessagePromise, false);
        assertEquals(2, queue.messageQueue.size());

        var future = queue.popAsync();
        checkFutureStatus(future, true);
        checkFutureStatus(queue.firstMessagePromise, false);
        assertEquals(1, queue.messageQueue.size());
        assertSame(msg4, queue.popSync());
        assertEquals(0, queue.messageQueue.size());
        assertSame(msg3, future.get());
    }

    @Test
    @SneakyThrows
    public void getting_messages_reordered_on_concurrent_async_and_sync_read() {
        var queue = new MessageHandler.PubSubMessageQueue();
        var msg1 = new PubSubMessage(gs("one"), gs("one"));
        var msg2 = new PubSubMessage(gs("two"), gs("two"));
        queue.push(msg1);
        queue.push(msg2);

        var readMessages = new ArrayList<PubSubMessage>(2);

        var future = queue.popAsync();
        var msg = queue.popSync();
        readMessages.add(msg);
        msg = future.get();
        readMessages.add(msg);

        assertEquals(List.of(msg2, msg1), readMessages);

        future = queue.popAsync();
        queue.push(msg1);
        queue.push(msg2);
        assertEquals(1, queue.messageQueue.size());
        assertSame(msg2, queue.popSync());
        assertSame(msg1, future.get());
    }

    @Test
    @SneakyThrows
    public void concurrent_write_async_read() {
        var queue = new MessageHandler.PubSubMessageQueue();
        var numMessages = 1000;
        var expected = new LinkedList<PubSubMessage>();
        var actual = new LinkedList<PubSubMessage>();
        var rand = new Random();
        for (int i = 0; i < numMessages; i++) {
            expected.add(
                    new PubSubMessage(gs(i + " " + UUID.randomUUID()), gs(UUID.randomUUID().toString())));
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
                            var message = queue.popAsync().get();
                            actual.add(message);
                            Thread.sleep(rand.nextInt(2));
                        } catch (Exception ignored) {
                        }
                    } while (actual.size() < expected.size());
                };

        CompletableFuture.allOf(CompletableFuture.runAsync(writer), CompletableFuture.runAsync(reader))
                .get();

        assertEquals(expected, actual);
    }

    @Test
    @SneakyThrows
    public void concurrent_write_sync_read() {
        var queue = new MessageHandler.PubSubMessageQueue();
        var numMessages = 1000;
        var expected = new LinkedList<PubSubMessage>();
        var actual = new LinkedList<PubSubMessage>();
        var rand = new Random();
        for (int i = 0; i < numMessages; i++) {
            expected.add(
                    new PubSubMessage(gs(i + " " + UUID.randomUUID()), gs(UUID.randomUUID().toString())));
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
                            Thread.sleep(rand.nextInt(2));
                        } catch (InterruptedException ignored) {
                        }
                    } while (actual.size() < expected.size());
                };
        CompletableFuture.allOf(CompletableFuture.runAsync(writer), CompletableFuture.runAsync(reader))
                .get();

        assertEquals(expected, actual);
    }
}


