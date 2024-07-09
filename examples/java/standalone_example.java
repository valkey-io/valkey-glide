import glide.api.GlideClient;
import glide.api.logging.Logger;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.GlideClientConfiguration;
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
        Integer port = 6379;
        boolean useSsl = false;

        GlideClientConfiguration config =
                GlideClientConfiguration.builder()
                        .address(NodeAddress.builder().host(host).port(port).build())
                        .useTLS(useSsl)
                        .build();

        try (GlideClient client = GlideClient.createClient(config).get()) {

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
