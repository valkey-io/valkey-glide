/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.examples.clients;

import glide.api.RedisClient;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClientConfiguration;
import glide.examples.ExamplesApp;
import java.util.concurrent.ExecutionException;

/** Connect to Jedis client. See: https://github.com/redis/jedis */
public class GlideClient {
    public static RedisClient connectToGlide(ExamplesApp.ConnectionSettings connectionSettings)
            throws ExecutionException, InterruptedException {
        if (connectionSettings.clusterMode) {
            throw new RuntimeException("Not implemented");
        }
        RedisClientConfiguration config =
                RedisClientConfiguration.builder()
                        .address(
                                NodeAddress.builder()
                                        .host(connectionSettings.host)
                                        .port(connectionSettings.port)
                                        .build())
                        .useTLS(connectionSettings.useSsl)
                        .build();
        return RedisClient.CreateClient(config).get();
    }
}
