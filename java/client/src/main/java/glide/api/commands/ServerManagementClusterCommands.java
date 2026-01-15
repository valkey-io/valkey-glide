/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.ClusterValue;
import glide.api.models.commands.FlushMode;
import glide.api.models.commands.InfoOptions;
import glide.api.models.commands.InfoOptions.Section;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for the "Server Management Commands" group for a cluster client.
 *
 * @see <a href="https://valkey.io/commands/?group=server">Server Management Commands</a>
 */
public interface ServerManagementClusterCommands {

    /**
     * Gets information and statistics about the server using the {@link Section#DEFAULT} option. The
     * command will be routed to all primary nodes.
     *
     * @see <a href="https://valkey.io/commands/info/">valkey.io</a> for details.
     * @return A <code>Map{@literal <String, String>}</code> with each address as the key and its
     *     corresponding value is the information for the node.
     * @example
     *     <pre>{@code
     * ClusterValue<String> payload = clusterClient.info().get();
     * // By default, the command is sent to multiple nodes, expecting a MultiValue result.
     * for (Map.Entry<String, String> entry : payload.getMultiValue().entrySet()) {
     *     System.out.println("Node [" + entry.getKey() + "]: " + entry.getValue());
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> info();

    /**
     * Gets information and statistics about the server. If no argument is provided, so the {@link
     * Section#DEFAULT} option is assumed.
     *
     * @see <a href="https://valkey.io/commands/info/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return A <code>String</code> containing the information for the default sections. When
     *     specifying a <code>route</code> other than a single node, it returns a <code>
     *     Map{@literal <String, String>}</code> with each address as the key and its corresponding
     *     value is the information for the node.
     * @example
     *     <pre>{@code
     * ClusterValue<String> payload = clusterClient.info(ALL_NODES).get();
     * // Command sent to all nodes via ALL_NODES route, expecting MultiValue result.
     * for (Map.Entry<String, String> entry : payload.getMultiValue().entrySet()) {
     *     System.out.println("Node [" + entry.getKey() + "]: " + entry.getValue());
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> info(Route route);

    /**
     * Gets information and statistics about the server.<br>
     * Starting from server version 7, command supports multiple section arguments.<br>
     * The command will be routed to all primary nodes.
     *
     * @see <a href="https://valkey.io/commands/info/">valkey.io</a> for details.
     * @param sections A list of {@link InfoOptions.Section} values specifying which sections of
     *     information to retrieve. When no parameter is provided, the {@link
     *     InfoOptions.Section#DEFAULT} option is assumed.
     * @return A <code>Map{@literal <String, String>}</code> with each address as the key and its
     *     corresponding value is the information of the sections requested for the node.
     * @example
     *     <pre>{@code
     * ClusterValue<String> payload = clusterClient.info(new Section[] { Section.STATS }).get();
     * // By default, the command is sent to multiple nodes, expecting a MultiValue result.
     * for (Map.Entry<String, String> entry : payload.getMultiValue().entrySet()) {
     *     System.out.println("Node [" + entry.getKey() + "]: " + entry.getValue());
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> info(Section[] sections);

    /**
     * Gets information and statistics about the server.<br>
     * Starting from server version 7, command supports multiple section arguments.
     *
     * @see <a href="https://valkey.io/commands/info/">valkey.io</a> for details.
     * @param sections A list of {@link InfoOptions.Section} values specifying which sections of
     *     information to retrieve. When no parameter is provided, the {@link
     *     InfoOptions.Section#DEFAULT} option is assumed.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return A <code>String</code> with the containing the information for the sections requested.
     *     When specifying a <code>route</code> other than a single node, it returns a <code>
     *     Map{@literal <String, String>}</code> with each address as the key and its corresponding
     *     value is the information of the sections requested for the node.
     * @example
     *     <pre>{@code
     * ClusterValue<String> payload = clusterClient.info(new Section[] { Section.STATS }, RANDOM).get();
     * // Command sent to a single random node via RANDOM route, expecting SingleValue result.
     * assert data.getSingleValue().contains("total_net_input_bytes");
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> info(Section[] sections, Route route);

    /**
     * Rewrites the configuration file with the current configuration.<br>
     * The command will be routed automatically to all nodes.
     *
     * @see <a href="https://valkey.io/commands/config-rewrite/">valkey.io</a> for details.
     * @return <code>OK</code> when the configuration was rewritten properly, otherwise an error is
     *     thrown.
     * @example
     *     <pre>{@code
     * String response = client.configRewrite().get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> configRewrite();

    /**
     * Rewrites the configuration file with the current configuration.
     *
     * @see <a href="https://valkey.io/commands/config-rewrite/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code> when the configuration was rewritten properly, otherwise an error is
     *     thrown.
     * @example
     *     <pre>{@code
     * String response = client.configRewrite(ALL_PRIMARIES).get();
     * // Expecting an "OK" for all primary nodes.
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> configRewrite(Route route);

    /**
     * Resets the statistics reported by the server using the <a
     * href="https://valkey.io/commands/info/">INFO</a> and <a
     * href="https://valkey.io/commands/latency-histogram/">LATENCY HISTOGRAM</a> commands.<br>
     * The command will be routed automatically to all nodes.
     *
     * @see <a href="https://valkey.io/commands/config-resetstat/">valkey.io</a> for details.
     * @return <code>OK</code> to confirm that the statistics were successfully reset.
     * @example
     *     <pre>{@code
     * String response = client.configResetStat().get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> configResetStat();

    /**
     * Resets the statistics reported by the server using the <a
     * href="https://valkey.io/commands/info/">INFO</a> and <a
     * href="https://valkey.io/commands/latency-histogram/">LATENCY HISTOGRAM</a> commands.
     *
     * @see <a href="https://valkey.io/commands/config-resetstat/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code> to confirm that the statistics were successfully reset.
     * @example
     *     <pre>{@code
     * String response = client.configResetStat(ALL_PRIMARIES).get();
     * // Expecting an "OK" for all primary nodes.
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> configResetStat(Route route);

    /**
     * Get the values of configuration parameters.<br>
     * Starting from server version 7, command supports multiple parameters.<br>
     * The command will be sent to a random node.
     *
     * @see <a href="https://valkey.io/commands/config-get/">valkey.io</a> for details.
     * @param parameters An <code>array</code> of configuration parameter names to retrieve values
     *     for.
     * @return A <code>map</code> of values corresponding to the configuration parameters.
     * @example
     *     <pre>{@code
     * Map<String, String> configParams = client.configGet(new String[] {"timeout" , "maxmemory"}).get();
     * assert configParams.get("timeout").equals("1000");
     * assert configParams.get("maxmemory").equals("1GB");
     * }</pre>
     */
    CompletableFuture<Map<String, String>> configGet(String[] parameters);

