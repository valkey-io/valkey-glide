/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

/**
 * Parameters for GETEX command. Provides methods to set expiration options for the GETEX command.
 */
public abstract class AbstractGetExParams<T extends AbstractGetExParams<T>> {

    @SuppressWarnings("unchecked")
    protected final T self() {
        return (T) this;
    }

    /** Creates parameters for GETEX (same as {@link #getExParams()}). */
    protected AbstractGetExParams() {}

    public enum ExpiryType {
        EX,
        PX,
        EXAT,
        PXAT,
        PERSIST
    }

    private ExpiryType expirationType;
    private Long expirationValue;

    /**
     * Set the specified expire time, in seconds.
     *
     * @param seconds seconds to expire
     * @return GetExParams instance
     */
    public T ex(long seconds) {
        this.expirationType = ExpiryType.EX;
        this.expirationValue = seconds;
        return self();
    }

    /**
     * Set the specified expire time, in milliseconds.
     *
     * @param milliseconds milliseconds to expire
     * @return GetExParams instance
     */
    public T px(long milliseconds) {
        this.expirationType = ExpiryType.PX;
        this.expirationValue = milliseconds;
        return self();
    }

    /**
     * Set the specified Unix time at which the key will expire, in seconds.
     *
     * @param unixTimeSeconds unix timestamp in seconds
     * @return GetExParams instance
     */
    public T exAt(long unixTimeSeconds) {
        this.expirationType = ExpiryType.EXAT;
        this.expirationValue = unixTimeSeconds;
        return self();
    }

    /**
     * Set the specified Unix time at which the key will expire, in milliseconds.
     *
     * @param unixTimeMilliseconds unix timestamp in milliseconds
     * @return GetExParams instance
     */
    public T pxAt(long unixTimeMilliseconds) {
        this.expirationType = ExpiryType.PXAT;
        this.expirationValue = unixTimeMilliseconds;
        return self();
    }

    /**
     * Remove the time to live associated with the key.
     *
     * @return GetExParams instance
     */
    public T persist() {
        this.expirationType = ExpiryType.PERSIST;
        this.expirationValue = null;
        return self();
    }

    /**
     * Get the expiration type.
     *
     * @return expiration type
     */
    public ExpiryType getExpirationType() {
        return expirationType;
    }

    /**
     * Get the expiration value.
     *
     * @return expiration value
     */
    public Long getExpirationValue() {
        return expirationValue;
    }
}
