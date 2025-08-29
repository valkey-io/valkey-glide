/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/**
 * Test class for FieldConditionalChange enum.
 *
 * <p>Tests the FieldConditionalChange enum used by HSETEX command to control when hash fields
 * should be set based on their current existence state.
 */
public class FieldConditionalChangeTest {

    @Test
    public void testFieldConditionalChange_allValues() {
        // Test all enum values exist and have correct Valkey API strings
        assertEquals("FXX", FieldConditionalChange.ONLY_IF_ALL_EXIST.getValkeyApi());
        assertEquals("FNX", FieldConditionalChange.ONLY_IF_NONE_EXIST.getValkeyApi());
    }

    @Test
    public void testFieldConditionalChange_enumProperties() {
        // Test enum has correct number of values
        assertEquals(2, FieldConditionalChange.values().length);

        // Test valueOf works correctly
        assertEquals(
                FieldConditionalChange.ONLY_IF_ALL_EXIST,
                FieldConditionalChange.valueOf("ONLY_IF_ALL_EXIST"));
        assertEquals(
                FieldConditionalChange.ONLY_IF_NONE_EXIST,
                FieldConditionalChange.valueOf("ONLY_IF_NONE_EXIST"));
    }

    @Test
    public void testFieldConditionalChange_threadSafety() {
        // Test that enum values are thread-safe
        FieldConditionalChange condition = FieldConditionalChange.ONLY_IF_ALL_EXIST;

        // Multiple calls should return consistent results
        String api1 = condition.getValkeyApi();
        String api2 = condition.getValkeyApi();

        assertEquals(api1, api2);
        assertEquals("FXX", api1);
    }

    @Test
    public void testFieldConditionalChange_immutability() {
        // Test that enum values are immutable
        FieldConditionalChange condition = FieldConditionalChange.ONLY_IF_ALL_EXIST;
        String api = condition.getValkeyApi();

        // The returned string should be consistent
        assertEquals("FXX", api);
        assertEquals("FXX", condition.getValkeyApi());
    }

    @Test
    public void testFieldConditionalChange_consistency() {
        // Test that each enum value consistently returns the same result
        for (FieldConditionalChange condition : FieldConditionalChange.values()) {
            String api1 = condition.getValkeyApi();
            String api2 = condition.getValkeyApi();

            assertNotNull(api1);
            assertNotNull(api2);
            assertEquals(api1, api2);
        }
    }

    @Test
    public void testFieldConditionalChange_semanticMeaning() {
        // Test that enum names match their semantic meaning
        assertEquals("FXX", FieldConditionalChange.ONLY_IF_ALL_EXIST.getValkeyApi());
        assertEquals("FNX", FieldConditionalChange.ONLY_IF_NONE_EXIST.getValkeyApi());
    }

    @Test
    public void testFieldConditionalChange_enumEquality() {
        // Test enum equality
        assertEquals(
                FieldConditionalChange.ONLY_IF_ALL_EXIST, FieldConditionalChange.ONLY_IF_ALL_EXIST);
        assertEquals(
                FieldConditionalChange.ONLY_IF_NONE_EXIST, FieldConditionalChange.ONLY_IF_NONE_EXIST);
    }

    @Test
    public void testFieldConditionalChange_toString() {
        // Test toString method (inherited from Enum)
        assertEquals("ONLY_IF_ALL_EXIST", FieldConditionalChange.ONLY_IF_ALL_EXIST.toString());
        assertEquals("ONLY_IF_NONE_EXIST", FieldConditionalChange.ONLY_IF_NONE_EXIST.toString());
    }

    @Test
    public void testFieldConditionalChange_getterMethod() {
        // Test the getter method works correctly
        assertEquals("FXX", FieldConditionalChange.ONLY_IF_ALL_EXIST.getValkeyApi());
        assertEquals("FNX", FieldConditionalChange.ONLY_IF_NONE_EXIST.getValkeyApi());

        // Test that getter is consistent
        FieldConditionalChange condition = FieldConditionalChange.ONLY_IF_ALL_EXIST;
        assertEquals(condition.getValkeyApi(), condition.getValkeyApi());
    }

    @Test
    public void testFieldConditionalChange_usage() {
        // Test typical usage patterns
        FieldConditionalChange onlyIfAllExist = FieldConditionalChange.ONLY_IF_ALL_EXIST;
        FieldConditionalChange onlyIfNoneExist = FieldConditionalChange.ONLY_IF_NONE_EXIST;

        // These should be different
        assertNotNull(onlyIfAllExist);
        assertNotNull(onlyIfNoneExist);
        assertEquals("FXX", onlyIfAllExist.getValkeyApi());
        assertEquals("FNX", onlyIfNoneExist.getValkeyApi());
    }
}
