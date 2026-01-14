/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validation utility for cluster commands to provide fast-fail behavior and better error messages.
 *
 * <p>This class validates parameters before sending commands to the server, providing immediate
 * feedback for invalid inputs rather than waiting for server-side validation.
 */
public final class ClusterCommandValidation {

    /** Pattern for validating node IDs (40 lowercase hexadecimal characters). */
    private static final Pattern NODE_ID_PATTERN = Pattern.compile("^[0-9a-f]{40}$");

    /** Minimum valid slot number in a cluster. */
    private static final int MIN_SLOT = 0;

    /** Maximum valid slot number in a cluster (16384 total slots, 0-16383). */
    private static final int MAX_SLOT = 16383;

    /** Minimum valid port number. */
    private static final int MIN_PORT = 1;

    /** Maximum valid port number. */
    private static final int MAX_PORT = 65535;

    private ClusterCommandValidation() {
        // Utility class, prevent instantiation
    }

    /**
     * Validates that a slot number is within the valid range (0-16383).
     *
     * @param slot The slot number to validate.
     * @throws IllegalArgumentException if the slot is not in the valid range.
     */
    public static void validateSlot(long slot) {
        if (slot < MIN_SLOT || slot > MAX_SLOT) {
            throw new IllegalArgumentException(
                    String.format("Slot must be between %d and %d, but got: %d", MIN_SLOT, MAX_SLOT, slot));
        }
    }

    /**
     * Validates that all slots in an array are within the valid range (0-16383).
     *
     * @param slots The array of slot numbers to validate.
     * @throws IllegalArgumentException if any slot is not in the valid range.
     */
    public static void validateSlots(long[] slots) {
        if (slots == null) {
            throw new IllegalArgumentException("Slots array cannot be null");
        }
        if (slots.length == 0) {
            throw new IllegalArgumentException("Slots array cannot be empty");
        }
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] < MIN_SLOT || slots[i] > MAX_SLOT) {
                throw new IllegalArgumentException(
                        String.format(
                                "Slot at index %d must be between %d and %d, but got: %d",
                                i, MIN_SLOT, MAX_SLOT, slots[i]));
            }
        }
    }

    /**
     * Validates that all slot ranges are within the valid range (0-16383) and properly formed.
     *
     * @param slotRanges The array of slot range entries to validate. Each entry represents a range
     *     where the key is the start slot and the value is the end slot.
     * @throws IllegalArgumentException if any range is invalid.
     */
    public static void validateSlotRanges(Map.Entry<Long, Long>[] slotRanges) {
        if (slotRanges == null) {
            throw new IllegalArgumentException("Slot ranges array cannot be null");
        }
        if (slotRanges.length == 0) {
            throw new IllegalArgumentException("Slot ranges array cannot be empty");
        }
        for (int i = 0; i < slotRanges.length; i++) {
            Map.Entry<Long, Long> range = slotRanges[i];
            if (range == null) {
                throw new IllegalArgumentException(
                        String.format("Slot range at index %d cannot be null", i));
            }
            if (range.getKey() == null || range.getValue() == null) {
                throw new IllegalArgumentException(
                        String.format("Slot range at index %d must have both start and end slots defined", i));
            }
            long start = range.getKey();
            long end = range.getValue();
            if (start < MIN_SLOT || start > MAX_SLOT) {
                throw new IllegalArgumentException(
                        String.format(
                                "Slot range start at index %d must be between %d and %d, but got: %d",
                                i, MIN_SLOT, MAX_SLOT, start));
            }
            if (end < MIN_SLOT || end > MAX_SLOT) {
                throw new IllegalArgumentException(
                        String.format(
                                "Slot range end at index %d must be between %d and %d, but got: %d",
                                i, MIN_SLOT, MAX_SLOT, end));
            }
            if (start > end) {
                throw new IllegalArgumentException(
                        String.format(
                                "Slot range at index %d is invalid: start (%d) must be <= end (%d)",
                                i, start, end));
            }
        }
    }

    /**
     * Validates that a node ID is properly formatted (40 lowercase hexadecimal characters).
     *
     * @param nodeId The node ID to validate.
     * @throws IllegalArgumentException if the node ID is not properly formatted.
     */
    public static void validateNodeId(String nodeId) {
        if (nodeId == null) {
            throw new IllegalArgumentException("Node ID cannot be null");
        }
        if (nodeId.isEmpty()) {
            throw new IllegalArgumentException("Node ID cannot be empty");
        }
        if (nodeId.length() != 40) {
            throw new IllegalArgumentException(
                    String.format(
                            "Node ID must be exactly 40 characters, but got %d characters: %s",
                            nodeId.length(), nodeId));
        }
        if (!NODE_ID_PATTERN.matcher(nodeId).matches()) {
            throw new IllegalArgumentException(
                    String.format(
                            "Node ID must contain only lowercase hexadecimal characters (0-9, a-f), but got: %s",
                            nodeId));
        }
    }

    /**
     * Validates that a port number is within the valid range (1-65535).
     *
     * @param port The port number to validate.
     * @throws IllegalArgumentException if the port is not in the valid range.
     */
    public static void validatePort(long port) {
        if (port < MIN_PORT || port > MAX_PORT) {
            throw new IllegalArgumentException(
                    String.format("Port must be between %d and %d, but got: %d", MIN_PORT, MAX_PORT, port));
        }
    }

    /**
     * Validates that a hostname is not null or empty.
     *
     * @param host The hostname to validate.
     * @throws IllegalArgumentException if the hostname is null or empty.
     */
    public static void validateHost(String host) {
        if (host == null) {
            throw new IllegalArgumentException("Hostname cannot be null");
        }
        if (host.trim().isEmpty()) {
            throw new IllegalArgumentException("Hostname cannot be empty");
        }
    }

    /**
     * Validates that a count parameter is positive.
     *
     * @param count The count value to validate.
     * @param paramName The name of the parameter (for error messages).
     * @throws IllegalArgumentException if the count is not positive.
     */
    public static void validatePositiveCount(long count, String paramName) {
        if (count <= 0) {
            throw new IllegalArgumentException(
                    String.format("%s must be positive, but got: %d", paramName, count));
        }
    }

    /**
     * Validates that an epoch value is non-negative.
     *
     * @param epoch The epoch value to validate.
     * @throws IllegalArgumentException if the epoch is negative.
     */
    public static void validateEpoch(long epoch) {
        if (epoch < 0) {
            throw new IllegalArgumentException(
                    String.format("Epoch must be non-negative, but got: %d", epoch));
        }
    }
}
