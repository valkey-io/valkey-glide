/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class HashFieldExpirationOptionsTest {

    @Test
    public void testConditionalChangeEnums() {
        assertEquals("XX", HashFieldExpirationOptions.ConditionalChange.ONLY_IF_EXISTS.getValkeyApi());
        assertEquals(
                "NX", HashFieldExpirationOptions.ConditionalChange.ONLY_IF_DOES_NOT_EXIST.getValkeyApi());
    }

    @Test
    public void testFieldConditionalChangeEnums() {
        assertEquals(
                "FXX", HashFieldExpirationOptions.FieldConditionalChange.ONLY_IF_ALL_EXIST.getValkeyApi());
        assertEquals(
                "FNX", HashFieldExpirationOptions.FieldConditionalChange.ONLY_IF_NONE_EXIST.getValkeyApi());
    }

    @Test
    public void testExpirationConditionEnums() {
        assertEquals(
                "NX", HashFieldExpirationOptions.ExpirationCondition.ONLY_IF_NO_EXPIRY.getValkeyApi());
        assertEquals(
                "XX", HashFieldExpirationOptions.ExpirationCondition.ONLY_IF_HAS_EXPIRY.getValkeyApi());
        assertEquals(
                "GT",
                HashFieldExpirationOptions.ExpirationCondition.ONLY_IF_GREATER_THAN_CURRENT.getValkeyApi());
        assertEquals(
                "LT",
                HashFieldExpirationOptions.ExpirationCondition.ONLY_IF_LESS_THAN_CURRENT.getValkeyApi());
    }

    @Test
    public void testExpirySetSeconds() {
        HashFieldExpirationOptions.ExpirySet expiry = HashFieldExpirationOptions.ExpirySet.Seconds(60L);
        String[] args = expiry.toArgs();
        assertEquals(2, args.length);
        assertEquals("EX", args[0]);
        assertEquals("60", args[1]);
    }

    @Test
    public void testExpirySetMilliseconds() {
        HashFieldExpirationOptions.ExpirySet expiry =
                HashFieldExpirationOptions.ExpirySet.Milliseconds(5000L);
        String[] args = expiry.toArgs();
        assertEquals(2, args.length);
        assertEquals("PX", args[0]);
        assertEquals("5000", args[1]);
    }

    @Test
    public void testExpirySetUnixSeconds() {
        HashFieldExpirationOptions.ExpirySet expiry =
                HashFieldExpirationOptions.ExpirySet.UnixSeconds(1640995200L);
        String[] args = expiry.toArgs();
        assertEquals(2, args.length);
        assertEquals("EXAT", args[0]);
        assertEquals("1640995200", args[1]);
    }

    @Test
    public void testExpirySetUnixMilliseconds() {
        HashFieldExpirationOptions.ExpirySet expiry =
                HashFieldExpirationOptions.ExpirySet.UnixMilliseconds(1640995200000L);
        String[] args = expiry.toArgs();
        assertEquals(2, args.length);
        assertEquals("PXAT", args[0]);
        assertEquals("1640995200000", args[1]);
    }

    @Test
    public void testExpirySetKeepExisting() {
        HashFieldExpirationOptions.ExpirySet expiry =
                HashFieldExpirationOptions.ExpirySet.KeepExisting();
        String[] args = expiry.toArgs();
        assertEquals(1, args.length);
        assertEquals("KEEPTTL", args[0]);
    }

    @Test
    public void testBasicOptionsToArgs() {
        HashFieldExpirationOptions options =
                HashFieldExpirationOptions.builder()
                        .conditionalChange(HashFieldExpirationOptions.ConditionalChange.ONLY_IF_EXISTS)
                        .fieldConditionalChange(
                                HashFieldExpirationOptions.FieldConditionalChange.ONLY_IF_ALL_EXIST)
                        .expiry(HashFieldExpirationOptions.ExpirySet.Seconds(60L))
                        .build();

        String[] args = options.toArgs();
        assertEquals(4, args.length);
        assertEquals("XX", args[0]);
        assertEquals("FXX", args[1]);
        assertEquals("EX", args[2]);
        assertEquals("60", args[3]);
    }

    @Test
    public void testExpirationConditionToArgs() {
        HashFieldExpirationOptions options =
                HashFieldExpirationOptions.builder()
                        .expirationCondition(
                                HashFieldExpirationOptions.ExpirationCondition.ONLY_IF_GREATER_THAN_CURRENT)
                        .build();

        String[] args = options.toArgs();
        assertEquals(1, args.length);
        assertEquals("GT", args[0]);
    }

    @Test
    public void testEmptyOptionsToArgs() {
        HashFieldExpirationOptions options = HashFieldExpirationOptions.builder().build();
        String[] args = options.toArgs();
        assertEquals(0, args.length);
    }

    @Test
    public void testValidationConflictingConditionalOptions() {
        // Test conflicting conditional options: hash doesn't exist (NX) but all fields must exist (FXX)
        HashFieldExpirationOptions options =
                HashFieldExpirationOptions.builder()
                        .conditionalChange(HashFieldExpirationOptions.ConditionalChange.ONLY_IF_DOES_NOT_EXIST)
                        .fieldConditionalChange(
                                HashFieldExpirationOptions.FieldConditionalChange.ONLY_IF_ALL_EXIST)
                        .build();

        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, options::toArgs);
        assertTrue(exception.getMessage().contains("Conflicting conditional options"));
    }

    @Test
    public void testValidationConflictingConditionalOptions2() {
        // Test conflicting conditional options: hash must exist (XX) but no fields should exist (FNX)
        HashFieldExpirationOptions options =
                HashFieldExpirationOptions.builder()
                        .conditionalChange(HashFieldExpirationOptions.ConditionalChange.ONLY_IF_EXISTS)
                        .fieldConditionalChange(
                                HashFieldExpirationOptions.FieldConditionalChange.ONLY_IF_NONE_EXIST)
                        .build();

        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, options::toArgs);
        assertTrue(exception.getMessage().contains("Conflicting conditional options"));
    }

    @Test
    public void testBuilderMethods() {
        HashFieldExpirationOptions options =
                HashFieldExpirationOptions.builder()
                        .conditionalSetOnlyIfExists()
                        .fieldConditionalSetOnlyIfAllExist()
                        .expirationConditionOnlyIfGreaterThanCurrent()
                        .build();

        String[] args = options.toArgs();
        assertEquals(3, args.length);
        assertEquals("XX", args[0]);
        assertEquals("FXX", args[1]);
        assertEquals("GT", args[2]);
    }

    @Test
    public void testBuilderMethodsAlternative() {
        HashFieldExpirationOptions options =
                HashFieldExpirationOptions.builder()
                        .conditionalSetOnlyIfNotExist()
                        .fieldConditionalSetOnlyIfNoneExist()
                        .expirationConditionOnlyIfNoExpiry()
                        .build();

        String[] args = options.toArgs();
        assertEquals(3, args.length);
        assertEquals("NX", args[0]);
        assertEquals("FNX", args[1]);
        assertEquals("NX", args[2]); // expiration condition NX
    }
}
