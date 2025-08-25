/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

public class HashFieldExpirationConditionOptionsTest {

    @Test
    public void testHashFieldExpirationConditionOptions_noCondition() {
        HashFieldExpirationConditionOptions options =
                HashFieldExpirationConditionOptions.builder().build();
        assertArrayEquals(new String[0], options.toArgs());
    }

    @Test
    public void testHashFieldExpirationConditionOptions_onlyIfNoExpiry() {
        HashFieldExpirationConditionOptions options =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();
        assertArrayEquals(new String[] {"NX"}, options.toArgs());
    }

    @Test
    public void testHashFieldExpirationConditionOptions_onlyIfHasExpiry() {
        HashFieldExpirationConditionOptions options =
                HashFieldExpirationConditionOptions.builder().onlyIfHasExpiry().build();
        assertArrayEquals(new String[] {"XX"}, options.toArgs());
    }

    @Test
    public void testHashFieldExpirationConditionOptions_onlyIfGreaterThanCurrent() {
        HashFieldExpirationConditionOptions options =
                HashFieldExpirationConditionOptions.builder().onlyIfGreaterThanCurrent().build();
        assertArrayEquals(new String[] {"GT"}, options.toArgs());
    }

    @Test
    public void testHashFieldExpirationConditionOptions_onlyIfLessThanCurrent() {
        HashFieldExpirationConditionOptions options =
                HashFieldExpirationConditionOptions.builder().onlyIfLessThanCurrent().build();
        assertArrayEquals(new String[] {"LT"}, options.toArgs());
    }

    @Test
    public void testHashFieldExpirationConditionOptions_equalsAndHashCode() {
        HashFieldExpirationConditionOptions options1 =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();
        HashFieldExpirationConditionOptions options2 =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();
        HashFieldExpirationConditionOptions options3 =
                HashFieldExpirationConditionOptions.builder().onlyIfHasExpiry().build();

        assertEquals(options1, options2);
        assertEquals(options1.hashCode(), options2.hashCode());
        assertNotEquals(options1, options3);
        assertNotEquals(options1.hashCode(), options3.hashCode());
    }

    @Test
    public void testHashFieldExpirationConditionOptions_toString() {
        HashFieldExpirationConditionOptions options =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();
        String toString = options.toString();
        assertEquals("HashFieldExpirationConditionOptions(condition=HAS_NO_EXPIRY)", toString);
    }

    @Test
    public void testThreadSafety_immutability() {
        // Test that all option classes are immutable by verifying that builder creates new instances
        HashFieldExpirationConditionOptions.HashFieldExpirationConditionOptionsBuilder builder1 =
                HashFieldExpirationConditionOptions.builder();
        HashFieldExpirationConditionOptions options1 = builder1.onlyIfNoExpiry().build();
        HashFieldExpirationConditionOptions options2 = builder1.onlyIfHasExpiry().build();

        // The second build should not affect the first instance
        assertArrayEquals(new String[] {"NX"}, options1.toArgs());
        assertArrayEquals(new String[] {"XX"}, options2.toArgs());

        // Test that options are immutable - toArgs() should always return the same result
        String[] args1 = options1.toArgs();
        String[] args2 = options1.toArgs();
        assertArrayEquals(args1, args2);

        // Modifying returned array should not affect the options
        args1[0] = "MODIFIED";
        assertArrayEquals(new String[] {"NX"}, options1.toArgs());
    }

    @Test
    public void testBuilderPattern_fluency() {
        // Test that builder methods return the builder instance for method chaining
        HashFieldExpirationConditionOptions options =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();
        assertArrayEquals(new String[] {"NX"}, options.toArgs());

        HashFieldExpirationConditionOptions gtOptions =
                HashFieldExpirationConditionOptions.builder().onlyIfGreaterThanCurrent().build();
        assertArrayEquals(new String[] {"GT"}, gtOptions.toArgs());

        HashFieldExpirationConditionOptions ltOptions =
                HashFieldExpirationConditionOptions.builder().onlyIfLessThanCurrent().build();
        assertArrayEquals(new String[] {"LT"}, ltOptions.toArgs());

        HashFieldExpirationConditionOptions xxOptions =
                HashFieldExpirationConditionOptions.builder().onlyIfHasExpiry().build();
        assertArrayEquals(new String[] {"XX"}, xxOptions.toArgs());
    }

    @Test
    public void testAllConditions() {
        // Test all four conditions work correctly
        assertArrayEquals(
                new String[] {"NX"},
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build().toArgs());
        assertArrayEquals(
                new String[] {"XX"},
                HashFieldExpirationConditionOptions.builder().onlyIfHasExpiry().build().toArgs());
        assertArrayEquals(
                new String[] {"GT"},
                HashFieldExpirationConditionOptions.builder().onlyIfGreaterThanCurrent().build().toArgs());
        assertArrayEquals(
                new String[] {"LT"},
                HashFieldExpirationConditionOptions.builder().onlyIfLessThanCurrent().build().toArgs());
    }
}
