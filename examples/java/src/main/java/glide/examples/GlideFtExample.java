/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.examples;

import static glide.api.logging.Logger.log;
import static glide.api.logging.Logger.Level.ERROR;
import static glide.api.logging.Logger.Level.INFO;
import static glide.api.logging.Logger.Level.WARN;
import static glide.api.models.GlideString.gs;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_NODES;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import glide.api.GlideClusterClient;
import glide.api.commands.servermodules.FT;
import glide.api.logging.Logger;
import glide.api.models.ClusterValue;
import glide.api.models.commands.InfoOptions.Section;
import glide.api.models.commands.FT.FTCreateOptions;
import glide.api.models.commands.FT.FTCreateOptions.DataType;
import glide.api.models.commands.FT.FTCreateOptions.DistanceMetric;
import glide.api.models.commands.FT.FTCreateOptions.FieldInfo;
import glide.api.models.commands.FT.FTCreateOptions.VectorFieldHnsw;
import glide.api.models.commands.FT.FTSearchOptions;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.ConnectionException;
import glide.api.models.exceptions.TimeoutException;

public class GlideFtExample {

    /** Waiting interval to let server process the data before querying */
    private static final int DATA_PROCESSING_TIMEOUT = 1000; // ms

    /**
     * Creates and returns a <code>GlideClusterClient</code> instance.
     *
     * <p>This function initializes a <code>GlideClusterClient</code> with the provided list of nodes.
     * The list may contain the address of one or more cluster nodes, and the client will
     * automatically discover all nodes in the cluster.
     *
     * @return A <code>GlideClusterClient</code> connected to the discovered nodes.
     * @throws CancellationException if the operation is cancelled.
     * @throws ExecutionException if the client fails due to execution errors.
     * @throws InterruptedException if the operation is interrupted.
     */
    public static GlideClusterClient createClient(List<NodeAddress> nodeList)
            throws CancellationException, ExecutionException, InterruptedException {
        // Check `GlideClusterClientConfiguration` for additional options.
        GlideClusterClientConfiguration config =
                GlideClusterClientConfiguration.builder()
                        .addresses(nodeList)
                        // Set request timeout - recommended to configure based on your use case.
                        .requestTimeout(500)
                        // Enable this field if the servers are configured with TLS.
                        // .useTLS(true);
                        .build();

        GlideClusterClient client = GlideClusterClient.createClient(config).get();
        return client;
    }

    /**
     * Executes the main logic of the application, performing basic operations such as FT.CREATE and
     * FT.SEARCH using the provided <code>GlideClusterClient</code>.
     *
     * @param client An instance of <code>GlideClusterClient</code>.
     * @throws ExecutionException if an execution error occurs during operations.
     * @throws InterruptedException if the operation is interrupted.
     */
    public static void appLogic(GlideClusterClient client)
            throws ExecutionException, InterruptedException {

        String prefix = "{" + UUID.randomUUID() + "}:";
        String index = prefix + "index";

        CompletableFuture<String> createResponse =
                FT.create(
                        client,
                        index,
                        new FieldInfo[] {
                            new FieldInfo("vec", "VEC", VectorFieldHnsw.builder(DistanceMetric.L2, 2).build())
                        },
                        FTCreateOptions.builder()
                                .dataType(DataType.HASH)
                                .prefixes(new String[] {prefix})
                                .build()); // "OK"

        CompletableFuture<Long> hsetResponse =
                client.hset(
                        gs(prefix + 0),
                        Map.of(
                                gs("vec"),
                                gs(
                                        new byte[] {
                                            0, 0, 0, 0, 0, 0, 0, 0
                                        }))); // response is 1L which represents the number of fields that were added.

        hsetResponse =
                client.hset(
                        gs(prefix + 1),
                        Map.of(
                                gs("vec"),
                                gs(
                                        new byte[] {
                                            0, 0, 0, 0, 0, 0, (byte) 0x80, (byte) 0xBF
                                        }))); // response is 1L which represents the number of fields that were added.
        Thread.sleep(DATA_PROCESSING_TIMEOUT); // let server digest the data and update

        // These are the optional arguments used for the FT.search command
        var options =
                FTSearchOptions.builder()
                        .params(Map.of(gs("query_vec"), gs(new byte[] {0, 0, 0, 0, 0, 0, 0, 0})))
                        .build();
        String query = "*=>[KNN 2 @VEC $query_vec]"; // This is the text query to search for
        CompletableFuture<Object[]> searchResponse = FT.search(client, index, query, options);

        // When you call .get() on searchResponse, the result will be an Object[] as shown in the
        // commented assert test below.
        // assertArrayEquals(
        //         new Object[] {
        //             2L,
        //             Map.of(
        //                     gs(prefix + 0),
        //                     Map.of(gs("__VEC_score"), gs("0"), gs("vec"), gs("\0\0\0\0\0\0\0\0")),
        //                     gs(prefix + 1),
        //                     Map.of(
        //                             gs("__VEC_score"),
        //                             gs("1"),
        //                             gs("vec"),
        //                             gs(
        //                                     new byte[] {
        //                                         0,
        //                                         0,
        //                                         0,
        //                                         0,
        //                                         0,
        //                                         0,
        //                                         (byte) 0x80,
        //                                         (byte) 0xBF
        //                                     })))
        //         },
        //         searchResponse.get());

        System.out.println("Create response: " + createResponse.get());
        System.out.println("Hset response: " + hsetResponse.get());
        System.out.println("Search response: " + searchResponse.get());

        // Send INFO REPLICATION with routing option to all nodes
        ClusterValue<String> infoResponse =
                client.info(new Section[] {Section.REPLICATION}, ALL_NODES).get();
        log(
                INFO,
                "app",
                "INFO REPLICATION responses from all nodes are " + infoResponse.getMultiValue());
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
            try (GlideClusterClient client = createClient(nodeList)) {
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
     * The entry point of the cluster example. This method sets up the logger configuration and
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
