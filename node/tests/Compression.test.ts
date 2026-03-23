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
const COMPRESSIBLE_PATTERN = "A".repeat(10) + "B".repeat(10) + "C".repeat(10);

function generateCompressibleText(sizeBytes: number): string {
    const repeats = Math.ceil(sizeBytes / COMPRESSIBLE_PATTERN.length);
    return COMPRESSIBLE_PATTERN.repeat(repeats).slice(0, sizeBytes);
}

const TEXT_1K = generateCompressibleText(1024);
const TEXT_10K = generateCompressibleText(10240);

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

/** Set a value and assert that total_values_compressed increased. */
async function setAndExpectCompression(
    client: GlideClient | GlideClusterClient,
    key: string,
    value: string,
): Promise<void> {
    const before = getNumericStats(client).total_values_compressed;
    await client.set(key, value);
    expect(getNumericStats(client).total_values_compressed).toBeGreaterThan(
        before,
    );
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

    function uniqueKey(prefix: string): string {
        return `${prefix}_${Date.now()}`;
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
            const before = getNumericStats(client).total_values_compressed;
            await client.set("test_key", TEXT_1K);
            expect(getNumericStats(client).total_values_compressed).toBe(
                before,
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
            const key = uniqueKey("compression_basic");
            await setAndExpectCompression(client, key, TEXT_1K);
            expect(await client.get(key)).toBe(TEXT_1K);
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
            const before = getNumericStats(client);

            await setAndExpectCompression(
                client,
                uniqueKey("compression_stats"),
                TEXT_10K,
            );

            const after = getNumericStats(client);
            const addedOriginal =
                after.total_original_bytes - before.total_original_bytes;
            const addedCompressed =
                after.total_bytes_compressed - before.total_bytes_compressed;
            expect(addedCompressed).toBeGreaterThan(0);
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
            const before = getNumericStats(client);

            // Value below threshold — should not compress
            await client.set(uniqueKey("small"), "A".repeat(100));
            const statsSmall = getNumericStats(client);
            expect(statsSmall.total_values_compressed).toBe(
                before.total_values_compressed,
            );
            expect(statsSmall.compression_skipped_count).toBeGreaterThan(
                before.compression_skipped_count,
            );

            // Value above threshold — should compress
            await setAndExpectCompression(client, uniqueKey("large"), TEXT_1K);
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
            const key = uniqueKey(`compression_backend_${backend}`);
            await setAndExpectCompression(client, key, TEXT_1K);
            expect(await client.get(key)).toBe(TEXT_1K);
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
                const key = uniqueKey("compression_cross");

                await compressedClient.set(key, TEXT_1K);
                expect(await compressedClient.get(key)).toBe(TEXT_1K);

                // Normal client reads raw compressed bytes — not valid UTF-8
                const normalRead = await normalClient.get(key, {
                    decoder: Decoder.Bytes,
                });
                expect(Buffer.isBuffer(normalRead)).toBe(true);
                expect((normalRead as Buffer).toString()).not.toBe(TEXT_1K);
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
            const patterns = [
                TEXT_1K,
                JSON.stringify({
                    id: 12345,
                    name: "Test",
                    data: "A".repeat(500),
                }),
                "<root>" + "<item>data</item>".repeat(50) + "</root>",
            ];

            for (let i = 0; i < patterns.length; i++) {
                const key = uniqueKey(`compression_type_${i}`);
                await setAndExpectCompression(client, key, patterns[i]);
                expect(await client.get(key)).toBe(patterns[i]);
            }
        },
        TIMEOUT,
    );
});
