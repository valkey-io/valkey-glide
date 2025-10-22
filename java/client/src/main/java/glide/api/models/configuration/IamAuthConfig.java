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
 * IamAuthConfig iamConfig = IamAuthConfig.builder()
 *     .clusterName("my-cluster")
 *     .service(ServiceType.ELASTICACHE)
 *     .region("us-east-1")
 *     .refreshIntervalSeconds(300)
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
}
