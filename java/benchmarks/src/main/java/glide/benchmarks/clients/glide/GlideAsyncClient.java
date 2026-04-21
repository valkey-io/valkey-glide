/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.clients.glide;

import static java.util.concurrent.TimeUnit.SECONDS;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.logging.Logger;
import glide.api.models.configuration.AdvancedGlideClusterClientConfiguration;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.PeriodicChecksManualInterval;
import glide.api.models.configuration.PeriodicChecksStatus;
import glide.api.models.configuration.TlsAdvancedConfiguration;
import glide.benchmarks.clients.AsyncClient;
import glide.benchmarks.utils.ConnectionSettings;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/** A Glide client with async capabilities */
public class GlideAsyncClient implements AsyncClient<String> {
    private BaseClient glideClient;

    @Override
    public void connectToValkey(ConnectionSettings connectionSettings) {
        Logger.init(Logger.Level.DEBUG);

        if (connectionSettings.clusterMode) {
            // Build advanced config with tcpNoDelay
            AdvancedGlideClusterClientConfiguration adv =
                    AdvancedGlideClusterClientConfiguration.builder()
                            .refreshTopologyFromInitialNodes(true)
                            .periodicChecks(
                                    PeriodicChecksStatus.DISABLED) // 10 minutes
                            .tlsAdvancedConfiguration(
                                    TlsAdvancedConfiguration.builder().useInsecureTLS(true).build())
                            .build();
            var configBuilder =
                    GlideClusterClientConfiguration.builder()
                            .address(NodeAddress.builder().host(connectionSettings.host).port(6379).build())
                            .useTLS(true)
                            .advancedConfiguration(adv)
                            // .readFrom(ReadFrom.AZ_AFFINITY_REPLICAS_AND_PRIMARY)
                            // .clientAZ("us-east-1c")
                            .requestTimeout(connectionSettings.requestTimeoutMs);
            // if (connectionSettings.inflightLimit > 0) {
            //     configBuilder.inflightRequestsLimit(connectionSettings.inflightLimit);
            //     System.out.printf("Setting inflight requests limit to %d%n", connectionSettings.inflightLimit);
            // }
            GlideClusterClientConfiguration config = configBuilder.build();
            try {
                glideClient = GlideClusterClient.createClient(config).get(10, SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new RuntimeException(e);
            }

        } else {
            GlideClientConfiguration config =
                    GlideClientConfiguration.builder()
                            .address(
                                    NodeAddress.builder()
                                            .host(connectionSettings.host)
                                            .port(connectionSettings.port)
                                            .build())
                            .useTLS(connectionSettings.useSsl)
                            .build();

            try {
                glideClient = GlideClient.createClient(config).get(10, SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public CompletableFuture<String> asyncSet(String key, String value) {
        return glideClient.set(key, value);
    }

    @Override
    public CompletableFuture<String> asyncGet(String key) {
        return glideClient.get(key);
    }

    @Override
    public CompletableFuture<Long> asyncDel(String[] keys) {
        return glideClient.del(keys);
    }

    @Override
    public void closeConnection() {
        try {
            glideClient.close();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getName() {
        return "glide";
    }

    public BaseClient getGlideClient() {
    return glideClient;
}
}
