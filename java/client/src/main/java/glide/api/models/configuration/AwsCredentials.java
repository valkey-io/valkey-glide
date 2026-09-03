/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import java.time.Instant;
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
     * Returns a new builder for constructing {@link AwsCredentials}.
     *
     * @return a new {@link AwsCredentialsBuilder} instance
     */
    public static AwsCredentialsBuilder builder() {
        return new AwsCredentialsBuilder();
    }

    /**
     * All-args constructor. Validates that {@code accessKeyId} and {@code secretAccessKey} are
     * non-blank. {@code sessionToken} and {@code expiresAt} may be {@code null}.
     *
     * <p>Use {@link #builder()} to construct instances.
     *
     * @throws NullPointerException if {@code accessKeyId} or {@code secretAccessKey} is {@code null}
     *     (enforced by {@code @NonNull})
     * @throws IllegalArgumentException if {@code accessKeyId} or {@code secretAccessKey} is blank
     */
    private AwsCredentials(
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

    /** Builder for {@link AwsCredentials}. */
    public static final class AwsCredentialsBuilder {
        private String accessKeyId;
        private String secretAccessKey;
        private String sessionToken;
        private Instant expiresAt;

        private AwsCredentialsBuilder() {}

        /** Sets the AWS Access Key ID. Required; must not be blank. */
        public AwsCredentialsBuilder accessKeyId(@NonNull String accessKeyId) {
            this.accessKeyId = accessKeyId;
            return this;
        }

        /** Sets the AWS Secret Access Key. Required; must not be blank. */
        public AwsCredentialsBuilder secretAccessKey(@NonNull String secretAccessKey) {
            this.secretAccessKey = secretAccessKey;
            return this;
        }

        /** Sets the AWS Session Token. Optional; omit for long-term credentials. */
        public AwsCredentialsBuilder sessionToken(String sessionToken) {
            this.sessionToken = sessionToken;
            return this;
        }

        /** Sets the credential expiry time. Optional; omit if credentials have no known expiry. */
        public AwsCredentialsBuilder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        /** Builds and returns a new {@link AwsCredentials} instance. */
        public AwsCredentials build() {
            return new AwsCredentials(accessKeyId, secretAccessKey, sessionToken, expiresAt);
        }
    }
}
