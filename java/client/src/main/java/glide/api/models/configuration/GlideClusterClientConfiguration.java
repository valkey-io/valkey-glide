/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import glide.api.GlideClusterClient;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * Represents the configuration settings for a Cluster mode client {@link GlideClusterClient}.
 *
 * @apiNote Currently, the reconnection strategy in cluster mode is not configurable, and
 *     exponential backoff with fixed values is used.
 * @example
 *     <pre>{@code
 * GlideClientConfiguration glideClientConfiguration =
 *     GlideClientConfiguration.builder()
 *         .address(node1address)
 *         .address(node2address)
 *         .useTLS(true)
 *         .readFrom(ReadFrom.PREFER_REPLICA)
 *         .credentials(credentialsConfiguration)
 *         .requestTimeout(2000)
 *         .clientName("GLIDE")
 *         .subscriptionConfiguration(subscriptionConfiguration)
 *         .inflightRequestsLimit(1000)
 *         .build();
 * }</pre>
 */
@SuperBuilder
@Getter
public class GlideClusterClientConfiguration extends BaseClientConfiguration {

    /** Subscription configuration for the current client. */
    private final ClusterSubscriptionConfiguration subscriptionConfiguration;
}
