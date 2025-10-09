/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class AdvancedGlideClusterClientConfigurationTest {

    @Test
    public void testRefreshTopologyFromInitialNodesDefault() {
        // Test that refreshTopologyFromInitialNodes defaults to false when not specified
        AdvancedGlideClusterClientConfiguration config =
                AdvancedGlideClusterClientConfiguration.builder().build();
        assertFalse(config.isRefreshTopologyFromInitialNodes());
    }

    @Test
    public void testRefreshTopologyFromInitialNodesEnabled() {
        // Test that refreshTopologyFromInitialNodes can be set to true
        AdvancedGlideClusterClientConfiguration config =
                AdvancedGlideClusterClientConfiguration.builder()
                        .refreshTopologyFromInitialNodes(true)
                        .build();
        assertTrue(config.isRefreshTopologyFromInitialNodes());
    }

    @Test
    public void testRefreshTopologyFromInitialNodesDisabled() {
        // Test that refreshTopologyFromInitialNodes can be explicitly set to false
        AdvancedGlideClusterClientConfiguration config =
                AdvancedGlideClusterClientConfiguration.builder()
                        .refreshTopologyFromInitialNodes(false)
                        .build();
        assertFalse(config.isRefreshTopologyFromInitialNodes());
    }
}
