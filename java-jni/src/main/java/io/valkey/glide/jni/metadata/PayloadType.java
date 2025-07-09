package io.valkey.glide.jni.metadata;

/**
 * Payload type enum - not used in simplified POC.
 * <p>
 * The POC uses simple byte arrays for all payloads.
 *
 * @deprecated Not used in simplified POC approach
 */
@Deprecated
public enum PayloadType {
    /**
     * Payload is raw bytes (used in POC)
     */
    RAW_BYTES((byte) 0);

    private final byte value;

    PayloadType(byte value) {
        this.value = value;
    }

    /**
     * Get the byte value of this payload type
     *
     * @return the byte value
     */
    public byte getValue() {
        return value;
    }
}
