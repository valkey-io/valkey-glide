/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { afterAll, afterEach, beforeAll, describe } from "@jest/globals";
import * as fs from "fs";
import ValkeyCluster from "../../utils/TestUtils";
import {
    ClusterBatch,
    GlideClient,
    GlideClusterClient,
    OpenTelemetry,
    OpenTelemetryConfig,
    ProtocolVersion,
    GlideSpanContext,
    GlideOpenTelemetryConfig,
} from "../build-ts";
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
const VALID_ENDPOINT_TRACES = "/tmp/spans.json";
const VALID_FILE_ENDPOINT_TRACES = "file://" + VALID_ENDPOINT_TRACES;
const VALID_ENDPOINT_METRICS = "https://valid-endpoint/v1/metrics";

/** Parent span context passed via init config's parentSpanContextProvider. */
const INIT_PARENT_CTX: GlideSpanContext = {
    traceId: "0af7651916cd43dd8448eb211c80319c",
    spanId: "b7ad6b7169203331",
    traceFlags: 1,
};

async function wrongOpenTelemetryConfig() {
    // wrong traces endpoint
    let openTelemetryConfig: OpenTelemetryConfig = {
        traces: {
            endpoint: "wrong.endpoint",
        },
    };
    expect(() => OpenTelemetry.init(openTelemetryConfig)).toThrow(
        /Parse error. /i,
    );

    // wrong metrics endpoint
    openTelemetryConfig = {
        metrics: {
            endpoint: "wrong.endpoint",
        },
    };
    expect(() => OpenTelemetry.init(openTelemetryConfig)).toThrow(
        /Parse error. /i,
    );

    // negative flush interval
    openTelemetryConfig = {
        traces: {
            endpoint: VALID_FILE_ENDPOINT_TRACES,
            samplePercentage: 1,
        },
        flushIntervalMs: -400,
    };
    expect(() => OpenTelemetry.init(openTelemetryConfig)).toThrow(
        /InvalidInput: flushIntervalMs must be a positive integer/i,
    );

    // negative requests percentage
    openTelemetryConfig = {
        traces: {
            endpoint: VALID_FILE_ENDPOINT_TRACES,
            samplePercentage: -400,
        },
    };
    expect(() => OpenTelemetry.init(openTelemetryConfig)).toThrow(
        /Trace sample percentage must be between 0 and 100/i,
    );

    // wrong traces file path
    openTelemetryConfig = {
        traces: {
            endpoint: "file:invalid-path/v1/traces.json",
        },
    };
    expect(() => OpenTelemetry.init(openTelemetryConfig)).toThrow(
        /File path must start with 'file:\/\/'/i,
    );

    // wrong metrics file path
    openTelemetryConfig = {
        metrics: {
            endpoint: "file:invalid-path/v1/metrics.json",
        },
    };
    expect(() => OpenTelemetry.init(openTelemetryConfig)).toThrow(
        /File path must start with 'file:\/\/'/i,
    );

    // wrong directory path
    openTelemetryConfig = {
        traces: {
            endpoint: "file:///no-exits-path/v1/traces.json",
        },
    };
    expect(() => OpenTelemetry.init(openTelemetryConfig)).toThrow(
        /The directory does not exist or is not a directory/i,
    );

    // Traces and metrics are not provided
    openTelemetryConfig = {};
    expect(() => OpenTelemetry.init(openTelemetryConfig)).toThrow(
        /At least one of traces or metrics must be provided for OpenTelemetry configuration./i,
    );
}

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

        // check wrong open telemetry config before initilise it
        await wrongOpenTelemetryConfig();

        await testSpanNotExportedBeforeInitOtel();

        // init open telemetry. The init can be called once per process.
        // Pass parentSpanContextProvider via init config to verify it is registered.
        const openTelemetryConfig: GlideOpenTelemetryConfig = {
            traces: {
                endpoint: VALID_FILE_ENDPOINT_TRACES,
                samplePercentage: 100,
            },
            metrics: {
                endpoint: VALID_ENDPOINT_METRICS,
            },
            flushIntervalMs: 100,
            parentSpanContextProvider: () => INIT_PARENT_CTX,
        };
        OpenTelemetry.init(openTelemetryConfig);
        await teardown_otel_test();
    }, 40000);

    async function teardown_otel_test() {
        // Clean up OpenTelemetry files
        if (fs.existsSync(VALID_ENDPOINT_TRACES)) {
            fs.unlinkSync(VALID_ENDPOINT_TRACES);
        }

        if (fs.existsSync(VALID_ENDPOINT_METRICS)) {
            fs.unlinkSync(VALID_ENDPOINT_METRICS);
        }
    }

    afterEach(async () => {
        await teardown_otel_test();
        await flushAndCloseClient(true, cluster?.getAddresses(), client);
    });

    afterAll(async () => {
        if (testsFailed === 0) {
            await cluster.close();
        } else {
            await cluster.close(true);
        }
    });

    async function testSpanNotExportedBeforeInitOtel() {
        await teardown_otel_test();

        const client = await GlideClusterClient.createClient({
            ...getClientConfigurationOption(
                cluster.getAddresses(),
                ProtocolVersion.RESP3,
            ),
        });

        await client.get("testSpanNotExportedBeforeInitOtel");

        // check that the spans not exporter to the file before initilise otel
        expect(fs.existsSync(VALID_ENDPOINT_TRACES)).toBe(false);

        client.close();
    }

    it("parentSpanContextProvider via init config is registered", () => {
        // The provider was set in beforeAll via GlideOpenTelemetryConfig.
        // Verify getParentSpanContext() returns the context from init config.
        expect(OpenTelemetry.getParentSpanContext()).toEqual(INIT_PARENT_CTX);
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
        `GlideClusterClient test percentage requests config_%p`,
        async (protocol) => {
            const client = await GlideClusterClient.createClient({
                ...getClientConfigurationOption(
                    cluster.getAddresses(),
                    protocol,
                ),
            });
            OpenTelemetry.setSamplePercentage(0);
            expect(OpenTelemetry.getSamplePercentage()).toBe(0);

            // wait for the spans to be flushed and removed the file
            await new Promise((resolve) => setTimeout(resolve, 500));

            await teardown_otel_test();

            for (let i = 0; i < 100; i++) {
                await client.set(
                    "GlideClusterClient_test_percentage_requests_config",
                    "value",
                );
            }

            await new Promise((resolve) => setTimeout(resolve, 500));
            // check that the spans not exporter to the file due to the requests percentage is 0
            expect(fs.existsSync(VALID_ENDPOINT_TRACES)).toBe(false);

            expect(() => OpenTelemetry.setSamplePercentage(-100)).toThrow(
                /Sample percentage must be between 0 and 100/i,
            );
            // check that the sample percentage is still 0
            expect(OpenTelemetry.getSamplePercentage()).toBe(0);
            OpenTelemetry.setSamplePercentage(100);
            expect(OpenTelemetry.getSamplePercentage()).toBe(100);

            // Execute a series of commands sequentially
            for (let i = 0; i < 10; i++) {
                const key = `GlideClusterClient_test_percentage_requests_config_${i}`;
                await client.get(key);
            }

            // Wait for spans to be flushed to file
            await new Promise((resolve) => setTimeout(resolve, 5000));

            // Read the span file and check span name
            const { spanNames } = readAndParseSpanFile(VALID_ENDPOINT_TRACES);

            expect(spanNames).toContain("Get");
            // check that the spans exported to the file exactly 10 times
            expect(spanNames.filter((name) => name === "Get").length).toBe(10);
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `GlideClusterClient test otel global config not reinitialize_%p`,
        async (protocol) => {
            const openTelemetryConfig: OpenTelemetryConfig = {
                traces: {
                    endpoint: "wrong.endpoint",
                    samplePercentage: 1,
                },
            };
            // the init will not throw error regarding the wrong endpoint because the init can be called once per process
            expect(() => OpenTelemetry.init(openTelemetryConfig)).not.toThrow();

            const client = await GlideClusterClient.createClient({
                ...getClientConfigurationOption(
                    cluster.getAddresses(),
                    protocol,
                ),
            });

            await client.set(
                "GlideClusterClient_test_otel_global_config",
                "value",
            );

            await new Promise((resolve) => setTimeout(resolve, 500));

            // Read the span file and check span name
            const { spanNames } = readAndParseSpanFile(VALID_ENDPOINT_TRACES);

            expect(spanNames).toContain("Set");
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
            });

            const batch = new ClusterBatch(true);

            batch.set("test_key", "foo");
            batch.objectRefcount("test_key");

            const response = await client.exec(batch, true);
            expect(response).not.toBeNull();

            if (response != null) {
                expect(response.length).toEqual(2);
                expect(response[0]).toEqual("OK"); // batch.set("test_key", "foo");
                expect(response[1]).toBeGreaterThanOrEqual(1); // batch.objectRefcount("test_key");
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
        `GlideClusterClient test number of clients with same config_%p`,
        async (protocol) => {
            const client1 = await GlideClusterClient.createClient({
                ...getClientConfigurationOption(
                    cluster.getAddresses(),
                    protocol,
                ),
            });
            const client2 = await GlideClusterClient.createClient({
                ...getClientConfigurationOption(
                    cluster.getAddresses(),
                    protocol,
                ),
            });

            client1.set("test_key", "value");
            client2.get("test_key");

            // Wait for spans to be flushed to file
            await new Promise((resolve) => setTimeout(resolve, 5000));

            // Read and check span names from the file using the helper function
            const { spanNames } = readAndParseSpanFile(VALID_ENDPOINT_TRACES);

            // Check for expected span names
            expect(spanNames).toContain("Get");
            expect(spanNames).toContain("Set");

            client1.close();
            client2.close();
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
            });

            const batch = new ClusterBatch(true);

            batch.set("test_key", "foo");
            batch.objectRefcount("test_key");

            const response = await client.exec(batch, true);
            expect(response).not.toBeNull();

            if (response != null) {
                expect(response.length).toEqual(2);
                expect(response[0]).toEqual("OK"); // transaction.set("test_key", "foo");
                expect(response[1]).toBeGreaterThanOrEqual(1); // transaction.objectRefcount("test_key");
            }

            // Wait for spans to be flushed to file
            await new Promise((resolve) => setTimeout(resolve, 5000));

            // Read and check span names from the file using the helper function
            const { spanNames } = readAndParseSpanFile(VALID_ENDPOINT_TRACES);

            // Check for expected span names
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
        // remove the span file
        if (fs.existsSync(VALID_ENDPOINT_TRACES)) {
            fs.unlinkSync(VALID_ENDPOINT_TRACES);
        }

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
            });

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
        "GlideClient test otel global config not reinitialize_%p",
        async (protocol) => {
            client = await GlideClient.createClient({
                ...getClientConfigurationOption(
                    cluster.getAddresses(),
                    protocol,
                ),
            });

            const openTelemetryConfig: OpenTelemetryConfig = {
                traces: {
                    endpoint: "wrong.endpoint",
                },
            };
            // the init will not throw error regarding the wrong endpoint because the init can be called once per process
            expect(() => OpenTelemetry.init(openTelemetryConfig)).not.toThrow();
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
            });

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
});

