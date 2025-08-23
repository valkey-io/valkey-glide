/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import glide.api.models.commands.HashFieldExpirationOptions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for hash field expiration commands options and validation. Integration tests are in
 * the integTest module.
 */
public class HashFieldExpirationOptionsCommandsTest {

    // Unit tests for HashFieldExpirationOptions validation
    @Test
    public void hashFieldExpirationOptions_validation_success() {
        // Test valid option combinations
        HashFieldExpirationOptions options1 =
                HashFieldExpirationOptions.builder()
                        .expiry(HashFieldExpirationOptions.ExpirySet.Seconds(60L))
                        .build();
        assertNotNull(options1.toArgs());

        HashFieldExpirationOptions options2 =
                HashFieldExpirationOptions.builder()
                        .fieldConditionalSetOnlyIfAllExist()
                        .expiry(HashFieldExpirationOptions.ExpirySet.Milliseconds(30000L))
                        .build();
        assertNotNull(options2.toArgs());

        HashFieldExpirationOptions options3 =
                HashFieldExpirationOptions.builder()
                        .fieldConditionalSetOnlyIfNoneExist()
                        .expiry(
                                HashFieldExpirationOptions.ExpirySet.UnixSeconds(
                                        System.currentTimeMillis() / 1000 + 3600))
                        .build();
        assertNotNull(options3.toArgs());

        HashFieldExpirationOptions options4 =
                HashFieldExpirationOptions.builder()
                        .expiry(
                                HashFieldExpirationOptions.ExpirySet.UnixMilliseconds(
                                        System.currentTimeMillis() + 3600000))
                        .build();
        assertNotNull(options4.toArgs());

        HashFieldExpirationOptions options5 =
                HashFieldExpirationOptions.builder()
                        .expiry(HashFieldExpirationOptions.ExpirySet.KeepExisting())
                        .build();
        assertNotNull(options5.toArgs());
    }

    @Test
    public void hashFieldExpirationOptions_validation_conflicting_options() {
        // Test that no conflicting options exist anymore since ConditionalChange was removed
        // This test is now mainly for demonstration that field conditional changes work independently
        HashFieldExpirationOptions options1 =
                HashFieldExpirationOptions.builder()
                        .fieldConditionalSetOnlyIfAllExist() // FXX
                        .build();
        assertNotNull(options1.toArgs());

        HashFieldExpirationOptions options2 =
                HashFieldExpirationOptions.builder()
                        .fieldConditionalSetOnlyIfNoneExist() // FNX
                        .build();
        assertNotNull(options2.toArgs());
    }

    @Test
    public void hashFieldExpirationOptions_toArgs_correctFormat() {
        // Test that options are converted to correct argument format
        HashFieldExpirationOptions options =
                HashFieldExpirationOptions.builder()
                        .fieldConditionalSetOnlyIfAllExist()
                        .expiry(HashFieldExpirationOptions.ExpirySet.Seconds(60L))
                        .build();

        String[] args = options.toArgs();
        assertEquals("FXX", args[0]);
        assertEquals("EX", args[1]);
        assertEquals("60", args[2]);
    }

    @Test
    public void hashFieldExpirationOptions_builder_methods() {
        // Test all builder methods work correctly
        HashFieldExpirationOptions options =
                HashFieldExpirationOptions.builder()
                        .fieldConditionalSetOnlyIfAllExist()
                        .expirationConditionOnlyIfGreaterThanCurrent()
                        .expiry(HashFieldExpirationOptions.ExpirySet.Seconds(60L))
                        .build();

        String[] args = options.toArgs();
        assertEquals(4, args.length);
        assertEquals("FXX", args[0]);
        assertEquals("GT", args[1]);
        assertEquals("EX", args[2]);
        assertEquals("60", args[3]);
    }

    @Test
    public void hashFieldExpirationOptions_expiration_conditions() {
        // Test all expiration conditions
        HashFieldExpirationOptions nxOptions =
                HashFieldExpirationOptions.builder().expirationConditionOnlyIfNoExpiry().build();
        assertEquals("NX", nxOptions.toArgs()[0]);

        HashFieldExpirationOptions xxOptions =
                HashFieldExpirationOptions.builder().expirationConditionOnlyIfHasExpiry().build();
        assertEquals("XX", xxOptions.toArgs()[0]);

        HashFieldExpirationOptions gtOptions =
                HashFieldExpirationOptions.builder().expirationConditionOnlyIfGreaterThanCurrent().build();
        assertEquals("GT", gtOptions.toArgs()[0]);

        HashFieldExpirationOptions ltOptions =
                HashFieldExpirationOptions.builder().expirationConditionOnlyIfLessThanCurrent().build();
        assertEquals("LT", ltOptions.toArgs()[0]);
    }

    @Test
    public void hashFieldExpirationOptions_empty_options() {
        // Test empty options
        HashFieldExpirationOptions options = HashFieldExpirationOptions.builder().build();
        String[] args = options.toArgs();
        assertEquals(0, args.length);
    }

    @Test
    public void hashFieldExpirationOptions_complex_combination() {
        // Test complex combination of options
        HashFieldExpirationOptions options =
                HashFieldExpirationOptions.builder()
                        .fieldConditionalSetOnlyIfNoneExist()
                        .expirationConditionOnlyIfLessThanCurrent()
                        .expiry(HashFieldExpirationOptions.ExpirySet.UnixMilliseconds(1640995200000L))
                        .build();

        String[] args = options.toArgs();
        assertEquals(4, args.length);
        assertEquals("FNX", args[0]);
        assertEquals("LT", args[1]);
        assertEquals("PXAT", args[2]);
        assertEquals("1640995200000", args[3]);
    }
}
