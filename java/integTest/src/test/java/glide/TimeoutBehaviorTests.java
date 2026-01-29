package glide;

import static glide.TestUtilities.commonClientConfig;
import static glide.TestUtilities.commonClusterClientConfig;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Named.named;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Timeout(20) // seconds
public class TimeoutBehaviorTests {

    private static Stream<Arguments> timeoutClients() {
        return Stream.of(
                Arguments.of(named("standalone", false)),
                Arguments.of(named("cluster", true)));
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("timeoutClients")
    public void request_timeout_fires_before_server_timeout(boolean clusterMode) {
        int requestTimeoutMs = 200;
        double serverTimeoutSeconds = 5.0;

        BaseClient client =
                clusterMode
                        ? GlideClusterClient.createClient(
                                        commonClusterClientConfig()
                                                .requestTimeout(requestTimeoutMs)
                                                .build())
                                .get()
                        : GlideClient.createClient(
                                        commonClientConfig().requestTimeout(requestTimeoutMs).build())
                                .get();

        String key = "{timeout}" + UUID.randomUUID();

        try {
            long start = System.nanoTime();
            ExecutionException exception =
                    assertThrows(
                            ExecutionException.class,
                            () -> client.blpop(new String[] {key}, serverTimeoutSeconds).get());
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertTrue(
                    isTimeout(exception.getCause()),
                    "Expected timeout, got: " + exception.getCause());
            assertTrue(
                    elapsedMs < (long) (serverTimeoutSeconds * 1000),
                    "Timeout should happen before server response. elapsedMs=" + elapsedMs);
            assertTrue(
                    elapsedMs <= requestTimeoutMs + 1000,
                    "Timeout exceeded client deadline. elapsedMs=" + elapsedMs);

        } finally {
            client.close();
        }
    }

    private static boolean isTimeout(Throwable cause) {
        return cause instanceof TimeoutException
                || cause instanceof glide.api.models.exceptions.TimeoutException;
    }
}
