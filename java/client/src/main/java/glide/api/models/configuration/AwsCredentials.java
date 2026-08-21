/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

/**
 * Immutable value object representing AWS credentials for IAM authentication token signing.
 *
 * <p>Use the builder to construct instances; the builder enforces that {@code accessKeyId} and
 * {@code secretAccessKey} are present and non-blank. {@code sessionToken} and {@code expiresAt} are
 * optional.
 *
 * <pre>{@code
 * // Long-term credentials:
 * AwsCredentials creds = AwsCredentials.builder()
 *     .accessKeyId("AKIAIOSFODNN7EXAMPLE")
 *     .secretAccessKey("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")
 *     .build();
 *
 * // Session credentials with expiry (e.g., from STS AssumeRole):
 * AwsCredentials sessionCreds = AwsCredentials.builder()
 *     .accessKeyId(vaultClient.getAccessKeyId())
 *     .secretAccessKey(vaultClient.getSecretAccessKey())
 *     .sessionToken(vaultClient.getSessionToken())
 *     .expiresAt(vaultClient.getExpiration().toInstant())
 *     .build();
 * }</pre>
 *
 * @see GlideCredentialProvider
 */
@Getter
@Builder
public class AwsCredentials {

    /** The AWS Access Key ID. Required; must not be blank. */
    @NonNull private final String accessKeyId;

    /** The AWS Secret Access Key. Required; must not be blank. */
    @NonNull private final String secretAccessKey;

    /** The AWS Session Token. Optional; {@code null} for long-term (non-session) credentials. */
    private final String sessionToken;

    /**
     * Optional expiry time for these credentials. When provided, the Rust core passes it to the AWS
     * SDK so the SDK has accurate metadata about credential validity. This does <em>not</em> override
     * {@code refreshIntervalSeconds} — the background refresh task still fires on its configured
     * schedule. Omit (leave {@code null}) if your credentials do not have a known expiry.
     */
    private final Instant expiresAt;

    /**
     * Package-private all-args constructor used by Lombok's {@code @Builder}.
     *
     * <p>Validates that {@code accessKeyId} and {@code secretAccessKey} are non-blank. {@code
     * sessionToken} and {@code expiresAt} may be {@code null}.
     *
     * @throws NullPointerException if {@code accessKeyId} or {@code secretAccessKey} is {@code null}
     *     (enforced by {@code @NonNull})
     * @throws IllegalArgumentException if {@code accessKeyId} or {@code secretAccessKey} is blank
     */
    AwsCredentials(
            @NonNull String accessKeyId,
            @NonNull String secretAccessKey,
            String sessionToken,
            Instant expiresAt) {
        if (accessKeyId.trim().isEmpty()) {
            throw new IllegalArgumentException("accessKeyId must not be blank");
        }
        if (secretAccessKey.trim().isEmpty()) {
            throw new IllegalArgumentException("secretAccessKey must not be blank");
        }
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
        this.sessionToken = sessionToken;
        this.expiresAt = expiresAt;
    }
}
