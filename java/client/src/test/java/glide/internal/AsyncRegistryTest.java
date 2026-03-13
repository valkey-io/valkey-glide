/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.internal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.models.exceptions.ClosingException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AsyncRegistryTest {

    @BeforeEach
    void setUp() {
        AsyncRegistry.reset();
    }

    @Test
    void failAllWithError_completesAllPendingFutures() {
        CompletableFuture<Object> f1 = new CompletableFuture<>();
        CompletableFuture<Object> f2 = new CompletableFuture<>();
        CompletableFuture<Object> f3 = new CompletableFuture<>();

        // timeoutMillis=0 avoids native call to markTimedOut
        AsyncRegistry.register(f1, 0, 1L, 0);
        AsyncRegistry.register(f2, 0, 1L, 0);
        AsyncRegistry.register(f3, 0, 1L, 0);

        assertEquals(3, AsyncRegistry.getActiveFutureCount());

        AsyncRegistry.failAllWithError("test error");

        assertTrue(f1.isCompletedExceptionally());
        assertTrue(f2.isCompletedExceptionally());
        assertTrue(f3.isCompletedExceptionally());
        assertEquals(0, AsyncRegistry.getActiveFutureCount());
        assertEquals(0, AsyncRegistry.getPendingTimeoutCount());

        assertClosingException(f1, "test error");
        assertClosingException(f2, "test error");
        assertClosingException(f3, "test error");
    }

    @Test
    void failAllWithError_withNullMessage_usesDefault() {
        CompletableFuture<Object> f = new CompletableFuture<>();
        AsyncRegistry.register(f, 0, 1L, 0);

        AsyncRegistry.failAllWithError(null);

        assertTrue(f.isCompletedExceptionally());
        assertClosingException(f, "Native callback infrastructure failed");
    }

    @Test
    void failAllWithError_withEmptyMessage_usesDefault() {
        CompletableFuture<Object> f = new CompletableFuture<>();
        AsyncRegistry.register(f, 0, 1L, 0);

        AsyncRegistry.failAllWithError("");

        assertTrue(f.isCompletedExceptionally());
        assertClosingException(f, "Native callback infrastructure failed");
    }

    @Test
    void failAllWithError_withEmptyTable_isNoOp() {
        assertEquals(0, AsyncRegistry.getActiveFutureCount());
        assertDoesNotThrow(() -> AsyncRegistry.failAllWithError("msg"));
        assertEquals(0, AsyncRegistry.getActiveFutureCount());
    }

    @Test
    void failAllWithError_raceWithNormalCompletion() {
        CompletableFuture<Object> f = new CompletableFuture<>();
        long id = AsyncRegistry.register(f, 0, 1L, 0);

        // Complete normally first
        AsyncRegistry.completeCallback(id, "normal result");

        // Then sweep — should not override the normal result
        AsyncRegistry.failAllWithError("late error");

        assertTrue(f.isDone());
        // First completion wins — should have normal result, not exception
        assertEquals("normal result", f.getNow(null));
    }

    @Test
    void failAllWithError_clearsInflightCounters() {
        CompletableFuture<Object> f1 = new CompletableFuture<>();
        // Register with inflight limit of 1
        AsyncRegistry.register(f1, 1, 42L, 0);

        // Sweep clears counters
        AsyncRegistry.failAllWithError("msg");

        // Should be able to register again on the same client (counter was reset)
        CompletableFuture<Object> f2 = new CompletableFuture<>();
        assertDoesNotThrow(() -> AsyncRegistry.register(f2, 1, 42L, 0));
    }

    // ==================== Shutdown Race Condition Tests ====================

    @Test
    void register_afterShutdown_returnsZeroAndFailsFuture() {
        // First, trigger shutdown
        AsyncRegistry.failAllWithError("shutdown");
        assertTrue(AsyncRegistry.isShutdown());

        // Now try to register a new future
        CompletableFuture<Object> f = new CompletableFuture<>();
        long id = AsyncRegistry.register(f, 0, 1L, 0);

        // Should return 0 (special ID indicating registration failed)
        assertEquals(0L, id);

        // Future should be completed exceptionally
        assertTrue(f.isCompletedExceptionally());
        assertClosingException(f, "Client is shutting down, cannot register new requests");

        // Should not be added to active futures
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
        // Trigger shutdown
        AsyncRegistry.failAllWithError("test");
        assertTrue(AsyncRegistry.isShutdown());

        // Reset should clear the flag
        AsyncRegistry.reset();

        assertFalse(AsyncRegistry.isShutdown());

        // Should be able to register again
        CompletableFuture<Object> f = new CompletableFuture<>();
        long id = AsyncRegistry.register(f, 0, 1L, 0);

        assertTrue(id > 0);
        assertEquals(1, AsyncRegistry.getActiveFutureCount());
    }

    @Test
    void register_afterShutdown_doesNotIncrementInflightCounter() {
        // Trigger shutdown
        AsyncRegistry.failAllWithError("shutdown");

        // Try to register with inflight limit
        CompletableFuture<Object> f = new CompletableFuture<>();
        long id = AsyncRegistry.register(f, 10, 42L, 0);

        assertEquals(0L, id);

        // Reset and verify we can register the full limit (counter wasn't incremented)
        AsyncRegistry.reset();

        for (int i = 0; i < 10; i++) {
            CompletableFuture<Object> fi = new CompletableFuture<>();
            long regId = AsyncRegistry.register(fi, 10, 42L, 0);
            assertTrue(regId > 0, "Registration " + i + " should succeed");
        }
    }

    @Test
    void isShutdown_initiallyFalse() {
        assertFalse(AsyncRegistry.isShutdown());
    }

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
}
