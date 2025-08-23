/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class HashFieldExpirationOptionsTest {

    @Test
    public void testFieldConditionalChangeEnums() {
        assertEquals(
                "FXX", HashFieldExpirationOptions.FieldConditionalChange.ONLY_IF_ALL_EXIST.getValkeyApi());
        assertEquals(
                "FNX", HashFieldExpirationOptions.FieldConditionalChange.ONLY_IF_NONE_EXIST.getValkeyApi());
    }

    @Test
    public void testExpirationConditionEnums() {
        assertEquals("NX", ExpireOptions.HAS_NO_EXPIRY.toArgs()[0]);
        assertEquals("XX", ExpireOptions.HAS_EXISTING_EXPIRY.toArgs()[0]);
        assertEquals("GT", ExpireOptions.NEW_EXPIRY_GREATER_THAN_CURRENT.toArgs()[0]);
        assertEquals("LT", ExpireOptions.NEW_EXPIRY_LESS_THAN_CURRENT.toArgs()[0]);
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
    public void testExpirySetPersist() {
        HashFieldExpirationOptions.ExpirySet expiry = HashFieldExpirationOptions.ExpirySet.Persist();
        String[] args = expiry.toArgs();
        assertEquals(1, args.length);
        assertEquals("PERSIST", args[0]);
    }

    @Test
    public void testBasicOptionsToArgs() {
        HashFieldExpirationOptions options =
                HashFieldExpirationOptions.builder()
                        .fieldConditionalChange(
                                HashFieldExpirationOptions.FieldConditionalChange.ONLY_IF_ALL_EXIST)
                        .expiry(HashFieldExpirationOptions.ExpirySet.Seconds(60L))
                        .build();

        String[] args = options.toArgs();
        assertEquals(3, args.length);
        assertEquals("FXX", args[0]);
        assertEquals("EX", args[1]);
        assertEquals("60", args[2]);
    }

    @Test
    public void testExpirationConditionToArgs() {
        HashFieldExpirationOptions options =
                HashFieldExpirationOptions.builder()
                        .expirationCondition(ExpireOptions.NEW_EXPIRY_GREATER_THAN_CURRENT)
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
    public void testBuilderMethods() {
        HashFieldExpirationOptions options =
                HashFieldExpirationOptions.builder()
                        .fieldConditionalSetOnlyIfAllExist()
                        .expirationConditionOnlyIfGreaterThanCurrent()
                        .build();

        String[] args = options.toArgs();
        assertEquals(2, args.length);
        assertEquals("FXX", args[0]);
        assertEquals("GT", args[1]);
    }

    @Test
    public void testBuilderMethodsAlternative() {
        HashFieldExpirationOptions options =
                HashFieldExpirationOptions.builder()
                        .fieldConditionalSetOnlyIfNoneExist()
                        .expirationConditionOnlyIfNoExpiry()
                        .build();

        String[] args = options.toArgs();
        assertEquals(2, args.length);
        assertEquals("FNX", args[0]);
        assertEquals("NX", args[1]); // expiration condition NX
    }

    @Test
    public void testPersistWithFieldConditionalChangeValidation() {
        // Test that PERSIST cannot be combined with field conditional change options
        HashFieldExpirationOptions options =
                HashFieldExpirationOptions.builder()
                        .fieldConditionalChange(
                                HashFieldExpirationOptions.FieldConditionalChange.ONLY_IF_ALL_EXIST)
                        .expiry(HashFieldExpirationOptions.ExpirySet.Persist())
                        .build();

        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, options::toArgs);
        assertTrue(
                exception
                        .getMessage()
                        .contains("PERSIST option cannot be combined with conditional options"));
    }

    @Test
    public void testPersistWithExpirationConditionValidation() {
        // Test that PERSIST cannot be combined with expiration condition options
        HashFieldExpirationOptions options =
                HashFieldExpirationOptions.builder()
                        .expirationCondition(ExpireOptions.HAS_NO_EXPIRY)
                        .expiry(HashFieldExpirationOptions.ExpirySet.Persist())
                        .build();

        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, options::toArgs);
        assertTrue(
                exception
                        .getMessage()
                        .contains("PERSIST option cannot be combined with conditional options"));
    }

    @Test
    public void testPersistAloneIsValid() {
        // Test that PERSIST by itself is valid for HGETEX
        HashFieldExpirationOptions options =
                HashFieldExpirationOptions.builder()
                        .expiry(HashFieldExpirationOptions.ExpirySet.Persist())
                        .build();

        String[] args = options.toArgs();
        assertEquals(1, args.length);
        assertEquals("PERSIST", args[0]);
    }
}
