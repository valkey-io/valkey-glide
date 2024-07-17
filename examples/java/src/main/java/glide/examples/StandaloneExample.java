/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.examples;

import glide.api.GlideClient;
import glide.api.logging.Logger;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.ConnectionException;
import glide.api.models.exceptions.ExecAbortException;
import glide.api.models.exceptions.TimeoutException;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class StandaloneExample {

    /**
     * Creates and returns a GlideClient instance. This function initializes a
     * GlideClient with the provided node address.
     *
     * @return A <code>GlideClient</code> connected to the provided nodes address.
     */
    public static GlideClient createClient() throws CancellationException, ExecutionException, InterruptedException {
        String host = "localhost";
        Integer port = 6379;

        // Check GlideClientConfiguration for additional options.
        GlideClientConfiguration config =
                GlideClientConfiguration.builder()
                        .address(NodeAddress.builder().host(host).port(port).build())
                        // Enable this field if the servers are configured with TLS.
                        //.useTLS(true);
                        .build();

        GlideClient client = GlideClient.createClient(config).get();
        return client;
    }

    /**
     * Executes the main logic of the application, performing basic operations such as SET, GET, and
     * PING using the provided GlideClient.
     *
     * @param client An instance of <code>GlideClient</code>.
     */
    public static void appLogic(GlideClient client) throws ExecutionException, InterruptedException {

        // Send SET and GET
        CompletableFuture<String> setResponse = client.set("foo", "bar");
        Logger.log(Logger.Level.INFO, "app", "Set response is " + setResponse.get());

        CompletableFuture<String> getResponse = client.get("foo");
        Logger.log(Logger.Level.INFO, "app", "Get response is " + getResponse.get());

        // Send PING to the primary node
        CompletableFuture<String> pong = client.ping();
        Logger.log(Logger.Level.INFO, "app", "Ping response is " + pong.get());
    }

    /** Executes the application logic with exception handling. */
    private static void execAppLogic() {

        while (true) {
            GlideClient client = null;
            try {
                client = createClient();
                appLogic(client);

            } catch (CancellationException e) {
                Logger.log(Logger.Level.ERROR, "glide", "Request cancelled: " + e.getMessage());
                throw e;
            } catch (InterruptedException e) {
                Logger.log(Logger.Level.ERROR, "glide", "Client creation interrupted: " + e.getMessage());
                Thread.currentThread().interrupt(); // Restore interrupt status
                throw new CancellationException("Interrupted while creating client");
            } catch (ExecutionException e) {
                // All Glide errors will be handled as ExecutionException
                if (e.getCause() instanceof ClosingException) {
                    // If the error message contains "NOAUTH", raise the exception
                    // because it indicates a critical authentication issue.
                    if (e.getMessage().contains("NOAUTH")) {
                        Logger.log(
                            Logger.Level.ERROR, "glide", "Authentication error encountered: " + e.getMessage());
                    } else {
                        Logger.log(
                            Logger.Level.WARN,
                            "glide",
                            "Client has closed and needs to be re-created: " + e.getMessage());
                    }
                } else if (e.getCause() instanceof ConnectionException) {
                    // The client wasn't able to reestablish the connection within the given retries
                    Logger.log(Logger.Level.ERROR, "glide", "Connection error encountered: " + e.getMessage());
                } else if (e.getCause() instanceof TimeoutException) {
                    // A request timed out. You may choose to retry the execution based on your application's
                    // logic
                    Logger.log(Logger.Level.ERROR, "glide", "Timeout encountered: " + e.getMessage());
                } else if (e.getCause() instanceof ExecAbortException) {
                    Logger.log(Logger.Level.ERROR, "glide", "ExecAbort error encountered: " + e.getMessage());
                } else {
                    Logger.log(Logger.Level.ERROR, "glide", "Execution error during client creation: " + e.getCause());
                }
            } finally {
                if (client != null) {
                    try {
                        client.close();
                    } catch (Exception e) {
                        Logger.log(
                                Logger.Level.WARN,
                                "glide",
                                "Error encountered while closing the client: " + e.getMessage());
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        // In this example, we will utilize the client's logger for all log messages
        Logger.setLoggerConfig(Logger.Level.INFO);
        // Optional - set the logger to write to a file
        // Logger.setLoggerConfig(Logger.Level.INFO, file)
        execAppLogic();
    }
}
