/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.ClusterBatch;
import glide.api.models.ClusterTransaction;
import glide.api.models.ClusterValue;
import glide.api.models.GlideString;
import glide.api.models.commands.batch.ClusterBatchOptions;
import glide.api.models.commands.scan.ClusterScanCursor;
import glide.api.models.commands.scan.ScanOptions;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import glide.api.models.configuration.RequestRoutingConfiguration.SingleNodeRoute;
import glide.api.models.exceptions.RequestException;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for the "Generic Commands" group for a cluster client.
 *
 * @see <a href="https://valkey.io/commands/?group=generic">Generic Commands</a>
 */
public interface GenericClusterCommands {

    /**
     * Executes a single command, without checking inputs. Every part of the command, including
     * subcommands, should be added as a separate value in <code>args</code>.<br>
     * The command will be routed automatically based on the passed command's default request policy.
     *
     * @see <a
     *     href="https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command">Valkey
     *     GLIDE Wiki</a> for details on the restrictions and limitations of the custom command API.
     * @param args Arguments for the custom command including the command name.
     * @return The returned value for the custom command.
     * @example
     *     <pre>{@code
     * ClusterValue<Object> data = client.customCommand(new String[] {"ping"}).get();
     * assert data.getSingleValue().equals("PONG");
     * }</pre>
     */
    CompletableFuture<ClusterValue<Object>> customCommand(String[] args);

    /**
     * Executes a single command, without checking inputs. Every part of the command, including
     * subcommands, should be added as a separate value in <code>args</code>.<br>
     * The command will be routed automatically based on the passed command's default request policy.
     *
     * @see <a
     *     href="https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command">Valkey
     *     GLIDE Wiki</a> for details on the restrictions and limitations of the custom command API.
     * @param args Arguments for the custom command including the command name.
     * @return The returned value for the custom command.
     * @example
     *     <pre>{@code
     * ClusterValue<Object> data = client.customCommand(new GlideString[] {gs("ping")}).get();
     * assert data.getSingleValue().equals(gs("PONG"));
     * }</pre>
     */
    CompletableFuture<ClusterValue<Object>> customCommand(GlideString[] args);

    /**
     * Executes a single command, without checking inputs. Every part of the command, including
     * subcommands, should be added as a separate value in <code>args</code>.
     *
     * @see <a
     *     href="https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command">Valkey
     *     GLIDE Wiki</a> for details on the restrictions and limitations of the custom command API.
     * @param args Arguments for the custom command including the command name
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The returning value depends on the executed command and route.
     * @example
     *     <pre>{@code
     * ClusterValue<Object> result = clusterClient.customCommand(new String[]{ "CONFIG", "GET", "maxmemory"}, ALL_NODES).get();
     * Map<String, Object> payload = result.getMultiValue();
     * assert payload.get("node1").equals("1GB");
     * assert payload.get("node2").equals("100MB");
     * }</pre>
     */
    CompletableFuture<ClusterValue<Object>> customCommand(String[] args, Route route);

    /**
     * Executes a single command, without checking inputs. Every part of the command, including
     * subcommands, should be added as a separate value in <code>args</code>.
     *
     * @see <a
     *     href="https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command">Valkey
     *     GLIDE Wiki</a> for details on the restrictions and limitations of the custom command API.
     * @param args Arguments for the custom command including the command name
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The returning value depends on the executed command and route.
     * @example
     *     <pre>{@code
     * ClusterValue<Object> result = clusterClient.customCommand(new GlideString[] { gs("CONFIG"), gs("GET"), gs("maxmemory") }, ALL_NODES).get();
     * Map<String, Object> payload = result.getMultiValue();
     * assert payload.get(gs("node1")).equals(gs("1GB"));
     * assert payload.get(gs("node2")).equals(gs("100MB"));
     * }</pre>
     */
    CompletableFuture<ClusterValue<Object>> customCommand(GlideString[] args, Route route);

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
    CompletableFuture<Object[]> exec(ClusterTransaction transaction, SingleNodeRoute route);

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
     * Returns a random key.
     *
     * @see <a href="https://valkey.io/commands/randomkey/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>, and will return the first successful
     *     result.
     * @return A random <code>key</code> from the database.
     * @example
     *     <pre>{@code
     * String value = client.set("key", "value").get();
     * String value_1 = client.set("key1", "value_1").get();
     * String key = client.randomKey(RANDOM).get();
     * System.out.println("The random key is: " + key);
     * // The value of key is either "key" or "key1"
     * }</pre>
     */
    CompletableFuture<String> randomKey(Route route);

