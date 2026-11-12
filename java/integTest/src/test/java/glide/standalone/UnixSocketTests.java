/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.standalone;

import static glide.TestUtilities.isWindows;
import static glide.api.BaseClient.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import glide.api.GlideClient;
import glide.api.MonitorClient;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.pool.ClientPool;
import glide.api.models.pool.ClientPoolConfig;
import glide.api.models.pool.PooledGlideClient;
import glide.api.models.scope.IsolatedScope;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Each test's server listens only on a Unix domain socket ({@code --port 0}). */
@Timeout(60)
public class UnixSocketTests {

    private Path directory;
    private String socketPath;
    private String cliBinary;
    private Process server;

    @BeforeEach
    @SneakyThrows
    public void startServer() {
        assumeTrue(!isWindows(), "Unix domain sockets are not supported on Windows");
        String serverBinary = findExecutable("valkey-server", "redis-server");
        cliBinary = findExecutable("valkey-cli", "redis-cli");
        // Unix socket paths are limited to ~104-108 bytes, so avoid long TMPDIRs.
        directory = Files.createTempDirectory(Paths.get("/tmp"), "glide-uds-");
        socketPath = directory.resolve("valkey.sock").toString();
        Path log = directory.resolve("server.log");
        Path output = directory.resolve("server.out");
        server =
                new ProcessBuilder(
                                serverBinary,
                                "--port",
                                "0",
                                "--unixsocket",
                                socketPath,
                                "--unixsocketperm",
                                "700",
                                "--dir",
                                directory.toString(),
                                "--logfile",
                                log.toString(),
                                "--save",
                                "",
                                "--appendonly",
                                "no")
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.to(output.toFile()))
                        .start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!serverAnswersPing()) {
            if (System.nanoTime() > deadline || !server.isAlive()) {
                Path logFile = Files.exists(log) ? log : output;
                String logText = new String(Files.readAllBytes(logFile), StandardCharsets.UTF_8);
                throw new IllegalStateException("server did not listen on " + socketPath + ":\n" + logText);
            }
            Thread.sleep(50);
        }
    }

    @AfterEach
    @SneakyThrows
    public void stopServer() {
        if (server != null) {
            server.destroyForcibly().waitFor();
        }
        if (directory != null) {
            try (Stream<Path> paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
    }

    private static String findExecutable(String... names) {
        for (String name : names) {
            for (String dir : System.getenv("PATH").split(File.pathSeparator)) {
                Path candidate = Paths.get(dir, name);
                if (Files.isExecutable(candidate)) {
                    return candidate.toString();
                }
            }
        }
        throw new IllegalStateException(String.join("/", names) + " is not installed locally");
    }

    /** Pings the server through its socket with the CLI (Java 8 has no Unix socket API). */
    private boolean serverAnswersPing() {
        Process ping = null;
        try {
            ping =
                    new ProcessBuilder(cliBinary, "-s", socketPath, "PING").redirectErrorStream(true).start();
            if (!ping.waitFor(2, TimeUnit.SECONDS)) {
                return false;
            }
            try (BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(ping.getInputStream(), StandardCharsets.UTF_8))) {
                return "PONG".equals(reader.readLine());
            }
        } catch (Exception e) {
            return false;
        } finally {
            if (ping != null) {
                ping.destroyForcibly();
            }
        }
    }

    private GlideClientConfiguration.GlideClientConfigurationBuilder<?, ?> socketConfig() {
        return GlideClientConfiguration.builder()
                .address(NodeAddress.builder().unixSocketPath(socketPath).build())
                .requestTimeout(2000);
    }

    /** Asserts that the server flags the client's connection as a Unix socket client (`U`). */
    @SneakyThrows
    private static String assertUsesUnixSocket(GlideClient client) {
        String info = client.customCommand(new String[] {"CLIENT", "INFO"}).get().toString();
        String flags =
                Arrays.stream(info.split("\\s+"))
                        .filter(field -> field.startsWith("flags="))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("no flags in CLIENT INFO: " + info));
        assertTrue(flags.substring("flags=".length()).contains("U"), info);
        return info;
    }

    @Test
    @SneakyThrows
    public void connectOverUnixSocket() {
        try (GlideClient client =
                GlideClient.createClient(socketConfig().databaseId(2).build()).get()) {
            assertEquals(OK, client.set("uds-key", "uds-value").get());
            assertEquals("uds-value", client.get("uds-key").get());
            String info = assertUsesUnixSocket(client);
            assertTrue(info.contains(" db=2 "), info);
        }
    }

    @Test
    @SneakyThrows
    public void monitorOverUnixSocket() {
        CountDownLatch seen = new CountDownLatch(1);
        String expectedAddr = "unix:" + socketPath;
        try (MonitorClient monitor =
                        MonitorClient.create(
                                socketConfig().build(),
                                msg -> {
                                    if ("SET".equalsIgnoreCase(msg.getCommand())
                                            && expectedAddr.equals(msg.getClientAddr())) {
                                        seen.countDown();
                                    }
                                });
                GlideClient client = GlideClient.createClient(socketConfig().build()).get()) {
            client.set("uds-monitor-key", "value").get();
            assertTrue(seen.await(15, TimeUnit.SECONDS), "SET over the socket not seen by MONITOR");
        }
    }

    @Test
    @SneakyThrows
    public void clientPoolOverUnixSocket() {
        try (ClientPool pool =
                        ClientPool.create(
                                ClientPoolConfig.builder()
                                        .maxSize(1)
                                        .minIdle(1)
                                        .acquireTimeout(Duration.ofSeconds(10))
                                        .clientConfig(socketConfig().build())
                                        .build());
                PooledGlideClient pooled = pool.acquire().get(10, TimeUnit.SECONDS)) {
            assertEquals(OK, pooled.set("uds-pool-key", "pooled").get());
            assertUsesUnixSocket(pooled.unwrap());
        }
    }

    @Test
    @SneakyThrows
    public void scopedConnectionOverUnixSocket() {
        try (GlideClient client = GlideClient.createClient(socketConfig().build()).get()) {
            try (IsolatedScope scope =
                    client.scopedConnection(Duration.ofSeconds(10)).get(10, TimeUnit.SECONDS)) {
                scope.set("uds-scope-key", "scoped").get(5, TimeUnit.SECONDS);
                assertEquals("scoped", scope.get("uds-scope-key").get(5, TimeUnit.SECONDS));
            }
            assertEquals("scoped", client.get("uds-scope-key").get());
        }
    }
}
