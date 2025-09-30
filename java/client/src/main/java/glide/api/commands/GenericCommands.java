/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.GlideString;
import glide.api.models.commands.scan.ScanOptions;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for the "Generic Commands" group for a standalone client.
 *
 * @see <a href="https://valkey.io/commands/?group=generic">Generic Commands</a>
 */
public interface GenericCommands {

    /**
     * Executes a single command, without checking inputs. Every part of the command, including
     * subcommands, should be added as a separate value in <code>args</code>.
     *
     * @see <a
     *     href="https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command">Valkey
     *     GLIDE Wiki</a> for details on the restrictions and limitations of the custom command API.
     * @param args Arguments for the custom command.
     * @return The returned value for the custom command.
     * @example
     *     <pre>{@code
     * Object response = client.customCommand(new String[] {"ping", "GLIDE"}).get();
     * assert response.equals("GLIDE");
     * // Get a list of all pub/sub clients:
     * Object result = client.customCommand(new String[]{ "CLIENT", "LIST", "TYPE", "PUBSUB" }).get();
     * }</pre>
     */
    CompletableFuture<Object> customCommand(String[] args);

    /**
     * Executes a single command, without checking inputs. Every part of the command, including
     * subcommands, should be added as a separate value in <code>args</code>.
     *
     * @see <a
     *     href="https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command">Valkey
     *     GLIDE Wiki</a> for details on the restrictions and limitations of the custom command API.
     * @param args Arguments for the custom command.
     * @return The returned value for the custom command.
     * @example
     *     <pre>{@code
     * Object response = client.customCommand(new GlideString[] {gs("ping"), gs("GLIDE")}).get();
     * assert response.equals(gs("GLIDE"));
     * // Get a list of all pub/sub clients:
     * Object result = client.customCommand(new GlideString[] { gs("CLIENT"), gs("LIST"), gs("TYPE"), gs("PUBSUB") }).get();
     * }</pre>
     */
    CompletableFuture<Object> customCommand(GlideString[] args);

    /**
     * Returns a random key from currently selected database.
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
     * Returns a random key from currently selected database.
     *
     * @see <a href="https://valkey.io/commands/randomkey/">valkey.io</a> for details.
     * @return A random <code>key</code> from the database.
     * @example
     *     <pre>{@code
     * String value = client.set(gs("key"), gs("value")).get();
     * String value_1 = client.set(gs("key1"), gs("value_1")).get();
     * String key = client.randomKeyBinary().get();
     * System.out.println("The random key is: " + key);
     * // The value of key is either "key" or "key1"
     * }</pre>
     */
    CompletableFuture<GlideString> randomKeyBinary();

    /**
     * Iterates incrementally over a database for matching keys.
     *
     * @see <a href="https://valkey.io/commands/scan/">valkey.io</a> for details.
     * @param cursor The cursor that points to the next iteration of results. A value of <code>"0"
     *     </code> indicates the start of the search.
     * @return An <code>Array</code> of <code>Objects</code>. The first element is always the <code>
     *     cursor</code> for the next iteration of results. <code>"0"</code> will be the <code>cursor
     *     </code> returned on the last iteration of the scan.<br>
     *     The second element is always an <code>Array</code> of matched keys from the database.
     * @example
     *     <pre>{@code
     * // Assume database contains a set with 200 keys
     * String cursor = "0";
     * Object[] result;
     * do {
     *     result = client.scan(cursor).get();
     *     cursor = result[0].toString();
     *     Object[] stringResults = (Object[]) result[1];
     *     String keyList = Arrays.stream(stringResults)
     *         .map(obj -> (String)obj)
     *         .collect(Collectors.joining(", "));
     *     System.out.println("\nSCAN iteration: " + keyList);
     * } while (!cursor.equals("0"));
     * }</pre>
     */
    CompletableFuture<Object[]> scan(String cursor);

