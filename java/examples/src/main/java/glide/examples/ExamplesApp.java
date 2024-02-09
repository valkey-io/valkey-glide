/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.examples;

import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleRoute.ALL_NODES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleRoute.ALL_PRIMARIES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleRoute.RANDOM;

import glide.api.RedisClient;
import glide.api.RedisClusterClient;
import glide.examples.clients.GlideClient;
import glide.examples.clients.GlideClusterClient;
import java.util.concurrent.ExecutionException;

public class ExamplesApp {

    // main application entrypoint
    public static void main(String[] args) {
        runGlideExamples();
        runGlideClusterModeExamples();
    }

    private static void runGlideExamples() {
        ConnectionSettings settings = new ConnectionSettings("localhost", 6379, false, false);

        try {
            RedisClient client = GlideClient.connectToGlide(settings);

            System.out.println("Glide PING: " + client.ping().get());
            System.out.println("Glide PING: " + client.ping("found you").get());

        } catch (ExecutionException | InterruptedException e) {
            System.out.println("Glide example failed with an exception: ");
            e.printStackTrace();
        }
    }

    private static void runGlideClusterModeExamples() {
        ConnectionSettings settings = new ConnectionSettings("localhost", 8000, false, true);

        try {
            RedisClusterClient client = GlideClusterClient.connectToGlide(settings);

            System.out.println("Glide PING: ");
            System.out.println(">>>> " + client.ping().get());

            System.out.println("Glide PING(msg): ");
            System.out.println(">>>> " + client.ping("msg").get());

        } catch (ExecutionException | InterruptedException e) {
            System.out.println("Glide example failed with an exception: ");
            e.printStackTrace();
        }
    }

    /** Redis-client settings */
    public static class ConnectionSettings {
        public final String host;
        public final int port;
        public final boolean useSsl;
        public final boolean clusterMode;

        public ConnectionSettings(String host, int port, boolean useSsl, boolean clusterMode) {
            this.host = host;
            this.port = port;
            this.useSsl = useSsl;
            this.clusterMode = clusterMode;
        }
    }
}
