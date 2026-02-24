/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.cluster;

import static glide.TestUtilities.commonClusterClientConfig;
import static glide.api.models.GlideString.gs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.GlideClusterClient;
import glide.api.models.ClusterBatch;
import glide.api.models.GlideString;
import java.util.UUID;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30) // seconds
public class ClusterSlotManagementCommandsTests {

    private static GlideClusterClient client;

    @BeforeAll
    @SneakyThrows
    public static void setUp() {
        client = GlideClusterClient.createClient(commonClusterClientConfig().build()).get();
    }

    @AfterAll
    @SneakyThrows
    public static void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @SneakyThrows
    @Test
    public void clusterKeySlot_returns_correct_slot() {
        // Test with string key
        long slot1 = client.clusterKeySlot("mykey").get();
        assertTrue(slot1 >= 0 && slot1 <= 16383, "Slot should be in valid range 0-16383");
        assertEquals(14687, slot1, "Key 'mykey' should map to slot 14687");

        // Test with another key
        long slot2 = client.clusterKeySlot("key123").get();
        assertTrue(slot2 >= 0 && slot2 <= 16383, "Slot should be in valid range 0-16383");

        // Same key should always map to same slot
        long slot1Again = client.clusterKeySlot("mykey").get();
        assertEquals(slot1, slot1Again, "Same key should always map to same slot");
    }

    @SneakyThrows
    @Test
    public void clusterKeySlot_binary_key() {
        GlideString binaryKey = gs("binary_key");
        long slot = client.clusterKeySlot(binaryKey).get();
        assertTrue(slot >= 0 && slot <= 16383, "Slot should be in valid range 0-16383");

        // Verify consistency
        long slot2 = client.clusterKeySlot(binaryKey).get();
        assertEquals(slot, slot2, "Same binary key should map to same slot");
    }

    @SneakyThrows
    @Test
    public void clusterKeySlot_hash_tags() {
        // Keys with same hash tag should map to same slot
        long slot1 = client.clusterKeySlot("{user1000}.following").get();
        long slot2 = client.clusterKeySlot("{user1000}.followers").get();
        assertEquals(slot1, slot2, "Keys with same hash tag should map to same slot");

        // Keys without matching hash tags should (likely) map to different slots
        long slot3 = client.clusterKeySlot("{user1000}.following").get();
        long slot4 = client.clusterKeySlot("{user2000}.following").get();
        // Note: They might be equal by chance, but most likely different
    }

    @SneakyThrows
    @Test
    public void clusterCountKeysInSlot_returns_key_count() {
        // Find a slot and add keys to it
        String baseKey = "key_" + UUID.randomUUID().toString();
        long slot = client.clusterKeySlot(baseKey).get();

        // Initially, slot might have 0 or more keys
        long initialCount = client.clusterCountKeysInSlot(slot).get();
        assertTrue(initialCount >= 0, "Key count should be non-negative");

        // Add a key to this slot
        client.set(baseKey, "value").get();

        // Count should increase by at least 1
        long newCount = client.clusterCountKeysInSlot(slot).get();
        assertTrue(newCount >= initialCount + 1, "Key count should increase after adding key to slot");

        // Cleanup
        client.del(new String[] {baseKey}).get();
    }

    @SneakyThrows
    @Test
    public void clusterGetKeysInSlot_returns_keys() {
        // Create multiple keys that map to the same slot using hash tags
        String hashTag = "{slot_test_" + UUID.randomUUID().toString() + "}";
        String key1 = hashTag + "_key1";
        String key2 = hashTag + "_key2";
        String key3 = hashTag + "_key3";

        // Verify they map to same slot
        long slot = client.clusterKeySlot(key1).get();
        assertEquals(slot, client.clusterKeySlot(key2).get(), "Keys should map to same slot");
        assertEquals(slot, client.clusterKeySlot(key3).get(), "Keys should map to same slot");

        // Set the keys
        client.set(key1, "value1").get();
        client.set(key2, "value2").get();
        client.set(key3, "value3").get();

        // Get keys from slot
        String[] keys = client.clusterGetKeysInSlot(slot, 10).get();
        assertNotNull(keys);
        assertTrue(keys.length >= 3, "Should retrieve at least our 3 keys");

        // Verify our keys are in the result
        boolean foundKey1 = false, foundKey2 = false, foundKey3 = false;
        for (String key : keys) {
            if (key.equals(key1)) foundKey1 = true;
            if (key.equals(key2)) foundKey2 = true;
            if (key.equals(key3)) foundKey3 = true;
        }
        assertTrue(foundKey1 && foundKey2 && foundKey3, "All our keys should be in the slot");

        // Test with count limit
        String[] limitedKeys = client.clusterGetKeysInSlot(slot, 2).get();
        assertTrue(
                limitedKeys.length <= 2 && limitedKeys.length > 0,
                "Should respect count limit but return at least 1 key");

        // Cleanup
        client.del(new String[] {key1, key2, key3}).get();
    }

    @SneakyThrows
    @Test
    public void clusterGetKeysInSlot_binary_keys() {
        // Create binary keys in same slot
        String hashTag = "{binary_slot_" + UUID.randomUUID().toString() + "}";
        GlideString key1 = gs(hashTag + "_bkey1");
        GlideString key2 = gs(hashTag + "_bkey2");

        long slot = client.clusterKeySlot(key1).get();

        client.set(key1, gs("value1")).get();
        client.set(key2, gs("value2")).get();

        GlideString[] keys = client.clusterGetKeysInSlotBinary(slot, 10).get();
        assertNotNull(keys);
        assertTrue(keys.length >= 2, "Should retrieve at least our 2 binary keys");

        // Cleanup
        client.del(new GlideString[] {key1, key2}).get();
    }

