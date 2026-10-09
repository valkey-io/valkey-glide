/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.models.configuration.AwsCredentials;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(5)
class CredentialsProviderInvokerTest {
    @Test
    @Timeout(30)
    void promptSequentialSubmissionsNeverSpuriouslyReject() throws Exception {
        AtomicInteger invocations = new AtomicInteger();
        try (CredentialsProviderInvoker invoker =
                new CredentialsProviderInvoker(
                        () -> {
                            invocations.incrementAndGet();
                            return CompletableFuture.completedFuture(credentials("access-key"));
                        })) {
            for (int invocation = 0; invocation < 100_000; invocation++) {
                invoker.submit().get(1, TimeUnit.SECONDS).get(1, TimeUnit.SECONDS);
            }
        }

        assertEquals(100_000, invocations.get());
    }

    @Test
    void providerThrowReleasesAdmissionBeforeFailureIsObserved() throws Exception {
        AtomicInteger invocations = new AtomicInteger();
        try (CredentialsProviderInvoker invoker =
                new CredentialsProviderInvoker(
                        () -> {
                            if (invocations.incrementAndGet() == 1) {
                                throw new IllegalStateException("provider failure");
                            }
                            return CompletableFuture.completedFuture(credentials("recovered"));
                        })) {
            Future<CompletableFuture<AwsCredentials>> failed = invoker.submit();
            ExecutionException error =
                    assertThrows(ExecutionException.class, () -> failed.get(1, TimeUnit.SECONDS));
            assertTrue(error.getCause().getMessage().contains("provider failure"));

            AwsCredentials recovered = invoker.submit().get(1, TimeUnit.SECONDS).get(1, TimeUnit.SECONDS);
            assertEquals("recovered", recovered.getAccessKeyId());
        }
    }

    @Test
    void cancelBeforeStartReleasesAdmissionAndRemovesCancelledTask() throws Exception {
        CountDownLatch startWorker = new CountDownLatch(1);
        AtomicInteger invocations = new AtomicInteger();
        CredentialsProviderInvoker invoker =
                new CredentialsProviderInvoker(
                        () -> {
                            invocations.incrementAndGet();
                            return CompletableFuture.completedFuture(credentials("recovered"));
                        },
                        delayedDaemonThreadFactory(startWorker));

        try {
            Future<CompletableFuture<AwsCredentials>> cancelled = invoker.submit();
            assertTrue(cancelled.cancel(true));
            assertFalse(invoker.hasInvocationInFlight());
            assertEquals(0, invoker.queuedInvocationCount());

            Future<CompletableFuture<AwsCredentials>> recovered = invoker.submit();
            assertEquals(1, invoker.queuedInvocationCount());
            startWorker.countDown();

            assertEquals(
                    "recovered",
                    recovered.get(1, TimeUnit.SECONDS).get(1, TimeUnit.SECONDS).getAccessKeyId());
            assertEquals(1, invocations.get());
        } finally {
            startWorker.countDown();
            invoker.close();
        }
    }

