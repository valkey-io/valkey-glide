/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.internal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.RequestException;
import glide.api.models.exceptions.TimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AsyncRegistryTest {

    @BeforeEach
    void setUp() {
        AsyncRegistry.reset();
    }

    // ==================== FailAll / Shutdown ====================

    @Test
    void failAllWithError_completesAllPendingFutures() {
        CompletableFuture<Object> f1 = new CompletableFuture<>();
        CompletableFuture<Object> f2 = new CompletableFuture<>();
        CompletableFuture<Object> f3 = new CompletableFuture<>();

        AsyncRegistry.register(f1, 0, 1L, 0);
        AsyncRegistry.register(f2, 0, 1L, 0);
        AsyncRegistry.register(f3, 0, 1L, 0);

        assertEquals(3, AsyncRegistry.getActiveFutureCount());
        AsyncRegistry.failAllWithError("test error");

        assertTrue(f1.isCompletedExceptionally());
        assertTrue(f2.isCompletedExceptionally());
        assertTrue(f3.isCompletedExceptionally());
        assertEquals(0, AsyncRegistry.getActiveFutureCount());

        assertClosingException(f1, "test error");
        assertClosingException(f2, "test error");
        assertClosingException(f3, "test error");
    }

    @Test
    void failAllWithError_withNullMessage_usesDefault() {
        CompletableFuture<Object> f = new CompletableFuture<>();
        AsyncRegistry.register(f, 0, 1L, 0);
        AsyncRegistry.failAllWithError(null);
        assertClosingException(f, "Native callback infrastructure failed");
    }

    @Test
    void failAllWithError_withEmptyMessage_usesDefault() {
        CompletableFuture<Object> f = new CompletableFuture<>();
        AsyncRegistry.register(f, 0, 1L, 0);
        AsyncRegistry.failAllWithError("");
        assertClosingException(f, "Native callback infrastructure failed");
    }

    @Test
    void failAllWithError_withEmptyTable_isNoOp() {
        assertEquals(0, AsyncRegistry.getActiveFutureCount());
        assertDoesNotThrow(() -> AsyncRegistry.failAllWithError("msg"));
    }

    @Test
    void failAllWithError_raceWithNormalCompletion() {
        CompletableFuture<Object> f = new CompletableFuture<>();
        long id = AsyncRegistry.register(f, 0, 1L, 0);
        AsyncRegistry.completeCallback(id, "normal result");
        AsyncRegistry.failAllWithError("late error");
        assertEquals("normal result", f.getNow(null));
    }

    @Test
    void failAllWithError_clearsInflightCounters() {
        CompletableFuture<Object> f1 = new CompletableFuture<>();
        AsyncRegistry.register(f1, 1, 42L, 0);
        AsyncRegistry.failAllWithError("msg");
        CompletableFuture<Object> f2 = new CompletableFuture<>();
        assertDoesNotThrow(() -> AsyncRegistry.register(f2, 1, 42L, 0));
    }

    @Test
    void register_afterShutdown_returnsZeroAndFailsFuture() {
        AsyncRegistry.failAllWithError("shutdown");
        CompletableFuture<Object> f = new CompletableFuture<>();
        assertEquals(0L, AsyncRegistry.register(f, 0, 1L, 0));
        assertClosingException(f, "Client is shutting down, cannot register new requests");
        assertEquals(0, AsyncRegistry.getActiveFutureCount());
    }

    @Test
    void failAllWithError_setsShutdownFlag() {
        assertFalse(AsyncRegistry.isShutdown());
        AsyncRegistry.failAllWithError("test");
        assertTrue(AsyncRegistry.isShutdown());
    }

    @Test
    void reset_clearsShutdownFlag() {
        AsyncRegistry.failAllWithError("test");
        AsyncRegistry.reset();
        assertFalse(AsyncRegistry.isShutdown());
        CompletableFuture<Object> f = new CompletableFuture<>();
        assertTrue(AsyncRegistry.register(f, 0, 1L, 0) > 0);
        assertEquals(1, AsyncRegistry.getActiveFutureCount());
    }

    @Test
    void register_afterShutdown_doesNotIncrementInflightCounter() {
        AsyncRegistry.failAllWithError("shutdown");
        CompletableFuture<Object> f = new CompletableFuture<>();
        assertEquals(0L, AsyncRegistry.register(f, 10, 42L, 0));
        AsyncRegistry.reset();
        for (int i = 0; i < 10; i++) {
            assertTrue(AsyncRegistry.register(new CompletableFuture<>(), 10, 42L, 0) > 0);
        }
    }

    @Test
    void isShutdown_initiallyFalse() {
        assertFalse(AsyncRegistry.isShutdown());
    }

    // ==================== Completion ====================

    @Test
    void completeCallback_normalCompletion() {
        CompletableFuture<Object> f = new CompletableFuture<>();
        long id = AsyncRegistry.register(f, 0, 1L, 0);
        assertTrue(AsyncRegistry.completeCallback(id, "result"));
        assertEquals("result", f.getNow(null));
        assertEquals(0, AsyncRegistry.getActiveFutureCount());
    }

    @Test
    void completeCallback_wrongId_returnsFalse() {
        CompletableFuture<Object> f = new CompletableFuture<>();
        long id = AsyncRegistry.register(f, 0, 1L, 0);
        assertFalse(AsyncRegistry.completeCallback(id + 99999, "wrong"));
        assertFalse(f.isDone());
        assertTrue(AsyncRegistry.completeCallback(id, "correct"));
        assertEquals("correct", f.getNow(null));
    }

    @Test
    void completeCallbackWithErrorCode_timeout() {
        CompletableFuture<Object> f = new CompletableFuture<>();
        long id = AsyncRegistry.register(f, 0, 1L, 1000);
        AsyncRegistry.completeCallbackWithErrorCode(id, 2, "timed out");
        assertTrue(f.isCompletedExceptionally());
        assertTimeoutException(f);
    }

    @Test
    void completeCallback_externallyCompletedFuture_stillReleasesInflight() {
        CompletableFuture<Object> f = new CompletableFuture<>();
        long id = AsyncRegistry.register(f, 1, 42L, 0);
        f.cancel(true);
        // Native callback arrives — inflight must still be released
        AsyncRegistry.completeCallback(id, "ignored");
        assertDoesNotThrow(() -> AsyncRegistry.register(new CompletableFuture<>(), 1, 42L, 0));
    }

    // ==================== Inflight counters ====================

    @Test
    void inflightCounter_releasedOnNormalCompletion() {
        CompletableFuture<Object> f = new CompletableFuture<>();
        long id = AsyncRegistry.register(f, 1, 42L, 0);
        assertThrows(
                RequestException.class, () -> AsyncRegistry.register(new CompletableFuture<>(), 1, 42L, 0));
        AsyncRegistry.completeCallback(id, "ok");
        assertDoesNotThrow(() -> AsyncRegistry.register(new CompletableFuture<>(), 1, 42L, 0));
    }

    @Test
    void inflightCounter_notReleasedUntilNativeCallback() {
        CompletableFuture<Object> f = new CompletableFuture<>();
        long id = AsyncRegistry.register(f, 1, 42L, 1000);

        // Even if future is externally timed out, inflight stays held
        f.completeExceptionally(new java.util.concurrent.TimeoutException("user timeout"));
        assertThrows(
                RequestException.class, () -> AsyncRegistry.register(new CompletableFuture<>(), 1, 42L, 0));

        // Only native callback frees the slot
        AsyncRegistry.completeCallback(id, "late");
        assertDoesNotThrow(() -> AsyncRegistry.register(new CompletableFuture<>(), 1, 42L, 0));
    }

    @Test
    void inflightCounter_releasedOnErrorCompletion() {
        CompletableFuture<Object> f = new CompletableFuture<>();
        long id = AsyncRegistry.register(f, 1, 42L, 0);
        AsyncRegistry.completeCallbackWithErrorCode(id, 0, "err");
        assertDoesNotThrow(() -> AsyncRegistry.register(new CompletableFuture<>(), 1, 42L, 0));
    }

    // ==================== Concurrent access ====================

    @Test
    void concurrentRegisterAndComplete() throws Exception {
        int threadCount = Runtime.getRuntime().availableProcessors() >= 4 ? 60 : 8;
        int opsPerThread = 5000;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            new Thread(
                            () -> {
                                try {
                                    startLatch.await();
                                    for (int i = 0; i < opsPerThread; i++) {
                                        CompletableFuture<Object> f = new CompletableFuture<>();
                                        long id = AsyncRegistry.register(f, 0, 1L, 0);
                                        if (AsyncRegistry.completeCallback(id, "ok")) {
                                            successCount.incrementAndGet();
                                        } else {
                                            failCount.incrementAndGet();
                                        }
                                    }
                                } catch (Exception e) {
                                    failCount.incrementAndGet();
                                } finally {
                                    doneLatch.countDown();
                                }
                            })
                    .start();
        }

        startLatch.countDown();
        doneLatch.await();

        int total = successCount.get() + failCount.get();
        assertEquals(threadCount * opsPerThread, total, "All operations should complete");
        assertEquals(0, AsyncRegistry.getActiveFutureCount(), "No futures should be leaked");
    }

    // ==================== Helpers ====================

    private static void assertClosingException(CompletableFuture<?> future, String expectedMessage) {
        try {
            future.get();
        } catch (ExecutionException e) {
            assertInstanceOf(ClosingException.class, e.getCause());
            assertEquals(expectedMessage, e.getCause().getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Unexpected interruption", e);
        }
    }

    private static void assertTimeoutException(CompletableFuture<?> future) {
        try {
            future.get();
        } catch (ExecutionException e) {
            assertInstanceOf(TimeoutException.class, e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Unexpected interruption", e);
        }
    }
}
