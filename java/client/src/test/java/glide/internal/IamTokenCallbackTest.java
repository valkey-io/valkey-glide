/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.models.configuration.AwsCredentials;
import glide.api.models.configuration.GlideCredentialProvider;
import glide.ffi.resolvers.NativeUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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

    private static native String invokeProvider(GlideCredentialProvider provider, long timeoutMillis);

    @Test
    void completedFutureReturnsCredentials() {
        GlideCredentialProvider provider =
                () ->
                        CompletableFuture.completedFuture(
                                AwsCredentials.builder()
                                        .accessKeyId("completed-access-key")
                                        .secretAccessKey("completed-secret-key")
                                        .build());

        assertEquals("completed-access-key", invokeProvider(provider, SHORT_TIMEOUT_MILLIS));
    }

    @Test
    void exceptionalFuturePreservesCauseChain() {
        CompletableFuture<AwsCredentials> failed = new CompletableFuture<>();
        failed.completeExceptionally(
                new IllegalStateException(
                        "provider failure", new IllegalArgumentException("root provider cause")));

        RuntimeException error =
                assertThrows(
                        RuntimeException.class, () -> invokeProvider(() -> failed, SHORT_TIMEOUT_MILLIS));

        assertTrue(error.getMessage().contains("provider failure"), error.getMessage());
        assertTrue(error.getMessage().contains("root provider cause"), error.getMessage());
    }

    @Test
    void neverCompletingFutureIsCancelledWithinBound() {
        CompletableFuture<AwsCredentials> never = new CompletableFuture<>();
        Instant started = Instant.now();

        RuntimeException error =
                assertThrows(
                        RuntimeException.class, () -> invokeProvider(() -> never, SHORT_TIMEOUT_MILLIS));

        assertTrue(error.getMessage().contains("Timed out waiting"), error.getMessage());
        assertTrue(never.isCancelled(), "Timed-out provider future was not cancelled");
        assertTrue(
                Duration.between(started, Instant.now()).toMillis() < 1000,
                "JNI callback wait did not return promptly");
    }

    @Test
    void repeatedTimeoutsDoNotOccupyJniWaitThreadIndefinitely() {
        List<CompletableFuture<AwsCredentials>> futures = new ArrayList<>();
        GlideCredentialProvider provider =
                () -> {
                    CompletableFuture<AwsCredentials> future = new CompletableFuture<>();
                    futures.add(future);
                    return future;
                };
        Instant started = Instant.now();

        for (int i = 0; i < 4; i++) {
            RuntimeException error =
                    assertThrows(
                            RuntimeException.class, () -> invokeProvider(provider, SHORT_TIMEOUT_MILLIS));
            assertTrue(error.getMessage().contains("Timed out waiting"), error.getMessage());
        }

        assertEquals(4, futures.size());
        assertTrue(futures.stream().allMatch(CompletableFuture::isCancelled));
        assertTrue(
                Duration.between(started, Instant.now()).toMillis() < 1500,
                "Repeated JNI callback waits did not remain bounded");
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

        assertEquals("nonpositive-expiry-access-key", invokeProvider(provider, SHORT_TIMEOUT_MILLIS));
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
                assertThrows(RuntimeException.class, () -> invokeProvider(provider, SHORT_TIMEOUT_MILLIS));

        assertTrue(error.getMessage().contains("expiresAt cannot be represented"), error.getMessage());
    }
}