    /**
     * Returns a random key.
     *
     * @see <a href="https://valkey.io/commands/randomkey/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>, and will return the first successful
     *     result.
     * @return A random <code>key</code> from the database.
     * @example
     *     <pre>{@code
     * String value = client.set("key", "value").get();
     * String value_1 = client.set("key1", "value_1").get();
     * GlideString key = client.randomKeyBinary(RANDOM).get();
     * System.out.println("The random key is: " + key);
     * // The value of key is either "key" or "key1"
     * }</pre>
     */
    CompletableFuture<GlideString> randomKeyBinary(Route route);

    /**
     * Returns a random key.<br>
     * The command will be routed to all primary nodes, and will return the first successful result.
     *
     * @see <a href="https://valkey.io/commands/randomkey/">valkey.io</a> for details.
     * @return A random <code>key</code> from the database.
     * @example
     *     <pre>{@code
     * String value = client.set("key", "value").get();
     * String value_1 = client.set("key1", "value_1").get();
     * String key = client.randomKey().get();
     * System.out.println("The random key is: " + key);
     * // The value of key is either "key" or "key1"
     * }</pre>
     */
    CompletableFuture<String> randomKey();

    /**
     * Returns a random key.<br>
     * The command will be routed to all primary nodes, and will return the first successful result.
     *
     * @see <a href="https://valkey.io/docs/commands/randomkey/">valkey.io</a> for details.
     * @return A random <code>key</code> from the database.
     * @example
     *     <pre>{@code
     * String value = client.set(gs("key"),gs( "value")).get();
     * String value_1 = client.set(gs("key1"), gs("value_1")).get();
     * GlideString key = client.randomKeyBinary().get();
     * System.out.println("The random key is: " + key);
     * // The value of key is either "key" or "key1"
     * }</pre>
     */
    CompletableFuture<GlideString> randomKeyBinary();

