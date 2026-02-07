/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.args;

/**
 * Flush mode for FLUSHDB, FLUSHALL, FUNCTION FLUSH, and SCRIPT FLUSH commands.
 *
 * @see <a href="https://redis.io/commands/flushall/">FLUSHALL</a>
 * @see <a href="https://redis.io/commands/flushdb/">FLUSHDB</a>
 * @see <a href="https://redis.io/commands/function-flush/">FUNCTION FLUSH</a>
 * @see <a href="https://redis.io/commands/script-flush/">SCRIPT FLUSH</a>
 */
public enum FlushMode implements Rawable {
    /** Flushes synchronously */
    SYNC,
    /** Flushes asynchronously */
    ASYNC;

    @Override
    public byte[] getRaw() {
        return name().getBytes();
    }

    /**
     * Convert to GLIDE FlushMode.
     *
     * @return The equivalent GLIDE FlushMode
     */
    public glide.api.models.commands.FlushMode toGlideFlushMode() {
        return this == SYNC
                ? glide.api.models.commands.FlushMode.SYNC
                : glide.api.models.commands.FlushMode.ASYNC;
    }
}
