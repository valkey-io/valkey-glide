/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.models.configuration.AwsCredentials;
import glide.api.models.configuration.GlideCredentialProvider;
import glide.ffi.resolvers.NativeUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(5)
public class IamTokenCallbackTest {
    private static final long SHORT_TIMEOUT_MILLIS = 50;

    @BeforeAll
    static void loadNativeLibrary() {
        NativeUtils.loadGlideLib();
    }

    private static native long createProviderCallback(
            GlideCredentialProvider provider, long timeoutMillis);

    private static native String invokeProvider(long callbackHandle);

    private static native void closeProviderCallback(long callbackHandle);

    private static native void failProviderCallbackInitialization(CredentialsProviderInvoker invoker);

    @Test
    void completedFutureReturnsCredentials() {
        GlideCredentialProvider provider =
                () -> CompletableFuture.completedFuture(credentials("completed-access-key"));

        assertEquals("completed-access-key", invokeOnce(provider, SHORT_TIMEOUT_MILLIS));
    }

    @Test
    void methodBodyThrowsAndPreservesMessage() {
        GlideCredentialProvider provider =
                () -> {
                    throw new IllegalStateException("method body failure");
                };

        RuntimeException error =
                assertThrows(RuntimeException.class, () -> invokeOnce(provider, SHORT_TIMEOUT_MILLIS));

        assertTrue(error.getMessage().contains("method body failure"), error.getMessage());
    }

    @Test
    void exceptionalFuturePreservesCauseChain() {
        CompletableFuture<AwsCredentials> failed = new CompletableFuture<>();
        failed.completeExceptionally(
                new IllegalStateException(
                        "provider failure", new IllegalArgumentException("root provider cause")));

        RuntimeException error =
                assertThrows(RuntimeException.class, () -> invokeOnce(() -> failed, SHORT_TIMEOUT_MILLIS));

        assertTrue(error.getMessage().contains("provider failure"), error.getMessage());
        assertTrue(error.getMessage().contains("root provider cause"), error.getMessage());
    }

    @Test
    void neverCompletingFutureIsCancelledWithinBound() {
        CompletableFuture<AwsCredentials> never = new CompletableFuture<>();
        Instant started = Instant.now();

        RuntimeException error =
                assertThrows(RuntimeException.class, () -> invokeOnce(() -> never, SHORT_TIMEOUT_MILLIS));

        assertTrue(error.getMessage().contains("CompletableFuture"), error.getMessage());
        assertTrue(never.isCancelled(), "Timed-out provider future was not cancelled");
        assertReturnsPromptly(started, 1000, "JNI callback future wait did not return promptly");
    }

    @Test
    void blockingMethodBodyIsInterruptedAndReturnsWithinBound() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        GlideCredentialProvider provider =
                () -> {
                    entered.countDown();
                    try {
                        Thread.sleep(TimeUnit.SECONDS.toMillis(30));
                    } catch (InterruptedException exception) {
                        interrupted.set(true);
                        Thread.currentThread().interrupt();
                    }
                    throw new IllegalStateException("body stopped");
                };
        Instant started = Instant.now();

        RuntimeException error =
                assertThrows(RuntimeException.class, () -> invokeOnce(provider, SHORT_TIMEOUT_MILLIS));

