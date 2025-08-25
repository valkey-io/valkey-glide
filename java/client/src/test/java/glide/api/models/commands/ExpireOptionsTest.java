/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/**
 * Test class for ExpireOptions enum.
 *
 * <p>Tests the shared ExpireOptions enum used across multiple hash field expiration commands
 * including HEXPIRE, HPEXPIRE, HEXPIREAT, and HPEXPIREAT.
 */
public class ExpireOptionsTest {

    @Test
    public void testExpireOptions_allValues() {
        // Test all enum values exist and have correct Valkey API strings
        assertEquals("NX", ExpireOptions.HAS_NO_EXPIRY.toArgs()[0]);
        assertEquals("XX", ExpireOptions.HAS_EXISTING_EXPIRY.toArgs()[0]);
        assertEquals("GT", ExpireOptions.NEW_EXPIRY_GREATER_THAN_CURRENT.toArgs()[0]);
        assertEquals("LT", ExpireOptions.NEW_EXPIRY_LESS_THAN_CURRENT.toArgs()[0]);
    }

    @Test
    public void testExpireOptions_toArgs() {
        // Test toArgs method returns single-element array for each option
        assertArrayEquals(new String[] {"NX"}, ExpireOptions.HAS_NO_EXPIRY.toArgs());
        assertArrayEquals(new String[] {"XX"}, ExpireOptions.HAS_EXISTING_EXPIRY.toArgs());
        assertArrayEquals(new String[] {"GT"}, ExpireOptions.NEW_EXPIRY_GREATER_THAN_CURRENT.toArgs());
        assertArrayEquals(new String[] {"LT"}, ExpireOptions.NEW_EXPIRY_LESS_THAN_CURRENT.toArgs());
    }

    @Test
    public void testExpireOptions_enumProperties() {
        // Test enum has correct number of values
        assertEquals(4, ExpireOptions.values().length);

        // Test valueOf works correctly
        assertEquals(ExpireOptions.HAS_NO_EXPIRY, ExpireOptions.valueOf("HAS_NO_EXPIRY"));
        assertEquals(ExpireOptions.HAS_EXISTING_EXPIRY, ExpireOptions.valueOf("HAS_EXISTING_EXPIRY"));
        assertEquals(
                ExpireOptions.NEW_EXPIRY_GREATER_THAN_CURRENT,
                ExpireOptions.valueOf("NEW_EXPIRY_GREATER_THAN_CURRENT"));
        assertEquals(
                ExpireOptions.NEW_EXPIRY_LESS_THAN_CURRENT,
                ExpireOptions.valueOf("NEW_EXPIRY_LESS_THAN_CURRENT"));
    }

    @Test
    public void testExpireOptions_threadSafety() {
        // Test that enum values are thread-safe
        ExpireOptions option = ExpireOptions.HAS_NO_EXPIRY;

        // Multiple calls should return consistent results
        String[] args1 = option.toArgs();
        String[] args2 = option.toArgs();

        assertArrayEquals(args1, args2);
        assertEquals("NX", args1[0]);
        assertEquals(1, args1.length);
    }

    @Test
    public void testExpireOptions_immutability() {
        // Test that enum values are immutable
        ExpireOptions option = ExpireOptions.HAS_NO_EXPIRY;
        String[] args = option.toArgs();

        // Modifying returned array should not affect subsequent calls
        args[0] = "MODIFIED";

        String[] newArgs = option.toArgs();
        assertEquals("NX", newArgs[0]);
        assertArrayEquals(new String[] {"NX"}, newArgs);
    }

    @Test
    public void testExpireOptions_consistency() {
        // Test that each enum value consistently returns the same result
        for (ExpireOptions option : ExpireOptions.values()) {
            String[] args1 = option.toArgs();
            String[] args2 = option.toArgs();

            assertNotNull(args1);
            assertNotNull(args2);
            assertEquals(1, args1.length);
            assertEquals(1, args2.length);
            assertArrayEquals(args1, args2);
        }
    }

    @Test
    public void testExpireOptions_semanticMeaning() {
        // Test that enum names match their semantic meaning
        assertEquals("NX", ExpireOptions.HAS_NO_EXPIRY.toArgs()[0]);
        assertEquals("XX", ExpireOptions.HAS_EXISTING_EXPIRY.toArgs()[0]);
        assertEquals("GT", ExpireOptions.NEW_EXPIRY_GREATER_THAN_CURRENT.toArgs()[0]);
        assertEquals("LT", ExpireOptions.NEW_EXPIRY_LESS_THAN_CURRENT.toArgs()[0]);
    }

    @Test
    public void testExpireOptions_enumEquality() {
        // Test enum equality
        assertEquals(ExpireOptions.HAS_NO_EXPIRY, ExpireOptions.HAS_NO_EXPIRY);
        assertEquals(ExpireOptions.HAS_EXISTING_EXPIRY, ExpireOptions.HAS_EXISTING_EXPIRY);
        assertEquals(
                ExpireOptions.NEW_EXPIRY_GREATER_THAN_CURRENT,
                ExpireOptions.NEW_EXPIRY_GREATER_THAN_CURRENT);
        assertEquals(
                ExpireOptions.NEW_EXPIRY_LESS_THAN_CURRENT, ExpireOptions.NEW_EXPIRY_LESS_THAN_CURRENT);
    }

    @Test
    public void testExpireOptions_toString() {
        // Test toString method (inherited from Enum)
        assertEquals("HAS_NO_EXPIRY", ExpireOptions.HAS_NO_EXPIRY.toString());
        assertEquals("HAS_EXISTING_EXPIRY", ExpireOptions.HAS_EXISTING_EXPIRY.toString());
        assertEquals(
                "NEW_EXPIRY_GREATER_THAN_CURRENT",
                ExpireOptions.NEW_EXPIRY_GREATER_THAN_CURRENT.toString());
        assertEquals(
                "NEW_EXPIRY_LESS_THAN_CURRENT", ExpireOptions.NEW_EXPIRY_LESS_THAN_CURRENT.toString());
    }
}
