/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for all hash field expiration command option classes.
 *
 * <p>This test class covers: - ExpireOptions enum - FieldConditionalChange enum - HGetExExpiry
 * class - HSetExOptions class - HGetExOptions class - HashFieldExpirationConditionOptions class -
 * HashFieldExpirationConditionOptions class - HashFieldExpirationConditionOptions class -
 * HashFieldExpirationConditionOptions class
 *
 * <p>Tests include validation of equals(), hashCode(), toString(), thread safety, and immutability.
 */
public class HashFieldExpirationCommandOptionsTest {

    // ========== ExpireOptions Enum Tests ==========

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
        // Test toArgs method returns single-element array
        assertArrayEquals(new String[] {"NX"}, ExpireOptions.HAS_NO_EXPIRY.toArgs());
        assertArrayEquals(new String[] {"XX"}, ExpireOptions.HAS_EXISTING_EXPIRY.toArgs());
        assertArrayEquals(new String[] {"GT"}, ExpireOptions.NEW_EXPIRY_GREATER_THAN_CURRENT.toArgs());
        assertArrayEquals(new String[] {"LT"}, ExpireOptions.NEW_EXPIRY_LESS_THAN_CURRENT.toArgs());
    }

    @Test
    public void testExpireOptions_enumProperties() {
        // Test enum properties
        assertEquals(4, ExpireOptions.values().length);
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

        // Multiple threads accessing the same enum value should be safe
        String[] args1 = option.toArgs();
        String[] args2 = option.toArgs();

        assertArrayEquals(args1, args2);
        assertEquals("NX", args1[0]);
    }

    // ========== FieldConditionalChange Enum Tests ==========

    @Test
    public void testFieldConditionalChange_allValues() {
        // Test all enum values exist and have correct Valkey API strings
        assertEquals("FXX", FieldConditionalChange.ONLY_IF_ALL_EXIST.getValkeyApi());
        assertEquals("FNX", FieldConditionalChange.ONLY_IF_NONE_EXIST.getValkeyApi());
    }

    @Test
    public void testFieldConditionalChange_enumProperties() {
        // Test enum properties
        assertEquals(2, FieldConditionalChange.values().length);
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

        // Multiple threads accessing the same enum value should be safe
        String api1 = condition.getValkeyApi();
        String api2 = condition.getValkeyApi();

        assertEquals(api1, api2);
        assertEquals("FXX", api1);
    }

    // ========== HGetExExpiry Class Tests ==========

    @Test
    public void testHGetExExpiry_seconds() {
        HGetExExpiry expiry = HGetExExpiry.Seconds(60L);
        assertArrayEquals(new String[] {"EX", "60"}, expiry.toArgs());
    }

    @Test
    public void testHGetExExpiry_milliseconds() {
        HGetExExpiry expiry = HGetExExpiry.Milliseconds(5000L);
        assertArrayEquals(new String[] {"PX", "5000"}, expiry.toArgs());
    }

    @Test
    public void testHGetExExpiry_unixSeconds() {
        HGetExExpiry expiry = HGetExExpiry.UnixSeconds(1640995200L);
        assertArrayEquals(new String[] {"EXAT", "1640995200"}, expiry.toArgs());
    }

    @Test
    public void testHGetExExpiry_unixMilliseconds() {
        HGetExExpiry expiry = HGetExExpiry.UnixMilliseconds(1640995200000L);
        assertArrayEquals(new String[] {"PXAT", "1640995200000"}, expiry.toArgs());
    }

    @Test
    public void testHGetExExpiry_persist() {
        HGetExExpiry expiry = HGetExExpiry.Persist();
        assertArrayEquals(new String[] {"PERSIST"}, expiry.toArgs());
    }

    @Test
    public void testHGetExExpiry_invalidArguments() {
        // Test null arguments
        assertThrows(IllegalArgumentException.class, () -> HGetExExpiry.Seconds(null));
        assertThrows(IllegalArgumentException.class, () -> HGetExExpiry.Milliseconds(null));
        assertThrows(IllegalArgumentException.class, () -> HGetExExpiry.UnixSeconds(null));
        assertThrows(IllegalArgumentException.class, () -> HGetExExpiry.UnixMilliseconds(null));

        // Test non-positive arguments
        assertThrows(IllegalArgumentException.class, () -> HGetExExpiry.Seconds(0L));
        assertThrows(IllegalArgumentException.class, () -> HGetExExpiry.Seconds(-1L));
        assertThrows(IllegalArgumentException.class, () -> HGetExExpiry.Milliseconds(0L));
        assertThrows(IllegalArgumentException.class, () -> HGetExExpiry.Milliseconds(-1L));
        assertThrows(IllegalArgumentException.class, () -> HGetExExpiry.UnixSeconds(0L));
        assertThrows(IllegalArgumentException.class, () -> HGetExExpiry.UnixSeconds(-1L));
        assertThrows(IllegalArgumentException.class, () -> HGetExExpiry.UnixMilliseconds(0L));
        assertThrows(IllegalArgumentException.class, () -> HGetExExpiry.UnixMilliseconds(-1L));
    }

    @Test
    public void testHGetExExpiry_equals() {
        HGetExExpiry expiry1 = HGetExExpiry.Seconds(60L);
        HGetExExpiry expiry2 = HGetExExpiry.Seconds(60L);
        HGetExExpiry expiry3 = HGetExExpiry.Seconds(30L);
        HGetExExpiry expiry4 = HGetExExpiry.Milliseconds(60000L);
        HGetExExpiry persist1 = HGetExExpiry.Persist();
        HGetExExpiry persist2 = HGetExExpiry.Persist();

        // Test equals
        assertEquals(expiry1, expiry2);
        assertNotEquals(expiry1, expiry3);
        assertNotEquals(expiry1, expiry4);
        assertEquals(persist1, persist2);
        assertNotEquals(expiry1, persist1);
        assertNotEquals(expiry1, null);
        assertNotEquals(expiry1, "not an HGetExExpiry");
        assertEquals(expiry1, expiry1); // reflexive
    }

    @Test
    public void testHGetExExpiry_hashCode() {
        HGetExExpiry expiry1 = HGetExExpiry.Seconds(60L);
        HGetExExpiry expiry2 = HGetExExpiry.Seconds(60L);
        HGetExExpiry expiry3 = HGetExExpiry.Seconds(30L);
        HGetExExpiry persist1 = HGetExExpiry.Persist();
        HGetExExpiry persist2 = HGetExExpiry.Persist();

        // Test hashCode consistency
        assertEquals(expiry1.hashCode(), expiry2.hashCode());
        assertEquals(persist1.hashCode(), persist2.hashCode());

        // Different objects should likely have different hash codes
        assertNotEquals(expiry1.hashCode(), expiry3.hashCode());
        assertNotEquals(expiry1.hashCode(), persist1.hashCode());
    }

    @Test
    public void testHGetExExpiry_toString() {
        HGetExExpiry expiry = HGetExExpiry.Seconds(60L);
        HGetExExpiry persist = HGetExExpiry.Persist();

        String expiryString = expiry.toString();
        String persistString = persist.toString();

        assertTrue(expiryString.contains("HGetExExpiry"));
        assertTrue(expiryString.contains("SECONDS"));
        assertTrue(expiryString.contains("60"));

        assertTrue(persistString.contains("HGetExExpiry"));
        assertTrue(persistString.contains("PERSIST"));
        assertTrue(persistString.contains("null"));
    }

    @Test
    public void testHGetExExpiry_immutability() {
        HGetExExpiry expiry = HGetExExpiry.Seconds(60L);

        // Multiple calls should return the same result
        String[] args1 = expiry.toArgs();
        String[] args2 = expiry.toArgs();

        assertArrayEquals(args1, args2);

        // Modifying returned array should not affect the expiry
        args1[0] = "MODIFIED";
        assertArrayEquals(new String[] {"EX", "60"}, expiry.toArgs());
    }

    @Test
    public void testHGetExExpiry_threadSafety() throws InterruptedException {
        HGetExExpiry expiry = HGetExExpiry.Seconds(60L);
        ExecutorService executor = Executors.newFixedThreadPool(10);
        AtomicInteger successCount = new AtomicInteger(0);

        // Submit multiple tasks that use the same expiry instance
        for (int i = 0; i < 100; i++) {
            executor.submit(
                    () -> {
                        String[] args = expiry.toArgs();
                        if (args.length == 2 && "EX".equals(args[0]) && "60".equals(args[1])) {
                            successCount.incrementAndGet();
                        }
                    });
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(100, successCount.get());
    }

    // ========== HSetExOptions Class Tests ==========

    @Test
    public void testHSetExOptions_emptyOptions() {
        HSetExOptions options = HSetExOptions.builder().build();
        assertArrayEquals(new String[0], options.toArgs());
    }

    @Test
    public void testHSetExOptions_fieldConditionalChangeOnly() {
        HSetExOptions options1 = HSetExOptions.builder().onlyIfAllExist().build();
        assertArrayEquals(new String[] {"FXX"}, options1.toArgs());

        HSetExOptions options2 = HSetExOptions.builder().onlyIfNoneExist().build();
        assertArrayEquals(new String[] {"FNX"}, options2.toArgs());
    }

    @Test
    public void testHSetExOptions_expiryOnly() {
        HSetExOptions options = HSetExOptions.builder().expiry(ExpirySet.Seconds(60L)).build();
        assertArrayEquals(new String[] {"EX", "60"}, options.toArgs());
    }

    @Test
    public void testHSetExOptions_combinedOptions() {
        HSetExOptions options =
                HSetExOptions.builder().onlyIfNoneExist().expiry(ExpirySet.Seconds(60L)).build();
        assertArrayEquals(new String[] {"FNX", "EX", "60"}, options.toArgs());
    }

    @Test
    public void testHSetExOptions_builderConvenienceMethods() {
        HSetExOptions options1 = HSetExOptions.builder().onlyIfAllExist().build();
        HSetExOptions options2 =
                HSetExOptions.builder()
                        .fieldConditionalChange(FieldConditionalChange.ONLY_IF_ALL_EXIST)
                        .build();

        assertEquals(options1, options2);
    }

    @Test
    public void testHSetExOptions_persistValidation() {
        HSetExOptions options = HSetExOptions.builder().expiry(ExpirySet.Persist()).build();

        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, options::toArgs);
        assertEquals(
                "PERSIST option is only supported by HGETEX command, not HSETEX", exception.getMessage());
    }

    @Test
    public void testHSetExOptions_equals() {
        HSetExOptions options1 =
                HSetExOptions.builder().onlyIfNoneExist().expiry(ExpirySet.Seconds(60L)).build();
        HSetExOptions options2 =
                HSetExOptions.builder().onlyIfNoneExist().expiry(ExpirySet.Seconds(60L)).build();
        HSetExOptions options3 =
                HSetExOptions.builder().onlyIfAllExist().expiry(ExpirySet.Seconds(60L)).build();

        assertEquals(options1, options2);
        assertNotEquals(options1, options3);
        assertNotEquals(options1, null);
        assertNotEquals(options1, "not an HSetExOptions");
    }

    @Test
    public void testHSetExOptions_hashCode() {
        HSetExOptions options1 =
                HSetExOptions.builder().onlyIfNoneExist().expiry(ExpirySet.Seconds(60L)).build();
        HSetExOptions options2 =
                HSetExOptions.builder().onlyIfNoneExist().expiry(ExpirySet.Seconds(60L)).build();

        assertEquals(options1.hashCode(), options2.hashCode());
    }

    @Test
    public void testHSetExOptions_toString() {
        HSetExOptions options =
                HSetExOptions.builder().onlyIfNoneExist().expiry(ExpirySet.Seconds(60L)).build();

        String toString = options.toString();
        assertTrue(toString.contains("HSetExOptions"));
        assertTrue(toString.contains("ONLY_IF_NONE_EXIST"));
        assertTrue(toString.contains("SECONDS"));
    }

    @Test
    public void testHSetExOptions_immutability() {
        HSetExOptions.HSetExOptionsBuilder builder =
                HSetExOptions.builder().onlyIfNoneExist().expiry(ExpirySet.Seconds(60L));

        HSetExOptions options1 = builder.build();
        HSetExOptions options2 = builder.build();

        assertEquals(options1, options2);
        assertArrayEquals(options1.toArgs(), options2.toArgs());
    }

    @Test
    public void testHSetExOptions_threadSafety() throws InterruptedException {
        HSetExOptions options =
                HSetExOptions.builder().onlyIfNoneExist().expiry(ExpirySet.Seconds(60L)).build();

        ExecutorService executor = Executors.newFixedThreadPool(10);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < 100; i++) {
            executor.submit(
                    () -> {
                        String[] args = options.toArgs();
                        if (args.length == 3
                                && "FNX".equals(args[0])
                                && "EX".equals(args[1])
                                && "60".equals(args[2])) {
                            successCount.incrementAndGet();
                        }
                    });
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(100, successCount.get());
    }

    // ========== HGetExOptions Class Tests ==========

    @Test
    public void testHGetExOptions_withExpiry() {
        HGetExExpiry expiry = HGetExExpiry.Seconds(60L);
        HGetExOptions options = HGetExOptions.builder().expiry(expiry).build();

        assertArrayEquals(new String[] {"EX", "60"}, options.toArgs());
    }

    @Test
    public void testHGetExOptions_withoutExpiry() {
        HGetExOptions options = HGetExOptions.builder().build();
        assertArrayEquals(new String[0], options.toArgs());
    }

    @Test
    public void testHGetExOptions_withNullExpiry() {
        HGetExOptions options = HGetExOptions.builder().expiry(null).build();
        assertArrayEquals(new String[0], options.toArgs());
    }

    @Test
    public void testHGetExOptions_equals() {
        HGetExExpiry expiry1 = HGetExExpiry.Seconds(60L);
        HGetExExpiry expiry2 = HGetExExpiry.Seconds(60L);

        HGetExOptions options1 = HGetExOptions.builder().expiry(expiry1).build();
        HGetExOptions options2 = HGetExOptions.builder().expiry(expiry2).build();
        HGetExOptions options3 = HGetExOptions.builder().build();

        assertEquals(options1, options2);
        assertNotEquals(options1, options3);
        assertNotEquals(options1, null);
        assertNotEquals(options1, "not an HGetExOptions");
    }

    @Test
    public void testHGetExOptions_hashCode() {
        HGetExExpiry expiry1 = HGetExExpiry.Seconds(60L);
        HGetExExpiry expiry2 = HGetExExpiry.Seconds(60L);

        HGetExOptions options1 = HGetExOptions.builder().expiry(expiry1).build();
        HGetExOptions options2 = HGetExOptions.builder().expiry(expiry2).build();

        assertEquals(options1.hashCode(), options2.hashCode());
    }

    @Test
    public void testHGetExOptions_toString() {
        HGetExExpiry expiry = HGetExExpiry.Seconds(60L);
        HGetExOptions options = HGetExOptions.builder().expiry(expiry).build();

        String toString = options.toString();
        assertTrue(toString.contains("HGetExOptions"));
        assertTrue(toString.contains("expiry"));
    }

    @Test
    public void testHGetExOptions_immutability() {
        HGetExExpiry expiry = HGetExExpiry.Seconds(60L);
        HGetExOptions options = HGetExOptions.builder().expiry(expiry).build();

        String[] args1 = options.toArgs();
        String[] args2 = options.toArgs();

        assertArrayEquals(args1, args2);
    }

    @Test
    public void testHGetExOptions_threadSafety() throws InterruptedException {
        HGetExExpiry expiry = HGetExExpiry.Seconds(60L);
        HGetExOptions options = HGetExOptions.builder().expiry(expiry).build();

        ExecutorService executor = Executors.newFixedThreadPool(10);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < 100; i++) {
            executor.submit(
                    () -> {
                        String[] args = options.toArgs();
                        if (args.length == 2 && "EX".equals(args[0]) && "60".equals(args[1])) {
                            successCount.incrementAndGet();
                        }
                    });
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(100, successCount.get());
    }

    // ========== Generic Tests for All Expiration Condition Option Classes ==========

    @Test
    public void testAllExpirationConditionOptions_noCondition() {
        // Test all expiration condition option classes with no condition
        assertArrayEquals(
                new String[0], HashFieldExpirationConditionOptions.builder().build().toArgs());
        assertArrayEquals(
                new String[0], HashFieldExpirationConditionOptions.builder().build().toArgs());
        assertArrayEquals(
                new String[0], HashFieldExpirationConditionOptions.builder().build().toArgs());
        assertArrayEquals(
                new String[0], HashFieldExpirationConditionOptions.builder().build().toArgs());
    }

    @Test
    public void testAllExpirationConditionOptions_allConditions() {
        // Test all condition types for each option class
        String[] expectedNX = {"NX"};
        String[] expectedXX = {"XX"};
        String[] expectedGT = {"GT"};
        String[] expectedLT = {"LT"};

        // HashFieldExpirationConditionOptions
        assertArrayEquals(
                expectedNX,
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build().toArgs());
        assertArrayEquals(
                expectedXX,
                HashFieldExpirationConditionOptions.builder().onlyIfHasExpiry().build().toArgs());
        assertArrayEquals(
                expectedGT,
                HashFieldExpirationConditionOptions.builder().onlyIfGreaterThanCurrent().build().toArgs());
        assertArrayEquals(
                expectedLT,
                HashFieldExpirationConditionOptions.builder().onlyIfLessThanCurrent().build().toArgs());

        // HashFieldExpirationConditionOptions
        assertArrayEquals(
                expectedNX,
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build().toArgs());
        assertArrayEquals(
                expectedXX,
                HashFieldExpirationConditionOptions.builder().onlyIfHasExpiry().build().toArgs());
        assertArrayEquals(
                expectedGT,
                HashFieldExpirationConditionOptions.builder().onlyIfGreaterThanCurrent().build().toArgs());
        assertArrayEquals(
                expectedLT,
                HashFieldExpirationConditionOptions.builder().onlyIfLessThanCurrent().build().toArgs());

        // HashFieldExpirationConditionOptions
        assertArrayEquals(
                expectedNX,
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build().toArgs());
        assertArrayEquals(
                expectedXX,
                HashFieldExpirationConditionOptions.builder().onlyIfHasExpiry().build().toArgs());
        assertArrayEquals(
                expectedGT,
                HashFieldExpirationConditionOptions.builder().onlyIfGreaterThanCurrent().build().toArgs());
        assertArrayEquals(
                expectedLT,
                HashFieldExpirationConditionOptions.builder().onlyIfLessThanCurrent().build().toArgs());

        // HashFieldExpirationConditionOptions
        assertArrayEquals(
                expectedNX,
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build().toArgs());
        assertArrayEquals(
                expectedXX,
                HashFieldExpirationConditionOptions.builder().onlyIfHasExpiry().build().toArgs());
        assertArrayEquals(
                expectedGT,
                HashFieldExpirationConditionOptions.builder().onlyIfGreaterThanCurrent().build().toArgs());
        assertArrayEquals(
                expectedLT,
                HashFieldExpirationConditionOptions.builder().onlyIfLessThanCurrent().build().toArgs());
    }

    @Test
    public void testAllExpirationConditionOptions_equals() {
        // Test equals for all expiration condition option classes
        HashFieldExpirationConditionOptions hExpire1 =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();
        HashFieldExpirationConditionOptions hExpire2 =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();
        HashFieldExpirationConditionOptions hExpire3 =
                HashFieldExpirationConditionOptions.builder().onlyIfHasExpiry().build();

        assertEquals(hExpire1, hExpire2);
        assertNotEquals(hExpire1, hExpire3);

        HashFieldExpirationConditionOptions hpExpire1 =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();
        HashFieldExpirationConditionOptions hpExpire2 =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();

        assertEquals(hpExpire1, hpExpire2);

        HashFieldExpirationConditionOptions hExpireAt1 =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();
        HashFieldExpirationConditionOptions hExpireAt2 =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();

        assertEquals(hExpireAt1, hExpireAt2);

        HashFieldExpirationConditionOptions hpExpireAt1 =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();
        HashFieldExpirationConditionOptions hpExpireAt2 =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();

        assertEquals(hpExpireAt1, hpExpireAt2);
    }

    @Test
    public void testAllExpirationConditionOptions_hashCode() {
        // Test hashCode for all expiration condition option classes
        HashFieldExpirationConditionOptions hExpire1 =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();
        HashFieldExpirationConditionOptions hExpire2 =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();

        assertEquals(hExpire1.hashCode(), hExpire2.hashCode());

        HashFieldExpirationConditionOptions hpExpire1 =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();
        HashFieldExpirationConditionOptions hpExpire2 =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();

        assertEquals(hpExpire1.hashCode(), hpExpire2.hashCode());

        HashFieldExpirationConditionOptions hExpireAt1 =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();
        HashFieldExpirationConditionOptions hExpireAt2 =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();

        assertEquals(hExpireAt1.hashCode(), hExpireAt2.hashCode());

        HashFieldExpirationConditionOptions hpExpireAt1 =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();
        HashFieldExpirationConditionOptions hpExpireAt2 =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();

        assertEquals(hpExpireAt1.hashCode(), hpExpireAt2.hashCode());
    }

    @Test
    public void testAllExpirationConditionOptions_toString() {
        // Test toString for all expiration condition option classes
        HashFieldExpirationConditionOptions hExpire =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();
        assertTrue(hExpire.toString().contains("HashFieldExpirationConditionOptions"));
        assertTrue(hExpire.toString().contains("HAS_NO_EXPIRY"));

        HashFieldExpirationConditionOptions hpExpire =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();
        assertTrue(hpExpire.toString().contains("HashFieldExpirationConditionOptions"));
        assertTrue(hpExpire.toString().contains("HAS_NO_EXPIRY"));

        HashFieldExpirationConditionOptions hExpireAt =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();
        assertTrue(hExpireAt.toString().contains("HashFieldExpirationConditionOptions"));
        assertTrue(hExpireAt.toString().contains("HAS_NO_EXPIRY"));

        HashFieldExpirationConditionOptions hpExpireAt =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();
        assertTrue(hpExpireAt.toString().contains("HashFieldExpirationConditionOptions"));
        assertTrue(hpExpireAt.toString().contains("HAS_NO_EXPIRY"));
    }

    @Test
    public void testAllExpirationConditionOptions_immutability() {
        // Test immutability for all expiration condition option classes
        HashFieldExpirationConditionOptions hExpire =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();
        String[] args1 = hExpire.toArgs();
        String[] args2 = hExpire.toArgs();
        assertArrayEquals(args1, args2);

        HashFieldExpirationConditionOptions hpExpire =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();
        args1 = hpExpire.toArgs();
        args2 = hpExpire.toArgs();
        assertArrayEquals(args1, args2);

        HashFieldExpirationConditionOptions hExpireAt =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();
        args1 = hExpireAt.toArgs();
        args2 = hExpireAt.toArgs();
        assertArrayEquals(args1, args2);

        HashFieldExpirationConditionOptions hpExpireAt =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();
        args1 = hpExpireAt.toArgs();
        args2 = hpExpireAt.toArgs();
        assertArrayEquals(args1, args2);
    }

    @Test
    public void testAllExpirationConditionOptions_threadSafety() throws InterruptedException {
        // Test thread safety for all expiration condition option classes
        HashFieldExpirationConditionOptions hExpire =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();
        HashFieldExpirationConditionOptions hpExpire =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();
        HashFieldExpirationConditionOptions hExpireAt =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();
        HashFieldExpirationConditionOptions hpExpireAt =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();

        ExecutorService executor = Executors.newFixedThreadPool(10);
        AtomicInteger successCount = new AtomicInteger(0);

        // Test all option classes concurrently
        for (int i = 0; i < 25; i++) {
            executor.submit(
                    () -> {
                        if ("NX".equals(hExpire.toArgs()[0])) successCount.incrementAndGet();
                    });
            executor.submit(
                    () -> {
                        if ("NX".equals(hpExpire.toArgs()[0])) successCount.incrementAndGet();
                    });
            executor.submit(
                    () -> {
                        if ("NX".equals(hExpireAt.toArgs()[0])) successCount.incrementAndGet();
                    });
            executor.submit(
                    () -> {
                        if ("NX".equals(hpExpireAt.toArgs()[0])) successCount.incrementAndGet();
                    });
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(100, successCount.get());
    }

    @Test
    public void testAllExpirationConditionOptions_builderMethodChaining() {
        // Test builder method chaining for all expiration condition option classes
        HashFieldExpirationConditionOptions hExpire =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();
        assertArrayEquals(new String[] {"NX"}, hExpire.toArgs());

        HashFieldExpirationConditionOptions hpExpire =
                HashFieldExpirationConditionOptions.builder().onlyIfGreaterThanCurrent().build();
        assertArrayEquals(new String[] {"GT"}, hpExpire.toArgs());

        HashFieldExpirationConditionOptions hExpireAt =
                HashFieldExpirationConditionOptions.builder().onlyIfLessThanCurrent().build();
        assertArrayEquals(new String[] {"LT"}, hExpireAt.toArgs());

        HashFieldExpirationConditionOptions hpExpireAt =
                HashFieldExpirationConditionOptions.builder().onlyIfHasExpiry().build();
        assertArrayEquals(new String[] {"XX"}, hpExpireAt.toArgs());
    }

    // ========== Cross-Class Compatibility Tests ==========

    @Test
    public void testConsolidatedClassCompatibility() {
        // Test that the consolidated class works correctly for all hash field expiration commands
        HashFieldExpirationConditionOptions nxOption =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();
        HashFieldExpirationConditionOptions xxOption =
                HashFieldExpirationConditionOptions.builder().onlyIfHasExpiry().build();
        HashFieldExpirationConditionOptions gtOption =
                HashFieldExpirationConditionOptions.builder().onlyIfGreaterThanCurrent().build();
        HashFieldExpirationConditionOptions ltOption =
                HashFieldExpirationConditionOptions.builder().onlyIfLessThanCurrent().build();

        // Test that same conditions produce same args and are equal
        HashFieldExpirationConditionOptions anotherNxOption =
                HashFieldExpirationConditionOptions.builder().onlyIfNoExpiry().build();
        assertArrayEquals(nxOption.toArgs(), anotherNxOption.toArgs());
        assertEquals(nxOption, anotherNxOption);

        // Test that different conditions produce different args and are not equal
        assertNotEquals(nxOption, xxOption);
        assertNotEquals(nxOption, gtOption);
        assertNotEquals(nxOption, ltOption);
        assertNotEquals(xxOption, gtOption);
        assertNotEquals(xxOption, ltOption);
        assertNotEquals(gtOption, ltOption);
    }

    @Test
    public void testBuilderReusability() {
        // Test that builders can be reused to create multiple instances
        HSetExOptions.HSetExOptionsBuilder builder = HSetExOptions.builder();

        HSetExOptions options1 = builder.onlyIfAllExist().build();
        HSetExOptions options2 = builder.onlyIfNoneExist().build();

        assertNotEquals(options1, options2);
        assertArrayEquals(new String[] {"FNX"}, options2.toArgs()); // Last setting wins
    }
}
