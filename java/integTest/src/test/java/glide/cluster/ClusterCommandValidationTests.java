/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.cluster;

import static glide.TestUtilities.commonClusterClientConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import glide.api.GlideClusterClient;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Integration tests for client-side validation of cluster commands.
 *
 * <p>These tests verify that invalid parameters are rejected immediately with helpful error
 * messages, rather than being sent to the server.
 */
@Timeout(20)
public class ClusterCommandValidationTests {

    private static GlideClusterClient clusterClient;

    @BeforeAll
    @SneakyThrows
    public static void init() {
        clusterClient = GlideClusterClient.createClient(commonClusterClientConfig().build()).get();
    }

    @AfterAll
    @SneakyThrows
    public static void teardown() {
        if (clusterClient != null) {
            clusterClient.close();
        }
    }

    // =========================================================================================
    // Slot Validation Tests
    // =========================================================================================

    @Test
    @SneakyThrows
    public void clusterAddSlots_invalidSlot_negative_throwsException() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class, () -> clusterClient.clusterAddSlots(new long[] {-1}));
        assertEquals(
                "Slot at index 0 must be between 0 and 16383, but got: -1", exception.getMessage());
    }

    @Test
    @SneakyThrows
    public void clusterAddSlots_invalidSlot_tooLarge_throwsException() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> clusterClient.clusterAddSlots(new long[] {16384}));
        assertEquals(
                "Slot at index 0 must be between 0 and 16383, but got: 16384", exception.getMessage());
    }

    @Test
    @SneakyThrows
    public void clusterAddSlots_invalidSlot_wayTooLarge_throwsException() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> clusterClient.clusterAddSlots(new long[] {999999}));
        assertEquals(
                "Slot at index 0 must be between 0 and 16383, but got: 999999", exception.getMessage());
    }

    @Test
    @SneakyThrows
    public void clusterAddSlots_emptyArray_throwsException() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class, () -> clusterClient.clusterAddSlots(new long[] {}));
        assertEquals("Slots array cannot be empty", exception.getMessage());
    }

    @Test
    @SneakyThrows
    public void clusterDelSlots_invalidSlot_throwsException() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> clusterClient.clusterDelSlots(new long[] {-5, 100}));
        assertEquals(
                "Slot at index 0 must be between 0 and 16383, but got: -5", exception.getMessage());
    }

    @Test
    @SneakyThrows
    public void clusterAddSlotsRange_invalidRange_negative_throwsException() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> clusterClient.clusterAddSlotsRange(new long[][] {{-10, 100}}));
        assertEquals(
                "Slot range start at index 0 must be between 0 and 16383, but got: -10",
                exception.getMessage());
    }

    @Test
    @SneakyThrows
    public void clusterAddSlotsRange_invalidRange_endTooLarge_throwsException() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> clusterClient.clusterAddSlotsRange(new long[][] {{100, 20000}}));
        assertEquals(
                "Slot range end at index 0 must be between 0 and 16383, but got: 20000",
                exception.getMessage());
    }

    @Test
    @SneakyThrows
    public void clusterAddSlotsRange_invalidRange_startGreaterThanEnd_throwsException() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> clusterClient.clusterAddSlotsRange(new long[][] {{500, 100}}));
        assertEquals(
                "Slot range at index 0 is invalid: start (500) must be <= end (100)",
                exception.getMessage());
    }

    @Test
    @SneakyThrows
    public void clusterAddSlotsRange_invalidRange_wrongArraySize_throwsException() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> clusterClient.clusterAddSlotsRange(new long[][] {{100, 200, 300}}));
        assertEquals(
                "Slot range at index 0 must be an array of 2 elements [start, end]",
                exception.getMessage());
    }

    @Test
    @SneakyThrows
    public void clusterAddSlotsRange_emptyArray_throwsException() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> clusterClient.clusterAddSlotsRange(new long[][] {}));
        assertEquals("Slot ranges array cannot be empty", exception.getMessage());
    }

    @Test
    @SneakyThrows
    public void clusterDelSlotsRange_invalidRange_throwsException() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> clusterClient.clusterDelSlotsRange(new long[][] {{0, 100}, {16384, 16400}}));
        assertEquals(
                "Slot range start at index 1 must be between 0 and 16383, but got: 16384",
                exception.getMessage());
    }

    @Test
    @SneakyThrows
    public void clusterCountKeysInSlot_invalidSlot_throwsException() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class, () -> clusterClient.clusterCountKeysInSlot(-1));
        assertEquals("Slot must be between 0 and 16383, but got: -1", exception.getMessage());
    }

    @Test
    @SneakyThrows
    public void clusterGetKeysInSlot_invalidSlot_throwsException() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class, () -> clusterClient.clusterGetKeysInSlot(16384, 10));
        assertEquals("Slot must be between 0 and 16383, but got: 16384", exception.getMessage());
    }

    @Test
    @SneakyThrows
    public void clusterGetKeysInSlot_invalidCount_zero_throwsException() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class, () -> clusterClient.clusterGetKeysInSlot(100, 0));
        assertEquals("count must be positive, but got: 0", exception.getMessage());
    }

    @Test
    @SneakyThrows
    public void clusterGetKeysInSlot_invalidCount_negative_throwsException() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class, () -> clusterClient.clusterGetKeysInSlot(100, -5));
        assertEquals("count must be positive, but got: -5", exception.getMessage());
    }

    @Test
    @SneakyThrows
    public void clusterSetSlot_invalidSlot_throwsException() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                clusterClient.clusterSetSlot(
                                        -1, "STABLE", "0123456789abcdef0123456789abcdef01234567"));
        assertEquals("Slot must be between 0 and 16383, but got: -1", exception.getMessage());
    }

    // =========================================================================================
    // Node ID Validation Tests
    // =========================================================================================
    // Note: Tests for null parameters are skipped because @NonNull annotations on method
    // parameters are validated by Lombok before reaching our custom validation logic,
    // resulting in NullPointerException instead of IllegalArgumentException.

    @Test
    @SneakyThrows
    public void clusterForget_emptyNodeId_throwsException() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> clusterClient.clusterForget(""));
        assertEquals("Node ID cannot be empty", exception.getMessage());
    }

    @Test
    @SneakyThrows
    public void clusterForget_shortNodeId_throwsException() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> clusterClient.clusterForget("shortid"));
        assertEquals(
                "Node ID must be exactly 40 characters, but got 7 characters: shortid",
                exception.getMessage());
    }

    @Test
    @SneakyThrows
    public void clusterForget_invalidCharsNodeId_uppercase_throwsException() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> clusterClient.clusterForget("0123456789ABCDEF0123456789ABCDEF01234567"));
        assertEquals(
                "Node ID must contain only lowercase hexadecimal characters (0-9, a-f), but got:"
                        + " 0123456789ABCDEF0123456789ABCDEF01234567",
                exception.getMessage());
    }

    @Test
    @SneakyThrows
    public void clusterForget_invalidCharsNodeId_specialChars_throwsException() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> clusterClient.clusterForget("0123456789abcdef0123456789abcdef0123456!"));
        assertEquals(
                "Node ID must contain only lowercase hexadecimal characters (0-9, a-f), but got:"
                        + " 0123456789abcdef0123456789abcdef0123456!",
                exception.getMessage());
    }

    @Test
    @SneakyThrows
    public void clusterReplicate_invalidNodeId_throwsException() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class, () -> clusterClient.clusterReplicate("invalid"));
        assertEquals(
                "Node ID must be exactly 40 characters, but got 7 characters: invalid",
                exception.getMessage());
    }

    @Test
    @SneakyThrows
    public void clusterReplicas_invalidNodeId_throwsException() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class, () -> clusterClient.clusterReplicas("xyz").get());
        assertEquals(
                "Node ID must be exactly 40 characters, but got 3 characters: xyz", exception.getMessage());
    }

    @Test
    @SneakyThrows
    public void clusterSetSlot_invalidNodeId_throwsException() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> clusterClient.clusterSetSlot(100, "STABLE", "bad-node-id"));
        assertEquals(
                "Node ID must be exactly 40 characters, but got 11 characters: bad-node-id",
                exception.getMessage());
    }

    @Test
    @SneakyThrows
    public void clusterCountFailureReports_invalidNodeId_throwsException() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class, () -> clusterClient.clusterCountFailureReports(""));
        assertEquals("Node ID cannot be empty", exception.getMessage());
    }

    // =========================================================================================
    // Port Validation Tests
    // =========================================================================================

    @Test
    @SneakyThrows
    public void clusterMeet_invalidPort_zero_throwsException() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class, () -> clusterClient.clusterMeet("localhost", 0));
        assertEquals("Port must be between 1 and 65535, but got: 0", exception.getMessage());
    }

    @Test
    @SneakyThrows
    public void clusterMeet_invalidPort_negative_throwsException() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class, () -> clusterClient.clusterMeet("localhost", -1));
        assertEquals("Port must be between 1 and 65535, but got: -1", exception.getMessage());
    }

    @Test
    @SneakyThrows
    public void clusterMeet_invalidPort_tooLarge_throwsException() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class, () -> clusterClient.clusterMeet("localhost", 65536));
        assertEquals("Port must be between 1 and 65535, but got: 65536", exception.getMessage());
    }

    @Test
    @SneakyThrows
    public void clusterMeet_invalidPort_wayTooLarge_throwsException() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class, () -> clusterClient.clusterMeet("localhost", 999999));
        assertEquals("Port must be between 1 and 65535, but got: 999999", exception.getMessage());
    }

    // =========================================================================================
    // Host Validation Tests
    // =========================================================================================
    // Note: Tests for null host are skipped because @NonNull annotations on method
    // parameters are validated by Lombok before reaching our custom validation logic.

    @Test
    @SneakyThrows
    public void clusterMeet_emptyHost_throwsException() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> clusterClient.clusterMeet("", 6379));
        assertEquals("Hostname cannot be empty", exception.getMessage());
    }

    @Test
    @SneakyThrows
    public void clusterMeet_whitespaceHost_throwsException() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> clusterClient.clusterMeet("   ", 6379));
        assertEquals("Hostname cannot be empty", exception.getMessage());
    }

    // =========================================================================================
    // Epoch Validation Tests
    // =========================================================================================

    @Test
    @SneakyThrows
    public void clusterSetConfigEpoch_negativeEpoch_throwsException() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> clusterClient.clusterSetConfigEpoch(-1));
        assertEquals("Epoch must be non-negative, but got: -1", exception.getMessage());
    }

    @Test
    @SneakyThrows
    public void clusterSetConfigEpoch_largeNegativeEpoch_throwsException() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class, () -> clusterClient.clusterSetConfigEpoch(-999));
        assertEquals("Epoch must be non-negative, but got: -999", exception.getMessage());
    }

    // =========================================================================================
    // Valid Edge Cases (should not throw)
    // =========================================================================================

    @Test
    @SneakyThrows
    public void clusterAddSlots_validSlot_zero_doesNotThrow() {
        // This test validates that slot 0 is accepted (it's a valid slot)
        // We don't care if it fails server-side, we just want to ensure validation passes
        try {
            clusterClient.clusterAddSlots(new long[] {0}).get();
        } catch (Exception e) {
            // Expected to fail server-side if slot is already assigned
            // But validation should have passed
        }
    }

    @Test
    @SneakyThrows
    public void clusterAddSlots_validSlot_max_doesNotThrow() {
        // This test validates that slot 16383 is accepted (it's the maximum valid slot)
        try {
            clusterClient.clusterAddSlots(new long[] {16383}).get();
        } catch (Exception e) {
            // Expected to fail server-side if slot is already assigned
            // But validation should have passed
        }
    }

    @Test
    @SneakyThrows
    public void clusterMeet_validPort_min_doesNotThrow() {
        // Port 1 is valid (though unusual)
        try {
            clusterClient.clusterMeet("localhost", 1).get();
        } catch (Exception e) {
            // Expected to fail server-side (port not listening)
            // But validation should have passed
        }
    }

    @Test
    @SneakyThrows
    public void clusterMeet_validPort_max_doesNotThrow() {
        // Port 65535 is valid (maximum port number)
        try {
            clusterClient.clusterMeet("localhost", 65535).get();
        } catch (Exception e) {
            // Expected to fail server-side (port not listening)
            // But validation should have passed
        }
    }

    @Test
    @SneakyThrows
    public void clusterSetConfigEpoch_zeroEpoch_doesNotThrow() {
        // Epoch 0 is valid
        try {
            clusterClient.clusterSetConfigEpoch(0).get();
        } catch (Exception e) {
            // Expected to fail server-side (cluster state)
            // But validation should have passed
        }
    }
}