    /**
     * Incrementally iterates over the keys in the Cluster.
     *
     * <p>This command is similar to the <code>SCAN</code> command, but it is designed to work in a
     * Cluster environment. The main difference is that this command uses a {@link ClusterScanCursor}
     * object to manage iterations. For more information about the Cluster Scan implementation, see <a
     * href="https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#cluster-scan">Cluster
     * Scan</a>.
     *
     * <p>As with the <code>SCAN</code> command, this command is a cursor-based iterator. This means
     * that at every call of the command, the server returns an updated cursor ({@link
     * ClusterScanCursor}) that the user needs to re-send as the <code>cursor</code> argument in the
     * next call. The iteration terminates when the returned cursor {@link
     * ClusterScanCursor#isFinished()} returns <code>true</code>.
     *
     * <p>This method guarantees that all keyslots available when the first SCAN is called will be
     * scanned before the cursor is finished. Any keys added after the initial scan request is made
     * are not guaranteed to be scanned.
     *
     * <p>Note that the same key may be returned in multiple scan iterations.
     *
     * <p>How to use the {@link ClusterScanCursor}: <br>
     * For each iteration, the previous scan {@link ClusterScanCursor} object should be used to
     * continue the <code>SCAN</code> by passing it in the <code>cursor</code> argument. Using the
     * same cursor object for multiple iterations may result in the same keys returned or unexpected
     * behavior.
     *
     * <p>When the cursor is no longer needed, call {@link ClusterScanCursor#releaseCursorHandle()} to
     * immediately free resources tied to the cursor. Note that this makes the cursor unusable in
     * subsequent calls to <code>SCAN</code>.
     *
     * @see <a href="https://valkey.io/commands/scan">valkey.io</a> for details.
     * @param cursor The {@link ClusterScanCursor} object that wraps the scan state. To start a new
     *     scan, create a new empty ClusterScanCursor using {@link ClusterScanCursor#initalCursor()}.
     * @return An <code>Array</code> with two elements. The first element is always the {@link
     *     ClusterScanCursor} for the next iteration of results. To see if there is more data on the
     *     given cursor, call {@link ClusterScanCursor#isFinished()}. To release resources for the
     *     current cursor immediately, call {@link ClusterScanCursor#releaseCursorHandle()} after
     *     using the cursor in a call to this method. The cursor cannot be used in a scan again after
     *     {@link ClusterScanCursor#releaseCursorHandle()} has been called. The second element is an
     *     <code>
     *     Array</code> of <code>String</code> elements each representing a key.
     * @example
     *     <pre>{@code
     * // Assume key contains a set with 200 keys
     * ClusterScanCursor cursor = ClusterScanCursor.initialCursor();
     * Object[] result;
     * while (!cursor.isFinished()) {
     *   result = client.scan(cursor).get();
     *   cursor.releaseCursorHandle();
     *   cursor = (ClusterScanCursor) result[0];
     *   Object[] stringResults = (Object[]) result[1];
     *   System.out.println("\nSCAN iteration:");
     *   Arrays.asList(stringResults).stream().forEach(i -> System.out.print(i + ", "));
     * }
     * }</pre>
     */
    CompletableFuture<Object[]> scan(ClusterScanCursor cursor);

    /**
     * Incrementally iterates over the keys in the Cluster.
     *
     * <p>This command is similar to the <code>SCAN</code> command, but it is designed to work in a
     * Cluster environment. The main difference is that this command uses a {@link ClusterScanCursor}
     * object to manage iterations. For more information about the Cluster Scan implementation, see <a
     * href="https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#cluster-scan">Cluster
     * Scan</a>.
     *
     * <p>As with the <code>SCAN</code> command, this command is a cursor-based iterator. This means
     * that at every call of the command, the server returns an updated cursor ({@link
     * ClusterScanCursor}) that the user needs to re-send as the <code>cursor</code> argument in the
     * next call. The iteration terminates when the returned cursor {@link
     * ClusterScanCursor#isFinished()} returns <code>true</code>.
     *
     * <p>This method guarantees that all keyslots available when the first SCAN is called will be
     * scanned before the cursor is finished. Any keys added after the initial scan request is made
     * are not guaranteed to be scanned.
     *
     * <p>Note that the same key may be returned in multiple scan iterations.
     *
     * <p>How to use the {@link ClusterScanCursor}: <br>
     * For each iteration, the previous scan {@link ClusterScanCursor} object should be used to
     * continue the <code>SCAN</code> by passing it in the <code>cursor</code> argument. Using the
     * same cursor object for multiple iterations may result in the same keys returned or unexpected
     * behavior.
     *
     * <p>When the cursor is no longer needed, call {@link ClusterScanCursor#releaseCursorHandle()} to
     * immediately free resources tied to the cursor. Note that this makes the cursor unusable in
     * subsequent calls to <code>SCAN</code>.
     *
     * @see <a href="https://valkey.io/commands/scan">valkey.io</a> for details.
     * @param cursor The {@link ClusterScanCursor} object that wraps the scan state. To start a new
     *     scan, create a new empty ClusterScanCursor using {@link ClusterScanCursor#initalCursor()}.
     * @return An <code>Array</code> with two elements. The first element is always the {@link
     *     ClusterScanCursor} for the next iteration of results. To see if there is more data on the
     *     given cursor, call {@link ClusterScanCursor#isFinished()}. To release resources for the
     *     current cursor immediately, call {@link ClusterScanCursor#releaseCursorHandle()} after
     *     using the cursor in a call to this method. The cursor cannot be used in a scan again after
     *     {@link ClusterScanCursor#releaseCursorHandle()} has been called. The second element is an
     *     <code>Array</code> of <code>GlideString</code> elements each representing a key.
     * @example
     *     <pre>{@code
     * // Assume key contains a set with 200 keys
     * ClusterScanCursor cursor = ClusterScanCursor.initialCursor();
     * Object[] result;
     * while (!cursor.isFinished()) {
     *   result = client.scan(cursor).get();
     *   cursor.releaseCursorHandle();
     *   cursor = (ClusterScanCursor) result[0];
     *   Object[] glideStringResults = (Object[]) result[1];
     *   System.out.println("\nSCAN iteration:");
     *   Arrays.asList(stringResults).stream().forEach(i -> System.out.print(i + ", "));
     * }
     * }</pre>
     */
    CompletableFuture<Object[]> scanBinary(ClusterScanCursor cursor);

