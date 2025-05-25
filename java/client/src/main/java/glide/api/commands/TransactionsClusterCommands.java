/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.ClusterBatch;
import glide.api.models.ClusterTransaction;
import glide.api.models.commands.batch.ClusterBatchOptions;
import glide.api.models.configuration.RequestRoutingConfiguration;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import glide.api.models.exceptions.RequestException;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for the "Transactions Commands" group for cluster clients.
 *
 * @see <a href="https://valkey.io/commands/?group=transactions">Transactions Commands</a>
 */
public interface TransactionsClusterCommands {
    /**
     * @deprecated Use {@link #exec(ClusterBatch, boolean)} instead. This method is being replaced by
     *     a more flexible approach using {@link ClusterBatch}.
     *     <p>Executes a transaction by processing the queued commands.
     *     <p>The transaction will be routed to the slot owner of the first key found in the
     *     transaction. If no key is found, the command will be sent to a random node.
     * @param transaction A {@link ClusterTransaction} object containing a list of commands to be
     *     executed.
     * @return A list of results corresponding to the execution of each command in the transaction.
     * @remarks
     *     <ul>
     *       <li>If a command returns a value, it will be included in the list.
     *       <li>If a command doesn't return a value, the list entry will be empty.
     *       <li>If the transaction failed due to a <code>WATCH</code> command, <code>exec</code> will
     *           return <code>null</code>.
     *     </ul>
     *
     * @see #exec(ClusterBatch, boolean)
     * @see <a href="https://valkey.io/docs/topics/transactions/">valkey.io documentation on
     *     Transactions</a>
     * @example
     *     <pre>{@code
     * ClusterTransaction transaction = new ClusterTransaction()
     *     .customCommand(new String[] {"info"});
     * Object[] result = clusterClient.exec(transaction).get();
     * assert ((String) result[0]).contains("# Stats");
     * }</pre>
     */
    @Deprecated
    CompletableFuture<Object[]> exec(ClusterTransaction transaction);

    /**
     * @deprecated Use {@link #exec(ClusterBatch, boolean, ClusterBatchOptions)} instead. This method
     *     is being replaced by a more flexible approach using {@link ClusterBatch} and {@link
     *     ClusterBatchOptions}.
     *     <p>Executes a transaction by processing the queued commands.
     * @param transaction A {@link ClusterTransaction} object containing a list of commands to be
     *     executed.
     * @param route A single-node routing configuration for the transaction. The client will route the
     *     transaction to the node defined by <code>route</code>.
     * @return A list of results corresponding to the execution of each command in the transaction.
     * @remarks
     *     <ul>
     *       <li>If a command returns a value, it will be included in the list.
     *       <li>If a command doesn't return a value, the list entry will be empty.
     *       <li>If the transaction failed due to a <code>WATCH</code> command, <code>exec</code> will
     *           return <code>null</code>.
     *     </ul>
     *
     * @see #exec(ClusterBatch, boolean, ClusterBatchOptions)
     * @see <a href="https://valkey.io/docs/topics/transactions/">valkey.io documentation on
     *     Transactions</a>
     * @example
     *     <pre>{@code
     * ClusterTransaction transaction = new ClusterTransaction().ping().info();
     * Object[] result = clusterClient.exec(transaction, RANDOM).get();
     * assert ((String) result[0]).equals("PONG");
     * assert ((String) result[1]).contains("# Stats");
     * }</pre>
     */
    @Deprecated
    CompletableFuture<Object[]> exec(
            ClusterTransaction transaction, RequestRoutingConfiguration.SingleNodeRoute route);

