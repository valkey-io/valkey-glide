/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

/**
 * Configuration settings for IAM authentication.
 *
 * @example
 *     <pre>{@code
 * // Standard IAM authentication (uses default AWS credential chain):
 * IamAuthConfig iamConfig = IamAuthConfig.builder()
 *     .clusterName("my-cluster")
 *     .service(ServiceType.ELASTICACHE)
 *     .region("us-east-1")
 *     .refreshIntervalSeconds(300)
 *     .build();
 *
 * // Custom credentials provider (e.g., from HashiCorp Vault):
 * GlideCredentialProvider vaultCallback = () -> new String[]{
 *     System.getenv("VAULT_ACCESS_KEY_ID"),
 *     System.getenv("VAULT_SECRET_ACCESS_KEY"),
 *     System.getenv("VAULT_SESSION_TOKEN")
 * };
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
     * <p>The provider must return a {@code String[]} with 2 or 3 elements:
     *
     * <ul>
     *   <li>{@code [0]} - AWS Access Key ID (required, must not be null)
     *   <li>{@code [1]} - AWS Secret Access Key (required, must not be null)
     *   <li>{@code [2]} - AWS Session Token (optional, may be null for long-term credentials)
     * </ul>
     *
     * @example
     *     <pre>{@code
     * // Using a custom credentials provider with session credentials:
     * GlideCredentialProvider provider = () -> new String[]{
     *     myVaultClient.getAccessKeyId(),
     *     myVaultClient.getSecretAccessKey(),
     *     myVaultClient.getSessionToken()
     * };
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
