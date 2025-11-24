/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.ClusterValue;
import glide.api.models.GlideString;
import glide.api.models.commands.scan.ClusterScanCursor;
import glide.api.models.commands.scan.ScanOptions;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
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
     *     scan, create a new empty ClusterScanCursor using {@link ClusterScanCursor#initialCursor()}.
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
     *     scan, create a new empty ClusterScanCursor using {@link ClusterScanCursor#initialCursor()}.
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
     *     scan, create a new empty ClusterScanCursor using {@link ClusterScanCursor#initialCursor()}.
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
     *     scan, create a new empty ClusterScanCursor using {@link ClusterScanCursor#initialCursor()}.
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