    /**
     * Executes a batch by processing the queued commands.
     *
     * <p><strong>Routing Behavior:</strong>
     *
     * <ul>
     *   <li><strong>For atomic batches (Transactions):</strong>
     *       <ul>
     *         <li>The transaction will be routed to the slot owner of the first key found in the
     *             batch.
     *         <li>If no key is found, the request will be sent to a random node.
     *       </ul>
     *   <li><strong>For non-atomic batches:</strong>
     *       <ul>
     *         <li>Each command will be routed to the node that owns the corresponding key's slot. If
     *             no key is present, the routing will follow the default policy for the command.
     *         <li>Multi-node commands will be automatically split and sent to the respective nodes.
     *       </ul>
     * </ul>
     *
     * <p><strong>Notes:</strong>
     *
     * <ul>
     *   <li><strong>Atomic Batches (Transactions):</strong> All key-based commands must map to the
     *       same hash slot. If keys span different slots, the transaction will fail. If the
     *       transaction fails due to a {@code WATCH} command, {@code EXEC} will return {@code null}.
     * </ul>
     *
     * @param batch A {@link ClusterBatch} object containing a list of commands to be executed.
     * @param raiseOnError Determines how errors are handled within the batch response.
     *     <p>When set to {@code true}, the first encountered error in the batch will be raised as an
     *     exception of type {@link RequestException} after all retries and reconnections have been
     *     executed.
     *     <p>When set to {@code false}, errors will be included as part of the batch response,
     *     allowing the caller to process both successful and failed commands together. In this case,
     *     error details will be provided as instances of {@link RequestException}.
     * @return A {@link CompletableFuture} resolving to an array of results, where each entry
     *     corresponds to a command’s execution result.
     * @see <a href="https://valkey.io/docs/topics/transactions/">Valkey Transactions (Atomic
     *     Batches)</a>
     * @see <a href="https://valkey.io/docs/topics/pipelining/">Valkey Pipelines (Non-Atomic
     *     Batches)</a>
     * @example
     *     <pre>{@code
     * // Example 1: Atomic Batch (Transaction)
     * ClusterBatch atomicBatch = new ClusterBatch(true) // Atomic (Transaction)
     *     .set("key", "1")
     *     .incr("key")
     *     .get("key");
     * Object[] atomicResult = clusterClient.exec(atomicBatch, true).get();
     * System.out.println("Atomic Batch Result: " + Arrays.toString(atomicResult));
     * // Expected Output: Atomic Batch Result: [OK, 2, 2]
     *
     * // Example 2: Non-Atomic Batch (Pipeline)
     * ClusterBatch nonAtomicBatch = new ClusterBatch(false) // Non-Atomic (Pipeline)
     *     .set("key1", "value1")
     *     .set("key2", "value2")
     *     .get("key1")
     *     .get("key2");
     * Object[] nonAtomicResult = clusterClient.exec(nonAtomicBatch, true).get();
     * System.out.println("Non-Atomic Batch Result: " + Arrays.toString(nonAtomicResult));
     * // Expected Output: Non-Atomic Batch Result: [OK, OK, value1, value2]
     * }</pre>
     */
    CompletableFuture<Object[]> exec(ClusterBatch batch, boolean raiseOnError);

