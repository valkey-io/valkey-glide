/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class HSetExOptionsTest {

    @Test
    public void testEmptyOptionsToArgs() {
        HSetExOptions options = HSetExOptions.builder().build();
        String[] args = options.toArgs();
        assertArrayEquals(new String[0], args);
    }

    @Test
    public void testOnlyFieldConditionalChangeToArgs() {
        HSetExOptions options = HSetExOptions.builder().onlyIfAllExist().build();
        String[] args = options.toArgs();
        assertArrayEquals(new String[] {"FXX"}, args);

        options = HSetExOptions.builder().onlyIfNoneExist().build();
        args = options.toArgs();
        assertArrayEquals(new String[] {"FNX"}, args);
    }

    @Test
    public void testOnlyExpiryToArgs() {
        HSetExOptions options = HSetExOptions.builder().expiry(ExpirySet.Seconds(60L)).build();
        String[] args = options.toArgs();
        assertArrayEquals(new String[] {"EX", "60"}, args);

        options = HSetExOptions.builder().expiry(ExpirySet.KeepExisting()).build();
        args = options.toArgs();
        assertArrayEquals(new String[] {"KEEPTTL"}, args);
    }

    @Test
    public void testCombinedOptionsToArgs() {
        HSetExOptions options =
                HSetExOptions.builder().onlyIfNoneExist().expiry(ExpirySet.Seconds(60L)).build();
        String[] args = options.toArgs();
        assertArrayEquals(new String[] {"FNX", "EX", "60"}, args);

        options =
                HSetExOptions.builder().onlyIfAllExist().expiry(ExpirySet.Milliseconds(5000L)).build();
        args = options.toArgs();
        assertArrayEquals(new String[] {"FXX", "PX", "5000"}, args);
    }

    @Test
    public void testBuilderConvenienceMethods() {
        HSetExOptions options1 = HSetExOptions.builder().onlyIfAllExist().build();

        HSetExOptions options2 =
                HSetExOptions.builder()
                        .fieldConditionalChange(FieldConditionalChange.ONLY_IF_ALL_EXIST)
                        .build();

        assertEquals(options1, options2);

        HSetExOptions options3 = HSetExOptions.builder().onlyIfNoneExist().build();

        HSetExOptions options4 =
                HSetExOptions.builder()
                        .fieldConditionalChange(FieldConditionalChange.ONLY_IF_NONE_EXIST)
                        .build();

        assertEquals(options3, options4);
    }

    @Test
    public void testValidationWithPersistThrowsException() {
        HSetExOptions options = HSetExOptions.builder().expiry(ExpirySet.Persist()).build();

        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, options::toArgs);

        assertEquals(
                "PERSIST option is only supported by HGETEX command, not HSETEX", exception.getMessage());
    }

    @Test
    public void testEqualsAndHashCode() {
        HSetExOptions options1 =
                HSetExOptions.builder().onlyIfNoneExist().expiry(ExpirySet.Seconds(60L)).build();

        HSetExOptions options2 =
                HSetExOptions.builder().onlyIfNoneExist().expiry(ExpirySet.Seconds(60L)).build();

        HSetExOptions options3 =
                HSetExOptions.builder().onlyIfAllExist().expiry(ExpirySet.Seconds(60L)).build();

        // Test equals
        assertEquals(options1, options2);
        assertNotEquals(options1, options3);
        assertNotEquals(options1, null);
        assertNotEquals(options1, "not an HSetExOptions");

        // Test hashCode
        assertEquals(options1.hashCode(), options2.hashCode());
        assertNotEquals(options1.hashCode(), options3.hashCode());
    }

    @Test
    public void testToString() {
        HSetExOptions options =
                HSetExOptions.builder().onlyIfNoneExist().expiry(ExpirySet.Seconds(60L)).build();

        String toString = options.toString();
        assertEquals(
                "HSetExOptions{fieldConditionalChange=ONLY_IF_NONE_EXIST, expiry=ExpirySet{type=SECONDS,"
                        + " count=60}}",
                toString);
    }

    @Test
    public void testImmutability() {
        HSetExOptions.HSetExOptionsBuilder builder =
                HSetExOptions.builder().onlyIfNoneExist().expiry(ExpirySet.Seconds(60L));

        HSetExOptions options1 = builder.build();
        HSetExOptions options2 = builder.build();

        // Both instances should be equal but different objects
        assertEquals(options1, options2);
        assertEquals(options1.hashCode(), options2.hashCode());

        // Verify they produce the same args
        assertArrayEquals(options1.toArgs(), options2.toArgs());
    }

    @Test
    public void testAllExpiryTypes() {
        // Test all supported expiry types for HSETEX
        HSetExOptions options;
        String[] args;

        // EX (seconds)
        options = HSetExOptions.builder().expiry(ExpirySet.Seconds(60L)).build();
        args = options.toArgs();
        assertArrayEquals(new String[] {"EX", "60"}, args);

        // PX (milliseconds)
        options = HSetExOptions.builder().expiry(ExpirySet.Milliseconds(5000L)).build();
        args = options.toArgs();
        assertArrayEquals(new String[] {"PX", "5000"}, args);

        // EXAT (Unix seconds)
        options = HSetExOptions.builder().expiry(ExpirySet.UnixSeconds(1640995200L)).build();
        args = options.toArgs();
        assertArrayEquals(new String[] {"EXAT", "1640995200"}, args);

        // PXAT (Unix milliseconds)
        options = HSetExOptions.builder().expiry(ExpirySet.UnixMilliseconds(1640995200000L)).build();
        args = options.toArgs();
        assertArrayEquals(new String[] {"PXAT", "1640995200000"}, args);

        // KEEPTTL
        options = HSetExOptions.builder().expiry(ExpirySet.KeepExisting()).build();
        args = options.toArgs();
        assertArrayEquals(new String[] {"KEEPTTL"}, args);
    }

    @Test
    public void testBuilderMethodChaining() {
        HSetExOptions options =
                HSetExOptions.builder()
                        .onlyIfNoneExist()
                        .expiry(ExpirySet.Seconds(60L))
                        .onlyIfAllExist() // This should override the previous setting
                        .build();

        String[] args = options.toArgs();
        assertArrayEquals(new String[] {"FXX", "EX", "60"}, args);
    }
}
