/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.examples;

import static glide.api.logging.Logger.log;
import static glide.api.logging.Logger.Level.ERROR;
import static glide.api.logging.Logger.Level.INFO;
import static glide.api.logging.Logger.Level.WARN;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import glide.api.GlideClient;
import glide.api.logging.Logger;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.ConnectionException;
import glide.api.models.exceptions.TimeoutException;

public class StandaloneExample {

    /**
     * Creates and returns a <code>GlideClient</code> instance.
     *
     * <p>This function initializes a <code>GlideClient</code> with the provided list of nodes. The
     * list may contain either only primary node or a mix of primary and replica nodes. The <code>
     * GlideClient
     * </code> use these nodes to connect to the Standalone setup servers.
     *
     * @return A <code>GlideClient</code> connected to the provided node address.
     * @throws CancellationException if the operation is cancelled.
     * @throws ExecutionException if the client fails due to execution errors.
     * @throws InterruptedException if the operation is interrupted.
     */
    public static GlideClient createClient(List<NodeAddress> nodeList)
            throws CancellationException, ExecutionException, InterruptedException {
        // Check `GlideClientConfiguration` for additional options.
        GlideClientConfiguration config =
                GlideClientConfiguration.builder()
                        .addresses(nodeList)
                        // Set request timeout - recommended to configure based on your use case.
                        .requestTimeout(500)
                        // Enable this field if the servers are configured with TLS.
                        // .useTLS(true);
                        .build();

        GlideClient client = GlideClient.createClient(config).get();
        return client;
    }

    /**
     * Executes the main logic of the application, performing basic operations such as SET, GET, and
     * PING using the provided <code>GlideClient</code>.
     *
     * @param client An instance of <code>GlideClient</code>.
     * @throws ExecutionException if an execution error occurs during operations.
     * @throws InterruptedException if the operation is interrupted.
     */
    public static void appLogic(GlideClient client) throws ExecutionException, InterruptedException {

        // Send SET and GET
        CompletableFuture<String> setResponse = client.set("foo", "bar");
        log(INFO, "app", "Set response is " + setResponse.get());

        CompletableFuture<String> getResponse = client.get("foo");
        log(INFO, "app", "Get response is " + getResponse.get());

        // Send PING
        CompletableFuture<String> pong = client.ping();
        log(INFO, "app", "Ping response is " + pong.get());
    }

    /**
     * Executes the application logic with exception handling.
     *
     * @throws ExecutionException if an execution error occurs during operations.
     */
    private static void execAppLogic() throws ExecutionException {

        // Define list of nodes
        List<NodeAddress> nodeList =
                Collections.singletonList(NodeAddress.builder().host("localhost").port(6379).build());

        while (true) {
            try (GlideClient client = createClient(nodeList)) {
                appLogic(client);
                return;
            } catch (CancellationException e) {
                log(ERROR, "glide", "Request cancelled: " + e.getMessage());
                throw e;
            } catch (InterruptedException e) {
                log(ERROR, "glide", "Client interrupted: " + e.getMessage());
                Thread.currentThread().interrupt(); // Restore interrupt status
                throw new CancellationException("Client was interrupted.");
            } catch (ExecutionException e) {
                // All Glide errors will be handled as ExecutionException
                if (e.getCause() instanceof ClosingException) {
                    // If the error message contains "NOAUTH", raise the exception
                    // because it indicates a critical authentication issue.
                    if (e.getMessage().contains("NOAUTH")) {
                        log(ERROR, "glide", "Authentication error encountered: " + e.getMessage());
                        throw e;
                    } else {
                        log(WARN, "glide", "Client has closed and needs to be re-created: " + e.getMessage());
                    }
                } else if (e.getCause() instanceof ConnectionException) {
                    // The client wasn't able to reestablish the connection within the given retries
                    log(ERROR, "glide", "Connection error encountered: " + e.getMessage());
                    throw e;
                } else if (e.getCause() instanceof TimeoutException) {
                    // A request timed out. You may choose to retry the execution based on your application's
                    // logic
                    log(ERROR, "glide", "Timeout encountered: " + e.getMessage());
                    throw e;
                } else {
                    log(ERROR, "glide", "Execution error encountered: " + e.getCause());
                    throw e;
                }
            }
        }
    }

    /**
     * The entry point of the standalone example. This method sets up the logger configuration and
     * executes the main application logic.
     *
     * @param args Command-line arguments passed to the application.
     * @throws ExecutionException if an error occurs during execution of the application logic.
     */
    public static void main(String[] args) throws ExecutionException {
        // In this example, we will utilize the client's logger for all log messages
        Logger.setLoggerConfig(INFO);
        // Optional - set the logger to write to a file
        // Logger.setLoggerConfig(Logger.Level.INFO, file)
        execAppLogic();
    }
}
