/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

/**
 * Parameters for SET command in Jedis compatibility layer. Provides a fluent API for setting
 * expiration and conditional set options.
 */
public class SetParams {

    public enum ExistenceCondition {
        NX, // Only set if key does not exist
        XX // Only set if key already exists
    }

    public enum ExpirationType {
        EX, // Expire in seconds
        PX, // Expire in milliseconds
        EXAT, // Expire at Unix timestamp in seconds
        PXAT, // Expire at Unix timestamp in milliseconds
        KEEPTTL // Keep existing TTL
    }

    private ExistenceCondition existenceCondition;
    private ExpirationType expirationType;
    private Long expirationValue;
    private boolean get = false; // GET option to return old value

    public static SetParams setParams() {
        return new SetParams();
    }

    /** Only set the key if it does not already exist. */
    public SetParams nx() {
        this.existenceCondition = ExistenceCondition.NX;
        return this;
    }

    /** Only set the key if it already exists. */
    public SetParams xx() {
        this.existenceCondition = ExistenceCondition.XX;
        return this;
    }

    /** Set the specified expire time, in seconds. */
    public SetParams ex(long seconds) {
        this.expirationType = ExpirationType.EX;
        this.expirationValue = seconds;
        return this;
    }

    /** Set the specified expire time, in milliseconds. */
    public SetParams px(long milliseconds) {
        this.expirationType = ExpirationType.PX;
        this.expirationValue = milliseconds;
        return this;
    }

    /** Set the specified Unix time at which the key will expire, in seconds. */
    public SetParams exAt(long unixTimeSeconds) {
        this.expirationType = ExpirationType.EXAT;
        this.expirationValue = unixTimeSeconds;
        return this;
    }

    /** Set the specified Unix time at which the key will expire, in milliseconds. */
    public SetParams pxAt(long unixTimeMilliseconds) {
        this.expirationType = ExpirationType.PXAT;
        this.expirationValue = unixTimeMilliseconds;
        return this;
    }

    /** Retain the time to live associated with the key. */
    public SetParams keepTtl() {
        this.expirationType = ExpirationType.KEEPTTL;
        this.expirationValue = null;
        return this;
    }

    /** Return the old string stored at key, or null if key did not exist. */
    public SetParams get() {
        this.get = true;
        return this;
    }

    // Getters for internal use
    public ExistenceCondition getExistenceCondition() {
        return existenceCondition;
    }

    public ExpirationType getExpirationType() {
        return expirationType;
    }

    public Long getExpirationValue() {
        return expirationValue;
    }

    public boolean isGet() {
        return get;
    }
}
