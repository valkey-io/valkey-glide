/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.args;

/** Enumeration of setting expiration options for Jedis compatibility layer. */
public enum ExpiryOption {
    /** Set expiry only when the key has no expiry. */
    NX,

    /** Set expiry only when the key has an existing expiry. */
    XX,

    /** Set expiry only when the new expiry is greater than the existing one. */
    GT,

    /** Set expiry only when the new expiry is less than the existing one. */
    LT
}
