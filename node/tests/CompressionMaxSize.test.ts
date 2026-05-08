/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 *
 * Tests for maxDecompressedSize enforcement.
 *
 * These tests verify that the maxDecompressedSize limit is properly enforced
 * during decompression to prevent decompression bombs.
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
    CompressionConfiguration,
    GlideClient,
    GlideClusterClient,
    ProtocolVersion,
    RequestError,
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

describe("Compression MaxDecompressedSize", () => {
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
    ): Promise<GlideClient | GlideClusterClient> {
        const config: BaseClientConfiguration = getClientConfigurationOption(
            getAddresses(clusterMode),
            ProtocolVersion.RESP3,
            { compression },
        );

        if (clusterMode) {
            return await GlideClusterClient.createClient(config);
        }

        return await GlideClient.createClient(config);
    }

    function uniqueKey(prefix: string): string {
        return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2)}`;
    }

    /**
     * Test that maxDecompressedSize limit is enforced during decompression.
     *
     * When a client with a small maxDecompressedSize tries to decompress a value
     * that exceeds the limit, it should throw a RequestError with a clear message
     * about the size limit being exceeded.
     */
    it.each([false, true])(
        "maxDecompressedSize_enforced_on_get cluster_mode=%p",
        async (clusterMode) => {
            // Step 1: Create a client with compression enabled (default max size - 512MB)
            const unlimitedClient = await createCompressedClient(clusterMode, {
                enabled: true,
            });

            try {
                // Step 2: Set a large compressible value (10KB)
                const key = uniqueKey("max_decomp_test");
                const largeValue = generateCompressibleText(10000); // 10KB

                await unlimitedClient.set(key, largeValue);

                // Step 3: Create a client with a small maxDecompressedSize limit (100 bytes)
                const limitedClient = await createCompressedClient(
                    clusterMode,
                    {
                        enabled: true,
                        maxDecompressedSize: 100, // Only allow 100 bytes decompressed
                    },
                );

                try {
                    // Step 4: GET should throw RequestError with size limit message
                    await expect(limitedClient.get(key)).rejects.toThrow(
                        RequestError,
                    );
                    await expect(limitedClient.get(key)).rejects.toThrow(
                        /size limit exceeded/i,
                    );
                } finally {
                    limitedClient.close();
                }

                // Cleanup
                await unlimitedClient.del([key]);
            } finally {
                unlimitedClient.close();
            }
        },
        TIMEOUT,
    );

    /**
     * Test maxDecompressedSize with MGET command.
     */
    it.each([false, true])(
        "maxDecompressedSize_enforced_on_mget cluster_mode=%p",
        async (clusterMode) => {
            const unlimitedClient = await createCompressedClient(clusterMode, {
                enabled: true,
            });

            try {
                // Set multiple large values
                const keys = [
                    uniqueKey("{mget_max}_1"),
                    uniqueKey("{mget_max}_2"),
                    uniqueKey("{mget_max}_3"),
                ];
                const largeValue = generateCompressibleText(5000); // 5KB each

                for (const key of keys) {
                    await unlimitedClient.set(key, largeValue);
                }

                // Create limited client
                const limitedClient = await createCompressedClient(
                    clusterMode,
                    {
                        enabled: true,
                        maxDecompressedSize: 100,
                    },
                );

                try {
                    // MGET should throw RequestError with size limit message
                    await expect(limitedClient.mget(keys)).rejects.toThrow(
                        RequestError,
                    );
                    await expect(limitedClient.mget(keys)).rejects.toThrow(
                        /size limit exceeded/i,
                    );
                } finally {
                    limitedClient.close();
                }

                // Cleanup
                await unlimitedClient.del(keys);
            } finally {
                unlimitedClient.close();
            }
        },
        TIMEOUT,
    );

    /**
     * Verify that values within the limit work correctly.
     */
    it.each([false, true])(
        "maxDecompressedSize_allows_values_within_limit cluster_mode=%p",
        async (clusterMode) => {
            // Create client with 1KB limit
            client = await createCompressedClient(clusterMode, {
                enabled: true,
                maxDecompressedSize: 1024, // 1KB limit
            });

            const key = uniqueKey("within_limit_test");
            const smallValue = generateCompressibleText(500); // 500 bytes, within limit

            await client.set(key, smallValue);
            const retrieved = await client.get(key);

            expect(retrieved).toBe(smallValue);

            await client.del([key]);
        },
        TIMEOUT,
    );
});
