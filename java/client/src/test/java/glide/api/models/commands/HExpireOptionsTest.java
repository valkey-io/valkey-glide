/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

public class HExpireOptionsTest {

    @Test
    public void testHExpireOptions_noCondition() {
        HExpireOptions options = HExpireOptions.builder().build();
        assertArrayEquals(new String[0], options.toArgs());
    }

    @Test
    public void testHExpireOptions_onlyIfNoExpiry() {
        HExpireOptions options = HExpireOptions.builder().onlyIfNoExpiry().build();
        assertArrayEquals(new String[] {"NX"}, options.toArgs());
    }

    @Test
    public void testHExpireOptions_onlyIfHasExpiry() {
        HExpireOptions options = HExpireOptions.builder().onlyIfHasExpiry().build();
        assertArrayEquals(new String[] {"XX"}, options.toArgs());
    }

    @Test
    public void testHExpireOptions_onlyIfGreaterThanCurrent() {
        HExpireOptions options = HExpireOptions.builder().onlyIfGreaterThanCurrent().build();
        assertArrayEquals(new String[] {"GT"}, options.toArgs());
    }

    @Test
    public void testHExpireOptions_onlyIfLessThanCurrent() {
        HExpireOptions options = HExpireOptions.builder().onlyIfLessThanCurrent().build();
        assertArrayEquals(new String[] {"LT"}, options.toArgs());
    }

    @Test
    public void testHExpireOptions_equalsAndHashCode() {
        HExpireOptions options1 = HExpireOptions.builder().onlyIfNoExpiry().build();
        HExpireOptions options2 = HExpireOptions.builder().onlyIfNoExpiry().build();
        HExpireOptions options3 = HExpireOptions.builder().onlyIfHasExpiry().build();

        assertEquals(options1, options2);
        assertEquals(options1.hashCode(), options2.hashCode());
        assertNotEquals(options1, options3);
        assertNotEquals(options1.hashCode(), options3.hashCode());
    }

    @Test
    public void testHExpireOptions_toString() {
        HExpireOptions options = HExpireOptions.builder().onlyIfNoExpiry().build();
        String toString = options.toString();
        assertEquals("HExpireOptions(condition=HAS_NO_EXPIRY)", toString);
    }

    @Test
    public void testHPExpireOptions_noCondition() {
        HPExpireOptions options = HPExpireOptions.builder().build();
        assertArrayEquals(new String[0], options.toArgs());
    }

    @Test
    public void testHPExpireOptions_onlyIfNoExpiry() {
        HPExpireOptions options = HPExpireOptions.builder().onlyIfNoExpiry().build();
        assertArrayEquals(new String[] {"NX"}, options.toArgs());
    }

    @Test
    public void testHPExpireOptions_onlyIfHasExpiry() {
        HPExpireOptions options = HPExpireOptions.builder().onlyIfHasExpiry().build();
        assertArrayEquals(new String[] {"XX"}, options.toArgs());
    }

    @Test
    public void testHPExpireOptions_onlyIfGreaterThanCurrent() {
        HPExpireOptions options = HPExpireOptions.builder().onlyIfGreaterThanCurrent().build();
        assertArrayEquals(new String[] {"GT"}, options.toArgs());
    }

    @Test
    public void testHPExpireOptions_onlyIfLessThanCurrent() {
        HPExpireOptions options = HPExpireOptions.builder().onlyIfLessThanCurrent().build();
        assertArrayEquals(new String[] {"LT"}, options.toArgs());
    }

    @Test
    public void testHPExpireOptions_equalsAndHashCode() {
        HPExpireOptions options1 = HPExpireOptions.builder().onlyIfNoExpiry().build();
        HPExpireOptions options2 = HPExpireOptions.builder().onlyIfNoExpiry().build();
        HPExpireOptions options3 = HPExpireOptions.builder().onlyIfHasExpiry().build();

        assertEquals(options1, options2);
        assertEquals(options1.hashCode(), options2.hashCode());
        assertNotEquals(options1, options3);
        assertNotEquals(options1.hashCode(), options3.hashCode());
    }

    @Test
    public void testHPExpireOptions_toString() {
        HPExpireOptions options = HPExpireOptions.builder().onlyIfNoExpiry().build();
        String toString = options.toString();
        assertEquals("HPExpireOptions(condition=HAS_NO_EXPIRY)", toString);
    }

    @Test
    public void testHExpireAtOptions_noCondition() {
        HExpireAtOptions options = HExpireAtOptions.builder().build();
        assertArrayEquals(new String[0], options.toArgs());
    }

    @Test
    public void testHExpireAtOptions_onlyIfNoExpiry() {
        HExpireAtOptions options = HExpireAtOptions.builder().onlyIfNoExpiry().build();
        assertArrayEquals(new String[] {"NX"}, options.toArgs());
    }

    @Test
    public void testHExpireAtOptions_onlyIfHasExpiry() {
        HExpireAtOptions options = HExpireAtOptions.builder().onlyIfHasExpiry().build();
        assertArrayEquals(new String[] {"XX"}, options.toArgs());
    }