// Unit tests for setParentSpanContextProvider / getParentSpanContext API
describe("OpenTelemetry GlideSpanContext propagation", () => {
    afterEach(() => {
        // Reset the callback after each test
        OpenTelemetry.setParentSpanContextProvider(null);
    });

    it("getParentSpanContext returns undefined when no callback is set", () => {
        OpenTelemetry.setParentSpanContextProvider(null);
        expect(OpenTelemetry.getParentSpanContext()).toBeUndefined();
    });

    it("getParentSpanContext returns the value from the registered callback", () => {
        const ctx: GlideSpanContext = {
            traceId: "0af7651916cd43dd8448eb211c80319c",
            spanId: "b7ad6b7169203331",
            traceFlags: 1,
        };
        OpenTelemetry.setParentSpanContextProvider(() => ctx);
        expect(OpenTelemetry.getParentSpanContext()).toEqual(ctx);
    });

    it("getParentSpanContext returns context with traceState", () => {
        const ctx: GlideSpanContext = {
            traceId: "0af7651916cd43dd8448eb211c80319c",
            spanId: "b7ad6b7169203331",
            traceFlags: 1,
            traceState: "vendorname1=opaqueValue1,vendorname2=opaqueValue2",
        };
        OpenTelemetry.setParentSpanContextProvider(() => ctx);
        const result = OpenTelemetry.getParentSpanContext();
        expect(result).toEqual(ctx);
        expect(result?.traceState).toBe(
            "vendorname1=opaqueValue1,vendorname2=opaqueValue2",
        );
    });

    it("getParentSpanContext returns context when traceState is omitted", () => {
        const ctx: GlideSpanContext = {
            traceId: "0af7651916cd43dd8448eb211c80319c",
            spanId: "b7ad6b7169203331",
            traceFlags: 1,
        };
        OpenTelemetry.setParentSpanContextProvider(() => ctx);
        const result = OpenTelemetry.getParentSpanContext();
        expect(result).toBeDefined();
        expect(result?.traceState).toBeUndefined();
    });

    it("getParentSpanContext returns undefined when callback returns undefined", () => {
        OpenTelemetry.setParentSpanContextProvider(() => undefined);
        expect(OpenTelemetry.getParentSpanContext()).toBeUndefined();
    });

    it("setParentSpanContextProvider(null) clears a previously set callback", () => {
        const ctx: GlideSpanContext = {
            traceId: "0af7651916cd43dd8448eb211c80319c",
            spanId: "b7ad6b7169203331",
            traceFlags: 1,
        };
        OpenTelemetry.setParentSpanContextProvider(() => ctx);
        expect(OpenTelemetry.getParentSpanContext()).toEqual(ctx);

        OpenTelemetry.setParentSpanContextProvider(null);
        expect(OpenTelemetry.getParentSpanContext()).toBeUndefined();
    });

    it("callback is invoked on each call to getParentSpanContext", () => {
        let callCount = 0;
        OpenTelemetry.setParentSpanContextProvider(() => {
            callCount++;
            return {
                traceId: "0af7651916cd43dd8448eb211c80319c",
                spanId: "b7ad6b7169203331",
                traceFlags: 1,
            };
        });

        OpenTelemetry.getParentSpanContext();
        OpenTelemetry.getParentSpanContext();
        OpenTelemetry.getParentSpanContext();
        expect(callCount).toBe(3);
    });

    it("getParentSpanContext returns undefined for invalid traceId", () => {
        OpenTelemetry.setParentSpanContextProvider(() => ({
            traceId: "not-a-valid-hex",
            spanId: "b7ad6b7169203331",
            traceFlags: 1,
        }));
        expect(OpenTelemetry.getParentSpanContext()).toBeUndefined();
    });

    it("getParentSpanContext returns undefined for invalid spanId", () => {
        OpenTelemetry.setParentSpanContextProvider(() => ({
            traceId: "0af7651916cd43dd8448eb211c80319c",
            spanId: "tooshort",
            traceFlags: 1,
        }));
        expect(OpenTelemetry.getParentSpanContext()).toBeUndefined();
    });

    it("getParentSpanContext returns undefined for uppercase hex", () => {
        OpenTelemetry.setParentSpanContextProvider(() => ({
            traceId: "0AF7651916CD43DD8448EB211C80319C",
            spanId: "B7AD6B7169203331",
            traceFlags: 1,
        }));
        expect(OpenTelemetry.getParentSpanContext()).toBeUndefined();
    });

    it("getParentSpanContext returns valid context for correct hex", () => {
        const ctx: GlideSpanContext = {
            traceId: "0af7651916cd43dd8448eb211c80319c",
            spanId: "b7ad6b7169203331",
            traceFlags: 1,
        };
        OpenTelemetry.setParentSpanContextProvider(() => ctx);
        expect(OpenTelemetry.getParentSpanContext()).toEqual(ctx);
    });

    it("getParentSpanContext returns undefined for too-short traceId", () => {
        OpenTelemetry.setParentSpanContextProvider(() => ({
            traceId: "0af7651916cd43dd",
            spanId: "b7ad6b7169203331",
            traceFlags: 1,
        }));
        expect(OpenTelemetry.getParentSpanContext()).toBeUndefined();
    });

    it("getParentSpanContext returns undefined for too-long spanId", () => {
        OpenTelemetry.setParentSpanContextProvider(() => ({
            traceId: "0af7651916cd43dd8448eb211c80319c",
            spanId: "b7ad6b716920333100",
            traceFlags: 1,
        }));
        expect(OpenTelemetry.getParentSpanContext()).toBeUndefined();
    });

    it("getParentSpanContext returns undefined for negative traceFlags", () => {
        OpenTelemetry.setParentSpanContextProvider(() => ({
            traceId: "0af7651916cd43dd8448eb211c80319c",
            spanId: "b7ad6b7169203331",
            traceFlags: -1,
        }));
        expect(OpenTelemetry.getParentSpanContext()).toBeUndefined();
    });

    it("getParentSpanContext returns undefined for traceFlags > 255", () => {
        OpenTelemetry.setParentSpanContextProvider(() => ({
            traceId: "0af7651916cd43dd8448eb211c80319c",
            spanId: "b7ad6b7169203331",
            traceFlags: 256,
        }));
        expect(OpenTelemetry.getParentSpanContext()).toBeUndefined();
    });

    it("getParentSpanContext returns undefined for non-integer traceFlags", () => {
        OpenTelemetry.setParentSpanContextProvider(() => ({
            traceId: "0af7651916cd43dd8448eb211c80319c",
            spanId: "b7ad6b7169203331",
            traceFlags: 1.5,
        }));
        expect(OpenTelemetry.getParentSpanContext()).toBeUndefined();
    });

    it("getParentSpanContext accepts traceFlags at boundaries (0 and 255)", () => {
        OpenTelemetry.setParentSpanContextProvider(() => ({
            traceId: "0af7651916cd43dd8448eb211c80319c",
            spanId: "b7ad6b7169203331",
            traceFlags: 0,
        }));
        expect(OpenTelemetry.getParentSpanContext()).toBeDefined();

        OpenTelemetry.setParentSpanContextProvider(() => ({
            traceId: "0af7651916cd43dd8448eb211c80319c",
            spanId: "b7ad6b7169203331",
            traceFlags: 255,
        }));
        expect(OpenTelemetry.getParentSpanContext()).toBeDefined();
    });

    it("getParentSpanContext accepts valid traceState", () => {
        OpenTelemetry.setParentSpanContextProvider(() => ({
            traceId: "0af7651916cd43dd8448eb211c80319c",
            spanId: "b7ad6b7169203331",
            traceFlags: 1,
            traceState: "vendor1=value1,vendor2=value2",
        }));
        const result = OpenTelemetry.getParentSpanContext();
        expect(result).toBeDefined();
        expect(result?.traceState).toBe("vendor1=value1,vendor2=value2");
    });

    it("getParentSpanContext accepts empty traceState", () => {
        OpenTelemetry.setParentSpanContextProvider(() => ({
            traceId: "0af7651916cd43dd8448eb211c80319c",
            spanId: "b7ad6b7169203331",
            traceFlags: 1,
            traceState: "",
        }));
        expect(OpenTelemetry.getParentSpanContext()).toBeDefined();
    });

    it("getParentSpanContext accepts multi-tenant traceState key", () => {
        OpenTelemetry.setParentSpanContextProvider(() => ({
            traceId: "0af7651916cd43dd8448eb211c80319c",
            spanId: "b7ad6b7169203331",
            traceFlags: 1,
            traceState: "fw529a3039@dt=value",
        }));
        expect(OpenTelemetry.getParentSpanContext()).toBeDefined();
    });

    it("getParentSpanContext returns undefined for traceState with missing value", () => {
        OpenTelemetry.setParentSpanContextProvider(() => ({
            traceId: "0af7651916cd43dd8448eb211c80319c",
            spanId: "b7ad6b7169203331",
            traceFlags: 1,
            traceState: "noequals",
        }));
        expect(OpenTelemetry.getParentSpanContext()).toBeUndefined();
    });

    it("getParentSpanContext returns undefined for traceState with uppercase key", () => {
        OpenTelemetry.setParentSpanContextProvider(() => ({
            traceId: "0af7651916cd43dd8448eb211c80319c",
            spanId: "b7ad6b7169203331",
            traceFlags: 1,
            traceState: "INVALID=value",
        }));
        expect(OpenTelemetry.getParentSpanContext()).toBeUndefined();
    });

    it("getParentSpanContext returns undefined for traceState value containing comma", () => {
        OpenTelemetry.setParentSpanContextProvider(() => ({
            traceId: "0af7651916cd43dd8448eb211c80319c",
            spanId: "b7ad6b7169203331",
            traceFlags: 1,
            traceState: "key=val,ue",
        }));
        // "key=val" is valid, "ue" has no '=' → invalid
        expect(OpenTelemetry.getParentSpanContext()).toBeUndefined();
    });

    it("getParentSpanContext returns undefined for traceState key exceeding 256 chars", () => {
        const longKey = "a".repeat(257);
        OpenTelemetry.setParentSpanContextProvider(() => ({
            traceId: "0af7651916cd43dd8448eb211c80319c",
            spanId: "b7ad6b7169203331",
            traceFlags: 1,
            traceState: `${longKey}=value`,
        }));
        expect(OpenTelemetry.getParentSpanContext()).toBeUndefined();
    });

    it("getParentSpanContext returns undefined for traceState value exceeding 256 chars", () => {
        const longValue = "a".repeat(257);
        OpenTelemetry.setParentSpanContextProvider(() => ({
            traceId: "0af7651916cd43dd8448eb211c80319c",
            spanId: "b7ad6b7169203331",
            traceFlags: 1,
            traceState: `key=${longValue}`,
        }));
        expect(OpenTelemetry.getParentSpanContext()).toBeUndefined();
    });
});