    /**
     * Get the values of configuration parameters.<br>
     * Starting from server version 7, command supports multiple parameters.
     *
     * @see <a href="https://valkey.io/commands/config-get/">valkey.io</a> for details.
     * @param parameters An <code>array</code> of configuration parameter names to retrieve values
     *     for.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return A <code>map</code> of values corresponding to the configuration parameters.<br>
     *     When specifying a route other than a single node, it returns a dictionary where each
     *     address is the key and its corresponding node response is the value.
     * @example
     *     <pre>{@code
     * Map<String, String> configParams = client.configGet("timeout", RANDOM).get().getSingleValue();
     * assert configParams.get("timeout").equals("1000");
     *
     * Map<String, Map<String, String>> configParamsPerNode = client.configGet("maxmemory", ALL_NODES).get().getMultiValue();
     * assert configParamsPerNode.get("node1.example.com:6379").get("maxmemory").equals("1GB");
     * assert configParamsPerNode.get("node2.example.com:6379").get("maxmemory").equals("2GB");
     * }</pre>
     */
    CompletableFuture<ClusterValue<Map<String, String>>> configGet(String[] parameters, Route route);

    /**
     * Sets configuration parameters to the specified values.<br>
     * Starting from server version 7, command supports multiple parameters.<br>
     * The command will be sent to all nodes.
     *
     * @see <a href="https://valkey.io/commands/config-set/">valkey.io</a> for details.
     * @param parameters A <code>map</code> consisting of configuration parameters and their
     *     respective values to set.
     * @return <code>OK</code> if all configurations have been successfully set. Otherwise, raises an
     *     error.
     * @example
     *     <pre>{@code
     * String response = client.configSet(Map.of("timeout", "1000", "maxmemory", "1GB")).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> configSet(Map<String, String> parameters);

    /**
     * Sets configuration parameters to the specified values.<br>
     * Starting from server version 7, command supports multiple parameters.
     *
     * @see <a href="https://valkey.io/commands/config-set/">valkey.io</a> for details.
     * @param parameters A <code>map</code> consisting of configuration parameters and their
     *     respective values to set.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code> if all configurations have been successfully set. Otherwise, raises an
     *     error.
     * @example
     *     <pre>{@code
     * String response = client.configSet(Map.of("timeout", "1000", "maxmemory", "1GB"), ALL_PRIMARIES).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> configSet(Map<String, String> parameters, Route route);

    /**
     * Returns the server time.<br>
     * The command will be routed to a random node.
     *
     * @see <a href="https://valkey.io/commands/time/">valkey.io</a> for details.
     * @return The current server time as a <code>String</code> array with two elements: A <code>
     *     UNIX TIME</code> and the amount of microseconds already elapsed in the current second. The
     *     returned array is in a <code>[UNIX TIME, Microseconds already elapsed]</code> format.
     * @example
     *     <pre>{@code
     * String[] serverTime = client.time().get();
     * System.out.println("Server time is: " + serverTime[0] + "." + serverTime[1]);
     * }</pre>
     */
    CompletableFuture<String[]> time();