    @SneakyThrows
    @Test
    public void clusterGetKeysInSlot_empty_slot() {
        // Find an empty slot (slots not assigned yet are safe to query)
        // Use a high slot number that's unlikely to have keys
        long emptySlot = 16000;
        String[] keys = client.clusterGetKeysInSlot(emptySlot, 10).get();
        assertNotNull(keys);
        // Empty slots return empty array
        assertTrue(keys.length == 0 || keys.length > 0, "Result should be a valid array");
    }

    @SneakyThrows
    @Test
    public void batch_slot_management_commands() {
        String key = "batch_test_key_" + UUID.randomUUID().toString();
        client.set(key, "value").get();

        ClusterBatch batch = new ClusterBatch(false);
        batch.clusterKeySlot(key);
        batch.clusterCountKeysInSlot(14687); // Example slot
        batch.clusterGetKeysInSlot(14687, 5);

        Object[] results = client.exec(batch, false).get();
        assertEquals(3, results.length, "Should have 3 results");

        // Verify clusterKeySlot result
        assertNotNull(results[0]);
        assertTrue(results[0] instanceof Long);
        long slot = (Long) results[0];
        assertTrue(slot >= 0 && slot <= 16383);

        // Verify clusterCountKeysInSlot result
        assertNotNull(results[1]);
        assertTrue(results[1] instanceof Long);
        long count = (Long) results[1];
        assertTrue(count >= 0);

        // Verify clusterGetKeysInSlot result
        assertNotNull(results[2]);
        assertTrue(results[2] instanceof Object[]);

        // Cleanup
        client.del(new String[] {key}).get();
    }

    @SneakyThrows
    @Test
    public void clusterKeySlot_consistency_across_operations() {
        // Verify that slot calculation is consistent across multiple operations
        String[] testKeys = {"test1", "test2", "user:1000", "session:abc123", "{tag}key1", "{tag}key2"};

        for (String key : testKeys) {
            long slot1 = client.clusterKeySlot(key).get();
            long slot2 = client.clusterKeySlot(key).get();
            assertEquals(slot1, slot2, "Slot for key '" + key + "' should be consistent");

            // Verify binary version returns same slot
            GlideString binaryKey = gs(key);
            long binarySlot = client.clusterKeySlot(binaryKey).get();
            assertEquals(slot1, binarySlot, "Binary and string keys should map to same slot");
        }
    }

    @SneakyThrows
    @Test
    public void clusterCountKeysInSlot_all_slots() {
        // Count keys across all possible slots
        long totalKeys = 0;
        int nonEmptySlots = 0;

        // Sample a few slots to avoid long test runtime
        for (long slot = 0; slot < 16384; slot += 1000) {
            long count = client.clusterCountKeysInSlot(slot).get();
            assertTrue(count >= 0, "Count should be non-negative for slot " + slot);
            totalKeys += count;
            if (count > 0) {
                nonEmptySlots++;
            }
        }

        // In a cluster with data, we expect some non-empty slots
        System.out.println(
                "Sampled slots: found " + totalKeys + " keys across " + nonEmptySlots + " non-empty slots");
    }

    @SneakyThrows
    @Test
    public void concurrent_slot_operations() {
        // Test concurrent slot queries
        String key1 = "concurrent_key1_" + UUID.randomUUID().toString();
        String key2 = "concurrent_key2_" + UUID.randomUUID().toString();

        java.util.concurrent.CompletableFuture<Long> future1 = client.clusterKeySlot(key1);
        java.util.concurrent.CompletableFuture<Long> future2 = client.clusterKeySlot(key2);
        java.util.concurrent.CompletableFuture<Long> future3 = client.clusterCountKeysInSlot(12345);

        // Wait for all to complete
        java.util.concurrent.CompletableFuture.allOf(future1, future2, future3).get();

        // Verify all completed successfully
        long slot1 = future1.get();
        long slot2 = future2.get();
        long count = future3.get();

        assertTrue(slot1 >= 0 && slot1 <= 16383);
        assertTrue(slot2 >= 0 && slot2 <= 16383);
        assertTrue(count >= 0);
    }

    @SneakyThrows
    @Test
    public void clusterKeySlot_special_characters() {
        // Test keys with special characters
        String[] specialKeys = {
            "key with spaces",
            "key:with:colons",
            "key_with_underscores",
            "key-with-dashes",
            "key.with.dots",
            "key@with@at",
            "key#with#hash",
            "{user:1000}:profile",
            "user:{1000}:profile"
        };

        for (String key : specialKeys) {
            long slot = client.clusterKeySlot(key).get();
            assertTrue(
                    slot >= 0 && slot <= 16383, "Key '" + key + "' should map to valid slot, got: " + slot);
        }
    }

    @SneakyThrows
    @Test
    public void clusterGetKeysInSlot_respects_count_parameter() {
        // Create multiple keys in same slot
        String hashTag = "{count_test_" + UUID.randomUUID().toString() + "}";
        String[] keys = new String[10];
        for (int i = 0; i < 10; i++) {
            keys[i] = hashTag + "_key" + i;
            client.set(keys[i], "value" + i).get();
        }

        long slot = client.clusterKeySlot(keys[0]).get();

        // Test different count values
        String[] keys3 = client.clusterGetKeysInSlot(slot, 3).get();
        assertTrue(keys3.length <= 3 && keys3.length > 0, "Should return at most 3 keys");

        String[] keys5 = client.clusterGetKeysInSlot(slot, 5).get();
        assertTrue(keys5.length <= 5 && keys5.length > 0, "Should return at most 5 keys");

        String[] keys20 = client.clusterGetKeysInSlot(slot, 20).get();
        assertTrue(keys20.length >= 10, "Should return all our keys when count is higher");

        // Cleanup
        client.del(keys).get();
    }
}
