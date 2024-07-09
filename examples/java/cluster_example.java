import glide.api.GlideClusterClient;
import glide.api.logging.Logger;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.RequestRoutingConfiguration;
import java.util.concurrent.ExecutionException;

import static glide.api.models.GlideString.gs;

public class Main {

    public static void main(String[] args) {
        // In this example, we will utilize the client's logger for all log messages
        Logger.setLoggerConfig(Logger.Level.INFO);

        runGlideExamples();
    }

    private static void runGlideExamples() {
        String host = "localhost";
        Integer port1 = 7001;
        Integer port2 = 7002;
        Integer port3 = 7003;
        Integer port4 = 7004;
        Integer port5 = 7005;
        Integer port6 = 7006;
        boolean useSsl = false;

        GlideClusterClientConfiguration config =
                GlideClusterClientConfiguration.builder()
                        .address(NodeAddress.builder().host(host).port(port1).port(port2).port(port3).port(port4).port(port5).port(port6).build())
                        .useTLS(useSsl)
                        .build();

        try (GlideClusterClient client = GlideClusterClient.createClient(config).get()) {

            Logger.log(Logger.Level.INFO, "app", "PING: " + client.ping(gs("PING")).get());
            Logger.log(Logger.Level.INFO, "app", "PING(found you): " + client.ping( gs("found you")).get());

            Logger.log(Logger.Level.INFO, "app", "SET(apples, oranges): " + client.set(gs("apples"), gs("oranges")).get());
            Logger.log(Logger.Level.INFO, "app", "GET(apples): " + client.get(gs("apples")).get());

        } catch (ExecutionException | InterruptedException e) {
            Logger.log(Logger.Level.ERROR, "glide", "Glide example failed with an exception: ");
            e.printStackTrace();
        }
    }
}