    /**
     * Returns the server time.
     *
     * @see <a href="https://valkey.io/commands/time/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The current server time as a <code>String</code> array with two elements: A <code>
     *     UNIX TIME</code> and the amount of microseconds already elapsed in the current second. The
     *     returned array is in a <code>[UNIX TIME, Microseconds already elapsed]</code> format.
     * @example
     *     <pre>{@code
     * // Command sent to a single random node via RANDOM route, expecting a SingleValue result.
     * String[] serverTime = client.time().get(RANDOM).getSingleValue();
     * System.out.println("Server time is: " + serverTime[0] + "." + serverTime[1]);
     *
     * // Command sent to all nodes via ALL_NODES route, expecting a MultiValue result.
     * Map<String, String[]> serverTimeForAllNodes = client.time(ALL_NODES).get().getMultiValue();
     * for(var serverTimePerNode : serverTimeForAllNodes.getMultiValue().entrySet()) {
     *     String node = serverTimePerNode.getKey();
     *     String serverTimePerNodeStr = serverTimePerNode.getValue()[0] + "." + serverTimePerNode.getValue()[1];
     *     System.out.println("Server time for node [" + node + "]: " + serverTimePerNodeStr);
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<String[]>> time(Route route);

    /**
     * Returns <code>UNIX TIME</code> of the last DB save timestamp or startup timestamp if no save
     * was made since then.<br>
     * The command will be routed to a random node.
     *
     * @see <a href="https://valkey.io/commands/lastsave/">valkey.io</a> for details.
     * @return <code>UNIX TIME</code> of the last DB save executed with success.
     * @example
     *     <pre>{@code
     * Long timestamp = client.lastsave().get();
     * System.out.printf("Last DB save was done at %s%n", Instant.ofEpochSecond(timestamp));
     * }</pre>
     */
    CompletableFuture<Long> lastsave();

