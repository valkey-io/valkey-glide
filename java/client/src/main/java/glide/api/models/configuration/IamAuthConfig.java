/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

/**
 * Configuration settings for IAM authentication.
 *
 * <p>Example:
 *
 * <pre>{@code
 * // Standard IAM authentication (uses default AWS credential chain):
 * IamAuthConfig iamConfig = IamAuthConfig.builder()
 *     .clusterName("my-cluster")
 *     .service(ServiceType.ELASTICACHE)
 *     .region("us-east-1")
 *     .refreshIntervalSeconds(300)
 *     .build();
 *
 * // Custom credentials provider (e.g., from HashiCorp Vault):
 * GlideCredentialProvider vaultCallback = () -> AwsCredentials.builder()
 *     .accessKeyId(vaultClient.getAccessKeyId())
 *     .secretAccessKey(vaultClient.getSecretAccessKey())
 *     .sessionToken(vaultClient.getSessionToken()) // optional
 *     .build();
 * IamAuthConfig iamConfigWithCustomProvider = IamAuthConfig.builder()
 *     .clusterName("my-cluster")
 *     .service(ServiceType.ELASTICACHE)
 *     .region("us-east-1")
 *     .credentialsProvider(vaultCallback)
 *     .build();
 * }</pre>
 */
@Getter
@Builder
public class IamAuthConfig {
    /** The name of the ElastiCache/MemoryDB cluster. */
    @NonNull private final String clusterName;

    /** The type of service being used (ElastiCache or MemoryDB). */
    @NonNull private final ServiceType service;

    /** The AWS region where the ElastiCache/MemoryDB cluster is located. */
    @NonNull private final String region;

    /**
     * Optional refresh interval in seconds for renewing IAM authentication tokens. If not provided,
     * defaults to 300 seconds (5 min).
     */
    private final Integer refreshIntervalSeconds;

    /**
     * Optional custom credentials provider. When set, this provider is invoked to retrieve AWS
     * credentials (access key ID, secret access key, and optional session token) used to sign IAM
     * authentication tokens, instead of relying on the default AWS credential chain in Rust.
     *
     * <p>This is useful in environments where credentials are managed by tools like HashiCorp Vault,
     * custom STS assume-role flows, or any other non-standard credential source.
     *
     * <p>When not set (null), the default AWS credential chain is used (environment variables,
     * ~/.aws/credentials, EC2/ECS metadata service, etc.).
     *
     * <p>The provider must return a non-null {@link AwsCredentials} instance. {@code accessKeyId} and
     * {@code secretAccessKey} must not be blank; {@code sessionToken} may be {@code null} for
     * long-term credentials.
     *
     * <p>Example:
     *
     * <pre>{@code
     * GlideCredentialProvider provider = () -> AwsCredentials.builder()
     *     .accessKeyId(myVaultClient.getAccessKeyId())
     *     .secretAccessKey(myVaultClient.getSecretAccessKey())
     *     .sessionToken(myVaultClient.getSessionToken())
     *     .build();
     * IamAuthConfig iamConfig = IamAuthConfig.builder()
     *     .clusterName("my-cluster")
     *     .service(ServiceType.ELASTICACHE)
     *     .region("us-east-1")
     *     .credentialsProvider(provider)
     *     .build();
     * }</pre>
     */
    private final GlideCredentialProvider credentialsProvider;
}
