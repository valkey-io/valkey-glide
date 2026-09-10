/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import java.util.concurrent.CompletableFuture;

/**
 * A callback interface used to supply AWS credentials for IAM authentication token signing.
 *
 * <p>This interface is used when a custom credentials source (e.g., HashiCorp Vault, a custom STS
 * assume-role flow, or any other non-standard credential provider) must be used instead of the
 * default AWS credential chain.
 *
 * <p>The callback is invoked by the native Rust layer each time a fresh IAM token needs to be
 * generated. Implement this interface and return a {@link CompletableFuture} that completes with an
 * {@link AwsCredentials} instance built with the {@link AwsCredentials#builder()}.
 *
 * <p>The future is resolved on a background thread ({@code spawn_blocking}) so it is safe to block
 * or perform I/O inside the provider. However, the Rust core imposes a 10-second timeout; providers
 * that do not complete within that window will cause token generation to fail.
 *
 * <pre>{@code
 * // Synchronous provider — wrap with completedFuture:
 * GlideCredentialProvider staticProvider = () ->
 *     CompletableFuture.completedFuture(
 *         AwsCredentials.builder()
 *             .accessKeyId(System.getenv("AWS_ACCESS_KEY_ID"))
 *             .secretAccessKey(System.getenv("AWS_SECRET_ACCESS_KEY"))
 *             .build());
 *
 * // Async provider — return a future that completes asynchronously:
 * GlideCredentialProvider asyncProvider = () ->
 *     vaultClient.getCredentialsAsync()
 *         .thenApply(creds -> AwsCredentials.builder()
 *             .accessKeyId(creds.getAccessKeyId())
 *             .secretAccessKey(creds.getSecretAccessKey())
 *             .sessionToken(creds.getSessionToken())
 *             .build());
 * }</pre>
 *
 * <p><b>Thread safety:</b> Implementations must be thread-safe. In cluster mode, multiple
 * reconnection attempts may invoke this callback concurrently from different threads. Each
 * invocation is independent — no serialization is provided by the framework.
 *
 * @see AwsCredentials
 * @see IamAuthConfig#getCredentialsProvider()
 */
@FunctionalInterface
public interface GlideCredentialProvider {

    /**
     * Retrieve the current AWS credentials asynchronously.
     *
     * @return a {@link CompletableFuture} that completes with an {@link AwsCredentials} instance
     *     containing the AWS Access Key ID, Secret Access Key, and an optional Session Token and
     *     expiry. The future must not complete with {@code null}.
     */
    CompletableFuture<AwsCredentials> getCredentials();
}
