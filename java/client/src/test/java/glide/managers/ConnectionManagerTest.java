/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import static org.junit.jupiter.api.Assertions.*;

import connection_request.ConnectionRequestOuterClass;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.ReadFrom;
import glide.api.models.exceptions.ConfigurationError;
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
}
