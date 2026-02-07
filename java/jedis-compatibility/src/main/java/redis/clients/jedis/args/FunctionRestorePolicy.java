/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.args;

/**
 * Policy options for FUNCTION RESTORE command.
 *
 * @see <a href="https://redis.io/commands/function-restore/">FUNCTION RESTORE</a>
 */
public enum FunctionRestorePolicy implements Rawable {
    /** Appends the restored libraries to the existing libraries and aborts on collision (default) */
    APPEND,
    /** Deletes all existing libraries before restoring the payload */
    FLUSH,
    /** Appends the restored libraries, replacing any existing ones in case of name collisions */
    REPLACE;

    @Override
    public byte[] getRaw() {
        return name().getBytes();
    }

    /**
     * Convert to GLIDE FunctionRestorePolicy.
     *
     * @return The equivalent GLIDE FunctionRestorePolicy
     */
    public glide.api.models.commands.function.FunctionRestorePolicy toGlideFunctionRestorePolicy() {
        switch (this) {
            case APPEND:
                return glide.api.models.commands.function.FunctionRestorePolicy.APPEND;
            case FLUSH:
                return glide.api.models.commands.function.FunctionRestorePolicy.FLUSH;
            case REPLACE:
                return glide.api.models.commands.function.FunctionRestorePolicy.REPLACE;
            default:
                throw new IllegalStateException("Unknown FunctionRestorePolicy: " + this);
        }
    }
}
