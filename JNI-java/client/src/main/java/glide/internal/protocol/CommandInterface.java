package glide.internal.protocol;

/**
 * Common interface for commands that can be executed in batches.
 * Provides the minimal contract needed for batch serialization.
 */
public interface CommandInterface {
    /**
     * Get the command type.
     *
     * @return The command type string
     */
    String getType();

    /**
     * Check if this command contains binary data that requires special handling.
     *
     * @return true if this is a binary command, false otherwise
     */
    boolean isBinaryCommand();
}