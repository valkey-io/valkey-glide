/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

/**
 * Parameters for SET command in Jedis compatibility layer. Provides a fluent API for setting
 * expiration and conditional set options.
 */
public abstract class AbstractSetParams<T extends AbstractSetParams<T>> {

    @SuppressWarnings("unchecked")
    protected final T self() {
        return (T) this;
    }

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

    /** Only set the key if it does not already exist. */
    public T nx() {
        this.existenceCondition = ExistenceCondition.NX;
        return self();
    }

    /** Only set the key if it already exists. */
    public T xx() {
        this.existenceCondition = ExistenceCondition.XX;
        return self();
    }

    /** Set the specified expire time, in seconds. */
    public T ex(long seconds) {
        this.expirationType = ExpirationType.EX;
        this.expirationValue = seconds;
        return self();
    }

    /** Set the specified expire time, in milliseconds. */
    public T px(long milliseconds) {
        this.expirationType = ExpirationType.PX;
        this.expirationValue = milliseconds;
        return self();
    }

    /** Set the specified Unix time at which the key will expire, in seconds. */
    public T exAt(long unixTimeSeconds) {
        this.expirationType = ExpirationType.EXAT;
        this.expirationValue = unixTimeSeconds;
        return self();
    }

    /** Set the specified Unix time at which the key will expire, in milliseconds. */
    public T pxAt(long unixTimeMilliseconds) {
        this.expirationType = ExpirationType.PXAT;
        this.expirationValue = unixTimeMilliseconds;
        return self();
    }

    /** Retain the time to live associated with the key. */
    public T keepTtl() {
        this.expirationType = ExpirationType.KEEPTTL;
        this.expirationValue = null;
        return self();
    }

    /** Return the old string stored at key, or null if key did not exist. */
    public T get() {
        this.get = true;
        return self();
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
