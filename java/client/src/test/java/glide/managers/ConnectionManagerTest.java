/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import static org.junit.jupiter.api.Assertions.*;

import connection_request.ConnectionRequestOuterClass;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.configuration.AddressResolver;
import glide.api.models.configuration.BackoffStrategy;
import glide.api.models.configuration.BaseClientConfiguration;
import glide.api.models.configuration.ClientCircuitBreakerConfiguration;
import glide.api.models.configuration.CompressionBackend;
import glide.api.models.configuration.CompressionConfiguration;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.GlideCredentialProvider;
import glide.api.models.configuration.IamAuthConfig;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.ReadFrom;
import glide.api.models.configuration.ServerCredentials;
import glide.api.models.configuration.ServiceType;
import glide.api.models.exceptions.ConfigurationError;
import glide.api.models.pool.ClientPool;
import glide.api.models.pool.ClientPoolConfig;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class ConnectionManagerTest {

    private static final String AZ = "us-east-1a";

    /**
     * The AZ-scoped strategies, which all use {@code clientAZ}. {@link
     * #requiresClientAz_isSetForExactlyAzAffinityAllNodes} fails when a strategy is added, forcing it
     * into this list or {@link #nonAzStrategies}.
     */
    private static Stream<ReadFrom> azStrategies() {
        return Stream.of(
                ReadFrom.AZ_AFFINITY,
                ReadFrom.AZ_AFFINITY_REPLICAS_AND_PRIMARY,
                ReadFrom.AZ_AFFINITY_ALL_NODES);
    }

    /**
     * The AZ strategies the core downgrades to {@code PreferReplica} when no AZ is given. Listed by
     * hand, not derived: 2.5.2 accepted these without an AZ, so 2.x must keep accepting them.
     */
    private static Stream<ReadFrom> downgradedAzStrategies() {
        return Stream.of(ReadFrom.AZ_AFFINITY, ReadFrom.AZ_AFFINITY_REPLICAS_AND_PRIMARY);
    }

    /** Missing in every form a caller can produce: unset, empty, or whitespace only. */
    private static final String[] MISSING_AZ = {null, "", " ", "   ", "\t", "\n", " \t\n "};

    private static String label(ReadFrom readFrom, String clientAz) {
        return readFrom
                + " with clientAZ "
                + (clientAz == null ? "null" : "'" + clientAz.replace("\n", "\\n") + "'");
    }

    /** A localhost address with nothing listening, so a connection attempt fails fast. */
    private static NodeAddress unusedAddress() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return NodeAddress.builder().host("localhost").port(socket.getLocalPort()).build();
        }
    }

    private static Stream<ReadFrom> nonAzStrategies() {
        return Stream.of(ReadFrom.PRIMARY, ReadFrom.PREFER_REPLICA, ReadFrom.ALL_NODES);
    }

    private static GlideClientConfiguration config(ReadFrom readFrom, String clientAz) {
        return GlideClientConfiguration.builder().readFrom(readFrom).clientAZ(clientAz).build();
    }

    @Test
    void mapReadFrom_mapsEveryStrategyToItsProtobufCounterpart() {
        assertEquals(
                ConnectionRequestOuterClass.ReadFrom.Primary,
                ConnectionManager.mapReadFrom(ReadFrom.PRIMARY));
        assertEquals(
                ConnectionRequestOuterClass.ReadFrom.PreferReplica,
                ConnectionManager.mapReadFrom(ReadFrom.PREFER_REPLICA));
        assertEquals(
                ConnectionRequestOuterClass.ReadFrom.AZAffinity,
                ConnectionManager.mapReadFrom(ReadFrom.AZ_AFFINITY));
        assertEquals(
                ConnectionRequestOuterClass.ReadFrom.AZAffinityReplicasAndPrimary,
                ConnectionManager.mapReadFrom(ReadFrom.AZ_AFFINITY_REPLICAS_AND_PRIMARY));
        assertEquals(
                ConnectionRequestOuterClass.ReadFrom.AllNodes,
                ConnectionManager.mapReadFrom(ReadFrom.ALL_NODES));
        assertEquals(
                ConnectionRequestOuterClass.ReadFrom.AZAffinityAllNodes,
                ConnectionManager.mapReadFrom(ReadFrom.AZ_AFFINITY_ALL_NODES));
    }

    /**
     * ALL_NODES is AZ-agnostic (proto value 5) while AZ_AFFINITY_ALL_NODES is AZ-scoped (proto value
     * 6). Pinning the wire numbers keeps the two from ever being swapped.
     */
    @Test
    void mapReadFrom_doesNotConflateAllNodesWithAzAffinityAllNodes() {
        assertEquals(5, ConnectionManager.mapReadFrom(ReadFrom.ALL_NODES).getNumber());
        assertEquals(6, ConnectionManager.mapReadFrom(ReadFrom.AZ_AFFINITY_ALL_NODES).getNumber());
    }

    /**
     * Guards the silent-default hazard the old string-matching chain had: an unmapped strategy throws
     * from {@code mapReadFrom} rather than quietly leaving the protobuf field at {@code Primary}.
     */
    @ParameterizedTest
    @EnumSource(ReadFrom.class)
    void mapReadFrom_hasAMappingForEveryStrategy(ReadFrom readFrom) {
        assertDoesNotThrow(() -> ConnectionManager.mapReadFrom(readFrom));
    }

    /**
     * The request builders forward an AZ strategy without a usable {@code clientAZ} as the strategy
     * with an empty {@code client_az}, on both the direct and the pooled path. The core then warns
     * and downgrades it. Rejection, where it applies, is {@link ConnectionManager#validateClientAz}'s
     * job, not the builders'.
     */
    @ParameterizedTest
    @MethodSource("azStrategies")
    void azStrategyWithoutClientAz_isSentWithoutClientAz(ReadFrom readFrom) throws Exception {
        for (String missing : MISSING_AZ) {
            GlideClientConfiguration config = config(readFrom, missing);
            String label = label(readFrom, missing);

            ConnectionRequestOuterClass.ConnectionRequest direct =
                    assertDoesNotThrow(() -> ConnectionManager.buildConnectionRequest(config), label);
            ConnectionRequestOuterClass.ConnectionRequest pooled =
                    ConnectionRequestOuterClass.ConnectionRequest.parseFrom(poolBytes(config));

            for (ConnectionRequestOuterClass.ConnectionRequest request :
                    new ConnectionRequestOuterClass.ConnectionRequest[] {direct, pooled}) {
                assertEquals(ConnectionManager.mapReadFrom(readFrom), request.getReadFrom(), label);
                assertEquals("", request.getClientAz(), label);
            }
        }
    }

    /**
     * 2.5.2 accepted these strategies without an AZ (the core warns and downgrades them to {@code
     * PreferReplica}), and existing 2.x clients rely on it, so validation must let them through.
     */
    @ParameterizedTest
    @MethodSource("downgradedAzStrategies")
    void validateClientAz_acceptsDowngradedStrategiesWithoutClientAz(ReadFrom readFrom) {
        for (String missing : MISSING_AZ) {
            assertDoesNotThrow(
                    () -> ConnectionManager.validateClientAz(config(readFrom, missing)),
                    label(readFrom, missing));
        }
    }

    /**
     * {@code AZ_AFFINITY_ALL_NODES} has thrown without an AZ since it was added, so no client depends
     * on it working without one, and keeping the throw breaks nobody.
     */
    @Test
    void validateClientAz_rejectsAzAffinityAllNodesWithoutClientAz() {
        for (String missing : MISSING_AZ) {
            ConfigurationError error =
                    assertThrows(
                            ConfigurationError.class,
                            () ->
                                    ConnectionManager.validateClientAz(
                                            config(ReadFrom.AZ_AFFINITY_ALL_NODES, missing)),
                            label(ReadFrom.AZ_AFFINITY_ALL_NODES, missing));
            assertTrue(error.getMessage().contains("clientAZ must be set"));
            assertTrue(error.getMessage().contains(ReadFrom.AZ_AFFINITY_ALL_NODES.name()));
        }
    }

    @ParameterizedTest
    @MethodSource("azStrategies")
    void validateClientAz_acceptsAzStrategyWithClientAz(ReadFrom readFrom) {
        assertDoesNotThrow(() -> ConnectionManager.validateClientAz(config(readFrom, AZ)));
        assertDoesNotThrow(() -> ConnectionManager.validateClientAz(config(readFrom, " " + AZ + "\n")));
    }

    @ParameterizedTest
    @MethodSource("nonAzStrategies")
    void validateClientAz_ignoresNonAzStrategies(ReadFrom readFrom) {
        assertDoesNotThrow(() -> ConnectionManager.validateClientAz(config(readFrom, null)));
    }

    /**
     * Pins the direct path end to end: {@code createClient} returns a future instead of throwing, and
     * the future fails only because nothing is listening, never with a {@code ConfigurationError}.
     * Fails if {@code validateClientAz} rejects these strategies again, or if the check moves into
     * the async path.
     */
    @ParameterizedTest
    @MethodSource("downgradedAzStrategies")
    void createClient_acceptsDowngradedStrategyWithoutClientAz(ReadFrom readFrom) throws Exception {
        NodeAddress address = unusedAddress();
        assertNotConfigurationError(
                assertDoesNotThrow(
                        () ->
                                GlideClient.createClient(
                                        GlideClientConfiguration.builder().address(address).readFrom(readFrom).build()),
                        "standalone " + readFrom),
                "standalone " + readFrom);
        assertNotConfigurationError(
                assertDoesNotThrow(
                        () ->
                                GlideClusterClient.createClient(
                                        GlideClusterClientConfiguration.builder()
                                                .address(address)
                                                .readFrom(readFrom)
                                                .clientAZ("   ")
                                                .build()),
                        "cluster " + readFrom),
                "cluster " + readFrom);
    }

    private static void assertNotConfigurationError(CompletableFuture<?> future, String label)
            throws Exception {
        try {
            Object client = future.get(30, TimeUnit.SECONDS);
            ((AutoCloseable) client).close();
        } catch (ExecutionException e) {
            assertFalse(
                    e.getCause() instanceof ConfigurationError,
                    label + " must not fail with ConfigurationError: " + e.getCause());
        }
    }

    /**
     * The check runs on the synchronous path in {@code BaseClient.createClient}, so callers see a
     * direct throw rather than an {@code ExecutionException} on the returned future. Asserting on
     * {@code createClient} itself, with no {@code get()}, is what pins that. The throw happens before
     * any connection is attempted, so no server is needed.
     */
    @Test
    void createClient_throwsSynchronouslyForAzAffinityAllNodesWithoutClientAz() {
        assertThrows(
                ConfigurationError.class,
                () ->
                        GlideClient.createClient(
                                GlideClientConfiguration.builder()
                                        .readFrom(ReadFrom.AZ_AFFINITY_ALL_NODES)
                                        .build()));
        assertThrows(
                ConfigurationError.class,
                () ->
                        GlideClusterClient.createClient(
                                GlideClusterClientConfiguration.builder()
                                        .readFrom(ReadFrom.AZ_AFFINITY_ALL_NODES)
                                        .clientAZ("   ")
                                        .build()));
    }

    /**
     * Pins the pooled path: with nothing listening, {@code ClientPool.create} fails on its
     * connectivity probe, not with a {@code ConfigurationError} from validation.
     */
    @ParameterizedTest
    @MethodSource("downgradedAzStrategies")
    void clientPoolCreate_acceptsDowngradedStrategyWithoutClientAz(ReadFrom readFrom)
            throws Exception {
        ClientPoolConfig poolConfig =
                ClientPoolConfig.builder()
                        .clientConfig(
                                GlideClientConfiguration.builder()
                                        .address(unusedAddress())
                                        .readFrom(readFrom)
                                        .build())
                        .build();

        RuntimeException error =
                assertThrows(RuntimeException.class, () -> ClientPool.create(poolConfig));
        assertFalse(error instanceof ConfigurationError, readFrom + ": " + error);
        assertFalse(error.getCause() instanceof ConfigurationError, readFrom + ": " + error.getCause());
    }

    @Test
    void clientPoolCreate_throwsConfigurationErrorForAzAffinityAllNodesWithoutClientAz() {
        // ClientPool.create validates before its connectivity probe, so a static-config mistake
        // surfaces as ConfigurationError naming the real reason. Without that ordering the throw lands
        // in the probe's catch (Exception) and is relabelled "Pool connectivity probe failed".
        ClientPoolConfig poolConfig =
                ClientPoolConfig.builder()
                        .clientConfig(
                                GlideClientConfiguration.builder().readFrom(ReadFrom.AZ_AFFINITY_ALL_NODES).build())
                        .build();

        ConfigurationError error =
                assertThrows(ConfigurationError.class, () -> ClientPool.create(poolConfig));
        assertTrue(error.getMessage().contains("clientAZ must be set"));
    }

    /**
     * A padded AZ is <em>normalized</em>. Forwarding it raw would engage the strategy and then match
     * no node, because the core compares AZs with exact equality, and {@code getenv} easily returns
     * {@code "us-east-1a\n"}.
     */
    @ParameterizedTest
    @MethodSource("azStrategies")
    void resolveClientAz_trimsSurroundingWhitespace(ReadFrom readFrom) {
        GlideClientConfiguration padded = config(readFrom, " " + AZ + "\n");

        assertEquals(AZ, ConnectionManager.resolveClientAz(padded), "forwarded value must be trimmed");
        assertEquals(AZ, ConnectionManager.buildConnectionRequest(padded).getClientAz());
    }

    /**
     * Blank values resolve to absent, so the core sees no AZ rather than one that matches no node.
     */
    @Test
    void resolveClientAz_treatsBlankAsAbsent() {
        for (String missing : MISSING_AZ) {
            assertNull(
                    ConnectionManager.resolveClientAz(config(ReadFrom.AZ_AFFINITY_ALL_NODES, missing)),
                    label(ReadFrom.AZ_AFFINITY_ALL_NODES, missing) + " must resolve to null");
        }
    }

    /**
     * Reflects {@code ClientPool.serializeConnectionRequest} and asserts on the parsed protobuf, so
     * the pooled path is pinned on the wire rather than through the switch it happens to call today.
     *
     * <p>Without this, dropping the {@code setClientAz} block or reverting to a local string match in
     * {@code ClientPool} both leave the suite green while pooled clients silently read from the
     * primary — the bug this PR fixed. Enum-driven so a strategy added later is covered
     * automatically.
     */
    @ParameterizedTest
    @EnumSource(ReadFrom.class)
    void clientPoolSerialization_carriesReadFromAndClientAzForEveryStrategy(ReadFrom readFrom)
            throws Exception {
        // Padded on purpose: this pins the mapping and the trimming on the pooled path at once.
        GlideClientConfiguration clientConfig =
                GlideClientConfiguration.builder().readFrom(readFrom).clientAZ(" " + AZ + " ").build();

        Method serialize =
                ClientPool.class.getDeclaredMethod(
                        "serializeConnectionRequest", BaseClientConfiguration.class);
        serialize.setAccessible(true);
        ConnectionRequestOuterClass.ConnectionRequest request =
                ConnectionRequestOuterClass.ConnectionRequest.parseFrom(
                        (byte[]) serialize.invoke(null, clientConfig));

        assertEquals(
                ConnectionManager.mapReadFrom(readFrom),
                request.getReadFrom(),
                "pooled read_from for " + readFrom);
        assertEquals(AZ, request.getClientAz(), "pooled client_az for " + readFrom);
    }

    /**
     * Only {@code AZ_AFFINITY_ALL_NODES} rejects a missing AZ. The other two AZ strategies fall back
     * to {@code PreferReplica}, which 2.x clients rely on.
     *
     * <p>{@code requiresClientAz()} drives {@code validateClientAz} and lists strategies by hand. The
     * count assertion keeps it, {@link #azStrategies} and {@link #nonAzStrategies} exhaustive: a new
     * constant fails here, forcing an explicit answer for it.
     */
    @Test
    void requiresClientAz_isSetForExactlyAzAffinityAllNodes() {
        assertTrue(ReadFrom.AZ_AFFINITY_ALL_NODES.requiresClientAz());
        assertFalse(ReadFrom.AZ_AFFINITY.requiresClientAz());
        assertFalse(ReadFrom.AZ_AFFINITY_REPLICAS_AND_PRIMARY.requiresClientAz());
        assertFalse(ReadFrom.PRIMARY.requiresClientAz());
        assertFalse(ReadFrom.PREFER_REPLICA.requiresClientAz());
        assertFalse(ReadFrom.ALL_NODES.requiresClientAz());

        assertEquals(
                6,
                ReadFrom.values().length,
                "A ReadFrom strategy was added: state whether a missing clientAZ is an error in"
                        + " ReadFrom.requiresClientAz(), add it to azStrategies() or nonAzStrategies(),"
                        + " then assert it above and update this count");
    }

    private static byte[] poolBytes(BaseClientConfiguration config) throws Exception {
        Method serialize =
                ClientPool.class.getDeclaredMethod(
                        "serializeConnectionRequest", BaseClientConfiguration.class);
        serialize.setAccessible(true);
        return (byte[]) serialize.invoke(null, config);
    }

    /**
     * Pins the circuit-breaker config onto the pooled wire. Before the fix {@code
     * serializeConnectionRequest} never set {@code client_circuit_breaker}, so a pooled client would
     * never fail fast; this decodes the produced request and asserts every field survives, which
     * fails on the pre-fix serializer.
     */
    @Test
    void clientPoolSerialization_carriesCircuitBreakerConfig() throws Exception {
        ClientCircuitBreakerConfiguration cb =
                ClientCircuitBreakerConfiguration.builder()
                        .windowSizeMs(2000)
                        .failureRateThreshold(0.25f)
                        .minErrors(7)
                        .openTimeoutMs(3000)
                        .countTimeouts(true)
                        .consecutiveSuccesses(4)
                        .build();
        GlideClientConfiguration clientConfig =
                GlideClientConfiguration.builder().clientCircuitBreakerConfiguration(cb).build();

        ConnectionRequestOuterClass.ConnectionRequest request =
                ConnectionRequestOuterClass.ConnectionRequest.parseFrom(poolBytes(clientConfig));

        assertTrue(request.hasClientCircuitBreaker(), "pooled client_circuit_breaker present");
        ConnectionRequestOuterClass.ClientCircuitBreakerConfig wire = request.getClientCircuitBreaker();
        assertEquals(2000, wire.getWindowSizeMs());
        assertEquals(0.25f, wire.getFailureRateThreshold());
        assertEquals(7, wire.getMinErrors());
        assertEquals(3000, wire.getOpenTimeoutMs());
        assertTrue(wire.getCountTimeouts());
        assertEquals(4, wire.getConsecutiveSuccesses());
    }

    /**
     * The pool must reject an invalid circuit-breaker config the same way a directly-created client
     * does, rather than silently emitting an out-of-range value.
     */
    @Test
    void clientPoolSerialization_rejectsInvalidCircuitBreakerConfig() {
        GlideClientConfiguration clientConfig =
                GlideClientConfiguration.builder()
                        .clientCircuitBreakerConfiguration(
                                ClientCircuitBreakerConfiguration.builder().failureRateThreshold(1.5f).build())
                        .build();

        Exception error = assertThrows(Exception.class, () -> poolBytes(clientConfig));
        // Reflection wraps the IllegalArgumentException in an InvocationTargetException.
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        assertTrue(cause instanceof IllegalArgumentException, "cause: " + cause);
        assertTrue(cause.getMessage().contains("failureRateThreshold"));
    }

    /** lazyConnect must survive onto the pooled wire; before the fix it was dropped. */
    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void clientPoolSerialization_carriesLazyConnect(boolean lazy) throws Exception {
        GlideClientConfiguration clientConfig =
                GlideClientConfiguration.builder().lazyConnect(lazy).build();

        ConnectionRequestOuterClass.ConnectionRequest request =
                ConnectionRequestOuterClass.ConnectionRequest.parseFrom(poolBytes(clientConfig));

        assertEquals(lazy, request.getLazyConnect(), "pooled lazy_connect");
    }

    /**
     * jitterPercent in the reconnect strategy, bounds included, must reach the pooled wire as set,
     * matching a directly-created client, rather than falling back to the core default of 20.
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 20, 100})
    void clientPoolSerialization_carriesReconnectJitterPercent(int jitter) throws Exception {
        GlideClientConfiguration clientConfig =
                GlideClientConfiguration.builder()
                        .reconnectStrategy(validBackoff().jitterPercent(jitter).build())
                        .build();

        ConnectionRequestOuterClass.ConnectionRetryStrategy pooled =
                ConnectionRequestOuterClass.ConnectionRequest.parseFrom(poolBytes(clientConfig))
                        .getConnectionRetryStrategy();

        assertTrue(pooled.hasJitterPercent(), "pooled jitter_percent present");
        assertEquals(jitter, pooled.getJitterPercent(), "pooled jitter_percent");
        assertEquals(
                ConnectionManager.buildConnectionRequest(clientConfig).getConnectionRetryStrategy(),
                pooled,
                "pooled retry strategy matches direct");
    }

    /** An all-zero strategy is sent as set, so the client makes 0 growing retries like the others. */
    @Test
    void buildConnectionRequest_sendsAllZeroReconnectStrategy() throws Exception {
        GlideClientConfiguration clientConfig =
                GlideClientConfiguration.builder()
                        .reconnectStrategy(
                                BackoffStrategy.builder().numOfRetries(0).factor(0).exponentBase(0).build())
                        .build();

        ConnectionRequestOuterClass.ConnectionRequest request =
                ConnectionManager.buildConnectionRequest(clientConfig);

        assertTrue(request.hasConnectionRetryStrategy(), "all-zero reconnect strategy is sent");
        ConnectionRequestOuterClass.ConnectionRetryStrategy strategy =
                request.getConnectionRetryStrategy();
        assertEquals(0, strategy.getNumberOfRetries(), "number_of_retries");
        assertEquals(0, strategy.getFactor(), "factor");
        assertEquals(0, strategy.getExponentBase(), "exponent_base");
    }

    private static BackoffStrategy.BackoffStrategyBuilder validBackoff() {
        return BackoffStrategy.builder().numOfRetries(3).factor(2).exponentBase(2);
    }

    static Stream<Arguments> outOfRangeReconnectStrategies() {
        return Stream.of(
                Arguments.of("numOfRetries", -1, validBackoff().numOfRetries(-1)),
                Arguments.of("factor", -1, validBackoff().factor(-1)),
                Arguments.of("exponentBase", -1, validBackoff().exponentBase(-1)),
                Arguments.of("jitterPercent", -1, validBackoff().jitterPercent(-1)),
                Arguments.of("jitterPercent", 101, validBackoff().jitterPercent(101)));
    }

    /**
     * An out-of-range reconnect value is rejected with a ConfigurationError naming the field, on both
     * the direct and the pooled path, instead of wrapping to a huge unsigned value or being dropped.
     */
    @ParameterizedTest
    @MethodSource("outOfRangeReconnectStrategies")
    void buildConnectionRequest_rejectsOutOfRangeReconnectStrategy(
            String field, int value, BackoffStrategy.BackoffStrategyBuilder strategy) {
        GlideClientConfiguration clientConfig =
                GlideClientConfiguration.builder().reconnectStrategy(strategy.build()).build();
        int max = "jitterPercent".equals(field) ? 100 : Integer.MAX_VALUE;
        String expected =
                "invalid reconnect strategy: " + field + " must be between 0 and " + max + ", got " + value;

        ConfigurationError direct =
                assertThrows(
                        ConfigurationError.class, () -> ConnectionManager.buildConnectionRequest(clientConfig));
        assertEquals(expected, direct.getMessage());

        Exception pooled = assertThrows(Exception.class, () -> poolBytes(clientConfig));
        // Reflection wraps the ConfigurationError in an InvocationTargetException.
        Throwable cause = pooled.getCause() != null ? pooled.getCause() : pooled;
        assertTrue(cause instanceof ConfigurationError, "cause: " + cause);
        assertEquals(expected, cause.getMessage());
    }

    /**
     * Locks in the deduplication fix: pooled serialization now delegates to the one shared {@code
     * ConnectionManager.buildConnectionRequest}, so a field the old hand-copied pool serializer never
     * mirrored — here compression — reaches the pooled wire. Fails on the pre-extraction copy, which
     * omitted the whole compression block. Stand-in for every field the copy dropped (IAM, mTLS,
     * cluster topology, client-side cache): if the pool ever forks its own serializer again, this
     * regresses.
     */
    @Test
    void clientPoolSerialization_carriesCompressionConfig() throws Exception {
        GlideClientConfiguration clientConfig =
                GlideClientConfiguration.builder()
                        .compressionConfiguration(
                                CompressionConfiguration.builder()
                                        .enabled(true)
                                        .backend(CompressionBackend.LZ4)
                                        .minCompressionSize(128)
                                        .build())
                        .build();

        ConnectionRequestOuterClass.ConnectionRequest request =
                ConnectionRequestOuterClass.ConnectionRequest.parseFrom(poolBytes(clientConfig));

        assertTrue(request.hasCompressionConfig(), "pooled compression_config present");
        assertTrue(request.getCompressionConfig().getEnabled());
        assertEquals(
                ConnectionRequestOuterClass.CompressionBackend.LZ4,
                request.getCompressionConfig().getBackend());
        assertEquals(128, request.getCompressionConfig().getMinCompressionSize());
    }

    @Test
    void buildConnectionRequest_doesNotSerializeJavaCredentialProviderAsKey() {
        GlideCredentialProvider provider = () -> null;
        GlideClientConfiguration clientConfig =
                GlideClientConfiguration.builder()
                        .credentials(
                                ServerCredentials.builder()
                                        .username("user")
                                        .iamConfig(
                                                IamAuthConfig.builder()
                                                        .clusterName("my-cluster")
                                                        .service(ServiceType.ELASTICACHE)
                                                        .region("us-east-1")
                                                        .credentialsProvider(provider)
                                                        .build())
                                        .build())
                        .build();

        ConnectionRequestOuterClass.ConnectionRequest request =
                ConnectionManager.buildConnectionRequest(clientConfig);

        assertTrue(request.getAuthenticationInfo().hasIamCredentials());
        assertFalse(request.hasCredentialProviderKey());
    }

    /**
     * A pool cannot forward the per-client IAM credentials-provider callback ({@code glidePoolCreate}
     * takes only request bytes), so {@code ClientPool.create} must reject it up front rather than let
     * the core silently fall back to its default AWS credential chain (a different principal). Throws
     * synchronously, before any native call.
     */
    @Test
    void clientPoolCreate_rejectsCustomIamCredentialsProvider() {
        GlideCredentialProvider provider = () -> null; // never invoked; the guard throws first
        ClientPoolConfig poolConfig =
                ClientPoolConfig.builder()
                        .clientConfig(
                                GlideClientConfiguration.builder()
                                        .credentials(
                                                ServerCredentials.builder()
                                                        .username("user")
                                                        .iamConfig(
                                                                IamAuthConfig.builder()
                                                                        .clusterName("my-cluster")
                                                                        .service(ServiceType.ELASTICACHE)
                                                                        .region("us-east-1")
                                                                        .credentialsProvider(provider)
                                                                        .build())
                                                        .build())
                                        .build())
                        .build();

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> ClientPool.create(poolConfig));
        assertTrue(error.getMessage().contains("custom IAM credentials provider"), error.getMessage());
    }

    /**
     * A pool cannot forward the per-client address-resolver callback either. The connectivity probe
     * runs the resolver and can pass, but pooled connections would use the untranslated address and
     * fail — so {@code ClientPool.create} must reject it up front. Throws synchronously, before any
     * native call.
     */
    @Test
    void clientPoolCreate_rejectsCustomAddressResolver() {
        AddressResolver resolver = (host, port) -> null; // never invoked; the guard throws first
        ClientPoolConfig poolConfig =
                ClientPoolConfig.builder()
                        .clientConfig(GlideClientConfiguration.builder().addressResolver(resolver).build())
                        .build();

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> ClientPool.create(poolConfig));
        assertTrue(error.getMessage().contains("custom address resolver"), error.getMessage());
    }
}
