/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import glide.api.GlideClient;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Represents the configuration settings for a Standalone {@link GlideClient}.
 *
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
 *         .reconnectStrategy(reconnectionConfiguration)
 *         .databaseId(1)
 *         .clientName("GLIDE")
 *         .subscriptionConfiguration(subscriptionConfiguration)
 *         .inflightRequestsLimit(1000)
 *         .advancedConfiguration(AdvancedGlideClientConfiguration.builder().connectionTimeout(500).build())
 *         .build();
 * }</pre>
 */
@Getter
@SuperBuilder
@ToString
public class GlideClientConfiguration extends BaseClientConfiguration {

    /** Subscription configuration for the current client. */
    private final StandaloneSubscriptionConfiguration subscriptionConfiguration;

    /** Advanced configuration settings for the client. */
    @Builder.Default
    private final AdvancedGlideClientConfiguration advancedConfiguration =
            AdvancedGlideClientConfiguration.builder().build();
}
