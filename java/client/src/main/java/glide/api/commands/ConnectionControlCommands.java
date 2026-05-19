/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for connection-level control in cluster mode, particularly for read operations
 * on replicas and slot migration scenarios.
 *
 * @see <a href="https://valkey.io/commands/?group=cluster">Cluster Commands</a>
 */
public interface ConnectionControlCommands {

    /**
     * Enables read queries for a connection to a cluster replica node. By default, replica nodes in a
     * cluster will redirect read commands to the primary node. This command allows read commands to
     * be executed on the replica node itself.<br>
     * This command affects only the current connection and must be sent to a replica node.
     *
     * @see <a href="https://valkey.io/commands/readonly/">valkey.io</a> for details.
     * @return <code>OK</code> if read-only mode was successfully enabled for this connection.
     * @example
     *     <pre>{@code
     * String result = clusterClient.readonly().get();
     * assert result.equals("OK");
     * // Now read commands can be executed on this replica connection
     * }</pre>
     */
    CompletableFuture<String> readonly();

    /**
     * Disables read queries for a connection to a cluster replica node. This is the default mode.
     * After calling this command, the replica node will redirect read commands to the primary node.
     * <br>
     * This command affects only the current connection and must be sent to a replica node.
     *
     * @see <a href="https://valkey.io/commands/readwrite/">valkey.io</a> for details.
     * @return <code>OK</code> if read-write mode was successfully restored for this connection.
     * @example
     *     <pre>{@code
     * String result = clusterClient.readwrite().get();
     * assert result.equals("OK");
     * // Now read commands will be redirected to the primary node
     * }</pre>
     */
    CompletableFuture<String> readwrite();

    /**
     * Allows the execution of commands in the context of a slot migration. When a slot is being
     * migrated from one node to another, this command signals that the current connection is aware of
     * the migration and should allow commands targeting the migrating slot to proceed.<br>
     * This is typically used after receiving an ASK redirection error during slot migration.
     *
     * @see <a href="https://valkey.io/commands/asking/">valkey.io</a> for details.
     * @return <code>OK</code> if the ASKING flag was successfully set for the next command.
     * @example
     *     <pre>{@code
     * // After receiving an ASK error during slot migration:
     * // 1. Connect to the target node
     * // 2. Execute ASKING
     * String result = clusterClient.asking().get();
     * assert result.equals("OK");
     * // 3. Retry the command on this connection
     * }</pre>
     */
    CompletableFuture<String> asking();
}