    /**
     * Returns <code>UNIX TIME</code> of the last DB save timestamp or startup timestamp if no save
     * was made since then.
     *
     * @see <a href="https://valkey.io/commands/lastsave/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>UNIX TIME</code> of the last DB save executed with success.
     * @example
     *     <pre>{@code
     * ClusterValue<Long> data = client.lastsave(ALL_NODES).get();
     * for (Map.Entry<String, Long> entry : data.getMultiValue().entrySet()) {
     *     System.out.printf("Last DB save on node %s was made at %s%n", entry.getKey(), Instant.ofEpochSecond(entry.getValue()));
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<Long>> lastsave(Route route);

    /**
     * Deletes all the keys of all the existing databases. This command never fails.<br>
     * The command will be routed to all primary nodes.
     *
     * @see <a href="https://valkey.io/commands/flushall/">valkey.io</a> for details.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.flushall().get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> flushall();

    /**
     * Deletes all the keys of all the existing databases. This command never fails.<br>
     * The command will be routed to all primary nodes.
     *
     * @see <a href="https://valkey.io/commands/flushall/">valkey.io</a> for details.
     * @param mode The flushing mode, could be either {@link FlushMode#SYNC} or {@link
     *     FlushMode#ASYNC}.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.flushall(ASYNC).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> flushall(FlushMode mode);

    /**
     * Deletes all the keys of all the existing databases. This command never fails.
     *
     * @see <a href="https://valkey.io/commands/flushall/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * Route route = new SlotKeyRoute("key", PRIMARY);
     * String response = client.flushall(route).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> flushall(Route route);

    /**
     * Deletes all the keys of all the existing databases. This command never fails.
     *
     * @see <a href="https://valkey.io/commands/flushall/">valkey.io</a> for details.
     * @param mode The flushing mode, could be either {@link FlushMode#SYNC} or {@link
     *     FlushMode#ASYNC}.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * Route route = new SlotKeyRoute("key", PRIMARY);
     * String response = client.flushall(SYNC, route).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> flushall(FlushMode mode, Route route);

    /**
     * Deletes all the keys of the currently selected database. This command never fails.<br>
     * The command will be routed to all primary nodes.
     *
     * @see <a href="https://valkey.io/commands/flushdb/">valkey.io</a> for details.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.flushdb().get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> flushdb();

    /**
     * Deletes all the keys of the currently selected database. This command never fails.<br>
     * The command will be routed to all primary nodes.
     *
     * @see <a href="https://valkey.io/commands/flushdb/">valkey.io</a> for details.
     * @param mode The flushing mode, could be either {@link FlushMode#SYNC} or {@link
     *     FlushMode#ASYNC}.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.flushdb(ASYNC).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> flushdb(FlushMode mode);

    /**
     * Deletes all the keys of the currently selected database. This command never fails.
     *
     * @see <a href="https://valkey.io/commands/flushdb/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * Route route = new SlotKeyRoute("key", PRIMARY);
     * String response = client.flushdb(route).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> flushdb(Route route);

    /**
     * Deletes all the keys of the currently selected database. This command never fails.
     *
     * @see <a href="https://valkey.io/commands/flushdb/">valkey.io</a> for details.
     * @param mode The flushing mode, could be either {@link FlushMode#SYNC} or {@link
     *     FlushMode#ASYNC}.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * Route route = new SlotKeyRoute("key", PRIMARY);
     * String response = client.flushdb(SYNC, route).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> flushdb(FlushMode mode, Route route);

    /**
     * Displays a piece of generative computer art and the Valkey version.<br>
     * The command will be routed to a random node.
     *
     * @see <a href="https://valkey.io/commands/lolwut/">valkey.io</a> for details.
     * @return A piece of generative computer art along with the current Valkey version.
     * @example
     *     <pre>{@code
     * String data = client.lolwut().get();
     * System.out.println(data);
     * assert data.contains("Redis ver. 7.2.3");
     * }</pre>
     */
    CompletableFuture<String> lolwut();

    /**
     * Displays a piece of generative computer art and the Valkey version.<br>
     * The command will be routed to a random node.
     *
     * @see <a href="https://valkey.io/commands/lolwut/">valkey.io</a> for details.
     * @param parameters Additional set of arguments in order to change the output:
     *     <ul>
     *       <li>On Valkey version <code>5</code>, those are length of the line, number of squares per
     *           row, and number of squares per column.
     *       <li>On Valkey version <code>6</code>, those are number of columns and number of lines.
     *       <li>On other versions parameters are ignored.
     *     </ul>
     *
     * @return A piece of generative computer art along with the current Valkey version.
     * @example
     *     <pre>{@code
     * String data = client.lolwut(new int[] { 40, 20 }).get();
     * System.out.println(data);
     * assert data.contains("Redis ver. 7.2.3");
     * }</pre>
     */
    CompletableFuture<String> lolwut(int[] parameters);

    /**
     * Displays a piece of generative computer art and the Valkey version.<br>
     * The command will be routed to a random node.
     *
     * @apiNote Versions 5 and 6 produce graphical things.
     * @see <a href="https://valkey.io/commands/lolwut/">valkey.io</a> for details.
     * @param version Version of computer art to generate.
     * @return A piece of generative computer art along with the current Valkey version.
     * @example
     *     <pre>{@code
     * String data = client.lolwut(6).get();
     * System.out.println(data);
     * assert data.contains("Redis ver. 7.2.3");
     * }</pre>
     */
    CompletableFuture<String> lolwut(int version);