    /**
     * Iterates incrementally over a database for matching keys.
     *
     * @see <a href="https://valkey.io/commands/scan/">valkey.io</a> for details.
     * @param cursor The cursor that points to the next iteration of results. A value of <code>gs("0")
     *     </code> indicates the start of the search.
     * @return An <code>Array</code> of <code>Objects</code>. The first element is always the <code>
     *     cursor</code> for the next iteration of results. <code>gs("0")</code> will be the <code>
     *     cursor
     *     </code> returned on the last iteration of the scan.<br>
     *     The second element is always an <code>Array</code> of matched keys from the database.
     * @example
     *     <pre>{@code
     * // Assume database contains a set with 200 keys
     * GlideString cursor = gs("0");
     * Object[] result;
     * do {
     *     result = client.scan(cursor).get();
     *     cursor = gs(result[0].toString());
     *     Object[] stringResults = (Object[]) result[1];
     *     String keyList = Arrays.stream(stringResults)
     *         .map(obj -> obj.toString())
     *         .collect(Collectors.joining(", "));
     *     System.out.println("\nSCAN iteration: " + keyList);
     * } while (!cursor.equals(gs("0")));
     * }</pre>
     */
    CompletableFuture<Object[]> scan(GlideString cursor);

    /**
     * Iterates incrementally over a database for matching keys.
     *
     * @see <a href="https://valkey.io/commands/scan/">valkey.io</a> for details.
     * @param cursor The cursor that points to the next iteration of results. A value of <code>"0"
     *     </code> indicates the start of the search.
     * @param options The {@link ScanOptions}.
     * @return An <code>Array</code> of <code>Objects</code>. The first element is always the <code>
     *     cursor</code> for the next iteration of results. <code>"0"</code> will be the <code>cursor
     *     </code> returned on the last iteration of the scan.<br>
     *     The second element is always an <code>Array</code> of matched keys from the database.
     * @example
     *     <pre>{@code
     * // Assume database contains a set with 200 keys
     * String cursor = "0";
     * Object[] result;
     * // match keys on pattern *11*
     * ScanOptions options = ScanOptions.builder().matchPattern("*11*").build();
     * do {
     *     result = client.scan(cursor, options).get();
     *     cursor = result[0].toString();
     *     Object[] stringResults = (Object[]) result[1];
     *     String keyList = Arrays.stream(stringResults)
     *         .map(obj -> (String)obj)
     *         .collect(Collectors.joining(", "));
     *     System.out.println("\nSCAN iteration: " + keyList);
     * } while (!cursor.equals("0"));
     * }</pre>
     */
    CompletableFuture<Object[]> scan(String cursor, ScanOptions options);

    /**
     * Iterates incrementally over a database for matching keys.
     *
     * @see <a href="https://valkey.io/commands/scan/">valkey.io</a> for details.
     * @param cursor The cursor that points to the next iteration of results. A value of <code>gs("0")
     *     </code> indicates the start of the search.
     * @param options The {@link ScanOptions}.
     * @return An <code>Array</code> of <code>Objects</code>. The first element is always the <code>
     *     cursor</code> for the next iteration of results. <code>gs("0")</code> will be the <code>
     *     cursor
     *     </code> returned on the last iteration of the scan.<br>
     *     The second element is always an <code>Array</code> of matched keys from the database.
     * @example
     *     <pre>{@code
     * // Assume database contains a set with 200 keys
     * GlideString cursor = gs("0");
     * Object[] result;
     * // match keys on pattern *11*
     * ScanOptions options = ScanOptions.builder().matchPattern("*11*").build();
     * do {
     *     result = client.scan(cursor, options).get();
     *     cursor = gs(result[0].toString());
     *     Object[] stringResults = (Object[]) result[1];
     *     String keyList = Arrays.stream(stringResults)
     *         .map(obj -> obj.toString())
     *         .collect(Collectors.joining(", "));
     *     System.out.println("\nSCAN iteration: " + keyList);
     * } while (!cursor.equals(gs("0")));
     * }</pre>
     */
    CompletableFuture<Object[]> scan(GlideString cursor, ScanOptions options);
}
