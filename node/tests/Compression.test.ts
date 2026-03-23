/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import {
    afterAll,
    afterEach,
    beforeAll,
    describe,
    expect,
    it,
} from "@jest/globals";
import { ValkeyCluster } from "../../utils/TestUtils.js";
import {
    BaseClientConfiguration,
    CompressionBackend,
    CompressionConfiguration,
    ConfigurationError,
    Decoder,
    GlideClient,
    GlideClusterClient,
    ProtocolVersion,
    validateCompressionConfiguration,
} from "../build-ts";
import {
    getClientConfigurationOption,
    getServerVersion,
    parseEndpoints,
} from "./TestUtilities";

const TIMEOUT = 30000;

function generateCompressibleText(sizeBytes: number): string {
    const pattern = "A".repeat(10) + "B".repeat(10) + "C".repeat(10);
    const repeats = Math.ceil(sizeBytes / pattern.length);
    return pattern.repeat(repeats).slice(0, sizeBytes);
}

/** getStatistics() returns string values; convert to numbers for assertions. */
function getNumericStats(
    client: GlideClient | GlideClusterClient,
): Record<string, number> {
    const raw = client.getStatistics() as Record<string, string>;
    const result: Record<string, number> = {};

    for (const [k, v] of Object.entries(raw)) {
        result[k] = Number(v);
    }

    return result;
}

