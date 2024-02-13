/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.examples;

import glide.api.RedisClient;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClientConfiguration;
import java.util.concurrent.ExecutionException;

public class ExamplesApp {

    // main application entrypoint
    public static void main(String[] args) {
        runGlideExamples();
    }

    private static void runGlideExamples() {
        String host = "localhost";
        Integer port = 6379;
        boolean useSsl = false;

        RedisClientConfiguration config =
                RedisClientConfiguration.builder()
                        .address(NodeAddress.builder().host(host).port(port).build())
                        .useTLS(useSsl)
                        .build();

        try {
            RedisClient client = RedisClient.CreateClient(config).get();

            System.out.println("Glide PING: " + client.ping().get());
            System.out.println("Glide PING: " + client.ping("found you").get());

        } catch (ExecutionException | InterruptedException e) {
            System.out.println("Glide example failed with an exception: ");
            e.printStackTrace();
        }
    }
}
