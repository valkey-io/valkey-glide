/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.examples.clients;

import glide.api.RedisClusterClient;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClusterClientConfiguration;
import glide.examples.ExamplesApp;
import java.util.concurrent.ExecutionException;

/** Connect to Jedis client. See: https://github.com/redis/jedis */
public class GlideClusterClient {
    public static RedisClusterClient connectToGlide(ExamplesApp.ConnectionSettings connectionSettings)
            throws ExecutionException, InterruptedException {
        if (!connectionSettings.clusterMode) {
            throw new RuntimeException("Not implemented");
        }
        RedisClusterClientConfiguration config =
                RedisClusterClientConfiguration.builder()
                        .address(
                                NodeAddress.builder()
                                        .host(connectionSettings.host)
                                        .port(connectionSettings.port)
                                        .build())
                        .useTLS(connectionSettings.useSsl)
                        .build();
        return RedisClusterClient.CreateClient(config).get();
    }
}