    @Test
    public void testHExpireAtOptions_onlyIfGreaterThanCurrent() {
        HExpireAtOptions options = HExpireAtOptions.builder().onlyIfGreaterThanCurrent().build();
        assertArrayEquals(new String[] {"GT"}, options.toArgs());
    }

    @Test
    public void testHExpireAtOptions_onlyIfLessThanCurrent() {
        HExpireAtOptions options = HExpireAtOptions.builder().onlyIfLessThanCurrent().build();
        assertArrayEquals(new String[] {"LT"}, options.toArgs());
    }

    @Test
    public void testHExpireAtOptions_equalsAndHashCode() {
        HExpireAtOptions options1 = HExpireAtOptions.builder().onlyIfNoExpiry().build();
        HExpireAtOptions options2 = HExpireAtOptions.builder().onlyIfNoExpiry().build();
        HExpireAtOptions options3 = HExpireAtOptions.builder().onlyIfHasExpiry().build();

        assertEquals(options1, options2);
        assertEquals(options1.hashCode(), options2.hashCode());
        assertNotEquals(options1, options3);
        assertNotEquals(options1.hashCode(), options3.hashCode());
    }

    @Test
    public void testHExpireAtOptions_toString() {
        HExpireAtOptions options = HExpireAtOptions.builder().onlyIfNoExpiry().build();
        String toString = options.toString();
        assertEquals("HExpireAtOptions(condition=HAS_NO_EXPIRY)", toString);
    }

    @Test
    public void testHPExpireAtOptions_noCondition() {
        HPExpireAtOptions options = HPExpireAtOptions.builder().build();
        assertArrayEquals(new String[0], options.toArgs());
    }

    @Test
    public void testHPExpireAtOptions_onlyIfNoExpiry() {
        HPExpireAtOptions options = HPExpireAtOptions.builder().onlyIfNoExpiry().build();
        assertArrayEquals(new String[] {"NX"}, options.toArgs());
    }

    @Test
    public void testHPExpireAtOptions_onlyIfHasExpiry() {
        HPExpireAtOptions options = HPExpireAtOptions.builder().onlyIfHasExpiry().build();
        assertArrayEquals(new String[] {"XX"}, options.toArgs());
    }

    @Test
    public void testHPExpireAtOptions_onlyIfGreaterThanCurrent() {
        HPExpireAtOptions options = HPExpireAtOptions.builder().onlyIfGreaterThanCurrent().build();
        assertArrayEquals(new String[] {"GT"}, options.toArgs());
    }

    @Test
    public void testHPExpireAtOptions_onlyIfLessThanCurrent() {
        HPExpireAtOptions options = HPExpireAtOptions.builder().onlyIfLessThanCurrent().build();
        assertArrayEquals(new String[] {"LT"}, options.toArgs());
    }

    @Test
    public void testHPExpireAtOptions_equalsAndHashCode() {
        HPExpireAtOptions options1 = HPExpireAtOptions.builder().onlyIfNoExpiry().build();
        HPExpireAtOptions options2 = HPExpireAtOptions.builder().onlyIfNoExpiry().build();
        HPExpireAtOptions options3 = HPExpireAtOptions.builder().onlyIfHasExpiry().build();

        assertEquals(options1, options2);
        assertEquals(options1.hashCode(), options2.hashCode());
        assertNotEquals(options1, options3);
        assertNotEquals(options1.hashCode(), options3.hashCode());
    }

    @Test
    public void testHPExpireAtOptions_toString() {
        HPExpireAtOptions options = HPExpireAtOptions.builder().onlyIfNoExpiry().build();
        String toString = options.toString();
        assertEquals("HPExpireAtOptions(condition=HAS_NO_EXPIRY)", toString);
    }

    @Test
    public void testThreadSafety_immutability() {
        // Test that all option classes are immutable by verifying that builder creates new instances
        HExpireOptions.HExpireOptionsBuilder builder1 = HExpireOptions.builder();
        HExpireOptions options1 = builder1.onlyIfNoExpiry().build();
        HExpireOptions options2 = builder1.onlyIfHasExpiry().build();

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
        HExpireOptions options = HExpireOptions.builder().onlyIfNoExpiry().build();
        assertArrayEquals(new String[] {"NX"}, options.toArgs());

        HPExpireOptions hpOptions = HPExpireOptions.builder().onlyIfGreaterThanCurrent().build();
        assertArrayEquals(new String[] {"GT"}, hpOptions.toArgs());

        HExpireAtOptions atOptions = HExpireAtOptions.builder().onlyIfLessThanCurrent().build();
        assertArrayEquals(new String[] {"LT"}, atOptions.toArgs());

        HPExpireAtOptions hpAtOptions = HPExpireAtOptions.builder().onlyIfHasExpiry().build();
        assertArrayEquals(new String[] {"XX"}, hpAtOptions.toArgs());
    }
}
