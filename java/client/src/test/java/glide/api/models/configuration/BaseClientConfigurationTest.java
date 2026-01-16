/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class BaseClientConfigurationTest {

    /** Test implementation of BaseClientConfiguration for testing purposes */
    @SuperBuilder
    private static class TestClientConfiguration
            extends BaseClientConfiguration<TestClientConfiguration> {

        @Override
        public BaseSubscriptionConfiguration getSubscriptionConfiguration() {
            return null;
        }

        @Override
        public AdvancedBaseClientConfiguration getAdvancedConfiguration() {
            return null;
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
}
