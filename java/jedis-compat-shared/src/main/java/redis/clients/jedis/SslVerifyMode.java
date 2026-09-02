/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

/** Enumeration of SSL/TLS hostname verification modes. */
public enum SslVerifyMode {

    /**
     * DO NOT USE THIS IN PRODUCTION.
     *
     * <p>No verification at all.
     */
    INSECURE,

    /** Verify the CA and certificate without verifying that the hostname matches. */
    CA,

    /** Full certificate verification. */
    FULL,

    /** No verification (alias for INSECURE). */
    NONE
}
