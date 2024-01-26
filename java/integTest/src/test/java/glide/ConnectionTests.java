package glide;

import glide.api.RedisClient;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClientConfiguration;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

public class ConnectionTests {

    @Test
    @SneakyThrows
    public void basic_client() {
        var regularClient =
                RedisClient.CreateClient(
                                RedisClientConfiguration.builder()
                                        .address(NodeAddress.builder().port(TestConfiguration.STANDALONE_PORT).build())
                                        .build())
                        .get(10, TimeUnit.SECONDS);
        regularClient.close();
    }

    // TODO cluster client once implemented
}
