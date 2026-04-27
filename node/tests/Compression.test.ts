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
    ClusterTransaction,
    CompressionBackend,
    CompressionConfiguration,
    ConfigurationError,
    Decoder,
    GlideClient,
    GlideClusterClient,
    ProtocolVersion,
    RequestError,
    TimeUnit,
    Transaction,
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

    // --- Supported commands tests ---

    it.each([false, true])(
        "compression_mset_mget cluster_mode=%p",
        async (clusterMode) => {
            client = await createCompressedClient(clusterMode, {
                enabled: true,
            });
            const key1 = uniqueKey("{mset_test}_1");
            const key2 = uniqueKey("{mset_test}_2");
            const key3 = uniqueKey("{mset_test}_3");

            const before = getNumericStats(client).total_values_compressed;

            // MSET should compress values
            await client.mset({
                [key1]: TEXT_1K,
                [key2]: TEXT_1K,
                [key3]: TEXT_1K,
            });

            expect(
                getNumericStats(client).total_values_compressed,
            ).toBeGreaterThanOrEqual(before + 3);

            // MGET should decompress values
            const retrieved = await client.mget([key1, key2, key3]);
            expect(retrieved).toEqual([TEXT_1K, TEXT_1K, TEXT_1K]);
        },
        TIMEOUT,
    );

    it.each([false, true])(
        "compression_msetnx cluster_mode=%p",
        async (clusterMode) => {
            client = await createCompressedClient(clusterMode, {
                enabled: true,
            });
            const key1 = uniqueKey("{msetnx_test}_1");
            const key2 = uniqueKey("{msetnx_test}_2");

            const before = getNumericStats(client).total_values_compressed;

            // MSETNX should compress values
            const result = await client.msetnx({
                [key1]: TEXT_1K,
                [key2]: TEXT_1K,
            });
            expect(result).toBe(true);

            expect(
                getNumericStats(client).total_values_compressed,
            ).toBeGreaterThanOrEqual(before + 2);

            // Verify values can be retrieved and decompressed
            expect(await client.get(key1)).toBe(TEXT_1K);
            expect(await client.get(key2)).toBe(TEXT_1K);
        },
        TIMEOUT,
    );

    it.each([false, true])(
        "compression_getex cluster_mode=%p",
        async (clusterMode) => {
            client = await createCompressedClient(clusterMode, {
                enabled: true,
            });
            const key = uniqueKey("getex_test");

            // Set value (should be compressed)
            const compressBefore =
                getNumericStats(client).total_values_compressed;
            await client.set(key, TEXT_1K);
            expect(
                getNumericStats(client).total_values_compressed,
            ).toBeGreaterThan(compressBefore);

            // GETEX should decompress value
            const decompressBefore =
                getNumericStats(client).total_values_decompressed;
            const retrieved = await client.getex(key, {
                expiry: { type: TimeUnit.Seconds, duration: 10 },
            });
            expect(retrieved).toBe(TEXT_1K);
            expect(
                getNumericStats(client).total_values_decompressed,
            ).toBeGreaterThan(decompressBefore);

            // Verify TTL was set
            const ttl = await client.ttl(key);
            expect(ttl).toBeGreaterThan(0);
            expect(ttl).toBeLessThanOrEqual(10);
        },
        TIMEOUT,
    );

    it.each([false, true])(
        "compression_getdel cluster_mode=%p",
        async (clusterMode) => {
            client = await createCompressedClient(clusterMode, {
                enabled: true,
            });
            const key = uniqueKey("getdel_test");

            // Set value (should be compressed)
            const compressBefore =
                getNumericStats(client).total_values_compressed;
            await client.set(key, TEXT_1K);
            expect(
                getNumericStats(client).total_values_compressed,
            ).toBeGreaterThan(compressBefore);

            // GETDEL should decompress value and delete key
            const decompressBefore =
                getNumericStats(client).total_values_decompressed;
            const retrieved = await client.getdel(key);
            expect(retrieved).toBe(TEXT_1K);
            expect(
                getNumericStats(client).total_values_decompressed,
            ).toBeGreaterThan(decompressBefore);

            // Verify key was deleted
            expect(await client.get(key)).toBeNull();
        },
        TIMEOUT,
    );

    it.each([false, true])(
        "compression_setex_via_custom_command cluster_mode=%p",
        async (clusterMode) => {
            client = await createCompressedClient(clusterMode, {
                enabled: true,
            });
            const key = uniqueKey("setex_test");

            const before = getNumericStats(client).total_values_compressed;

            // SETEX via custom command should compress value
            await client.customCommand(["SETEX", key, "10", TEXT_1K]);

            expect(
                getNumericStats(client).total_values_compressed,
            ).toBeGreaterThan(before);

            // Verify value can be retrieved and decompressed
            expect(await client.get(key)).toBe(TEXT_1K);

            // Verify TTL was set
            const ttl = await client.ttl(key);
            expect(ttl).toBeGreaterThan(0);
            expect(ttl).toBeLessThanOrEqual(10);
        },
        TIMEOUT,
    );

    it.each([false, true])(
        "compression_psetex_via_custom_command cluster_mode=%p",
        async (clusterMode) => {
            client = await createCompressedClient(clusterMode, {
                enabled: true,
            });
            const key = uniqueKey("psetex_test");

            const before = getNumericStats(client).total_values_compressed;

            // PSETEX via custom command should compress value
            await client.customCommand(["PSETEX", key, "10000", TEXT_1K]);

            expect(
                getNumericStats(client).total_values_compressed,
            ).toBeGreaterThan(before);

            // Verify value can be retrieved and decompressed
            expect(await client.get(key)).toBe(TEXT_1K);
        },
        TIMEOUT,
    );

    it.each([false, true])(
        "compression_setnx_via_custom_command cluster_mode=%p",
        async (clusterMode) => {
            client = await createCompressedClient(clusterMode, {
                enabled: true,
            });
            const key = uniqueKey("setnx_test");

            // Ensure key doesn't exist
            await client.del([key]);

            const before = getNumericStats(client).total_values_compressed;

            // SETNX via custom command should compress value
            const result = await client.customCommand(["SETNX", key, TEXT_1K]);
            expect(result).toBe(1);

            expect(
                getNumericStats(client).total_values_compressed,
            ).toBeGreaterThan(before);

            // Verify value can be retrieved and decompressed
            expect(await client.get(key)).toBe(TEXT_1K);
        },
        TIMEOUT,
    );

    // --- Incompatible commands tests ---

    it.each([false, true])(
        "compression_append_incompatible cluster_mode=%p",
        async (clusterMode) => {
            client = await createCompressedClient(clusterMode, {
                enabled: true,
            });
            const key = uniqueKey("append_test");
            await client.set(key, "initial_value");

            // APPEND should fail with compression enabled
            await expect(client.append(key, "_appended")).rejects.toThrow(
                RequestError,
            );
            await expect(client.append(key, "_appended")).rejects.toThrow(
                /incompatible|compression/i,
            );
        },
        TIMEOUT,
    );

    it.each([false, true])(
        "compression_getrange_incompatible cluster_mode=%p",
        async (clusterMode) => {
            client = await createCompressedClient(clusterMode, {
                enabled: true,
            });
            const key = uniqueKey("getrange_test");
            await client.set(key, TEXT_1K);

            // GETRANGE should fail with compression enabled
            await expect(client.getrange(key, 0, 10)).rejects.toThrow(
                RequestError,
            );
            await expect(client.getrange(key, 0, 10)).rejects.toThrow(
                /incompatible|compression/i,
            );
        },
        TIMEOUT,
    );

    it.each([false, true])(
        "compression_setrange_incompatible cluster_mode=%p",
        async (clusterMode) => {
            client = await createCompressedClient(clusterMode, {
                enabled: true,
            });
            const key = uniqueKey("setrange_test");
            await client.set(key, TEXT_1K);

            // SETRANGE should fail with compression enabled
            await expect(
                client.setrange(key, 5, "replacement"),
            ).rejects.toThrow(RequestError);
            await expect(
                client.setrange(key, 5, "replacement"),
            ).rejects.toThrow(/incompatible|compression/i);
        },
        TIMEOUT,
    );

    it.each([false, true])(
        "compression_strlen_incompatible cluster_mode=%p",
        async (clusterMode) => {
            client = await createCompressedClient(clusterMode, {
                enabled: true,
            });
            const key = uniqueKey("strlen_test");
            await client.set(key, TEXT_1K);

            // STRLEN should fail with compression enabled
            await expect(client.strlen(key)).rejects.toThrow(RequestError);
            await expect(client.strlen(key)).rejects.toThrow(
                /incompatible|compression/i,
            );
        },
        TIMEOUT,
    );

    it.each([false, true])(
        "compression_incr_incompatible cluster_mode=%p",
        async (clusterMode) => {
            client = await createCompressedClient(clusterMode, {
                enabled: true,
            });
            const key = uniqueKey("incr_test");
            await client.set(key, "100");

            // INCR should fail with compression enabled
            await expect(client.incr(key)).rejects.toThrow(RequestError);
            await expect(client.incr(key)).rejects.toThrow(
                /incompatible|compression/i,
            );
        },
        TIMEOUT,
    );

    it.each([false, true])(
        "compression_incrby_incompatible cluster_mode=%p",
        async (clusterMode) => {
            client = await createCompressedClient(clusterMode, {
                enabled: true,
            });
            const key = uniqueKey("incrby_test");
            await client.set(key, "100");

            // INCRBY should fail with compression enabled
            await expect(client.incrBy(key, 10)).rejects.toThrow(RequestError);
            await expect(client.incrBy(key, 10)).rejects.toThrow(
                /incompatible|compression/i,
            );
        },
        TIMEOUT,
    );

    it.each([false, true])(
        "compression_incrbyfloat_incompatible cluster_mode=%p",
        async (clusterMode) => {
            client = await createCompressedClient(clusterMode, {
                enabled: true,
            });
            const key = uniqueKey("incrbyfloat_test");
            await client.set(key, "100.5");

            // INCRBYFLOAT should fail with compression enabled
            await expect(client.incrByFloat(key, 0.5)).rejects.toThrow(
                RequestError,
            );
            await expect(client.incrByFloat(key, 0.5)).rejects.toThrow(
                /incompatible|compression/i,
            );
        },
        TIMEOUT,
    );

    it.each([false, true])(
        "compression_decr_incompatible cluster_mode=%p",
        async (clusterMode) => {
            client = await createCompressedClient(clusterMode, {
                enabled: true,
            });
            const key = uniqueKey("decr_test");
            await client.set(key, "100");

            // DECR should fail with compression enabled
            await expect(client.decr(key)).rejects.toThrow(RequestError);
            await expect(client.decr(key)).rejects.toThrow(
                /incompatible|compression/i,
            );
        },
        TIMEOUT,
    );

    it.each([false, true])(
        "compression_decrby_incompatible cluster_mode=%p",
        async (clusterMode) => {
            client = await createCompressedClient(clusterMode, {
                enabled: true,
            });
            const key = uniqueKey("decrby_test");
            await client.set(key, "100");

            // DECRBY should fail with compression enabled
            await expect(client.decrBy(key, 10)).rejects.toThrow(RequestError);
            await expect(client.decrBy(key, 10)).rejects.toThrow(
                /incompatible|compression/i,
            );
        },
        TIMEOUT,
    );

    it.each([false, true])(
        "compression_getbit_incompatible cluster_mode=%p",
        async (clusterMode) => {
            client = await createCompressedClient(clusterMode, {
                enabled: true,
            });
            const key = uniqueKey("getbit_test");
            await client.set(key, "test_value");

            // GETBIT should fail with compression enabled
            await expect(client.getbit(key, 0)).rejects.toThrow(RequestError);
            await expect(client.getbit(key, 0)).rejects.toThrow(
                /incompatible|compression/i,
            );
        },
        TIMEOUT,
    );

    it.each([false, true])(
        "compression_setbit_incompatible cluster_mode=%p",
        async (clusterMode) => {
            client = await createCompressedClient(clusterMode, {
                enabled: true,
            });
            const key = uniqueKey("setbit_test");
            await client.set(key, "test_value");

            // SETBIT should fail with compression enabled
            await expect(client.setbit(key, 0, 1)).rejects.toThrow(
                RequestError,
            );
            await expect(client.setbit(key, 0, 1)).rejects.toThrow(
                /incompatible|compression/i,
            );
        },
        TIMEOUT,
    );

    it.each([false, true])(
        "compression_bitcount_incompatible cluster_mode=%p",
        async (clusterMode) => {
            client = await createCompressedClient(clusterMode, {
                enabled: true,
            });
            const key = uniqueKey("bitcount_test");
            await client.set(key, "test_value");

            // BITCOUNT should fail with compression enabled
            await expect(client.bitcount(key)).rejects.toThrow(RequestError);
            await expect(client.bitcount(key)).rejects.toThrow(
                /incompatible|compression/i,
            );
        },
        TIMEOUT,
    );

    it.each([false, true])(
        "compression_bitpos_incompatible cluster_mode=%p",
        async (clusterMode) => {
            client = await createCompressedClient(clusterMode, {
                enabled: true,
            });
            const key = uniqueKey("bitpos_test");
            await client.set(key, "test_value");

            // BITPOS should fail with compression enabled
            await expect(client.bitpos(key, 1)).rejects.toThrow(RequestError);
            await expect(client.bitpos(key, 1)).rejects.toThrow(
                /incompatible|compression/i,
            );
        },
        TIMEOUT,
    );

    it(
        "compression_incompatible_commands_work_without_compression",
        async () => {
            client = await GlideClient.createClient(
                getClientConfigurationOption(
                    getAddresses(false),
                    ProtocolVersion.RESP3,
                ),
            );
            const key = uniqueKey("no_compression_test");

            // Set initial value
            await client.set(key, "100");

            // All these commands should work without compression
            // INCR
            expect(await client.incr(key)).toBe(101);

            // INCRBY
            expect(await client.incrBy(key, 10)).toBe(111);

            // DECR
            expect(await client.decr(key)).toBe(110);

            // DECRBY
            expect(await client.decrBy(key, 10)).toBe(100);

            // STRLEN
            await client.set(key, "hello");
            expect(await client.strlen(key)).toBe(5);

            // APPEND
            expect(await client.append(key, " world")).toBe(11);

            // GETRANGE
            expect(await client.getrange(key, 0, 4)).toBe("hello");

            // SETRANGE
            expect(await client.setrange(key, 6, "WORLD")).toBe(11);

            // GETBIT
            await client.set(key, "\x00");
            expect(await client.getbit(key, 0)).toBe(0);

            // SETBIT
            expect(await client.setbit(key, 0, 1)).toBe(0);

            // BITCOUNT
            expect(await client.bitcount(key)).toBeGreaterThanOrEqual(0);
        },
        TIMEOUT,
    );

    // --- Batch/Transaction Compression Tests ---

    it(
        "compression_transaction_set_get_standalone",
        async () => {
            client = await createCompressedClient(false, { enabled: true });
            const key1 = uniqueKey("{tx_test}_1");
            const key2 = uniqueKey("{tx_test}_2");
            const key3 = uniqueKey("{tx_test}_3");

            const before = getNumericStats(client);

            // Build transaction with SET and GET commands
            const transaction = new Transaction();
            transaction.set(key1, TEXT_1K);
            transaction.set(key2, TEXT_1K);
            transaction.set(key3, TEXT_1K);
            transaction.get(key1);
            transaction.get(key2);
            transaction.get(key3);

            // Execute transaction
            const results = await (client as GlideClient).exec(
                transaction,
                true,
            );
            expect(results).not.toBeNull();
            expect(results!.length).toBe(6);

            // Verify SET results
            expect(results![0]).toBe("OK");
            expect(results![1]).toBe("OK");
            expect(results![2]).toBe("OK");

            // Verify GET results (decompressed)
            expect(results![3]).toBe(TEXT_1K);
            expect(results![4]).toBe(TEXT_1K);
            expect(results![5]).toBe(TEXT_1K);

            const after = getNumericStats(client);
            // All 3 SET values should be compressed
            expect(
                after.total_values_compressed - before.total_values_compressed,
            ).toBeGreaterThanOrEqual(3);
            // All 3 GET values should be decompressed
            expect(
                after.total_values_decompressed -
                    before.total_values_decompressed,
            ).toBeGreaterThanOrEqual(3);
        },
        TIMEOUT,
    );

    it(
        "compression_transaction_batch_many_keys_standalone",
        async () => {
            client = await createCompressedClient(false, { enabled: true });
            const numKeys = 50;
            const keyPrefix = uniqueKey("{batch_test}");
            const keys: string[] = [];

            const before = getNumericStats(client);

            // Build transaction with SET commands
            const setTransaction = new Transaction();

            for (let i = 0; i < numKeys; i++) {
                const key = `${keyPrefix}_${i}`;
                keys.push(key);
                setTransaction.set(key, TEXT_1K);
            }

            // Execute SET transaction
            const setResults = await (client as GlideClient).exec(
                setTransaction,
                true,
            );
            expect(setResults).not.toBeNull();
            expect(setResults!.length).toBe(numKeys);

            for (const result of setResults!) {
                expect(result).toBe("OK");
            }

            // Verify compression happened
            const afterSet = getNumericStats(client);
            expect(
                afterSet.total_values_compressed -
                    before.total_values_compressed,
            ).toBeGreaterThanOrEqual(numKeys);

            // Build transaction with GET commands
            const getTransaction = new Transaction();

            for (const key of keys) {
                getTransaction.get(key);
            }

            // Execute GET transaction
            const getResults = await (client as GlideClient).exec(
                getTransaction,
                true,
            );
            expect(getResults).not.toBeNull();
            expect(getResults!.length).toBe(numKeys);

            // Verify all values are correct
            for (const result of getResults!) {
                expect(result).toBe(TEXT_1K);
            }

            // Verify decompression happened
            const afterGet = getNumericStats(client);
            expect(
                afterGet.total_values_decompressed -
                    before.total_values_decompressed,
            ).toBeGreaterThanOrEqual(numKeys);
        },
        TIMEOUT,
    );

    it(
        "compression_cluster_transaction_set_get",
        async () => {
            client = await createCompressedClient(true, { enabled: true });
            const numKeys = 30;
            const keyPrefix = uniqueKey("{cluster_tx_test}");
            const keys: string[] = [];

            const before = getNumericStats(client);

            // Build cluster transaction with SET commands (using hash tag for same slot)
            const setTransaction = new ClusterTransaction();

            for (let i = 0; i < numKeys; i++) {
                const key = `${keyPrefix}_${i}`;
                keys.push(key);
                setTransaction.set(key, TEXT_1K);
            }

            // Execute SET transaction
            const setResults = await (client as GlideClusterClient).exec(
                setTransaction,
                true,
            );
            expect(setResults).not.toBeNull();
            expect(setResults!.length).toBe(numKeys);

            for (const result of setResults!) {
                expect(result).toBe("OK");
            }

            // Verify compression happened
            const afterSet = getNumericStats(client);
            expect(
                afterSet.total_values_compressed -
                    before.total_values_compressed,
            ).toBeGreaterThanOrEqual(numKeys);

            // Build cluster transaction with GET commands
            const getTransaction = new ClusterTransaction();

            for (const key of keys) {
                getTransaction.get(key);
            }

            // Execute GET transaction
            const getResults = await (client as GlideClusterClient).exec(
                getTransaction,
                true,
            );
            expect(getResults).not.toBeNull();
            expect(getResults!.length).toBe(numKeys);

            // Verify all values are correct
            for (const result of getResults!) {
                expect(result).toBe(TEXT_1K);
            }

            // Verify decompression happened
            const afterGet = getNumericStats(client);
            expect(
                afterGet.total_values_decompressed -
                    before.total_values_decompressed,
            ).toBeGreaterThanOrEqual(numKeys);
        },
        TIMEOUT,
    );

    it(
        "compression_transaction_mixed_commands",
        async () => {
            client = await createCompressedClient(false, { enabled: true });
            const key1 = uniqueKey("{mixed_test}_1");
            const key2 = uniqueKey("{mixed_test}_2");

            const before = getNumericStats(client);

            // Build transaction with mixed SET and GET commands
            const transaction = new Transaction();
            transaction.set(key1, TEXT_1K);
            transaction.get(key1);
            transaction.set(key2, TEXT_1K);
            transaction.get(key2);
            transaction.get(key1);

            // Execute transaction
            const results = await (client as GlideClient).exec(
                transaction,
                true,
            );
            expect(results).not.toBeNull();
            expect(results!.length).toBe(5);

            // Verify results
            expect(results![0]).toBe("OK"); // SET key1
            expect(results![1]).toBe(TEXT_1K); // GET key1
            expect(results![2]).toBe("OK"); // SET key2
            expect(results![3]).toBe(TEXT_1K); // GET key2
            expect(results![4]).toBe(TEXT_1K); // GET key1 again

            const after = getNumericStats(client);
            // 2 SET values should be compressed
            expect(
                after.total_values_compressed - before.total_values_compressed,
            ).toBeGreaterThanOrEqual(2);
            // 3 GET values should be decompressed
            expect(
                after.total_values_decompressed -
                    before.total_values_decompressed,
            ).toBeGreaterThanOrEqual(3);
        },
        TIMEOUT,
    );

    it(
        "compression_transaction_below_threshold_not_compressed",
        async () => {
            client = await createCompressedClient(false, {
                enabled: true,
                minCompressionSize: 256,
            });
            const key1 = uniqueKey("{threshold_test}_1");
            const key2 = uniqueKey("{threshold_test}_2");
            const smallValue = "A".repeat(100); // Below 256 threshold

            const before = getNumericStats(client);

            // Build transaction with small values
            const transaction = new Transaction();
            transaction.set(key1, smallValue);
            transaction.set(key2, smallValue);
            transaction.get(key1);
            transaction.get(key2);

            // Execute transaction
            const results = await (client as GlideClient).exec(
                transaction,
                true,
            );
            expect(results).not.toBeNull();
            expect(results!.length).toBe(4);

            // Verify SET results
            expect(results![0]).toBe("OK");
            expect(results![1]).toBe("OK");

            // Verify GET results
            expect(results![2]).toBe(smallValue);
            expect(results![3]).toBe(smallValue);

            const after = getNumericStats(client);
            // Values below threshold should not be compressed
            expect(after.total_values_compressed).toBe(
                before.total_values_compressed,
            );
            // Skipped count should increase
            expect(after.compression_skipped_count).toBeGreaterThan(
                before.compression_skipped_count,
            );
        },
        TIMEOUT,
    );

    it.each([false, true])(
        "compression_empty_batch cluster_mode=%p",
        async (clusterMode) => {
            client = await createCompressedClient(clusterMode, {
                enabled: true,
            });

            const before = getNumericStats(client);

            // Build empty transaction
            const transaction = clusterMode
                ? new ClusterTransaction()
                : new Transaction();

            // Execute empty transaction
            const results = clusterMode
                ? await (client as GlideClusterClient).exec(
                      transaction as ClusterTransaction,
                      true,
                  )
                : await (client as GlideClient).exec(
                      transaction as Transaction,
                      true,
                  );

            // Empty batch should return empty array
            expect(results).toEqual([]);

            // No compression/decompression should happen
            const after = getNumericStats(client);
            expect(after.total_values_compressed).toBe(
                before.total_values_compressed,
            );
            expect(after.total_values_decompressed).toBe(
                before.total_values_decompressed,
            );
        },
        TIMEOUT,
    );

    // --- Max Decompressed Size Tests ---

    it("compression_config_default_max_decompressed_size", () => {
        // Default config should not throw
        expect(() => {
            validateCompressionConfiguration({
                enabled: true,
            });
        }).not.toThrow();
    });

    it("compression_config_custom_max_decompressed_size", () => {
        // Custom max decompressed size should not throw
        expect(() => {
            validateCompressionConfiguration({
                enabled: true,
                maxDecompressedSize: 100 * 1024 * 1024, // 100MB
            });
        }).not.toThrow();
    });

    it("compression_config_zero_max_decompressed_size_throws", () => {
        expect(() => {
            validateCompressionConfiguration({
                enabled: true,
                maxDecompressedSize: 0,
            });
        }).toThrow(ConfigurationError);
    });

    it("compression_config_negative_max_decompressed_size_throws", () => {
        expect(() => {
            validateCompressionConfiguration({
                enabled: true,
                maxDecompressedSize: -1,
            });
        }).toThrow(ConfigurationError);
    });

    it.each([false, true])(
        "compression_client_with_custom_max_decompressed_size cluster_mode=%p",
        async (clusterMode) => {
            // Test that client can be created with custom max_decompressed_size
            client = await createCompressedClient(clusterMode, {
                enabled: true,
                maxDecompressedSize: 100 * 1024 * 1024, // 100MB
            });
            const key = uniqueKey("max_decomp_test");

            // Basic set/get should work
            await setAndExpectCompression(client, key, TEXT_1K);
            expect(await client.get(key)).toBe(TEXT_1K);
        },
        TIMEOUT,
    );
});