describe("Compression", () => {
    let standaloneCluster: ValkeyCluster;
    let clusterCluster: ValkeyCluster;
    let client: GlideClient | GlideClusterClient | undefined;

    beforeAll(async () => {
        const standaloneAddresses = global.STAND_ALONE_ENDPOINT as string;
        standaloneCluster = standaloneAddresses
            ? await ValkeyCluster.initFromExistingCluster(
                  false,
                  parseEndpoints(standaloneAddresses),
                  getServerVersion,
              )
            : await ValkeyCluster.createCluster(false, 1, 1, getServerVersion);

        const clusterAddresses = global.CLUSTER_ENDPOINTS as string;
        clusterCluster = clusterAddresses
            ? await ValkeyCluster.initFromExistingCluster(
                  true,
                  parseEndpoints(clusterAddresses),
                  getServerVersion,
              )
            : await ValkeyCluster.createCluster(true, 3, 1, getServerVersion);
    }, TIMEOUT);

    afterEach(async () => {
        client?.close();
        client = undefined;
    });

    afterAll(async () => {
        await standaloneCluster?.close();
        await clusterCluster?.close();
    }, TIMEOUT);

    function getAddresses(clusterMode: boolean): [string, number][] {
        return (
            clusterMode ? clusterCluster : standaloneCluster
        ).getAddresses();
    }

    async function createCompressedClient(
        clusterMode: boolean,
        compression: CompressionConfiguration,
        protocol: ProtocolVersion = ProtocolVersion.RESP3,
    ): Promise<GlideClient | GlideClusterClient> {
        const config: BaseClientConfiguration = getClientConfigurationOption(
            getAddresses(clusterMode),
            protocol,
            { compression },
        );

        if (clusterMode) {
            return await GlideClusterClient.createClient(config);
        }

        return await GlideClient.createClient(config);
    }

    // --- Configuration validation tests ---

    it(
        "compression_disabled_by_default",
        async () => {
            client = await GlideClient.createClient(
                getClientConfigurationOption(
                    getAddresses(false),
                    ProtocolVersion.RESP3,
                ),
            );
            const stats = getNumericStats(client);
            expect(stats).toHaveProperty("total_values_compressed");
            await client.set("test_key", generateCompressibleText(1024));
            const statsAfter = getNumericStats(client);
            expect(statsAfter.total_values_compressed).toBe(
                stats.total_values_compressed,
            );
        },
        TIMEOUT,
    );

    it("compression_config_invalid_min_size", () => {
        expect(() => {
            validateCompressionConfiguration({
                enabled: true,
                minCompressionSize: 3,
            });
        }).toThrow(ConfigurationError);
    });

    // --- Basic compression tests ---

    it.each([false, true])(
        "compression_basic_set_get cluster_mode=%p",
        async (clusterMode) => {
            client = await createCompressedClient(clusterMode, {
                enabled: true,
            });
            const stats = getNumericStats(client);
            const initialCompressed = stats.total_values_compressed;

            const key = `compression_basic_${Date.now()}`;
            const value = generateCompressibleText(1024);
            await (client as GlideClient).set(key, value);

            const retrieved = await (client as GlideClient).get(key);
            expect(retrieved).toBe(value);

            const statsAfter = getNumericStats(client);
            expect(statsAfter.total_values_compressed).toBeGreaterThan(
                initialCompressed,
            );
        },
        TIMEOUT,
    );

    // --- Statistics tests ---

    it.each([false, true])(
        "compression_statistics cluster_mode=%p",
        async (clusterMode) => {
            client = await createCompressedClient(clusterMode, {
                enabled: true,
            });
            const statsBefore = getNumericStats(client);
            const initialCompressed = statsBefore.total_values_compressed;
            const initialOriginalBytes = statsBefore.total_original_bytes;
            const initialBytesCompressed = statsBefore.total_bytes_compressed;

            const key = `compression_stats_${Date.now()}`;
            const value = generateCompressibleText(10240);
            await (client as GlideClient).set(key, value);

            const statsAfter = getNumericStats(client);
            expect(statsAfter.total_values_compressed).toBeGreaterThan(
                initialCompressed,
            );
            expect(statsAfter.total_original_bytes).toBeGreaterThan(
                initialOriginalBytes,
            );
            expect(statsAfter.total_bytes_compressed).toBeGreaterThan(
                initialBytesCompressed,
            );
            // Compressed should be smaller than original
            const addedOriginal =
                statsAfter.total_original_bytes - initialOriginalBytes;
            const addedCompressed =
                statsAfter.total_bytes_compressed - initialBytesCompressed;
            expect(addedCompressed).toBeLessThanOrEqual(addedOriginal);
        },
        TIMEOUT,
    );

    // --- Min size threshold test ---

    it.each([false, true])(
        "compression_min_size_threshold cluster_mode=%p",
        async (clusterMode) => {
            client = await createCompressedClient(clusterMode, {
                enabled: true,
                minCompressionSize: 256,
            });
            const statsBefore = getNumericStats(client);
            const initialCompressed = statsBefore.total_values_compressed;
            const initialSkipped = statsBefore.compression_skipped_count;

            // Value below threshold — should not compress
            const smallKey = `compression_small_${Date.now()}`;
            await (client as GlideClient).set(smallKey, "A".repeat(100));
            const statsSmall = getNumericStats(client);
            expect(statsSmall.total_values_compressed).toBe(initialCompressed);
            expect(statsSmall.compression_skipped_count).toBeGreaterThan(
                initialSkipped,
            );

            // Value above threshold — should compress
            const largeKey = `compression_large_${Date.now()}`;
            await (client as GlideClient).set(
                largeKey,
                generateCompressibleText(1024),
            );
            const statsLarge = getNumericStats(client);
            expect(statsLarge.total_values_compressed).toBeGreaterThan(
                initialCompressed,
            );
        },
        TIMEOUT,
    );

    // --- Backend tests ---

    it.each([CompressionBackend.ZSTD, CompressionBackend.LZ4])(
        "compression_backend_%p",
        async (backend) => {
            client = await createCompressedClient(false, {
                enabled: true,
                backend,
            });
            const statsBefore = getNumericStats(client);
            const initialCompressed = statsBefore.total_values_compressed;

            const key = `compression_backend_${backend}_${Date.now()}`;
            await (client as GlideClient).set(
                key,
                generateCompressibleText(1024),
            );

            const statsAfter = getNumericStats(client);
            expect(statsAfter.total_values_compressed).toBeGreaterThan(
                initialCompressed,
            );

            const retrieved = await (client as GlideClient).get(key);
            expect(retrieved).toBe(generateCompressibleText(1024));
        },
        TIMEOUT,
    );

    // --- Cross-client read test ---

    it(
        "compression_cross_client_read",
        async () => {
            const compressedClient = await createCompressedClient(false, {
                enabled: true,
            });
            const normalClient = await GlideClient.createClient(
                getClientConfigurationOption(
                    getAddresses(false),
                    ProtocolVersion.RESP3,
                ),
            );

            try {
                const key = `compression_cross_${Date.now()}`;
                const value = generateCompressibleText(1024);

                // Write with compressed client
                await compressedClient.set(key, value);

                // Read with compressed client — should decompress
                const compressedRead = await compressedClient.get(key);
                expect(compressedRead).toBe(value);

                // Read with normal client using Bytes decoder to avoid UTF-8 decoding error
                // on raw compressed data
                const normalRead = await normalClient.get(key, {
                    decoder: Decoder.Bytes,
                });
                // The normal client reads the compressed bytes as-is,
                // which should differ from the original value
                expect(Buffer.isBuffer(normalRead)).toBe(true);
                expect((normalRead as Buffer).toString()).not.toBe(value);
            } finally {
                compressedClient.close();
                normalClient.close();
            }
        },
        TIMEOUT,
    );

    // --- Data types test ---

    it.each([false, true])(
        "compression_data_types cluster_mode=%p",
        async (clusterMode) => {
            client = await createCompressedClient(clusterMode, {
                enabled: true,
            });
            const statsBefore = getNumericStats(client);
            const initialCompressed = statsBefore.total_values_compressed;

            // Test with different data patterns
            const patterns = [
                generateCompressibleText(1024),
                JSON.stringify({
                    id: 12345,
                    name: "Test",
                    data: "A".repeat(500),
                }),
                "<root>" + "<item>data</item>".repeat(50) + "</root>",
            ];

            for (let i = 0; i < patterns.length; i++) {
                const key = `compression_type_${i}_${Date.now()}`;
                await (client as GlideClient).set(key, patterns[i]);
                const retrieved = await (client as GlideClient).get(key);
                expect(retrieved).toBe(patterns[i]);
            }

            const statsAfter = getNumericStats(client);
            expect(statsAfter.total_values_compressed).toBeGreaterThan(
                initialCompressed,
            );
        },
        TIMEOUT,
    );
});
