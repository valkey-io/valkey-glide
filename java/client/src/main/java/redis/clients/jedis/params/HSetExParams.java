/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

/**
 * Parameters for HSETEX command. Provides methods to set expiration options and existence
 * conditions for the HSETEX command.
 */
public class HSetExParams {

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

    public static HSetExParams hSetExParams() {
        return new HSetExParams();
    }

    /**
     * Set the specified expire time, in seconds.
     *
     * @param seconds seconds to expire
     * @return HSetExParams instance
     */
    public HSetExParams ex(long seconds) {
        this.expirationType = ExpiryType.EX;
        this.expirationValue = seconds;
        return this;
    }

    /**
     * Set the specified expire time, in milliseconds.
     *
     * @param milliseconds milliseconds to expire
     * @return HSetExParams instance
     */
    public HSetExParams px(long milliseconds) {
        this.expirationType = ExpiryType.PX;
        this.expirationValue = milliseconds;
        return this;
    }

    /**
     * Set the specified Unix time at which the key will expire, in seconds.
     *
     * @param unixTimeSeconds unix timestamp in seconds
     * @return HSetExParams instance
     */
    public HSetExParams exAt(long unixTimeSeconds) {
        this.expirationType = ExpiryType.EXAT;
        this.expirationValue = unixTimeSeconds;
        return this;
    }

    /**
     * Set the specified Unix time at which the key will expire, in milliseconds.
     *
     * @param unixTimeMilliseconds unix timestamp in milliseconds
     * @return HSetExParams instance
     */
    public HSetExParams pxAt(long unixTimeMilliseconds) {
        this.expirationType = ExpiryType.PXAT;
        this.expirationValue = unixTimeMilliseconds;
        return this;
    }

    /**
     * Retain the time to live associated with the key.
     *
     * @return HSetExParams instance
     */
    public HSetExParams keepTtl() {
        this.expirationType = ExpiryType.KEEPTTL;
        this.expirationValue = null;
        return this;
    }

    /**
     * Only set the field if it does not already exist.
     *
     * @return HSetExParams instance
     */
    public HSetExParams fnx() {
        this.existenceCondition = ExistenceCondition.FNX;
        return this;
    }

    /**
     * Only set the field if it already exists.
     *
     * @return HSetExParams instance
     */
    public HSetExParams fxx() {
        this.existenceCondition = ExistenceCondition.FXX;
        return this;
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
