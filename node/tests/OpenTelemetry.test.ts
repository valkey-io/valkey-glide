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

const TIMEOUT = 50000;

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
                        tracesCollectorEndPoint:
                            "https://valid-endpoint/v1/traces",
                        metricsCollectorEndPoint:
                            "https://valid-endpoint/v1/metrics",
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

            console.log(`Memory before: ${startMemory}, after: ${endMemory}`);
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
                        tracesCollectorEndPoint:
                           "https://valid-endpoint/v1/traces", // "file:///tmp/",
                        metricsCollectorEndPoint:
                            "https://valid-endpoint/v1/metrics",
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

            // Wait for spans to be flushed to file
            await new Promise((resolve) => setTimeout(resolve, 5000));

            // Read and check span names from the file
            let spanData: string;

            try {
                spanData = fs.readFileSync("/tmp/spans.json", "utf8");
            } catch (error: unknown) {
                // Fail the test if we can't read the span file
                throw new Error(
                    `Failed to read or validate span file: ${error instanceof Error ? error.message : String(error)}`,
                );
            }

            const spans = spanData
                .split("\n")
                .filter((line: string) => line.trim() !== "");

            // Check that we have spans
            expect(spans.length).toBeGreaterThan(0);

            // Parse and check span names
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

            console.log("Found span names:", spanNames);

            // Check for expected span names - these checks will fail the test if not found
            expect(spanNames).toContain("Batch");
            expect(spanNames).toContain("send_command");

            // Force GC and check memory
            if (global.gc) {
                global.gc();
            }

            const endMemory = process.memoryUsage().heapUsed;

            console.log(`Memory before: ${startMemory}, after: ${endMemory}`);
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
                            tracesCollectorEndPoint:
                                "https://valid-endpoint/v1/traces",
                            metricsCollectorEndPoint:
                                "https://valid-endpoint/v1/metrics",
                            flushIntervalMs: -400,
                        },
                    },
                }),
            ).rejects.toThrow(/InvalidInput/i);
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
                            tracesCollectorEndPoint: "wrong.endpoint",
                            metricsCollectorEndPoint: "wrong.endpoint",
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

            client = await GlideClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            // Execute multiple commands - each should automatically create and clean up its span
            await client.set("test_key1", "value1");
            await client.get("test_key1");
            await client.set("test_key2", "value2");
            await client.get("test_key2");

            if (global.gc) {
                global.gc(); // Run GC again to clean up
            }

            const endMemory = process.memoryUsage().heapUsed;

            console.log(`Memory before: ${startMemory}, after: ${endMemory}`);
            expect(endMemory).toBeLessThan(startMemory * 1.1); // Allow small fluctuations
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `GlideClient test concurrent commands span lifecycle_%p`,
        async (protocol) => {
            if (global.gc) {
                global.gc(); // Run garbage collection
            }

            const startMemory = process.memoryUsage().heapUsed;

            client = await GlideClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

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

            console.log(`Memory before: ${startMemory}, after: ${endMemory}`);
            expect(endMemory).toBeLessThan(startMemory * 1.1); // Allow small fluctuations
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "opentelemetry config_%p",
        async (protocol) => {
            await GlideClient.createClient({
                ...getClientConfigurationOption(
                    cluster.getAddresses(),
                    protocol,
                ),
                advancedConfiguration: {
                    openTelemetryConfig: {
                        tracesCollectorEndPoint:
                            "https://valid-endpoint/v1/traces",
                        metricsCollectorEndPoint:
                            "https://valid-endpoint/v1/metrics",
                        flushIntervalMs: 400,
                    },
                },
            });
        },
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
                            tracesCollectorEndPoint: "wrong.endpoint",
                            metricsCollectorEndPoint: "wrong.endpoint",
                            flushIntervalMs: 400,
                        },
                    },
                }),
            ).rejects.toThrow(/InvalidInput/i); // Ensure InvalidInput error
        },
    );
});
