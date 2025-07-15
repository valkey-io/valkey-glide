/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import io.valkey.glide.core.commands.Command;
import io.valkey.glide.core.commands.CommandType;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for all batch operations.
 * This is a simple data structure for collecting commands to be executed as a batch.
 * Command implementations are located in the client classes (BaseClient, GlideClient, etc.).
 */
public abstract class BaseBatch<T extends BaseBatch<T>> {
    /** List of commands to execute as a batch */
    protected final List<Command> commands;
    
    /** Whether this batch should be executed atomically (transaction) */
    protected final boolean isAtomic;

    /**
     * Creates a new BaseBatch instance.
     *
     * @param isAtomic Whether the batch should be executed atomically
     */
    protected BaseBatch(boolean isAtomic) {
        this.isAtomic = isAtomic;
        this.commands = new ArrayList<>();
    }

    /**
     * Add a command to the batch.
     *
     * @param command The command to add
     * @return This batch instance for method chaining
     */
    @SuppressWarnings("unchecked")
    public T addCommand(Command command) {
        commands.add(command);
        return (T) this;
    }

    /**
     * Add a command to the batch by type and arguments.
     *
     * @param commandType The command type
     * @param args The command arguments
     * @return This batch instance for method chaining
     */
    @SuppressWarnings("unchecked")
    public T addCommand(CommandType commandType, String... args) {
        commands.add(new Command(commandType, args));
        return (T) this;
    }

    /**
     * Get all commands in this batch.
     *
     * @return List of commands
     */
    public List<Command> getCommands() {
        return new ArrayList<>(commands);
    }

    /**
     * Check if this batch is atomic (transaction).
     *
     * @return true if atomic, false otherwise
     */
    public boolean isAtomic() {
        return isAtomic;
    }

    /**
     * Get the number of commands in this batch.
     *
     * @return Number of commands
     */
    public int size() {
        return commands.size();
    }

    /**
     * Check if this batch is empty.
     *
     * @return true if empty, false otherwise
     */
    public boolean isEmpty() {
        return commands.isEmpty();
    }
}
