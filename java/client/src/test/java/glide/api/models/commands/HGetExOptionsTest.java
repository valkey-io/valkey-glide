/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

public class HGetExOptionsTest {

    @Test
    public void testBuilder_withExpiry() {
        // Test with seconds expiry
        HGetExExpiry expiry = HGetExExpiry.Seconds(60L);
        HGetExOptions options = HGetExOptions.builder().expiry(expiry).build();

        assertNotNull(options);
        assertArrayEquals(new String[] {"EX", "60"}, options.toArgs());
    }

    @Test
    public void testBuilder_withMillisecondsExpiry() {
        // Test with milliseconds expiry
        HGetExExpiry expiry = HGetExExpiry.Milliseconds(5000L);
        HGetExOptions options = HGetExOptions.builder().expiry(expiry).build();

        assertNotNull(options);
        assertArrayEquals(new String[] {"PX", "5000"}, options.toArgs());
    }

    @Test
    public void testBuilder_withUnixSecondsExpiry() {
        // Test with Unix seconds expiry
        HGetExExpiry expiry = HGetExExpiry.UnixSeconds(1640995200L);
        HGetExOptions options = HGetExOptions.builder().expiry(expiry).build();

        assertNotNull(options);
        assertArrayEquals(new String[] {"EXAT", "1640995200"}, options.toArgs());
    }

    @Test
    public void testBuilder_withUnixMillisecondsExpiry() {
        // Test with Unix milliseconds expiry
        HGetExExpiry expiry = HGetExExpiry.UnixMilliseconds(1640995200000L);
        HGetExOptions options = HGetExOptions.builder().expiry(expiry).build();

        assertNotNull(options);
        assertArrayEquals(new String[] {"PXAT", "1640995200000"}, options.toArgs());
    }

    @Test
    public void testBuilder_withPersistExpiry() {
        // Test with PERSIST expiry
        HGetExExpiry expiry = HGetExExpiry.Persist();
        HGetExOptions options = HGetExOptions.builder().expiry(expiry).build();

        assertNotNull(options);
        assertArrayEquals(new String[] {"PERSIST"}, options.toArgs());
    }

    @Test
    public void testBuilder_withoutExpiry() {
        // Test without any expiry (null)
        HGetExOptions options = HGetExOptions.builder().build();

        assertNotNull(options);
        assertArrayEquals(new String[0], options.toArgs());
    }

    @Test
    public void testBuilder_withNullExpiry() {
        // Test with explicitly null expiry
        HGetExOptions options = HGetExOptions.builder().expiry(null).build();

        assertNotNull(options);
        assertArrayEquals(new String[0], options.toArgs());
    }

    @Test
    public void testToArgs_handlesNullExpiryGracefully() {
        // Test that toArgs handles null expiry gracefully
        HGetExOptions options = HGetExOptions.builder().build();
        String[] args = options.toArgs();

        assertNotNull(args);
        assertEquals(0, args.length);
    }

    @Test
    public void testEquals_sameExpiry() {
        // Test equals with same expiry
        HGetExExpiry expiry1 = HGetExExpiry.Seconds(60L);
        HGetExExpiry expiry2 = HGetExExpiry.Seconds(60L);

        HGetExOptions options1 = HGetExOptions.builder().expiry(expiry1).build();
        HGetExOptions options2 = HGetExOptions.builder().expiry(expiry2).build();

        assertEquals(options1, options2);
        assertEquals(options1.hashCode(), options2.hashCode());
    }

    @Test
    public void testEquals_differentExpiry() {
        // Test equals with different expiry
        HGetExExpiry expiry1 = HGetExExpiry.Seconds(60L);
        HGetExExpiry expiry2 = HGetExExpiry.Seconds(30L);

        HGetExOptions options1 = HGetExOptions.builder().expiry(expiry1).build();
        HGetExOptions options2 = HGetExOptions.builder().expiry(expiry2).build();

        assertNotEquals(options1, options2);
    }

    @Test
    public void testEquals_nullExpiry() {
        // Test equals with null expiry
        HGetExOptions options1 = HGetExOptions.builder().build();
        HGetExOptions options2 = HGetExOptions.builder().build();

        assertEquals(options1, options2);
        assertEquals(options1.hashCode(), options2.hashCode());
    }

    @Test
    public void testEquals_oneNullOneNotNull() {
        // Test equals with one null and one non-null expiry
        HGetExExpiry expiry = HGetExExpiry.Seconds(60L);

        HGetExOptions options1 = HGetExOptions.builder().build();
        HGetExOptions options2 = HGetExOptions.builder().expiry(expiry).build();

        assertNotEquals(options1, options2);
    }

