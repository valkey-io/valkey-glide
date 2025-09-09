/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.ClusterBatch;
import glide.api.models.ClusterTransaction;
import glide.api.models.commands.batch.ClusterBatchOptions;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for the "Transactions Commands" group for cluster clients.
 *
 * @see <a href="https://valkey.io/commands/?group=transactions">Transactions Commands</a>
 */
public interface TransactionsClusterCommands extends TransactionsBaseCommands {

    /**
     * @deprecated Use {@link #exec(ClusterBatch, boolean)} instead. This method is being replaced by
     *     a more flexible approach using {@link ClusterBatch}.
     *     <p>Executes a transaction by processing the queued commands.
     *     <p>The transaction will be routed to the slot owner of the first key found in the
     *     transaction. If no key is found, the command will be sent to a random node.
     * @param transaction A {@link ClusterTransaction} object containing a list of commands to be
     *     executed.
     * @return A list of results corresponding to the execution of each command in the transaction.
     * @apiNote
     *     <ul>
     *       <li>If a command returns a value, it will be included in the list.
     *       <li>If a command doesn't return a value, the list entry will be empty.
     *       <li>If the transaction failed due to a <code>WATCH</code> command, <code>exec</code> will
     *           return <code>null</code>.
     *     </ul>
     *
     * @see #exec(ClusterBatch, boolean)
     * @see <a href="https://valkey.io/docs/topics/transactions/">valkey.io</a> for details on
     *     Transactions.
     * @example
     *     <pre>{@code
     * ClusterTransaction transaction = new ClusterTransaction().set("key", "value");
     * Object[] result = client.exec(transaction).get();
     * assert result[0].equals("OK");
     * }</pre>
     */
    @Deprecated
    CompletableFuture<Object[]> exec(ClusterTransaction transaction);

    /**
     * Executes a batch by processing the queued commands.
     *
     * <p><strong>Routing Behavior:</strong>
     *
     * <ul>
     *   <li>If a {@code route} is specified in options, the entire batch is sent to the specified node.
     *   <li>If no {@code route} is specified:
     *       <ul>
     *         <li><strong>Atomic batches (Transactions):</strong> Routed to the slot owner of the
     *             first key in the batch. If no key is found, the request is sent to a random node.
     *         <li><strong>Non-atomic batches (Pipelines):</strong> Each command is routed independently
     *             to the appropriate nodes based on the keys involved.
     *       </ul>
     * </ul>
     *
     * <p><strong>Notes:</strong>
     *
     * <ul>
     *   <li><strong>Atomic Batches - Transactions:</strong> If the transaction fails due to a <code>
     *       WATCH</code> command, <code>EXEC</code> will return <code>null</code>.
     * </ul>
     *
     * @param batch A {@link ClusterBatch} containing the commands to execute.
     * @param raiseOnError Determines how errors are handled within the batch response.
     *     <p>When set to {@code true}, the first encountered error in the batch will be raised as an
     *     exception after all retries and reconnections have been executed.
     *     <p>When set to {@code false}, errors will be included as part of the batch response,
     *     allowing the caller to process both successful and failed commands together.
     * @return A {@link CompletableFuture} resolving to an array of results, where each entry
     *     corresponds to a command's execution result.
     * @see <a href="https://valkey.io/docs/topics/transactions/">Valkey Transactions (Atomic
     *     Batches)</a>
     * @see <a href="https://valkey.io/docs/topics/pipelining/">Valkey Pipelines (Non-Atomic
     *     Batches)</a>
     * @example
     *     <pre>{@code
     * // Example: Atomic batch (transaction): all keys must share the same hash slot
     * ClusterBatch atomicBatch = new ClusterBatch(true)
     *     .set("key", "1")
     *     .get("key");
     * Object[] atomicResult = clusterClient.exec(atomicBatch, false).get();
     * System.out.println("Atomic Batch Result: " + Arrays.toString(atomicResult));
     * // Output: [OK, 1]
     * }</pre>
     */
    CompletableFuture<Object[]> exec(ClusterBatch batch, boolean raiseOnError);

    /**
     * Executes a batch by processing the queued commands with additional options.
     *
     * <p><strong>Routing Behavior:</strong>
     *
     * <ul>
     *   <li>If a {@code route} is specified in {@link ClusterBatchOptions}, the entire batch is sent
     *       to the specified node.
     *   <li>If no {@code route} is specified:
     *       <ul>
     *         <li><strong>Atomic batches (Transactions):</strong> Routed to the slot owner of the
     *             first key in the batch. If no key is found, the request is sent to a random node.
     *         <li><strong>Non-atomic batches (Pipelines):</strong> Each command is routed independently
     *             to the appropriate nodes based on the keys involved.
     *       </ul>
     * </ul>
     *
     * @param batch A {@link ClusterBatch} containing the commands to execute.
     * @param raiseOnError Determines how errors are handled within the batch response.
     *     <p>When set to {@code true}, the first encountered error in the batch will be raised as an
     *     exception after all retries and reconnections have been executed.
     *     <p>When set to {@code false}, errors will be included as part of the batch response,
     *     allowing the caller to process both successful and failed commands together.
     * @param options A {@link ClusterBatchOptions} object containing execution options.
     * @return A {@link CompletableFuture} resolving to an array of results, where each entry
     *     corresponds to a command's execution result.
     * @see <a href="https://valkey.io/docs/topics/transactions/">Valkey Transactions (Atomic
     *     Batches)</a>
     * @see <a href="https://valkey.io/docs/topics/pipelining/">Valkey Pipelines (Non-Atomic
     *     Batches)</a>
     * @example
     *     <pre>{@code
     * // Atomic batch (transaction) with options
     * ClusterBatchOptions options = ClusterBatchOptions.builder()
     *     .timeout(1000) // Set a timeout of 1000 milliseconds
     *     .build();
     *
     * ClusterBatch atomicBatch = new ClusterBatch(true)
     *     .set("key", "1")
     *     .get("key");
     * Object[] atomicResult = clusterClient.exec(atomicBatch, false, options).get();
     * System.out.println("Atomic Batch Result: " + Arrays.toString(atomicResult));
     * // Output: [OK, 1]
     * }</pre>
     */
    CompletableFuture<Object[]> exec(
            ClusterBatch batch, boolean raiseOnError, ClusterBatchOptions options);

    /**
     * Flushes all the previously watched keys for a transaction on specific nodes. Executing a 
     * transaction will automatically flush all previously watched keys.
     *
     * @param route Routing configuration for the command. The command will be routed to the specified nodes.
     * @see <a href="https://valkey.io/commands/unwatch/">valkey.io</a> for details.
     * @return <code>OK</code> from the specified nodes.
     * @example
     *     <pre>{@code
    * String result = clusterClient.unwatch(ALL_PRIMARIES).get();
    * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> unwatch(glide.api.models.configuration.RequestRoutingConfiguration.Route route);
}