        assertTrue(entered.await(1, TimeUnit.SECONDS));
        assertTrue(error.getMessage().contains("method body"), error.getMessage());
        assertTrue(waitUntilTrue(interrupted, 1000), "Timed-out method body was not interrupted");
        assertReturnsPromptly(started, 1000, "Blocked method-body invocation was not bounded");
    }

    @Test
    void hungMethodBodyRejectsRepeatedCallsWithoutMoreInvocations() throws Exception {
        AtomicBoolean release = new AtomicBoolean();
        AtomicInteger invocations = new AtomicInteger();
        AtomicInteger interrupts = new AtomicInteger();
        GlideCredentialProvider provider =
                () -> {
                    invocations.incrementAndGet();
                    while (!release.get()) {
                        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
                        // Deliberately ignore and clear cancellation interrupts.
                        if (Thread.interrupted()) {
                            interrupts.incrementAndGet();
                        }
                    }
                    return CompletableFuture.completedFuture(credentials("released"));
                };
        Instant started = Instant.now();
        NativeCallback callback = new NativeCallback(provider, SHORT_TIMEOUT_MILLIS);

        try {
            RuntimeException timeout = assertThrows(RuntimeException.class, callback::invoke);
            assertTrue(timeout.getMessage().contains("method body"), timeout.getMessage());
            assertTrue(
                    waitUntilAtLeast(interrupts, 1, 1000),
                    "Timed-out invocation did not interrupt the provider body");

            for (int attempt = 0; attempt < 3; attempt++) {
                RuntimeException rejected = assertThrows(RuntimeException.class, callback::invoke);
                assertTrue(rejected.getMessage().contains("still busy"), rejected.getMessage());
                assertTrue(
                        rejected.getMessage().contains("no invocation was queued"), rejected.getMessage());
            }

            assertEquals(1, invocations.get(), "Blocked provider was invoked more than once");
            assertReturnsPromptly(started, 1000, "Rejected callbacks did not fail promptly");

            callback.close();
            assertTrue(
                    waitUntilAtLeast(interrupts, 2, 1000),
                    "Dropping the native callback did not shut down and interrupt its invoker");
        } finally {
            callback.close();
            release.set(true);
        }
    }

    @Test
    void providerMethodRunsOnDaemonWorker() {
        GlideCredentialProvider provider =
                () ->
                        CompletableFuture.completedFuture(
                                credentials(Boolean.toString(Thread.currentThread().isDaemon())));

        assertEquals("true", invokeOnce(provider, SHORT_TIMEOUT_MILLIS));
    }

    @Test
    void postCreationInitializationFailureShutsDownInvokerWorker() throws Exception {
        CountDownLatch workerStarted = new CountDownLatch(1);
        AtomicReference<Thread> worker = new AtomicReference<>();
        CredentialsProviderInvoker invoker =
                new CredentialsProviderInvoker(
                        () -> CompletableFuture.completedFuture(credentials("unused")),
                        task -> {
                            Thread thread =
                                    new Thread(
                                            () -> {
                                                workerStarted.countDown();
                                                task.run();
                                            },
                                            "failed-initialization-invoker-worker");
                            thread.setDaemon(true);
                            worker.set(thread);
                            return thread;
                        });

        try {
            assertTrue(workerStarted.await(1, TimeUnit.SECONDS), "Invoker worker did not start");
            assertTrue(worker.get().isAlive(), "Invoker worker terminated before initialization");

            failProviderCallbackInitialization(invoker);

            assertTrue(invoker.isShutdown(), "Failed initialization did not close the invoker");
            worker.get().join(1000);
            assertFalse(worker.get().isAlive(), "Failed initialization leaked the invoker worker");
        } finally {
            invoker.close();
        }
    }

    @Test
    @Timeout(15)
    void methodBodyAndFutureShareOneDeadline() throws Exception {
        long timeoutMillis = 3000;
        long methodBodyHoldMillis = 1000;
        long sharedDeadlineCompletionBoundMillis = 2500;
        CountDownLatch methodBodyEntered = new CountDownLatch(1);
        CountDownLatch releaseMethodBody = new CountDownLatch(1);
        CompletableFuture<AwsCredentials> returnedFuture = new CompletableFuture<>();
        AtomicReference<RuntimeException> invocationError = new AtomicReference<>();
        GlideCredentialProvider provider =
                () -> {
                    methodBodyEntered.countDown();
                    try {
                        releaseMethodBody.await();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Method body interrupted", exception);
                    }
                    return returnedFuture;
                };

        NativeCallback callback = new NativeCallback(provider, timeoutMillis);
        Thread invocation =
                new Thread(
                        () -> {
                            try {
                                callback.invoke();
                            } catch (RuntimeException exception) {
                                invocationError.set(exception);
                            }
                        },
                        "shared-credentials-deadline-test");
        invocation.setDaemon(true);
        Throwable testFailure = null;
        boolean invocationTerminated = false;

        try {
            invocation.start();
            assertTrue(
                    methodBodyEntered.await(1, TimeUnit.SECONDS),
                    "Credentials provider method body did not start");
            sleepUnchecked(methodBodyHoldMillis);
            Instant methodBodyReleased = Instant.now();
            releaseMethodBody.countDown();

            invocation.join(sharedDeadlineCompletionBoundMillis);
            boolean completedWithinSharedDeadline = !invocation.isAlive();
            long elapsedAfterMethodBodyMillis =
                    Duration.between(methodBodyReleased, Instant.now()).toMillis();

            assertTrue(
                    completedWithinSharedDeadline,
                    "Callback was still waiting "
                            + elapsedAfterMethodBodyMillis
                            + " ms after the method body returned; deadlines appear independent");
            assertFalse(invocation.isAlive(), "Callback invocation thread did not terminate");
            RuntimeException error = invocationError.get();
            assertTrue(error != null, "Callback unexpectedly returned credentials");
            assertTrue(error.getMessage().contains("3000 ms total"), error.getMessage());
            assertTrue(returnedFuture.isCancelled(), "Returned future was not cancelled");
        } catch (Throwable failure) {
            testFailure = failure;
        } finally {
            releaseMethodBody.countDown();
            returnedFuture.complete(credentials("cleanup-release"));
            invocationTerminated = joinWithin(invocation, 5000);
            if (invocationTerminated) {
                callback.close();
            }
        }

        if (!invocationTerminated) {
            AssertionError lifetimeFailure =
                    new AssertionError(
                            "Callback invocation thread did not terminate; native callback handle was"
                                    + " intentionally leaked");
            if (testFailure != null) {
                lifetimeFailure.addSuppressed(testFailure);
            }
            throw lifetimeFailure;
        }
        if (testFailure instanceof Error) {
            throw (Error) testFailure;
        }
        if (testFailure instanceof Exception) {
            throw (Exception) testFailure;
        }
        if (testFailure != null) {
            throw new AssertionError(testFailure);
        }
    }

    @Test
    void nonPositiveExpiryIsTreatedAsAbsent() {
        GlideCredentialProvider provider =
                () ->
                        CompletableFuture.completedFuture(
                                AwsCredentials.builder()
                                        .accessKeyId("nonpositive-expiry-access-key")
                                        .secretAccessKey("secret-key")
                                        .expiresAt(Instant.EPOCH.minusMillis(1))
                                        .build());

        assertEquals("nonpositive-expiry-access-key", invokeOnce(provider, SHORT_TIMEOUT_MILLIS));
    }

    @Test
    void unrepresentableExpiryReturnsControlledCredentialsError() {
        GlideCredentialProvider provider =
                () ->
                        CompletableFuture.completedFuture(
                                AwsCredentials.builder()
                                        .accessKeyId("overflow-access-key")
                                        .secretAccessKey("secret-key")
                                        .expiresAt(Instant.MAX)
                                        .build());

        RuntimeException error =
                assertThrows(RuntimeException.class, () -> invokeOnce(provider, SHORT_TIMEOUT_MILLIS));

        assertTrue(error.getMessage().contains("expiresAt cannot be represented"), error.getMessage());
    }

    private static AwsCredentials credentials(String accessKeyId) {
        return AwsCredentials.builder().accessKeyId(accessKeyId).secretAccessKey("secret-key").build();
    }

    private static String invokeOnce(GlideCredentialProvider provider, long timeoutMillis) {
        try (NativeCallback callback = new NativeCallback(provider, timeoutMillis)) {
            return callback.invoke();
        }
    }

    private static boolean waitUntilTrue(AtomicBoolean value, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (!value.get() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        return value.get();
    }

    private static boolean waitUntilAtLeast(AtomicInteger value, int expected, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (value.get() < expected && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        return value.get() >= expected;
    }

    private static boolean joinWithin(Thread thread, long timeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        boolean interrupted = false;
        while (thread.isAlive()) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                break;
            }
            try {
                TimeUnit.NANOSECONDS.timedJoin(thread, remainingNanos);
            } catch (InterruptedException exception) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        return !thread.isAlive();
    }

    private static void assertReturnsPromptly(
            Instant started, long boundMillis, String failureMessage) {
        assertTrue(Duration.between(started, Instant.now()).toMillis() < boundMillis, failureMessage);
    }

    private static void sleepUnchecked(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Unexpected interruption", exception);
        }
    }

    private static final class NativeCallback implements AutoCloseable {
        private long handle;

        private NativeCallback(GlideCredentialProvider provider, long timeoutMillis) {
            handle = createProviderCallback(provider, timeoutMillis);
            if (handle == 0) {
                throw new IllegalStateException("Native callback was not created");
            }
        }

        private String invoke() {
            return invokeProvider(handle);
        }

        @Override
        public void close() {
            if (handle != 0) {
                closeProviderCallback(handle);
                handle = 0;
            }
        }
    }
}
