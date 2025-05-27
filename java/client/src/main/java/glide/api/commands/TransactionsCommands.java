/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.Batch;
import glide.api.models.Transaction;
import glide.api.models.commands.batch.BatchOptions;
import glide.api.models.exceptions.RequestException;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for the "Transactions Commands" group for standalone clients.
 *
 * @see <a href="https://valkey.io/commands/?group=transactions">Transactions Commands</a>
 */
public interface TransactionsCommands {

    /**
     * @deprecated Use {@link #exec(Batch, boolean)} instead. This method is being replaced by a more
     *     flexible approach using {@link Batch}.
     *     <p>Executes a transaction by processing the queued commands.
     * @param transaction A {@link Transaction} object containing a list of commands to be executed.
     * @return A list of results corresponding to the execution of each command in the transaction.
     * @remarks
     *     <ul>
     *       <li>If a command returns a value, it will be included in the list.
     *       <li>If a command doesn't return a value, the list entry will be empty.
     *       <li>If the transaction failed due to a <code>WATCH</code> command, <code>exec</code> will
     *           return <code>null</code>.
     *     </ul>
     *
     * @see #exec(Batch, boolean)
     * @see <a href="https://valkey.io/docs/topics/transactions/">valkey.io</a> for details on
     *     Transactions.
     * @example
     *     <pre>{@code
     * Transaction transaction = new Transaction().customCommand(new String[] {"info"});
     * Object[] result = client.exec(transaction).get();
     * assert ((String) result[0]).contains("# Stats");
     * }</pre>
     */
    @Deprecated
    CompletableFuture<Object[]> exec(Transaction transaction);

    /**
     * Executes a batch by processing the queued commands.
     *
     * <p><strong>Notes:</strong>
     *
     * <ul>
     *   <li><strong>Atomic Batches - Transactions:</strong> If the transaction fails due to a <code>
     *       WATCH</code> command, <code>EXEC</code> will return <code>null</code>.
     * </ul>
     *
     * @param batch A {@link Batch} containing the commands to execute.
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
     * Batch transaction = new Batch(true) // Atomic (Transactional)
     *     .set("key", "1")                 // Set a value for key
     *     .incr("key")                     // Increment the value of the key
     *     .get("key");                     // Get the value of the key
     * Object[] result = client.exec(transaction, true).get();
     * System.out.println("Transaction Batch Result: " + Arrays.toString(result));
     * // Expected Output: Transaction Batch Result: [OK, 2, 2]
     *
     * // Example 2: Non-Atomic Batch (Pipeline)
     * Batch pipeline = new Batch(false) // Non-Atomic (Pipeline)
     *     .set("key1", "value1")          // Set value for key1
     *     .set("key2", "value2")          // Set value for key2
     *     .get("key1")                    // Get value for key1
     *     .get("key2");                   // Get value for key2
     * Object[] pipelineResult = client.exec(pipeline, true).get();
     * System.out.println("Pipeline Batch Result: " + Arrays.toString(pipelineResult));
     * // Expected Output: Pipeline Batch Result: [OK, OK, value1, value2]
     * }</pre>
     */
    CompletableFuture<Object[]> exec(Batch batch, boolean raiseOnError);

    /**
     * Executes a batch by processing the queued commands with additional options.
     *
     * <p><strong>Notes:</strong>
     *
     * <ul>
     *   <li><strong>Atomic Batches - Transactions:</strong> If the transaction fails due to a <code>
     *       WATCH</code> command, <code>EXEC</code> will return <code>null</code>.
     * </ul>
     *
     * @param batch A {@link Batch} containing the commands to execute.
     * @param raiseOnError Determines how errors are handled within the batch response.
     *     <p>When set to {@code true}, the first encountered error in the batch will be raised as an
     *     exception of type {@link RequestException} after all retries and reconnections have been
     *     executed.
     *     <p>When set to {@code false}, errors will be included as part of the batch response,
     *     allowing the caller to process both successful and failed commands together. In this case,
     *     error details will be provided as instances of {@link RequestException}.
     * @param options A {@link BatchOptions} object containing execution options.
     * @return A {@link CompletableFuture} resolving to an array of results, where each entry
     *     corresponds to a command’s execution result.
     * @see <a href="https://valkey.io/docs/topics/transactions/">Valkey Transactions (Atomic
     *     Batches)</a>
     * @see <a href="https://valkey.io/docs/topics/pipelining/">Valkey Pipelines (Non-Atomic
     *     Batches)</a>
     * @example
     *     <pre>{@code
     * // Example 1: Atomic Batch (Transaction) with BatchOptions
     *  BatchOptions options = BatchOptions.builder()
     *     .timeout(1000) // Set a timeout of 1000 milliseconds
     *     .build();
     *
     * Batch transaction = new Batch(true) // Atomic (Transactional)
     *     .set("key", "1")
     *     .incr("key")
     *     .customCommand(new String[] {"get", "key"});
     * Object[] result = client.exec(transaction, false, options).get();
     * System.out.println("Transaction Result: " + Arrays.toString(result));
     * // Expected Output: Transaction Result: [OK, 2, 2]
     *
     * // Example 2: Non-Atomic Batch (Pipeline) with BatchOptions
     * // Commands can operate on different hash slots.
     * BatchOptions options = BatchOptions.builder()
     *     .timeout(1000) // Set a timeout of 1000 milliseconds
     *     .build();
     *
     * Batch pipeline = new Batch(false) // Non-Atomic (Pipeline)
     *     .customCommand(new String[] {"set", "key1", "value1"})
     *     .customCommand(new String[] {"set", "key2", "value2"})
     *     .customCommand(new String[] {"get", "key1"})
     *     .customCommand(new String[] {"get", "key2"});
     * Object[] result = client.exec(pipeline, false, options).get();
     * System.out.println("Pipeline Result: " + Arrays.toString(result));
     * // Expected Output: Pipeline Result: [OK, OK, value1, value2]
     * }</pre>
     */
    CompletableFuture<Object[]> exec(Batch batch, boolean raiseOnError, BatchOptions options);

    /**
     * Flushes all the previously watched keys for a transaction. Executing a transaction will
     * automatically flush all previously watched keys.
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
}
