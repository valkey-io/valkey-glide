/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { afterAll, afterEach, beforeAll, describe } from "@jest/globals";
import * as fs from "fs";
import {
    ClusterTransaction,
    GlideClient,
    GlideClusterClient,
    ProtocolVersion,
} from "..";
import ValkeyCluster from "../../utils/TestUtils";
import {
    flushAndCloseClient,
    getClientConfigurationOption,
    getServerVersion,
    parseEndpoints,
} from "./TestUtilities";

/**
 * Reads and parses a span file, extracting span data and names.
 *
 * @param path - The path to the span file
 * @returns An object containing the raw span data, array of spans, and array of span names
 * @throws Error if the file cannot be read or parsed
 */
function readAndParseSpanFile(path: string): {
    spanData: string;
    spans: string[];
    spanNames: string[];
} {
    let spanData: string;

    try {
        spanData = fs.readFileSync(path, "utf8");
    } catch (error: unknown) {
        throw new Error(
            `Failed to read or validate file: ${error instanceof Error ? error.message : String(error)}`,
        );
    }

    const spans = spanData
        .split("\n")
        .filter((line: string) => line.trim() !== "");

    // Check that we have spans
    if (spans.length === 0) {
        throw new Error("No spans found in the span file");
    }

    // Parse and extract span names
    const spanNames = spans
        .map((line: string) => {
            try {
                const span = JSON.parse(line);
                return span.name;
            } catch {
                return null;
            }
        })
        .filter((name: string | null) => name !== null);

    return {
        spanData,
        spans,
        spanNames,
    };
}

const TIMEOUT = 50000;
const VALID_ENDPOINT_TRACES = "file:///tmp/spans.json";
const VALID_ENDPOINT_METRICS = "https://valid-endpoint/v1/metrics";

