/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

/**
 * Parameters for HSETEX command. Provides methods to set expiration options and existence
 * conditions for the HSETEX command.
 */
public abstract class AbstractHSetExParams<T extends AbstractHSetExParams<T>> {

    @SuppressWarnings("unchecked")
    protected final T self() {
        return (T) this;
    }

    public enum ExpiryType {
        EX,
        PX,
        EXAT,
        PXAT,
        KEEPTTL
    }

    public enum ExistenceCondition {
        FNX, // Field Not eXists - only set if field does not exist
        FXX // Field eXists - only set if field already exists
    }

    private ExpiryType expirationType;
    private Long expirationValue;
    private ExistenceCondition existenceCondition;

    /**
     * Set the specified expire time, in seconds.
     *
     * @param seconds seconds to expire
     * @return HSetExParams instance
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
     * @return HSetExParams instance
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
     * @return HSetExParams instance
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
     * @return HSetExParams instance
     */
    public T pxAt(long unixTimeMilliseconds) {
        this.expirationType = ExpiryType.PXAT;
        this.expirationValue = unixTimeMilliseconds;
        return self();
    }

    /**
     * Retain the time to live associated with the key.
     *
     * @return HSetExParams instance
     */
    public T keepTtl() {
        this.expirationType = ExpiryType.KEEPTTL;
        this.expirationValue = null;
        return self();
    }

    /**
     * Only set the field if it does not already exist.
     *
     * @return HSetExParams instance
     */
    public T fnx() {
        this.existenceCondition = ExistenceCondition.FNX;
        return self();
    }

    /**
     * Only set the field if it already exists.
     *
     * @return HSetExParams instance
     */
    public T fxx() {
        this.existenceCondition = ExistenceCondition.FXX;
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

    /**
     * Get the existence condition.
     *
     * @return existence condition
     */
    public ExistenceCondition getExistenceCondition() {
        return existenceCondition;
    }
}
