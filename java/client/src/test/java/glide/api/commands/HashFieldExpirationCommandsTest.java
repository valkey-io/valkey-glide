/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import glide.api.models.commands.HashFieldExpirationOptions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for hash field expiration commands options and validation. Integration tests are in
 * the integTest module.
 */
public class HashFieldExpirationCommandsTest {

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
                        .conditionalSetOnlyIfExists()
                        .fieldConditionalSetOnlyIfAllExist()
                        .expiry(HashFieldExpirationOptions.ExpirySet.Milliseconds(30000L))
                        .build();
        assertNotNull(options2.toArgs());

        HashFieldExpirationOptions options3 =
                HashFieldExpirationOptions.builder()
                        .conditionalSetOnlyIfNotExist()
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
        // Test conflicting conditional options
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    HashFieldExpirationOptions.builder()
                            .conditionalSetOnlyIfNotExist() // NX
                            .fieldConditionalSetOnlyIfAllExist() // FXX
                            .build()
                            .toArgs();
                });

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    HashFieldExpirationOptions.builder()
                            .conditionalSetOnlyIfExists() // XX
                            .fieldConditionalSetOnlyIfNoneExist() // FNX
                            .build()
                            .toArgs();
                });
    }

    @Test
    public void hashFieldExpirationOptions_toArgs_correctFormat() {
        // Test that options are converted to correct argument format
        HashFieldExpirationOptions options =
                HashFieldExpirationOptions.builder()
                        .conditionalSetOnlyIfExists()
                        .fieldConditionalSetOnlyIfAllExist()
                        .expiry(HashFieldExpirationOptions.ExpirySet.Seconds(60L))
                        .build();

        String[] args = options.toArgs();
        assertEquals("XX", args[0]);
        assertEquals("FXX", args[1]);
        assertEquals("EX", args[2]);
        assertEquals("60", args[3]);
    }

    @Test
    public void hashFieldExpirationOptions_builder_methods() {
        // Test all builder methods work correctly
        HashFieldExpirationOptions options =
                HashFieldExpirationOptions.builder()
                        .conditionalSetOnlyIfExists()
                        .fieldConditionalSetOnlyIfAllExist()
                        .expirationConditionOnlyIfGreaterThanCurrent()
                        .expiry(HashFieldExpirationOptions.ExpirySet.Seconds(60L))
                        .build();

        String[] args = options.toArgs();
        assertEquals(5, args.length);
        assertEquals("XX", args[0]);
        assertEquals("FXX", args[1]);
        assertEquals("GT", args[2]);
        assertEquals("EX", args[3]);
        assertEquals("60", args[4]);
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
                        .conditionalSetOnlyIfNotExist()
                        .fieldConditionalSetOnlyIfNoneExist()
                        .expirationConditionOnlyIfLessThanCurrent()
                        .expiry(HashFieldExpirationOptions.ExpirySet.UnixMilliseconds(1640995200000L))
                        .build();

        String[] args = options.toArgs();
        assertEquals(5, args.length);
        assertEquals("NX", args[0]);
        assertEquals("FNX", args[1]);
        assertEquals("LT", args[2]);
        assertEquals("PXAT", args[3]);
        assertEquals("1640995200000", args[4]);
    }
}
