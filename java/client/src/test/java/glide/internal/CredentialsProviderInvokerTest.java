/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.internal;

import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.models.configuration.AwsCredentials;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(5)
class CredentialsProviderInvokerTest {
    @Test
    void workerIsDaemon() throws Exception {
        try (CredentialsProviderInvoker invoker =
                new CredentialsProviderInvoker(
                        () ->
                                java.util.concurrent.CompletableFuture.completedFuture(
                                        AwsCredentials.builder()
                                                .accessKeyId(Boolean.toString(Thread.currentThread().isDaemon()))
                                                .secretAccessKey("secret")
                                                .build()))) {
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
                            return new java.util.concurrent.CompletableFuture<>();
                        });

        invoker.submit();
        assertTrue(entered.await(1, TimeUnit.SECONDS));
        invoker.close();

        assertTrue(invoker.isShutdown());
        assertTrue(waitUntilTrue(interrupted, 1000), "Worker was not interrupted during shutdown");
    }

    private static boolean waitUntilTrue(AtomicBoolean value, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (!value.get() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        return value.get();
    }
}