// Integration test for parent span context propagation
describe("OpenTelemetry parent span context propagation", () => {
    let cluster: ValkeyCluster;
    let client: GlideClusterClient;

    beforeAll(async () => {
        const clusterAddresses = global.CLUSTER_ENDPOINTS;
        cluster = clusterAddresses
            ? await ValkeyCluster.initFromExistingCluster(
                  true,
                  parseEndpoints(clusterAddresses),
                  getServerVersion,
              )
            : await ValkeyCluster.createCluster(true, 3, 1, getServerVersion);
    }, 40000);

    afterEach(async () => {
        OpenTelemetry.setParentSpanContextProvider(null);

        if (fs.existsSync(VALID_ENDPOINT_TRACES)) {
            fs.unlinkSync(VALID_ENDPOINT_TRACES);
        }

        await flushAndCloseClient(true, cluster?.getAddresses(), client);
    });

    afterAll(async () => {
        await cluster.close();
    });

    it(
        "GLIDE spans inherit trace_id and parent_span_id from provided context",
        async () => {
            // Fixed parent context to verify propagation
            const parentTraceId = "0af7651916cd43dd8448eb211c80319c";
            const parentSpanId = "b7ad6b7169203331";
            const parentTraceFlags = 1;

            OpenTelemetry.setParentSpanContextProvider(() => ({
                traceId: parentTraceId,
                spanId: parentSpanId,
                traceFlags: parentTraceFlags,
            }));

            // Ensure 100% sampling so all commands produce spans
            OpenTelemetry.setSamplePercentage(100);

            // Drain any stale spans from previous test suites.
            // The OTel batch exporter flushes every 100ms, so waiting 200ms
            // ensures pending spans are written, then we delete the file.
            if (fs.existsSync(VALID_ENDPOINT_TRACES)) {
                fs.unlinkSync(VALID_ENDPOINT_TRACES);
            }

            await new Promise((resolve) => setTimeout(resolve, 200));

            if (fs.existsSync(VALID_ENDPOINT_TRACES)) {
                fs.unlinkSync(VALID_ENDPOINT_TRACES);
            }

            client = await GlideClusterClient.createClient({
                ...getClientConfigurationOption(
                    cluster.getAddresses(),
                    ProtocolVersion.RESP3,
                ),
            });

            await client.set(
                "ctx_propagation_test_key",
                "ctx_propagation_test_value",
            );
            await client.get("ctx_propagation_test_key");

            // Wait for spans to be flushed to file
            await new Promise((resolve) => setTimeout(resolve, 5000));

            const { spans, spanNames } = readAndParseSpanFile(
                VALID_ENDPOINT_TRACES,
            );

            // Verify we got Set and Get spans
            expect(spanNames).toContain("Set");
            expect(spanNames).toContain("Get");

            // Verify each span has the correct trace_id and parent_span_id
            for (const line of spans) {
                let spanJson: Record<string, unknown>;

                try {
                    spanJson = JSON.parse(line);
                } catch {
                    continue;
                }

                if (spanJson.name === "Set" || spanJson.name === "Get") {
                    expect(spanJson.trace_id).toBe(parentTraceId);
                    expect(spanJson.parent_span_id).toBe(parentSpanId);
                    expect(spanJson.span_id).toBeDefined();
                    expect(spanJson.span_id).not.toBe(parentSpanId);
                }
            }
        },
        TIMEOUT,
    );

    it(
        "invalid parent context falls back to standalone span without failing the command",
        async () => {
            // Set a provider with invalid traceFlags (256 exceeds u8 range).
            // Without TS-side validation this would cause a NAPI conversion error
            // and fail the command. With validation, getParentSpanContext() returns
            // undefined and the client creates a standalone span instead.
            OpenTelemetry.setParentSpanContextProvider(() => ({
                traceId: "0af7651916cd43dd8448eb211c80319c",
                spanId: "b7ad6b7169203331",
                traceFlags: 256,
            }));

            OpenTelemetry.setSamplePercentage(100);

            if (fs.existsSync(VALID_ENDPOINT_TRACES)) {
                fs.unlinkSync(VALID_ENDPOINT_TRACES);
            }

            await new Promise((resolve) => setTimeout(resolve, 200));

            if (fs.existsSync(VALID_ENDPOINT_TRACES)) {
                fs.unlinkSync(VALID_ENDPOINT_TRACES);
            }

            client = await GlideClusterClient.createClient({
                ...getClientConfigurationOption(
                    cluster.getAddresses(),
                    ProtocolVersion.RESP3,
                ),
            });

            // Commands must succeed despite the invalid parent context
            await client.set("fallback_test_key", "fallback_test_value");
            const result = await client.get("fallback_test_key");
            expect(result).toBe("fallback_test_value");

            // Wait for spans to be flushed to file
            await new Promise((resolve) => setTimeout(resolve, 5000));

            const { spans, spanNames } = readAndParseSpanFile(
                VALID_ENDPOINT_TRACES,
            );

            // Spans should still be created (standalone, not linked to parent)
            expect(spanNames).toContain("Set");
            expect(spanNames).toContain("Get");

            // Verify spans are standalone (no parent_span_id)
            for (const line of spans) {
                let spanJson: Record<string, unknown>;

                try {
                    spanJson = JSON.parse(line);
                } catch {
                    continue;
                }

                if (spanJson.name === "Set" || spanJson.name === "Get") {
                    expect(spanJson.parent_span_id).toBe("0000000000000000");
                }
            }
        },
        TIMEOUT,
    );
});