    /**
     * Displays a piece of generative computer art and the Valkey version.<br>
     * The command will be routed to a random node.
     *
     * @apiNote Versions 5 and 6 produce graphical things.
     * @see <a href="https://valkey.io/commands/lolwut/">valkey.io</a> for details.
     * @param version Version of computer art to generate.
     * @param parameters Additional set of arguments in order to change the output:
     *     <ul>
     *       <li>For version <code>5</code>, those are length of the line, number of squares per row,
     *           and number of squares per column.
     *       <li>For version <code>6</code>, those are number of columns and number of lines.
     *     </ul>
     *
     * @return A piece of generative computer art along with the current Valkey version.
     * @example
     *     <pre>{@code
     * String data = client.lolwut(6, new int[] { 40, 20 }).get();
     * System.out.println(data);
     * assert data.contains("Redis ver. 7.2.3");
     * data = client.lolwut(5, new int[] { 30, 5, 5 }).get();
     * System.out.println(data);
     * assert data.contains("Redis ver. 7.2.3");
     *
     * }</pre>
     */
    CompletableFuture<String> lolwut(int version, int[] parameters);

    /**
     * Displays a piece of generative computer art and the Valkey version.
     *
     * @see <a href="https://valkey.io/commands/lolwut/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return A piece of generative computer art along with the current Valkey version.
     * @example
     *     <pre>{@code
     * ClusterValue<String> response = client.lolwut(ALL_NODES).get();
     * for (String data : response.getMultiValue().values()) {
     *     System.out.println(data);
     *     assert data.contains("Redis ver. 7.2.3");
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> lolwut(Route route);

    /**
     * Displays a piece of generative computer art and the Valkey version.
     *
     * @see <a href="https://valkey.io/commands/lolwut/">valkey.io</a> for details.
     * @param parameters Additional set of arguments in order to change the output:
     *     <ul>
     *       <li>On Valkey version <code>5</code>, those are length of the line, number of squares per
     *           row, and number of squares per column.
     *       <li>On Valkey version <code>6</code>, those are number of columns and number of lines.
     *       <li>On other versions parameters are ignored.
     *     </ul>
     *
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return A piece of generative computer art along with the current Valkey version.
     * @example
     *     <pre>{@code
     * String data = client.lolwut(new int[] { 40, 20 }, ALL_NODES).get();
     * for (String data : response.getMultiValue().values()) {
     *     System.out.println(data);
     *     assert data.contains("Redis ver. 7.2.3");
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> lolwut(int[] parameters, Route route);

    /**
     * Displays a piece of generative computer art and the Valkey version.
     *
     * @apiNote Versions 5 and 6 produce graphical things.
     * @see <a href="https://valkey.io/commands/lolwut/">valkey.io</a> for details.
     * @param version Version of computer art to generate.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return A piece of generative computer art along with the current Valkey version.
     * @example
     *     <pre>{@code
     * ClusterValue<String> response = client.lolwut(6, ALL_NODES).get();
     * for (String data : response.getMultiValue().values()) {
     *     System.out.println(data);
     *     assert data.contains("Redis ver. 7.2.3");
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> lolwut(int version, Route route);

    /**
     * Displays a piece of generative computer art and the Valkey version.
     *
     * @apiNote Versions 5 and 6 produce graphical things.
     * @see <a href="https://valkey.io/commands/lolwut/">valkey.io</a> for details.
     * @param version Version of computer art to generate.
     * @param parameters Additional set of arguments in order to change the output:
     *     <ul>
     *       <li>For version <code>5</code>, those are length of the line, number of squares per row,
     *           and number of squares per column.
     *       <li>For version <code>6</code>, those are number of columns and number of lines.
     *     </ul>
     *
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return A piece of generative computer art along with the current Valkey version.
     * @example
     *     <pre>{@code
     * String data = client.lolwut(6, new int[] { 40, 20 }, ALL_NODES).get();
     * for (String data : response.getMultiValue().values()) {
     *     System.out.println(data);
     *     assert data.contains("Redis ver. 7.2.3");
     * }
     * data = client.lolwut(5, new int[] { 30, 5, 5 }, ALL_NODES).get();
     * for (String data : response.getMultiValue().values()) {
     *     System.out.println(data);
     *     assert data.contains("Redis ver. 7.2.3");
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> lolwut(int version, int[] parameters, Route route);

    /**
     * Returns the number of keys in the database.<br>
     * The command will be routed to all primary nodes.
     *
     * @see <a href="https://valkey.io/commands/dbsize/">valkey.io</a> for details.
     * @return The total number of keys across the primary nodes.
     * @example
     *     <pre>{@code
     * Long numKeys = client.dbsize().get();
     * System.out.printf("Number of keys across the primary nodes: %d%n", numKeys);
     * }</pre>
     */
    CompletableFuture<Long> dbsize();

