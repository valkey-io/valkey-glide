/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Test class for HGetExExpiry class.
 *
 * <p>Tests the HGetExExpiry class used specifically by HGETEX command for setting field expiration
 * times, including the PERSIST option.
 */
public class HGetExExpiryTest {

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
    public void testHGetExExpiry_validPositiveArguments() {
        // Test that positive arguments work correctly
        HGetExExpiry expiry1 = HGetExExpiry.Seconds(1L);
        assertArrayEquals(new String[] {"EX", "1"}, expiry1.toArgs());

        HGetExExpiry expiry2 = HGetExExpiry.Milliseconds(1L);
        assertArrayEquals(new String[] {"PX", "1"}, expiry2.toArgs());

        HGetExExpiry expiry3 = HGetExExpiry.UnixSeconds(1L);
        assertArrayEquals(new String[] {"EXAT", "1"}, expiry3.toArgs());

        HGetExExpiry expiry4 = HGetExExpiry.UnixMilliseconds(1L);
        assertArrayEquals(new String[] {"PXAT", "1"}, expiry4.toArgs());
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

        // Test hashCode consistency for same object
        assertEquals(expiry1.hashCode(), expiry1.hashCode());
        assertEquals(persist1.hashCode(), persist1.hashCode());
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

    @Test
    public void testHGetExExpiry_persistThreadSafety() throws InterruptedException {
        HGetExExpiry persist = HGetExExpiry.Persist();
        ExecutorService executor = Executors.newFixedThreadPool(10);
        AtomicInteger successCount = new AtomicInteger(0);

        // Submit multiple tasks that use the same persist instance
        for (int i = 0; i < 100; i++) {
            executor.submit(
                    () -> {
                        String[] args = persist.toArgs();
                        if (args.length == 1 && "PERSIST".equals(args[0])) {
                            successCount.incrementAndGet();
                        }
                    });
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(100, successCount.get());
    }

    @Test
    public void testHGetExExpiry_allExpiryTypes() {
        // Test all supported expiry types
        HGetExExpiry seconds = HGetExExpiry.Seconds(60L);
        HGetExExpiry milliseconds = HGetExExpiry.Milliseconds(5000L);
        HGetExExpiry unixSeconds = HGetExExpiry.UnixSeconds(1640995200L);
        HGetExExpiry unixMilliseconds = HGetExExpiry.UnixMilliseconds(1640995200000L);
        HGetExExpiry persist = HGetExExpiry.Persist();

        assertArrayEquals(new String[] {"EX", "60"}, seconds.toArgs());
        assertArrayEquals(new String[] {"PX", "5000"}, milliseconds.toArgs());
        assertArrayEquals(new String[] {"EXAT", "1640995200"}, unixSeconds.toArgs());
        assertArrayEquals(new String[] {"PXAT", "1640995200000"}, unixMilliseconds.toArgs());
        assertArrayEquals(new String[] {"PERSIST"}, persist.toArgs());
    }

    @Test
    public void testHGetExExpiry_largeValues() {
        // Test with large values
        HGetExExpiry largeSeconds = HGetExExpiry.Seconds(Long.MAX_VALUE);
        HGetExExpiry largeMilliseconds = HGetExExpiry.Milliseconds(Long.MAX_VALUE);
        HGetExExpiry largeUnixSeconds = HGetExExpiry.UnixSeconds(Long.MAX_VALUE);
        HGetExExpiry largeUnixMilliseconds = HGetExExpiry.UnixMilliseconds(Long.MAX_VALUE);

        assertArrayEquals(new String[] {"EX", String.valueOf(Long.MAX_VALUE)}, largeSeconds.toArgs());
        assertArrayEquals(
                new String[] {"PX", String.valueOf(Long.MAX_VALUE)}, largeMilliseconds.toArgs());
        assertArrayEquals(
                new String[] {"EXAT", String.valueOf(Long.MAX_VALUE)}, largeUnixSeconds.toArgs());
        assertArrayEquals(
                new String[] {"PXAT", String.valueOf(Long.MAX_VALUE)}, largeUnixMilliseconds.toArgs());
    }

    @Test
    public void testHGetExExpiry_staticFactoryMethods() {
        // Test that static factory methods create correct instances
        assertNotNull(HGetExExpiry.Seconds(60L));
        assertNotNull(HGetExExpiry.Milliseconds(5000L));
        assertNotNull(HGetExExpiry.UnixSeconds(1640995200L));
        assertNotNull(HGetExExpiry.UnixMilliseconds(1640995200000L));
        assertNotNull(HGetExExpiry.Persist());
    }

    @Test
    public void testHGetExExpiry_differentInstancesWithSameValues() {
        // Test that different instances with same values are equal
        HGetExExpiry expiry1 = HGetExExpiry.Seconds(60L);
        HGetExExpiry expiry2 = HGetExExpiry.Seconds(60L);

        assertEquals(expiry1, expiry2);
        assertEquals(expiry1.hashCode(), expiry2.hashCode());
        assertArrayEquals(expiry1.toArgs(), expiry2.toArgs());
    }

    @Test
    public void testHGetExExpiry_persistSpecialCase() {
        // Test that PERSIST is handled as a special case
        HGetExExpiry persist = HGetExExpiry.Persist();

        String[] args = persist.toArgs();
        assertEquals(1, args.length);
        assertEquals("PERSIST", args[0]);

        // PERSIST should not have a count value in its string representation
        String toString = persist.toString();
        assertTrue(toString.contains("PERSIST"));
        assertTrue(toString.contains("null"));
    }
}
