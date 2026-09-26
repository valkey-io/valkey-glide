/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.internal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.GlideClient;
import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.RequestException;
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

    // ==================== JVM Shutdown Hook Behavior (issue #4809) ====================

    @Test
    void handleJvmShutdown_doesNotSetShutdownFlag() {
        assertFalse(AsyncRegistry.isShutdown());

        AsyncRegistry.handleJvmShutdown();

        // The automatic JVM-exit hook must be non-destructive so concurrent user shutdown
        // hooks can keep using the client.
        assertFalse(AsyncRegistry.isShutdown());
    }

    @Test
    void handleJvmShutdown_allowsSubsequentRegistration() {
        // Simulate the JVM exit hook firing.
        AsyncRegistry.handleJvmShutdown();

        // A command issued from a user's own shutdown hook (running concurrently) must still
        // register successfully rather than being rejected with a ClosingException. This is the
        // regression guard for issue #4809.
        CompletableFuture<Object> f = new CompletableFuture<>();
        long id = AsyncRegistry.register(f, 0, 1L, 0);

        assertTrue(id > 0, "register() must succeed after the JVM shutdown hook runs");
        assertFalse(f.isDone(), "future must not be pre-failed");
        assertEquals(1, AsyncRegistry.getActiveFutureCount());
    }

    @Test
    void handleJvmShutdown_doesNotCancelPendingFutures() {
        CompletableFuture<Object> f = new CompletableFuture<>();
        // Register with a Java-side timeout so we also cover the scheduled timeout-task path.
        AsyncRegistry.register(f, 0, 1L, 60_000);

        assertEquals(1, AsyncRegistry.getActiveFutureCount());
        assertEquals(1, AsyncRegistry.getPendingTimeoutCount());

        AsyncRegistry.handleJvmShutdown();

        // In-flight requests must not be aborted by the JVM-exit hook; they are left to complete
        // (or be reclaimed at process exit). Both futures and their scheduled timeout tasks must
        // survive.
        assertFalse(f.isDone());
        assertEquals(1, AsyncRegistry.getActiveFutureCount());
        assertEquals(1, AsyncRegistry.getPendingTimeoutCount());

        // Clean up: cancel the abandoned future so its 60s timeout task is cancelled and doesn't
        // outlive this test and invoke GlideNativeBridge.markTimedOut in the test JVM.
        f.cancel(true);
    }

    // ==================== Inflight Counter Key Tests ====================

    @Test
    void register_sameCounterKey_sharesTheInflightCounter() {
        // Characterizes the collision the pooled-client fix guards against: two clients keyed on the
        // same value share one counter, so requests from one count against the other's limit.
        long sharedKey = 7L;
        CompletableFuture<Object> f1 = new CompletableFuture<>();
        CompletableFuture<Object> f2 = new CompletableFuture<>();
        AsyncRegistry.register(f1, 1, sharedKey, sharedKey, 0);

        // Second registration on the same counter key exceeds the limit of 1 and is rejected.
        RequestException ex =
                assertThrows(
                        RequestException.class, () -> AsyncRegistry.register(f2, 1, sharedKey, sharedKey, 0));
        assertEquals("Client reached maximum inflight requests", ex.getMessage());
    }

    @Test
    void register_distinctCounterKeys_doNotShareTheInflightCounter() {
        // The fix: a pooled client keys its inflight counter on a value disjoint from a directly-
        // created client's native handle, so saturating one does not falsely reject the other even
        // when their native handles collide. The pooled key is derived from the production mapping
        // GlideClient.poolInflightCounterKey rather than a hard-coded copy, so reverting that method
        // to return the raw id (the collision) makes this test fail.
        long collidingHandle = 5L;
        long directKey = collidingHandle; // direct client keys on its native handle
        long pooledKey = GlideClient.poolInflightCounterKey(collidingHandle); // the fix under test

        // The mapping must actually produce a value that cannot collide with the positive handle.
        assertNotEquals(
                directKey, pooledKey, "pooled counter key must differ from the colliding native handle");

        // Saturate the direct client's counter (limit 1).
        CompletableFuture<Object> direct = new CompletableFuture<>();
        AsyncRegistry.register(direct, 1, collidingHandle, directKey, 0);

        // The pooled client (same native handle, distinct counter key) must not be rejected.
        CompletableFuture<Object> pooled = new CompletableFuture<>();
        assertDoesNotThrow(
                () -> AsyncRegistry.register(pooled, 1, collidingHandle, pooledKey, 0),
                "pooled client must not share the direct client's inflight counter");
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
