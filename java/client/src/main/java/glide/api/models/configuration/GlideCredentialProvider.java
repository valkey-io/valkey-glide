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
 * <p>The Java object bridge validates strings during memory-safe JNI conversion. It does not use
 * the buffer-negotiation protocol or its 1 MiB field/aggregate cap, which applies only to the Go
 * and Python C FFI adapters.
 *
 * <p>The {@code getCredentials()} method body runs on a dedicated single daemon worker and should
 * return its future promptly. The method-body invocation and completion of its returned future
 * share a nine-second deadline. On timeout, GLIDE requests cancellation with interruption.
 * Providers should respond to interruption; if a method body ignores it and remains blocked, later
 * invocations for that client are rejected instead of queued. The daemon worker cannot prevent JVM
 * shutdown.
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
 * <p><b>Thread safety:</b> A client's method-body invocations use one worker; an invocation is
 * rejected rather than queued while that worker is blocked. The same provider instance can still be
 * configured on multiple clients and invoked concurrently by their independent workers, so shared
 * provider state must be thread-safe.
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