//cluster tests
describe("OpenTelemetry GlideClusterClient", () => {
    const testsFailed = 0;
    let cluster: ValkeyCluster;
    let client: GlideClusterClient;
    beforeAll(async () => {
        const clusterAddresses = global.CLUSTER_ENDPOINTS;
        // Connect to cluster or create a new one based on the parsed addresses
        cluster = clusterAddresses
            ? await ValkeyCluster.initFromExistingCluster(
                  true,
                  parseEndpoints(clusterAddresses),
                  getServerVersion,
              )
            : // setting replicaCount to 1 to facilitate tests routed to replicas
              await ValkeyCluster.createCluster(true, 3, 1, getServerVersion);
    }, 40000);

    afterEach(async () => {
        await flushAndCloseClient(true, cluster.getAddresses(), client);
    });

    afterAll(async () => {
        if (testsFailed === 0) {
            await cluster.close();
        } else {
            await cluster.close(true);
        }
    });

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `GlideClusterClient test span memory leak_%p`,
        async (protocol) => {
            if (global.gc) {
                global.gc(); // Run garbage collection
            }

            const startMemory = process.memoryUsage().heapUsed;

            client = await GlideClusterClient.createClient({
                ...getClientConfigurationOption(
                    cluster.getAddresses(),
                    protocol,
                ),
                advancedConfiguration: {
                    openTelemetryConfig: {
                        tracesCollectorEndpoint: VALID_ENDPOINT_TRACES,
                        metricsCollectorEndpoint: VALID_ENDPOINT_METRICS,
                    },
                },
            });

            // Execute a series of commands sequentially
            for (let i = 0; i < 100; i++) {
                const key = `test_key_${i}`;
                await client.set(key, `value_${i}`);
                await client.get(key);
            }

            // Force GC and check memory
            if (global.gc) {
                global.gc();
            }

            const endMemory = process.memoryUsage().heapUsed;

            expect(endMemory).toBeLessThan(startMemory * 1.1); // Allow 10% growth
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `GlideClusterClient test span transaction memory leak_%p`,
        async (protocol) => {
            if (global.gc) {
                global.gc(); // Run garbage collection
            }

            const startMemory = process.memoryUsage().heapUsed;
            client = await GlideClusterClient.createClient({
                ...getClientConfigurationOption(
                    cluster.getAddresses(),
                    protocol,
                ),
                advancedConfiguration: {
                    openTelemetryConfig: {
                        tracesCollectorEndpoint: VALID_ENDPOINT_TRACES,
                        metricsCollectorEndpoint: VALID_ENDPOINT_METRICS,
                        flushIntervalMs: 100,
                    },
                },
            });

            // Remove the span file if it exists
            if (fs.existsSync("/tmp/spans.json")) {
                fs.unlinkSync("/tmp/spans.json");
            }

            const transaction = new ClusterTransaction();

            transaction.set("test_key", "foo");
            transaction.objectRefcount("test_key");

            const response = await client.exec(transaction);
            expect(response).not.toBeNull();

            if (response != null) {
                expect(response.length).toEqual(2);
                expect(response[0]).toEqual("OK"); // transaction.set("test_key", "foo");
                expect(response[1]).toBeGreaterThanOrEqual(1); // transaction.objectRefcount("test_key");
            }

            // Force GC and check memory
            if (global.gc) {
                global.gc();
            }

            const endMemory = process.memoryUsage().heapUsed;

            expect(endMemory).toBeLessThan(startMemory * 1.1); // Allow 10% growth
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `GlideClusterClient test span batch file_%p`,
        async (protocol) => {
            if (global.gc) {
                global.gc(); // Run garbage collection
            }

            const startMemory = process.memoryUsage().heapUsed;
            client = await GlideClusterClient.createClient({
                ...getClientConfigurationOption(
                    cluster.getAddresses(),
                    protocol,
                ),
                advancedConfiguration: {
                    openTelemetryConfig: {
                        tracesCollectorEndpoint: VALID_ENDPOINT_TRACES,
                        metricsCollectorEndpoint: VALID_ENDPOINT_METRICS,
                        flushIntervalMs: 100,
                    },
                },
            });

            const path = "/tmp/spans.json";

            // Remove the span file if it exists
            if (fs.existsSync(path)) {
                fs.unlinkSync(path);
            }

            const transaction = new ClusterTransaction();

            transaction.set("test_key", "foo");
            transaction.objectRefcount("test_key");

            const response = await client.exec(transaction);
            expect(response).not.toBeNull();

            if (response != null) {
                expect(response.length).toEqual(2);
                expect(response[0]).toEqual("OK"); // transaction.set("test_key", "foo");
                expect(response[1]).toBeGreaterThanOrEqual(1); // transaction.objectRefcount("test_key");
            }

            // Wait for spans to be flushed to file
            await new Promise((resolve) => setTimeout(resolve, 5000));

            // Read and check span names from the file using the helper function
            const { spanNames } = readAndParseSpanFile(path);

            // Check for expected span names - these checks will fail the test if not found
            expect(spanNames).toContain("Batch");
            expect(spanNames).toContain("send_batch");

            // Force GC and check memory
            if (global.gc) {
                global.gc();
            }

            const endMemory = process.memoryUsage().heapUsed;

            expect(endMemory).toBeLessThan(startMemory * 1.1); // Allow 10% growth
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "opentelemetry wrong config - negative flush interval_%p",
        async (protocol) => {
            await expect(
                GlideClusterClient.createClient({
                    ...getClientConfigurationOption(
                        cluster.getAddresses(),
                        protocol,
                    ),
                    advancedConfiguration: {
                        openTelemetryConfig: {
                            tracesCollectorEndpoint: VALID_ENDPOINT_TRACES,
                            metricsCollectorEndpoint: VALID_ENDPOINT_METRICS,
                            flushIntervalMs: -400,
                        },
                    },
                }),
            ).rejects.toThrow(/InvalidInput/i);
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "opentelemetry wrong file path config_%p",
        async (protocol) => {
            await expect(
                GlideClusterClient.createClient({
                    ...getClientConfigurationOption(
                        cluster.getAddresses(),
                        protocol,
                    ),
                    advancedConfiguration: {
                        openTelemetryConfig: {
                            tracesCollectorEndpoint:
                                "file:invalid-path/v1/traces.json",
                            metricsCollectorEndpoint: VALID_ENDPOINT_METRICS,
                            flushIntervalMs: 400,
                        },
                    },
                }),
            ).rejects.toThrow(/InvalidInput/i);
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "opentelemetry not exits folder path config_%p",
        async (protocol) => {
            await expect(
                GlideClusterClient.createClient({
                    ...getClientConfigurationOption(
                        cluster.getAddresses(),
                        protocol,
                    ),
                    advancedConfiguration: {
                        openTelemetryConfig: {
                            tracesCollectorEndpoint:
                                "file:///no-exits-path/v1/traces.json",
                            metricsCollectorEndpoint: VALID_ENDPOINT_METRICS,
                            flushIntervalMs: 400,
                        },
                    },
                }),
            ).rejects.toThrow(/InvalidInput/i);
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "opentelemetry folder path config_%p",
        async (protocol) => {
            const path = "/tmp/glide-test/";
            const file = path + "signals.json";

            // Remove the span file if it exists
            if (fs.existsSync(file)) {
                fs.unlinkSync(file);
            }

            // Create the directory if it doesn't exist
            if (!fs.existsSync(path)) {
                fs.mkdirSync(path, { recursive: true });
            }

            const client = await GlideClusterClient.createClient({
                ...getClientConfigurationOption(
                    cluster.getAddresses(),
                    protocol,
                ),
                advancedConfiguration: {
                    openTelemetryConfig: {
                        tracesCollectorEndpoint: "file://" + path,
                        metricsCollectorEndpoint: VALID_ENDPOINT_METRICS,
                        flushIntervalMs: 400,
                    },
                },
            });
            await client.set("test_key", "foo");
            await client.get("test_key");

            // Wait for spans to be flushed to file
            await new Promise((resolve) => setTimeout(resolve, 400));

            const { spanNames } = readAndParseSpanFile(file);
            expect(spanNames).toContain("Set");
            expect(spanNames).toContain("Get");
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "opentelemetry txt file path config_%p",
        async (protocol) => {
            const path = "/tmp/traces.txt";
            const client = await GlideClusterClient.createClient({
                ...getClientConfigurationOption(
                    cluster.getAddresses(),
                    protocol,
                ),
                advancedConfiguration: {
                    openTelemetryConfig: {
                        tracesCollectorEndpoint: "file://" + path,
                        metricsCollectorEndpoint: VALID_ENDPOINT_METRICS,
                        flushIntervalMs: 400,
                    },
                },
            });
            await client.set("test_key", "foo");
            await client.get("test_key");

            // Wait for spans to be flushed to file
            await new Promise((resolve) => setTimeout(resolve, 400));

            const { spanNames } = readAndParseSpanFile(path);
            expect(spanNames).toContain("Set");
            expect(spanNames).toContain("Get");
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "opentelemetry config wrong parameter_%p",
        async (protocol) => {
            await expect(
                GlideClusterClient.createClient({
                    ...getClientConfigurationOption(
                        cluster.getAddresses(),
                        protocol,
                    ),
                    advancedConfiguration: {
                        openTelemetryConfig: {
                            tracesCollectorEndpoint: "wrong.endpoint",
                            metricsCollectorEndpoint: "wrong.endpoint",
                            flushIntervalMs: 400,
                        },
                    },
                }),
            ).rejects.toThrow(/InvalidInput/i); // Ensure InvalidInput error
        },
    );
});

//standalone tests
describe("OpenTelemetry GlideClient", () => {
    const testsFailed = 0;
    let cluster: ValkeyCluster;
    let client: GlideClient;
    beforeAll(async () => {
        const standaloneAddresses = global.STAND_ALONE_ENDPOINT;
        cluster = standaloneAddresses
            ? await ValkeyCluster.initFromExistingCluster(
                  false,
                  parseEndpoints(standaloneAddresses),
                  getServerVersion,
              )
            : await ValkeyCluster.createCluster(false, 1, 1, getServerVersion);
    }, 20000);

    afterEach(async () => {
        await flushAndCloseClient(false, cluster.getAddresses(), client);
    });

    afterAll(async () => {
        if (testsFailed === 0) {
            await cluster.close();
        } else {
            await cluster.close(true);
        }
    });

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `GlideClient test automatic span lifecycle_%p`,
        async (protocol) => {
            if (global.gc) {
                global.gc(); // Run garbage collection
            }

            const startMemory = process.memoryUsage().heapUsed;

            client = await GlideClient.createClient({
                ...getClientConfigurationOption(
                    cluster.getAddresses(),
                    protocol,
                ),
                advancedConfiguration: {
                    openTelemetryConfig: {
                        tracesCollectorEndpoint: VALID_ENDPOINT_TRACES,
                        metricsCollectorEndpoint: VALID_ENDPOINT_METRICS,
                        flushIntervalMs: 400,
                    },
                },
            });

            // Remove the span file if it exists
            if (fs.existsSync("/tmp/spans.json")) {
                fs.unlinkSync("/tmp/spans.json");
            }

            // Execute multiple commands - each should automatically create and clean up its span
            await client.set("test_key1", "value1");
            await client.get("test_key1");
            await client.set("test_key2", "value2");
            await client.get("test_key2");

            if (global.gc) {
                global.gc(); // Run GC again to clean up
            }

            const endMemory = process.memoryUsage().heapUsed;

            expect(endMemory).toBeLessThan(startMemory * 1.1); // Allow small fluctuations
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "opentelemetry config_%p",
        async (protocol) => {
            client = await GlideClient.createClient({
                ...getClientConfigurationOption(
                    cluster.getAddresses(),
                    protocol,
                ),
                advancedConfiguration: {
                    openTelemetryConfig: {
                        tracesCollectorEndpoint: VALID_ENDPOINT_TRACES,
                        metricsCollectorEndpoint: VALID_ENDPOINT_METRICS,
                        flushIntervalMs: 400,
                    },
                },
            });
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP3, ProtocolVersion.RESP2])(
        `GlideClient test concurrent commands span lifecycle_%p`,
        async (protocol) => {
            if (global.gc) {
                global.gc(); // Run garbage collection
            }

            const startMemory = process.memoryUsage().heapUsed;

            client = await GlideClient.createClient({
                ...getClientConfigurationOption(
                    cluster.getAddresses(),
                    protocol,
                ),
                advancedConfiguration: {
                    openTelemetryConfig: {
                        tracesCollectorEndpoint: VALID_ENDPOINT_TRACES,
                        metricsCollectorEndpoint: VALID_ENDPOINT_METRICS,
                        flushIntervalMs: 400,
                    },
                },
            });

            // Remove the span file if it exists
            if (fs.existsSync("/tmp/spans.json")) {
                fs.unlinkSync("/tmp/spans.json");
            }

            // Execute multiple concurrent commands
            const commands = [
                client.set("test_key1", "value1"),
                client.get("test_key1"),
                client.set("test_key2", "value2"),
                client.get("test_key2"),
                client.set("test_key3", "value3"),
                client.get("test_key3"),
            ];

            await Promise.all(commands);

            if (global.gc) {
                global.gc(); // Run GC again to clean up
            }

            const endMemory = process.memoryUsage().heapUsed;

            expect(endMemory).toBeLessThan(startMemory * 1.1); // Allow small fluctuations
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "opentelemetry config wrong parameter_%p",
        async (protocol) => {
            await expect(
                GlideClient.createClient({
                    ...getClientConfigurationOption(
                        cluster.getAddresses(),
                        protocol,
                    ),
                    advancedConfiguration: {
                        openTelemetryConfig: {
                            tracesCollectorEndpoint: "wrong.endpoint",
                            metricsCollectorEndpoint: "wrong.endpoint",
                            flushIntervalMs: 400,
                        },
                    },
                }),
            ).rejects.toThrow(/InvalidInput/i); // Ensure InvalidInput error
        },
        TIMEOUT,
    );
});
