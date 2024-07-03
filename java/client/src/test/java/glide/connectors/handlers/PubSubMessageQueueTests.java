/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.connectors.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.models.PubSubMessage;
import java.util.LinkedList;
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
    public void read_messages_then_add() {
        var queue = new PubSubMessageQueue();
        var promise1 = queue.pop();
        var promise2 = queue.pop();

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

        // promise should get resolved automagically
        checkFutureStatus(promise1, true);
        assertSame(msg1, promise1.get());
        // and `head` too
        checkFutureStatus(queue.head, true);
        assertSame(msg2, queue.head.get());
        // and the last message remains in the MQ
        assertEquals(1, queue.messageQueue.size());
    }

    @Test
    @SneakyThrows
    public void add_messages_then_read() {
        var queue = new PubSubMessageQueue();

        var msg1 = new PubSubMessage("one", "one");
        var msg2 = new PubSubMessage("two", "two");
        var msg3 = new PubSubMessage("three", "three");
        queue.push(msg1);
        queue.push(msg2);
        queue.push(msg3);

        checkFutureStatus(queue.head, true);
        // MQ stores only second and third message, first is stored in `head`
        assertEquals(2, queue.messageQueue.size());
        assertSame(msg1, queue.head.get());
        assertSame(msg2, queue.messageQueue.peek());

        // now - read
        assertSame(msg1, queue.pop().get());
        // second messages should be shifted to `head`
        assertEquals(1, queue.messageQueue.size());
        checkFutureStatus(queue.head, true);
        assertSame(msg2, queue.head.get());
        // keep reading
        assertSame(msg2, queue.pop().get());
        // MQ is empty, but `head` isn't
        assertTrue(queue.messageQueue.isEmpty());
        checkFutureStatus(queue.head, true);
        assertSame(msg3, queue.head.get());
        // read more
        assertSame(msg2, queue.pop().get());
        // MQ and `head` are both empty
        assertTrue(queue.messageQueue.isEmpty());
        checkFutureStatus(queue.head, false);
    }

    @Test
    @SneakyThrows
    public void concurrent_read_write() {
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
                    for (int i = 0; i < numMessages; i++) {
                        queue.push(expected.get(i));
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
                            message = queue.pop().get(15, TimeUnit.MILLISECONDS);
                            actual.add(message);
                            Thread.sleep(rand.nextInt(2));
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
}
