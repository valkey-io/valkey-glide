/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    @Test
    public void testDatabaseIdValidValues() {
        // Test valid database ID values
        TestClientConfiguration config0 = TestClientConfiguration.builder().databaseId(0).build();
        assertEquals(0, config0.getDatabaseId());

        TestClientConfiguration config1 = TestClientConfiguration.builder().databaseId(1).build();
        assertEquals(1, config1.getDatabaseId());

        TestClientConfiguration config15 = TestClientConfiguration.builder().databaseId(15).build();
        assertEquals(15, config15.getDatabaseId());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 5, 10, 15})
    public void testDatabaseIdValidRange(int databaseId) {
        // Test that valid database IDs are accepted
        TestClientConfiguration config =
                TestClientConfiguration.builder().databaseId(databaseId).build();
        assertEquals(databaseId, config.getDatabaseId());
    }

    @Test
    public void testDatabaseIdNegativeValue() {
        // Test that negative database IDs are handled (validation should be done at connection time)
        TestClientConfiguration config = TestClientConfiguration.builder().databaseId(-1).build();
        assertEquals(-1, config.getDatabaseId());
    }

    @Test
    public void testDatabaseIdLargeValue() {
        // Test that large database IDs are handled (validation should be done at connection time)
        TestClientConfiguration config = TestClientConfiguration.builder().databaseId(100).build();
        assertEquals(100, config.getDatabaseId());
    }

    @Test
    public void testDatabaseIdWithOtherConfiguration() {
        // Test that databaseId works with other configuration options
        TestClientConfiguration config =
                TestClientConfiguration.builder()
                        .databaseId(5)
                        .requestTimeout(1000)
                        .clientName("test-client")
                        .build();

        assertEquals(5, config.getDatabaseId());
        assertEquals(1000, config.getRequestTimeout());
        assertEquals("test-client", config.getClientName());
    }
}
