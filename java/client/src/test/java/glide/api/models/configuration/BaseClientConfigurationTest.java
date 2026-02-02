/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class BaseClientConfigurationTest {

    /** Test implementation of BaseClientConfiguration for testing purposes */
    private static class TestClientConfiguration extends BaseClientConfiguration {
        private TestClientConfiguration(TestClientConfigurationBuilder builder) {
            super(builder);
        }

        public static TestClientConfigurationBuilder builder() {
            return new TestClientConfigurationBuilder();
        }

        @Override
        public BaseSubscriptionConfiguration getSubscriptionConfiguration() {
            return null;
        }

        public static class TestClientConfigurationBuilder
                extends BaseClientConfigurationBuilder<
                        TestClientConfiguration, TestClientConfigurationBuilder> {
            @Override
            protected TestClientConfigurationBuilder self() {
                return this;
            }

            @Override
            public TestClientConfiguration build() {
                return new TestClientConfiguration(this);
            }
        }
    }

    @Test
    public void testDatabaseIdDefault() {
        // Test that databaseId defaults to null when not specified
        TestClientConfiguration config = TestClientConfiguration.builder().build();
        assertNull(config.getDatabaseId());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 5, 10, 15, 50, 100, 1000})
    public void testDatabaseIdValidRange(int databaseId) {
        // Test that non-negative database IDs are accepted (server-side validation will handle range
        // checks)
        TestClientConfiguration config =
                TestClientConfiguration.builder().databaseId(databaseId).build();
        assertEquals(databaseId, config.getDatabaseId());
    }

    @Test
    public void testPubsubReconciliationIntervalDefault() {
        // Test that pubsubReconciliationIntervalMs defaults to null when not specified
        GlideClientConfiguration config =
                GlideClientConfiguration.builder()
                        .address(NodeAddress.builder().host("localhost").port(6379).build())
                        .build();
        assertNull(config.getAdvancedConfiguration().getPubsubReconciliationIntervalMs());
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 500, 1000, 5000, 10000})
    public void testPubsubReconciliationIntervalValidRange(int intervalMs) {
        // Test that pubsubReconciliationIntervalMs is properly set in advanced config
        GlideClientConfiguration config =
                GlideClientConfiguration.builder()
                        .address(NodeAddress.builder().host("localhost").port(6379).build())
                        .advancedConfiguration(
                                AdvancedGlideClientConfiguration.builder()
                                        .pubsubReconciliationIntervalMs(intervalMs)
                                        .build())
                        .build();
        assertEquals(intervalMs, config.getAdvancedConfiguration().getPubsubReconciliationIntervalMs());
    }

    @Test
    public void testPubsubReconciliationIntervalCluster() {
        // Test that pubsubReconciliationIntervalMs works for cluster configuration
        int intervalMs = 2000;
        GlideClusterClientConfiguration config =
                GlideClusterClientConfiguration.builder()
                        .address(NodeAddress.builder().host("localhost").port(7000).build())
                        .advancedConfiguration(
                                AdvancedGlideClusterClientConfiguration.builder()
                                        .pubsubReconciliationIntervalMs(intervalMs)
                                        .build())
                        .build();
        assertEquals(intervalMs, config.getAdvancedConfiguration().getPubsubReconciliationIntervalMs());
    }

    @Test
    public void testPubsubReconciliationIntervalMs_zero_throws() {
        var builder = AdvancedGlideClientConfiguration.builder();
        var exception =
                assertThrows(
                        IllegalArgumentException.class, () -> builder.pubsubReconciliationIntervalMs(0));
        assertEquals("pubsubReconciliationIntervalMs must be positive, got: 0", exception.getMessage());
    }

    @Test
    public void testPubsubReconciliationIntervalMs_negative_throws() {
        var builder = AdvancedGlideClientConfiguration.builder();
        var exception =
                assertThrows(
                        IllegalArgumentException.class, () -> builder.pubsubReconciliationIntervalMs(-1));
        assertEquals(
                "pubsubReconciliationIntervalMs must be positive, got: -1", exception.getMessage());
    }
}