    /**
     * Returns the number of keys in the database.
     *
     * @see <a href="https://valkey.io/commands/dbsize/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The number of keys in the database.<br>
     *     If the query is routed to multiple nodes, returns the sum of the number of keys across all
     *     routed nodes.
     * @example
     *     <pre>{@code
     * Route route = new ByAddressRoute("localhost", 8000);
     * Long numKeys = client.dbsize(route).get();
     * System.out.printf("Number of keys for node at port 8000: %d%n", numKeys);
     * }</pre>
     */
    CompletableFuture<Long> dbsize(Route route);

    /**
     * Returns a list of all ACL categories, or a list of commands within a category.<br>
     * The command will be routed to a random node.
     *
     * @see <a href="https://valkey.io/commands/acl-cat/">valkey.io</a> for details.
     * @return An array of ACL categories or commands.
     * @example
     *     <pre>{@code
     * String[] categories = client.aclCat().get();
     * assert Arrays.asList(categories).contains("string");
     * }</pre>
     */
    CompletableFuture<String[]> aclCat();

    /**
     * Returns a list of commands within the specified ACL category.<br>
     * The command will be routed to a random node.
     *
     * @see <a href="https://valkey.io/commands/acl-cat/">valkey.io</a> for details.
     * @param category The ACL category to list commands for.
     * @return An array of commands within the specified category.
     * @example
     *     <pre>{@code
     * String[] commands = client.aclCat("string").get();
     * assert Arrays.asList(commands).contains("get");
     * }</pre>
     */
    CompletableFuture<String[]> aclCat(String category);

    /**
     * Deletes all specified ACL users and terminates their connections.<br>
     * The command will be routed to all nodes.
     *
     * @see <a href="https://valkey.io/commands/acl-deluser/">valkey.io</a> for details.
     * @param usernames An array of usernames to delete.
     * @return The number of users deleted.
     * @example
     *     <pre>{@code
     * Long deletedCount = client.aclDelUser(new String[] {"user1", "user2"}).get();
     * assert deletedCount == 2L;
     * }</pre>
     */
    CompletableFuture<Long> aclDelUser(String[] usernames);

    /**
     * Simulates the execution of a command by a user without actually executing the command.<br>
     * The command will be routed to a random node.
     *
     * @see <a href="https://valkey.io/commands/acl-dryrun/">valkey.io</a> for details.
     * @param username The username to simulate command execution for.
     * @param command The command to simulate.
     * @param args The command arguments.
     * @return <code>"OK"</code> if the user can execute the command, otherwise an error is returned.
     * @example
     *     <pre>{@code
     * String result = client.aclDryRun("user1", "get", new String[] {"key"}).get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> aclDryRun(String username, String command, String[] args);

    /**
     * Generates a random password for ACL users.<br>
     * The command will be routed to a random node.
     *
     * @see <a href="https://valkey.io/commands/acl-genpass/">valkey.io</a> for details.
     * @return A randomly generated password string.
     * @example
     *     <pre>{@code
     * String password = client.aclGenPass().get();
     * assert password.length() == 64; // Default length
     * }</pre>
     */
    CompletableFuture<String> aclGenPass();

    /**
     * Generates a random password with the specified number of bits for ACL users.<br>
     * The command will be routed to a random node.
     *
     * @see <a href="https://valkey.io/commands/acl-genpass/">valkey.io</a> for details.
     * @param bits The number of bits for the password (must be a multiple of 4, between 1 and 4096).
     * @return A randomly generated password string.
     * @example
     *     <pre>{@code
     * String password = client.aclGenPass(128).get();
     * assert password.length() == 32; // 128 bits = 32 hex characters
     * }</pre>
     */
    CompletableFuture<String> aclGenPass(int bits);

