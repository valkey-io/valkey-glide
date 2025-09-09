/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.Batch;
import glide.api.models.Transaction;
import glide.api.models.commands.batch.BatchOptions;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for the "Transactions Commands" group for standalone clients.
 *
 * @see <a href="https://valkey.io/commands/?group=transactions">Transactions Commands</a>
 */
public interface TransactionsCommands extends TransactionsBaseCommands {

    /**
     * @deprecated Use {@link #exec(Batch, boolean)} instead. This method is being replaced by a more
     *     flexible approach using {@link Batch}.
     *     <p>Executes a transaction by processing the queued commands.
     * @param transaction A {@link Transaction} object containing a list of commands to be executed.
     * @return A list of results corresponding to the execution of each command in the transaction.
     * @apiNote
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
     * Transaction transaction = new Transaction().set("key", "value");
     * Object[] result = client.exec(transaction).get();
     * assert result[0].equals("OK");
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
     * // Example 1: Atomic Batch (Transaction)
     * Batch transaction = new Batch(true) // Atomic (Transactional)
     *     .set("key", "1")                 // Set a value for key
     *     .get("key");                     // GET the value of the key
     * Object[] result = client.exec(transaction, true).get();
     * System.out.println("Transaction Batch Result: " + Arrays.toString(result));
     * // Expected Output: Transaction Batch Result: [OK, 1]
     *
     * // Example 2: Non-Atomic Batch (Pipeline)
     * Batch pipeline = new Batch(false) // Non-Atomic (Pipeline)
     *     .set("key1", "value1")
     *     .set("key2", "value2")
     *     .get("key1")
     *     .get("key2");
     * Object[] result = client.exec(pipeline, false).get();
     * System.out.println("Pipeline Batch Result: " + Arrays.toString(result));
     * // Expected Output: Pipeline Batch Result: [OK, OK, value1, value2]
     * }</pre>
     */
    CompletableFuture<Object[]> exec(Batch batch, boolean raiseOnError);

    /**
     * Executes a batch by processing the queued commands with additional options.
     *
     * @param batch A {@link Batch} containing the commands to execute.
     * @param raiseOnError Determines how errors are handled within the batch response.
     *     <p>When set to {@code true}, the first encountered error in the batch will be raised as an
     *     exception after all retries and reconnections have been executed.
     *     <p>When set to {@code false}, errors will be included as part of the batch response.
     * @param options A {@link BatchOptions} object containing execution options such as timeout.
     * @return A {@link CompletableFuture} resolving to an array of results, where each entry
     *     corresponds to a command's execution result.
     * @see <a href="https://valkey.io/docs/topics/transactions/">Valkey Transactions (Atomic
     *     Batches)</a>
     * @see <a href="https://valkey.io/docs/topics/pipelining/">Valkey Pipelines (Non-Atomic
     *     Batches)</a>
     */
    CompletableFuture<Object[]> exec(Batch batch, boolean raiseOnError, BatchOptions options);
}