    /**
     * Incrementally iterates over the keys in the Cluster.
     *
     * <p>This command is similar to the <code>SCAN</code> command, but it is designed to work in a
     * Cluster environment. The main difference is that this command uses a {@link ClusterScanCursor}
     * object to manage iterations. For more information about the Cluster Scan implementation, see <a
     * href="https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#cluster-scan">Cluster
     * Scan</a>.
     *
     * <p>As with the <code>SCAN</code> command, this command is a cursor-based iterator. This means
     * that at every call of the command, the server returns an updated cursor ({@link
     * ClusterScanCursor}) that the user needs to re-send as the <code>cursor</code> argument in the
     * next call. The iteration terminates when the returned cursor {@link
     * ClusterScanCursor#isFinished()} returns <code>true</code>.
     *
     * <p>This method guarantees that all keyslots available when the first SCAN is called will be
     * scanned before the cursor is finished. Any keys added after the initial scan request is made
     * are not guaranteed to be scanned.
     *
     * <p>Note that the same key may be returned in multiple scan iterations.
     *
     * <p>How to use the {@link ClusterScanCursor}: <br>
     * For each iteration, the previous scan {@link ClusterScanCursor} object should be used to
     * continue the <code>SCAN</code> by passing it in the <code>cursor</code> argument. Using the
     * same cursor object for multiple iterations may result in the same keys returned or unexpected
     * behavior.
     *
     * <p>When the cursor is no longer needed, call {@link ClusterScanCursor#releaseCursorHandle()} to
     * immediately free resources tied to the cursor. Note that this makes the cursor unusable in
     * subsequent calls to <code>SCAN</code>.
     *
     * @see <a href="https://valkey.io/commands/scan">valkey.io</a> for details.
     * @param cursor The {@link ClusterScanCursor} object that wraps the scan state. To start a new
     *     scan, create a new empty ClusterScanCursor using {@link ClusterScanCursor#initalCursor()}.
     * @param options The {@link ScanOptions}.
     * @return An <code>Array</code> with two elements. The first element is always the {@link
     *     ClusterScanCursor} for the next iteration of results. To see if there is more data on the
     *     given cursor, call {@link ClusterScanCursor#isFinished()}. To release resources for the
     *     current cursor immediately, call {@link ClusterScanCursor#releaseCursorHandle()} after
     *     using the cursor in a call to this method. The cursor cannot be used in a scan again after
     *     {@link ClusterScanCursor#releaseCursorHandle()} has been called. The second element is an
     *     <code>Array</code> of <code>String</code> elements each representing a key.
     * @example
     *     <pre>{@code
     * // Assume key contains a set with 200 keys
     * ClusterScanCursor cursor = ClusterScanCursor.initialCursor();
     * // Scan for keys with archived in the name
     * ScanOptions options = ScanOptions.builder().matchPattern("*archived*").build();
     * Object[] result;
     * while (!cursor.isFinished()) {
     *   result = client.scan(cursor, options).get();
     *   cursor.releaseCursorHandle();
     *   cursor = (ClusterScanCursor) result[0];
     *   Object[] stringResults = (Object[]) result[1];
     *   System.out.println("\nSCAN iteration:");
     *   Arrays.asList(stringResults).stream().forEach(i -> System.out.print(i + ", "));
     * }
     * }</pre>
     */
    CompletableFuture<Object[]> scan(ClusterScanCursor cursor, ScanOptions options);

