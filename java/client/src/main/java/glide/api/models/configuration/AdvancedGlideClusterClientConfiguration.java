/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import glide.api.GlideClusterClient;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Represents advanced configuration settings for a Standalone {@link GlideClusterClient} used in
 * {@link GlideClusterClientConfiguration}.
 *
 * @example
 *     <pre>{@code
 * AdvancedGlideClusterClientConfiguration config = AdvancedGlideClusterClientConfiguration.builder()
 *     .connectionTimeout(500)
 *     .tlsAdvancedConfiguration(
 *        TlsAdvancedConfiguration.builder().useInsecureTLS(false).build())
 *     .refreshTopologyFromInitialNodes(true)
 *     .build();
 * }</pre>
 */
@Getter
@SuperBuilder
@ToString
public class AdvancedGlideClusterClientConfiguration extends AdvancedBaseClientConfiguration {

    /**
     * Enables refreshing the cluster topology using only the initial nodes.
     *
     * <p>When this option is enabled, all topology updates (both the periodic checks and on-demand
     * refreshes triggered by topology changes) will query only the initial nodes provided when
     * creating the client, rather than using the internal cluster view.
     *
     * <p>If not set, defaults to {@code false} (uses internal cluster view for topology refresh).
     */
    @Builder.Default private final boolean refreshTopologyFromInitialNodes = false;
}
