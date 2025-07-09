package io.valkey.glide.jni.metadata;

/**
 * Route type enum - not used in simplified POC.
 * <p>
 * The POC connects directly to a single Redis instance without routing complexity.
 *
 * @deprecated Not used in simplified POC approach
 */
@Deprecated
public enum RouteType {
    /**
     * Direct connection (used in POC)
     */
    DIRECT((byte) 0);

    private final byte value;

    RouteType(byte value) {
        this.value = value;
    }

    /**
     * Get the byte value of this route type
     *
     * @return the byte value
     */
    public byte getValue() {
        return value;
    }
}