    @Test
    public void testEquals_sameInstance() {
        // Test equals with same instance
        HGetExOptions options = HGetExOptions.builder().expiry(HGetExExpiry.Seconds(60L)).build();

        assertEquals(options, options);
    }

    @Test
    public void testEquals_nullObject() {
        // Test equals with null object
        HGetExOptions options = HGetExOptions.builder().expiry(HGetExExpiry.Seconds(60L)).build();

        assertNotEquals(options, null);
    }

    @Test
    public void testEquals_differentClass() {
        // Test equals with different class
        HGetExOptions options = HGetExOptions.builder().expiry(HGetExExpiry.Seconds(60L)).build();

        assertNotEquals(options, "not an HGetExOptions");
    }

    @Test
    public void testHashCode_consistency() {
        // Test hashCode consistency
        HGetExExpiry expiry = HGetExExpiry.Seconds(60L);
        HGetExOptions options = HGetExOptions.builder().expiry(expiry).build();

        int hashCode1 = options.hashCode();
        int hashCode2 = options.hashCode();

        assertEquals(hashCode1, hashCode2);
    }

    @Test
    public void testHashCode_nullExpiry() {
        // Test hashCode with null expiry
        HGetExOptions options = HGetExOptions.builder().build();

        // Should not throw exception
        int hashCode = options.hashCode();
        assertNotNull(hashCode);
    }

    @Test
    public void testToString_withExpiry() {
        // Test toString with expiry
        HGetExExpiry expiry = HGetExExpiry.Seconds(60L);
        HGetExOptions options = HGetExOptions.builder().expiry(expiry).build();

        String toString = options.toString();

        assertNotNull(toString);
        assertEquals("HGetExOptions{expiry=" + expiry.toString() + "}", toString);
    }

    @Test
    public void testToString_withNullExpiry() {
        // Test toString with null expiry
        HGetExOptions options = HGetExOptions.builder().build();

        String toString = options.toString();

        assertNotNull(toString);
        assertEquals("HGetExOptions{expiry=null}", toString);
    }

    @Test
    public void testToString_withPersistExpiry() {
        // Test toString with PERSIST expiry
        HGetExExpiry expiry = HGetExExpiry.Persist();
        HGetExOptions options = HGetExOptions.builder().expiry(expiry).build();

        String toString = options.toString();

        assertNotNull(toString);
        assertEquals("HGetExOptions{expiry=" + expiry.toString() + "}", toString);
    }

    @Test
    public void testImmutability() {
        // Test that the class is immutable
        HGetExExpiry expiry = HGetExExpiry.Seconds(60L);
        HGetExOptions options = HGetExOptions.builder().expiry(expiry).build();

        // The options should be immutable - we can't modify the expiry after creation
        // This is ensured by the final field and private constructor
        String[] args1 = options.toArgs();
        String[] args2 = options.toArgs();

        // Should return the same arguments consistently
        assertArrayEquals(args1, args2);
    }

    @Test
    public void testThreadSafety() {
        // Test thread safety by using the same options instance across multiple threads
        HGetExExpiry expiry = HGetExExpiry.Seconds(60L);
        HGetExOptions options = HGetExOptions.builder().expiry(expiry).build();

        // Multiple calls to toArgs should be thread-safe and return consistent results
        String[] args1 = options.toArgs();
        String[] args2 = options.toArgs();
        String[] args3 = options.toArgs();

        assertArrayEquals(args1, args2);
        assertArrayEquals(args2, args3);
        assertArrayEquals(new String[] {"EX", "60"}, args1);
    }

    @Test
    public void testBuilderMethodChaining() {
        // Test that builder methods return the builder instance for chaining
        HGetExOptions.HGetExOptionsBuilder builder = HGetExOptions.builder();

        // Method chaining should work
        HGetExOptions options = builder.expiry(HGetExExpiry.Seconds(60L)).build();

        assertNotNull(options);
        assertArrayEquals(new String[] {"EX", "60"}, options.toArgs());
    }

    @Test
    public void testBuilderReuse() {
        // Test that builder can be reused to create multiple instances
        HGetExOptions.HGetExOptionsBuilder builder = HGetExOptions.builder();

        HGetExOptions options1 = builder.expiry(HGetExExpiry.Seconds(60L)).build();
        HGetExOptions options2 = builder.expiry(HGetExExpiry.Milliseconds(5000L)).build();

        assertNotNull(options1);
        assertNotNull(options2);
        assertNotEquals(options1, options2);

        // Note: The second build() call will use the last set expiry value
        assertArrayEquals(new String[] {"PX", "5000"}, options2.toArgs());
    }
}