    @Test
    void cancelWhileRunningKeepsAdmissionUntilCooperativeBodyExits() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicInteger invocations = new AtomicInteger();
        try (CredentialsProviderInvoker invoker =
                new CredentialsProviderInvoker(
                        () -> {
                            if (invocations.incrementAndGet() == 1) {
                                entered.countDown();
                                try {
                                    Thread.sleep(TimeUnit.SECONDS.toMillis(30));
                                } catch (InterruptedException exception) {
                                    interrupted.set(true);
                                    Thread.currentThread().interrupt();
                                }
                            }
                            return CompletableFuture.completedFuture(credentials("recovered"));
                        })) {
            Future<CompletableFuture<AwsCredentials>> cancelled = invoker.submit();
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            assertTrue(cancelled.cancel(true));
            assertTrue(waitUntilTrue(interrupted, 1000), "Running provider was not interrupted");
            assertTrue(
                    waitUntilNotInFlight(invoker, 1000),
                    "Admission was not released after the provider body exited");

            assertEquals(
                    "recovered",
                    invoker.submit().get(1, TimeUnit.SECONDS).get(1, TimeUnit.SECONDS).getAccessKeyId());
        }
    }

    @Test
    void busyInvocationRejectsConcurrentAndRepeatedSubmissionsWithoutQueueing() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger invocations = new AtomicInteger();
        try (CredentialsProviderInvoker invoker =
                new CredentialsProviderInvoker(
                        () -> {
                            invocations.incrementAndGet();
                            entered.countDown();
                            while (release.getCount() != 0) {
                                try {
                                    release.await(5, TimeUnit.MILLISECONDS);
                                } catch (InterruptedException exception) {
                                    Thread.interrupted();
                                }
                            }
                            return CompletableFuture.completedFuture(credentials("released"));
                        })) {
            Future<CompletableFuture<AwsCredentials>> running = invoker.submit();
            assertTrue(entered.await(1, TimeUnit.SECONDS));

            ExecutorService callers = Executors.newFixedThreadPool(8);
            try {
                CountDownLatch startCallers = new CountDownLatch(1);
                List<Future<Boolean>> results = new ArrayList<>();
                for (int attempt = 0; attempt < 32; attempt++) {
                    results.add(
                            callers.submit(
                                    () -> {
                                        startCallers.await();
                                        try {
                                            invoker.submit();
                                            return false;
                                        } catch (RejectedExecutionException exception) {
                                            return exception.getMessage().contains("still busy")
                                                    && exception.getMessage().contains("no invocation was queued");
                                        }
                                    }));
                }
                startCallers.countDown();
                for (Future<Boolean> result : results) {
                    assertTrue(result.get(1, TimeUnit.SECONDS));
                }
            } finally {
                callers.shutdownNow();
            }

            Instant repeatedSubmissionsStarted = Instant.now();
            for (int attempt = 0; attempt < 10; attempt++) {
                RejectedExecutionException rejected =
                        assertThrows(RejectedExecutionException.class, invoker::submit);
                assertTrue(rejected.getMessage().contains("still busy"));
                assertEquals(0, invoker.queuedInvocationCount());
            }
            assertTrue(
                    Duration.between(repeatedSubmissionsStarted, Instant.now()).toMillis() < 1000,
                    "Busy submissions did not reject promptly");
            assertEquals(1, invocations.get());

            release.countDown();
            assertEquals(
                    "released", running.get(1, TimeUnit.SECONDS).get(1, TimeUnit.SECONDS).getAccessKeyId());
            assertEquals(
                    "released",
                    invoker.submit().get(1, TimeUnit.SECONDS).get(1, TimeUnit.SECONDS).getAccessKeyId());
            assertEquals(2, invocations.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    void workerIsDaemon() throws Exception {
        try (CredentialsProviderInvoker invoker =
                new CredentialsProviderInvoker(
                        () ->
                                CompletableFuture.completedFuture(
                                        credentials(Boolean.toString(Thread.currentThread().isDaemon()))))) {
            AwsCredentials credentials = invoker.submit().get(1, TimeUnit.SECONDS).get();
            assertTrue(Boolean.parseBoolean(credentials.getAccessKeyId()));
        }
    }

    @Test
    void closeInterruptsWorkerAndDoesNotWaitForIt() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        CredentialsProviderInvoker invoker =
                new CredentialsProviderInvoker(
                        () -> {
                            entered.countDown();
                            try {
                                Thread.sleep(TimeUnit.SECONDS.toMillis(30));
                            } catch (InterruptedException exception) {
                                interrupted.set(true);
                                Thread.currentThread().interrupt();
                            }
                            return new CompletableFuture<>();
                        });

        invoker.submit();
        assertTrue(entered.await(1, TimeUnit.SECONDS));
        Instant closeStarted = Instant.now();
        invoker.close();

        assertTrue(invoker.isShutdown());
        assertTrue(
                Duration.between(closeStarted, Instant.now()).toMillis() < 1000,
                "Closing the invoker blocked");
        assertTrue(waitUntilTrue(interrupted, 1000), "Worker was not interrupted during shutdown");
        assertTrue(
                waitUntilNotInFlight(invoker, 1000),
                "Admission was not released after shutdown interrupted the provider");
    }

    @Test
    void closeCancelsDroppedTaskAndShutdownRejectionsDoNotStickAdmission() throws Exception {
        CountDownLatch startWorker = new CountDownLatch(1);
        CredentialsProviderInvoker invoker =
                new CredentialsProviderInvoker(
                        () -> CompletableFuture.completedFuture(credentials("unused")),
                        delayedDaemonThreadFactory(startWorker));
        Future<CompletableFuture<AwsCredentials>> dropped = invoker.submit();
        assertEquals(1, invoker.queuedInvocationCount());

        invoker.close();

        assertTrue(dropped.isCancelled());
        assertFalse(invoker.hasInvocationInFlight());
        for (int attempt = 0; attempt < 2; attempt++) {
            RejectedExecutionException rejected =
                    assertThrows(RejectedExecutionException.class, invoker::submit);
            assertTrue(rejected.getMessage().contains("shut down"));
            assertFalse(invoker.hasInvocationInFlight());
        }
    }

    @Test
    void closeReleasesAdmissionWhenRunningProviderIgnoresInterruption() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CredentialsProviderInvoker invoker =
                new CredentialsProviderInvoker(
                        () -> {
                            entered.countDown();
                            while (release.getCount() != 0) {
                                try {
                                    release.await(5, TimeUnit.MILLISECONDS);
                                } catch (InterruptedException exception) {
                                    Thread.interrupted();
                                }
                            }
                            return CompletableFuture.completedFuture(credentials("released"));
                        });

        try {
            invoker.submit();
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            invoker.close();

            assertFalse(invoker.hasInvocationInFlight());
            RejectedExecutionException rejected =
                    assertThrows(RejectedExecutionException.class, invoker::submit);
            assertTrue(rejected.getMessage().contains("shut down"));
            assertFalse(invoker.hasInvocationInFlight());
        } finally {
            release.countDown();
            invoker.close();
        }
    }

    private static AwsCredentials credentials(String accessKeyId) {
        return AwsCredentials.builder().accessKeyId(accessKeyId).secretAccessKey("secret").build();
    }

    private static ThreadFactory delayedDaemonThreadFactory(CountDownLatch startWorker) {
        return task -> {
            Thread thread =
                    new Thread(
                            () -> {
                                try {
                                    startWorker.await();
                                    task.run();
                                } catch (InterruptedException exception) {
                                    Thread.currentThread().interrupt();
                                }
                            },
                            "delayed-glide-iam-credentials-provider");
            thread.setDaemon(true);
            return thread;
        };
    }

    private static boolean waitUntilTrue(AtomicBoolean value, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (!value.get() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        return value.get();
    }

    private static boolean waitUntilNotInFlight(
            CredentialsProviderInvoker invoker, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (invoker.hasInvocationInFlight() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        return !invoker.hasInvocationInFlight();
    }
}