    /**
     * Executes a batch by processing the queued commands.
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
     *         <li><strong>Non-atomic batches (Pipelines):</strong> Each command is routed to the node
     *             owning the corresponding key's slot. If no key is present, routing follows the
     *             command's request policy. Multi-node commands are automatically split and
     *             dispatched to the appropriate nodes.
     *       </ul>
     * </ul>
     *
     * <p><strong>Behavior notes:</strong>
     *
     * <ul>
     *   <li><strong>Atomic Batches (Transactions):</strong> All key-based commands must map to the
     *       same hash slot. If keys span different slots, the transaction will fail. If the
     *       transaction fails due to a {@code WATCH} command, {@code EXEC} will return {@code null}.
     * </ul>
     *
     * <p><strong>Retry and Redirection:</strong>
     *
     * <ul>
     *   <li>If a redirection error occurs:
     *       <ul>
     *         <li><strong>Atomic batches (Transactions):</strong> The entire transaction will be
     *             redirected.
     *         <li><strong>Non-atomic batches:</strong> Only commands that encountered redirection
     *             errors will be redirected.
     *       </ul>
     *   <li>Retries for failures will be handled according to the configured {@link
     *       ClusterBatchRetryStrategy}.
     * </ul>
     *
     * @param batch A {@link ClusterBatch} containing the commands to execute.
     * @param raiseOnError Determines how errors are handled within the batch response.
     *     <p>When set to {@code true}, the first encountered error in the batch will be raised as an
     *     exception of type {@link RequestException} after all retries and reconnections have been
     *     executed.
     *     <p>When set to {@code false}, errors will be included as part of the batch response,
     *     allowing the caller to process both successful and failed commands together. In this case,
     *     error details will be provided as instances of {@link RequestException}.
     * @param options A {@link ClusterBatchOptions} object containing execution options.
     * @return A {@link CompletableFuture} resolving to an array of results, where each entry
     *     corresponds to a command’s execution result.
     * @see <a href="https://valkey.io/docs/topics/transactions/">Valkey Transactions (Atomic
     *     Batches)</a>
     * @see <a href="https://valkey.io/docs/topics/pipelining/">Valkey Pipelines (Non-Atomic
     *     Batches)</a>
     * @example
     *     <pre>{@code
     * // Atomic batch (transaction): all keys must share the same hash slot
     * ClusterBatchOptions options = ClusterBatchOptions.builder()
     *     .timeout(1000) // Set a timeout of 1000 milliseconds
     *     .build();
     *
     * ClusterBatch atomicBatch = new ClusterBatch(true)
     *     .set("key", "1")
     *     .incr("key")
     *     .get("key");
     *
     * Object[] atomicResult = clusterClient.exec(atomicBatch, false, options).get();
     * System.out.println("Atomic Batch Result: " + Arrays.toString(atomicResult));
     * // Output: [OK, 2, 2]
     *
     * // Non-atomic batch (pipeline): keys may span different hash slots
     * ClusterBatchOptions pipelineOptions = ClusterBatchOptions.builder()
     *     .retryStrategy(ClusterBatchRetryStrategy.builder()
     *         .retryServerError(true)
     *         .retryConnectionError(false)
     *         .build())
     *     .build();
     *
     * ClusterBatch nonAtomicBatch = new ClusterBatch(false)
     *     .set("key1", "value1")
     *     .set("key2", "value2")
     *     .get("key1")
     *     .get("key2");
     *
     * Object[] nonAtomicResult = clusterClient.exec(nonAtomicBatch, false, pipelineOptions).get();
     * System.out.println("Non-Atomic Batch Result: " + Arrays.toString(nonAtomicResult));
     * // Output: [OK, OK, value1, value2]
     * }</pre>
     */
    CompletableFuture<Object[]> exec(
            ClusterBatch batch, boolean raiseOnError, ClusterBatchOptions options);

    /**
     * Flushes all the previously watched keys for a transaction. Executing a transaction will
     * automatically flush all previously watched keys.<br>
     * The command will be routed to all primary nodes.
     *
     * @see <a href="https://valkey.io/commands/unwatch/">valkey.io</a> for details.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * assert client.watch(new String[] {"sampleKey"}).get().equals("OK");
     * assert client.unwatch().get().equals("OK"); // Flushes "sampleKey" from watched keys.
     * }</pre>
     */
    CompletableFuture<String> unwatch();

    /**
     * Flushes all the previously watched keys for a transaction. Executing a transaction will
     * automatically flush all previously watched keys.
     *
     * @see <a href="https://valkey.io/commands/unwatch/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * assert client.watch(new String[] {"sampleKey"}).get().equals("OK");
     * assert client.unwatch(ALL_PRIMARIES).get().equals("OK"); // Flushes "sampleKey" from watched keys for all primary nodes.
     * }</pre>
     */
    CompletableFuture<String> unwatch(Route route);
}
