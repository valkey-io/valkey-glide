/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

/**
 * Parameters for HGETEX command. Provides methods to set expiration options for the HGETEX command.
 */
public abstract class AbstractHGetExParams<T extends AbstractHGetExParams<T>> {

    @SuppressWarnings("unchecked")
    protected final T self() {
        return (T) this;
    }

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
     * @return HGetExParams instance
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
     * @return HGetExParams instance
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
     * @return HGetExParams instance
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
     * @return HGetExParams instance
     */
    public T pxAt(long unixTimeMilliseconds) {
        this.expirationType = ExpiryType.PXAT;
        this.expirationValue = unixTimeMilliseconds;
        return self();
    }

    /**
     * Remove the time to live associated with the key.
     *
     * @return HGetExParams instance
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