    /**
     * Incrementally iterates over the keys in the Cluster.
     *
     * <p>This command is similar to the <code>SCAN</code> command, but it is designed to work in a
     * Cluster environment. The main difference is that this command uses a {@link ClusterScanCursor}
     * object to manage iterations. For more information about the Cluster Scan implementation, see <a
     * href="https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#cluster-scan">Cluster
     * Scan</a>.
     *
     * <p>As with the <code>SCAN</code> command, this command is a cursor-based iterator. This means
     * that at every call of the command, the server returns an updated cursor ({@link
     * ClusterScanCursor}) that the user needs to re-send as the <code>cursor</code> argument in the
     * next call. The iteration terminates when the returned cursor {@link
     * ClusterScanCursor#isFinished()} returns <code>true</code>.
     *
     * <p>This method guarantees that all keyslots available when the first SCAN is called will be
     * scanned before the cursor is finished. Any keys added after the initial scan request is made
     * are not guaranteed to be scanned.
     *
     * <p>Note that the same key may be returned in multiple scan iterations.
     *
     * <p>How to use the {@link ClusterScanCursor}: <br>
     * For each iteration, the previous scan {@link ClusterScanCursor} object should be used to
     * continue the <code>SCAN</code> by passing it in the <code>cursor</code> argument. Using the
     * same cursor object for multiple iterations may result in the same keys returned or unexpected
     * behavior.
     *
     * <p>When the cursor is no longer needed, call {@link ClusterScanCursor#releaseCursorHandle()} to
     * immediately free resources tied to the cursor. Note that this makes the cursor unusable in
     * subsequent calls to <code>SCAN</code>.
     *
     * @see <a href="https://valkey.io/commands/scan">valkey.io</a> for details.
     * @param cursor The {@link ClusterScanCursor} object that wraps the scan state. To start a new
     *     scan, create a new empty ClusterScanCursor using {@link ClusterScanCursor#initalCursor()}.
     * @param options The {@link ScanOptions}.
     * @return An <code>Array</code> with two elements. The first element is always the {@link
     *     ClusterScanCursor} for the next iteration of results. To see if there is more data on the
     *     given cursor, call {@link ClusterScanCursor#isFinished()}. To release resources for the
     *     current cursor immediately, call {@link ClusterScanCursor#releaseCursorHandle()} after
     *     using the cursor in a call to this method. The cursor cannot be used in a scan again after
     *     {@link ClusterScanCursor#releaseCursorHandle()} has been called. The second element is an
     *     <code>Array</code> of <code>GlideString</code> elements each representing a key.
     * @example
     *     <pre>{@code
     * // Assume key contains a set with 200 keys
     * ClusterScanCursor cursor = ClusterScanCursor.initialCursor();
     * // Scan for keys with archived in the name
     * ScanOptions options = ScanOptions.builder().matchPattern("*archived*").build();
     * Object[] result;
     * while (!cursor.isFinished()) {
     *   result = client.scan(cursor, options).get();
     *   cursor.releaseCursorHandle();
     *   cursor = (ClusterScanCursor) result[0];
     *   Object[] glideStringResults = (Object[]) result[1];
     *   System.out.println("\nSCAN iteration:");
     *   Arrays.asList(stringResults).stream().forEach(i -> System.out.print(i + ", "));
     * }
     * }</pre>
     */
    CompletableFuture<Object[]> scanBinary(ClusterScanCursor cursor, ScanOptions options);
}
