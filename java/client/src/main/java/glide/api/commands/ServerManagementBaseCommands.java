/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface ServerManagementBaseCommands {

    /**
     * Reads the configuration parameters of a running Redis server.
     *
     * @see <a href="https://redis.io/commands/config-get/">redis.io</a> for details.
     * @param parameters An <code>array</code> of configuration parameter names to retrieve values
     *     for.
     * @return A <code>map</code> of values corresponding to the configuration parameters.
     * @example
     *     <pre>
     * Map&lt;String, String&gt; configParams = client.configGet("logfile", "*port").get();
     * var logFile = configParams.get("logfile");
     * var port = configParams.get("port");
     * var tlsPort = configParams.get("tls-port");
     * </pre>
     */
    CompletableFuture<Map<String, String>> configGet(String[] parameters);

    /**
     * Sets configuration parameters to the specified values.
     *
     * @see <a href="https://redis.io/commands/config-set/">redis.io</a> for details.
     * @param parameters A <code>map</code> consisting of configuration parameters and their
     *     respective values to set.
     * @return <code>OK</code> if all configurations have been successfully set. Otherwise, raises an
     *     error.
     * @example
     *     <pre>
     * String response = client.configSet(Map.of("syslog-enabled", "yes")).get();
     * assert response.equals("OK")
     * </pre>
     */
    CompletableFuture<String> configSet(Map<String, String> parameters);
}
