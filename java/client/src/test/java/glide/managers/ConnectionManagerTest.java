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
import glide.api.models.configuration.IamAuthConfig;
import glide.api.models.configuration.ReadFrom;
import glide.api.models.configuration.ServerCredentials;
import glide.api.models.configuration.ServiceType;
import glide.api.models.exceptions.ConfigurationError;
import glide.api.models.pool.ClientPool;
import glide.api.models.pool.ClientPoolConfig;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

public class ConnectionManagerTest {

    private static final String AZ = "us-east-1a";

    /** Derived from the enum so a strategy added later is covered without editing this file. */
    private static Stream<ReadFrom> azStrategies() {
        return Arrays.stream(ReadFrom.values()).filter(ReadFrom::requiresClientAz);
    }

    private static Stream<ReadFrom> nonAzStrategies() {
        return Arrays.stream(ReadFrom.values()).filter(readFrom -> !readFrom.requiresClientAz());
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

    @ParameterizedTest
    @MethodSource("azStrategies")
    void validateClientAz_rejectsAzStrategyWithoutClientAz(ReadFrom readFrom) {
        ConfigurationError nullAz =
                assertThrows(
                        ConfigurationError.class,
                        () -> ConnectionManager.validateClientAz(config(readFrom, null)));
        assertTrue(nullAz.getMessage().contains("clientAZ must be set"));
        assertTrue(nullAz.getMessage().contains(readFrom.name()));

        assertThrows(
                ConfigurationError.class, () -> ConnectionManager.validateClientAz(config(readFrom, "")));
    }

    /**
     * A whitespace-only AZ is not "set" in any useful sense: the core compares AZs exactly and never
     * trims, so it would engage the strategy, match no node, and silently fall back to routing across
     * all nodes.
     */
    @ParameterizedTest
    @MethodSource("azStrategies")
    void validateClientAz_rejectsWhitespaceOnlyClientAz(ReadFrom readFrom) {
        for (String blank : new String[] {" ", "   ", "\t", "\n", " \t\n "}) {
            assertThrows(
                    ConfigurationError.class,
                    () -> ConnectionManager.validateClientAz(config(readFrom, blank)),
                    "Expected rejection for blank clientAZ " + blank.replace("\n", "\\n"));
        }
    }

    /**
     * A padded AZ is accepted and <em>normalized</em>. Forwarding it raw would satisfy validation and
     * then match no node, because the core compares AZs with exact equality — the same
     * silent-fallback outcome as a blank value, and easily produced by {@code getenv} returning
     * {@code "us-east-1a\n"}.
     */
    @ParameterizedTest
    @MethodSource("azStrategies")
    void validateClientAz_normalizesSurroundingWhitespace(ReadFrom readFrom) {
        GlideClientConfiguration padded = config(readFrom, " " + AZ + "\n");

        assertDoesNotThrow(() -> ConnectionManager.validateClientAz(padded));
        assertEquals(AZ, ConnectionManager.resolveClientAz(padded), "forwarded value must be trimmed");
    }

    /** Blank values resolve to absent, which is what makes the validation above reject them. */
    @Test
    void resolveClientAz_treatsBlankAsAbsent() {
        for (String blank : new String[] {"", " ", "   ", "\t", "\n", " \t\n "}) {
            assertNull(
                    ConnectionManager.resolveClientAz(config(ReadFrom.AZ_AFFINITY_ALL_NODES, blank)),
                    "blank clientAZ must resolve to null: " + blank.replace("\n", "\\n"));
        }
        assertNull(ConnectionManager.resolveClientAz(config(ReadFrom.AZ_AFFINITY_ALL_NODES, null)));
    }

    /**
     * The check runs on the synchronous path in {@code BaseClient.createClient}, so callers see a
     * direct throw rather than an {@code ExecutionException} on the returned future. Asserting on
     * {@code createClient} itself — with no {@code get()} — is what pins that; the throw happens
     * before any connection is attempted, so no server is needed.
     */
    @Test
    void createClient_throwsSynchronouslyWhenAzStrategyHasNoClientAz() {
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

    @ParameterizedTest
    @MethodSource("azStrategies")
    void validateClientAz_acceptsAzStrategyWithClientAz(ReadFrom readFrom) {
        assertDoesNotThrow(() -> ConnectionManager.validateClientAz(config(readFrom, AZ)));
    }

    @ParameterizedTest
    @MethodSource("nonAzStrategies")
    void validateClientAz_ignoresNonAzStrategies(ReadFrom readFrom) {
        assertDoesNotThrow(() -> ConnectionManager.validateClientAz(config(readFrom, null)));
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

    @Test
    void clientPoolCreate_throwsConfigurationErrorWhenAzStrategyHasNoClientAz() {
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
     * The three AZ-affinity strategies, and only those, must require an AZ.
     *
     * <p>{@code requiresClientAz()} lists the AZ strategies by hand, so a strategy added to the enum
     * without being listed there would silently skip {@code clientAZ} validation and let the core
     * downgrade it to {@code PreferReplica}. The count assertion is what surfaces that: a new
     * constant fails here, forcing an explicit answer for it.
     */
    @Test
    void requiresClientAz_isSetForExactlyTheAzStrategies() {
        assertTrue(ReadFrom.AZ_AFFINITY.requiresClientAz());
        assertTrue(ReadFrom.AZ_AFFINITY_REPLICAS_AND_PRIMARY.requiresClientAz());
        assertTrue(ReadFrom.AZ_AFFINITY_ALL_NODES.requiresClientAz());
        assertFalse(ReadFrom.PRIMARY.requiresClientAz());
        assertFalse(ReadFrom.PREFER_REPLICA.requiresClientAz());
        assertFalse(ReadFrom.ALL_NODES.requiresClientAz());

        assertEquals(
                6,
                ReadFrom.values().length,
                "A ReadFrom strategy was added: state whether it requires a clientAZ in"
                        + " ReadFrom.requiresClientAz(), then assert it above and update this count");
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

    /** jitterPercent in the reconnect strategy must survive onto the pooled wire. */
    @Test
    void clientPoolSerialization_carriesReconnectJitterPercent() throws Exception {
        GlideClientConfiguration clientConfig =
                GlideClientConfiguration.builder()
                        .reconnectStrategy(
                                BackoffStrategy.builder()
                                        .numOfRetries(3)
                                        .factor(2)
                                        .exponentBase(2)
                                        .jitterPercent(20)
                                        .build())
                        .build();

        ConnectionRequestOuterClass.ConnectionRequest request =
                ConnectionRequestOuterClass.ConnectionRequest.parseFrom(poolBytes(clientConfig));

        assertEquals(
                20, request.getConnectionRetryStrategy().getJitterPercent(), "pooled jitter_percent");
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