    /**
     * Returns all ACL rules for the specified user.<br>
     * The command will be routed to a random node.
     *
     * @see <a href="https://valkey.io/commands/acl-getuser/">valkey.io</a> for details.
     * @param username The username to get ACL rules for.
     * @return An array or map describing the ACL rules for the user, or <code>null</code> if user
     *     doesn't exist.
     * @example
     *     <pre>{@code
     * Object userInfo = client.aclGetUser("default").get();
     * assert userInfo != null;
     * }</pre>
     */
    CompletableFuture<Object> aclGetUser(String username);

    /**
     * Returns a list of all ACL users and their rules in ACL configuration file format.<br>
     * The command will be routed to a random node.
     *
     * @see <a href="https://valkey.io/commands/acl-list/">valkey.io</a> for details.
     * @return An array of ACL rules for all users.
     * @example
     *     <pre>{@code
     * String[] aclList = client.aclList().get();
     * assert aclList.length > 0;
     * }</pre>
     */
    CompletableFuture<String[]> aclList();

    /**
     * Reloads ACL rules from the configured ACL configuration file.<br>
     * The command will be routed to all nodes.
     *
     * @see <a href="https://valkey.io/commands/acl-load/">valkey.io</a> for details.
     * @return <code>"OK"</code> on success.
     * @example
     *     <pre>{@code
     * String result = client.aclLoad().get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> aclLoad();

    /**
     * Returns the ACL security events log.<br>
     * The command will be routed to a random node.
     *
     * @see <a href="https://valkey.io/commands/acl-log/">valkey.io</a> for details.
     * @return An array of ACL security events.
     * @example
     *     <pre>{@code
     * Object[] log = client.aclLog().get();
     * System.out.printf("ACL log has %d entries%n", log.length);
     * }</pre>
     */
    CompletableFuture<Object[]> aclLog();

    /**
     * Returns the specified number of ACL security events from the log.<br>
     * The command will be routed to a random node.
     *
     * @see <a href="https://valkey.io/commands/acl-log/">valkey.io</a> for details.
     * @param count The number of entries to return.
     * @return An array of ACL security events.
     * @example
     *     <pre>{@code
     * Object[] log = client.aclLog(10).get();
     * assert log.length <= 10;
     * }</pre>
     */
    CompletableFuture<Object[]> aclLog(int count);

    /**
     * Saves the current ACL rules to the configured ACL configuration file.<br>
     * The command will be routed to all nodes.
     *
     * @see <a href="https://valkey.io/commands/acl-save/">valkey.io</a> for details.
     * @return <code>"OK"</code> on success.
     * @example
     *     <pre>{@code
     * String result = client.aclSave().get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> aclSave();

    /**
     * Creates or modifies an ACL user and its rules.<br>
     * The command will be routed to all nodes.
     *
     * @see <a href="https://valkey.io/commands/acl-setuser/">valkey.io</a> for details.
     * @param username The username for the ACL user.
     * @param rules An array of ACL rules to apply to the user.
     * @return <code>"OK"</code> on success.
     * @example
     *     <pre>{@code
     * String result = client.aclSetUser("user1", new String[] {"on", "+get", "~*"}).get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> aclSetUser(String username, String[] rules);

    /**
     * Returns a list of all ACL usernames.<br>
     * The command will be routed to a random node.
     *
     * @see <a href="https://valkey.io/commands/acl-users/">valkey.io</a> for details.
     * @return An array of ACL usernames.
     * @example
     *     <pre>{@code
     * String[] users = client.aclUsers().get();
     * assert Arrays.asList(users).contains("default");
     * }</pre>
     */
    CompletableFuture<String[]> aclUsers();

    /**
     * Returns the username of the current connection.<br>
     * The command will be routed to a random node.
     *
     * @see <a href="https://valkey.io/commands/acl-whoami/">valkey.io</a> for details.
     * @return The username of the current connection.
     * @example
     *     <pre>{@code
     * String username = client.aclWhoami().get();
     * assert username.equals("default");
     * }</pre>
     */
    CompletableFuture<String> aclWhoami();
}
