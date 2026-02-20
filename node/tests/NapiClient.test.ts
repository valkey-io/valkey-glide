/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 *
 * Integration tests for the direct NAPI layer implementation.
 * These tests verify the command flow through the NAPI bindings.
 */

import {
    afterAll,
    afterEach,
    beforeAll,
    describe,
    expect,
    it,
} from "@jest/globals";
import {
    Batch,
    ClosingError,
    ClusterBatch,
    ClusterScanCursor,
    ClusterTransaction,
    Decoder,
    GlideClient,
    GlideClusterClient,
    GlideString,
    InfoOptions,
    ProtocolVersion,
    RequestError,
    Script,
    Transaction,
} from "../build-ts";

const CLUSTER_PORTS = process.env.CLUSTER_ENDPOINTS || "127.0.0.1:37287";
const STANDALONE_PORT = process.env.STAND_ALONE_ENDPOINT || "127.0.0.1:35086";

function parseEndpoint(endpoint: string): { host: string; port: number } {
    const [host, portStr] = endpoint.split(":");
    return { host, port: parseInt(portStr, 10) };
}

describe("NAPI Client Integration Tests", () => {
    describe("GlideClient (Standalone)", () => {
        let client: GlideClient;

        beforeAll(async () => {
            const endpoint = parseEndpoint(STANDALONE_PORT);
            client = await GlideClient.createClient({
                addresses: [endpoint],
            });
        });

        afterAll(async () => {
            if (client) {
                client.close();
            }
        });

        afterEach(async () => {
            // Clean up keys between tests
            await client.flushall();
        });

        describe("Single Command Operations", () => {
            it("should run SET and GET commands", async () => {
                const setResult = await client.set("test-key", "test-value");
                expect(setResult).toBe("OK");

                const getResult = await client.get("test-key");
                expect(getResult).toBe("test-value");
            });

            it("should handle null responses", async () => {
                const result = await client.get("nonexistent-key");
                expect(result).toBeNull();
            });

            it("should handle numeric responses", async () => {
                await client.set("counter", "0");
                const result = await client.incr("counter");
                expect(result).toBe(1);

                const result2 = await client.incrBy("counter", 5);
                expect(result2).toBe(6);
            });

            it("should handle array responses", async () => {
                await client.set("key1", "val1");
                await client.set("key2", "val2");

                const result = await client.mget([
                    "key1",
                    "key2",
                    "nonexistent",
                ]);
                expect(result).toEqual(["val1", "val2", null]);
            });

            it("should handle hash responses", async () => {
                await client.hset("myhash", { field1: "val1", field2: "val2" });

                const result = await client.hgetall("myhash");
                // Result is a GlideRecord or object
                expect(result).toBeDefined();
            });

            it("should handle float responses", async () => {
                await client.set("floatkey", "3.14");
                const result = await client.incrByFloat("floatkey", 0.01);
                expect(result).toBeCloseTo(3.15, 2);
            });

            it("should handle binary data", async () => {
                const binaryData = Buffer.from([0x00, 0x01, 0x02, 0xff]);
                await client.set("binary-key", binaryData);

                const result = await client.get("binary-key", {
                    decoder: Decoder.Bytes,
                });
                expect(Buffer.isBuffer(result)).toBe(true);
                expect(result).toEqual(binaryData);
            });

            it("should handle large values", async () => {
                const largeValue = "x".repeat(1024 * 1024); // 1MB
                await client.set("large-key", largeValue);

                const result = await client.get("large-key");
                expect(result).toBe(largeValue);
            });
        });

        describe("Batch Operations (Pipeline)", () => {
            it("should run pipeline batch", async () => {
                const batch = new Batch(false);
                batch.set("batch-key1", "val1");
                batch.set("batch-key2", "val2");
                batch.get("batch-key1");
                batch.get("batch-key2");

                const results = await client.exec(batch, true);
                expect(results).toEqual(["OK", "OK", "val1", "val2"]);
            });

            it("should handle errors in pipeline batch", async () => {
                const batch = new Batch(false);
                batch.set("err-key", "not-a-number");
                batch.incr("err-key"); // This will error
                batch.get("err-key");

                const results = await client.exec(batch, false);
                expect(results?.[0]).toBe("OK");
                // Second result should be an error object
                expect(results?.[1]).not.toBeNull();
                expect((results?.[1] as Error).message).toContain("integer");
                expect(results?.[2]).toBe("not-a-number");
            });

            it("should return RequestError instances for batch errors", async () => {
                // Run a batch with an intentional error (e.g., LPOP on a string key)
                await client.set("batch-error-key", "hello");

                const batch = new Batch(false); // non-atomic pipeline
                batch.set("batch-error-ok", "value");
                batch.lpop("batch-error-key"); // WRONGTYPE error
                batch.get("batch-error-ok");

                const result = await client.exec(batch, false); // raiseOnError=false

                expect(result).not.toBeNull();
                expect(result!.length).toBe(3);
                expect(result![0]).toBe("OK");
                expect(result![1]).toBeInstanceOf(RequestError);
                expect((result![1] as RequestError).message).toContain(
                    "WRONGTYPE",
                );
                expect(result![2]).toBe("value");
            });

            it("should run large batch", async () => {
                const batch = new Batch(false);
                const count = 100;

                for (let i = 0; i < count; i++) {
                    batch.set(`batch-${i}`, `value-${i}`);
                }

                for (let i = 0; i < count; i++) {
                    batch.get(`batch-${i}`);
                }

                const results = await client.exec(batch, true);
                expect(results?.length).toBe(count * 2);

                // First 100 should be OK
                for (let i = 0; i < count; i++) {
                    expect(results?.[i]).toBe("OK");
                }

                // Next 100 should be values
                for (let i = 0; i < count; i++) {
                    expect(results?.[count + i]).toBe(`value-${i}`);
                }
            });
        });

        describe("Transaction Operations", () => {
            it("should run atomic transaction", async () => {
                const tx = new Transaction();
                tx.set("tx-key1", "val1");
                tx.set("tx-key2", "val2");
                tx.get("tx-key1");

                const results = await client.exec(tx, true);
                expect(results).toEqual(["OK", "OK", "val1"]);
            });

            it("should handle transaction with watch", async () => {
                await client.set("watched-key", "initial");

                // Watch the key
                await client.watch(["watched-key"]);

                const tx = new Transaction();
                tx.set("watched-key", "modified");
                tx.get("watched-key");

                const results = await client.exec(tx, true);
                expect(results).toEqual(["OK", "modified"]);
            });
        });

        describe("Script Operations", () => {
            it("should run Lua script", async () => {
                const script = new Script("return ARGV[1]");

                try {
                    const result = await client.invokeScript(script, {
                        args: ["hello"],
                    });
                    expect(result).toBe("hello");
                } finally {
                    script.release();
                }
            });

            it("should run script with keys", async () => {
                await client.set("script-key", "script-value");

                const script = new Script("return redis.call('GET', KEYS[1])");

                try {
                    const result = await client.invokeScript(script, {
                        keys: ["script-key"],
                    });
                    expect(result).toBe("script-value");
                } finally {
                    script.release();
                }
            });

            it("should cache script and reuse", async () => {
                const script = new Script("return ARGV[1] .. ARGV[2]");

                try {
                    // First call loads the script
                    const result1 = await client.invokeScript(script, {
                        args: ["hello", "world"],
                    });
                    expect(result1).toBe("helloworld");

                    // Second call uses cached EVALSHA
                    const result2 = await client.invokeScript(script, {
                        args: ["foo", "bar"],
                    });
                    expect(result2).toBe("foobar");
                } finally {
                    script.release();
                }
            });
        });

        describe("Error Handling", () => {
            it("should throw RequestError for invalid commands", async () => {
                await client.set("string-key", "value");

                await expect(
                    client.lpush("string-key", ["item"]),
                ).rejects.toThrow(RequestError);
            });

            it("should throw error after close", async () => {
                const tempClient = await GlideClient.createClient({
                    addresses: [parseEndpoint(STANDALONE_PORT)],
                });

                tempClient.close();

                // Commands after close should fail
                await expect(tempClient.get("key")).rejects.toThrow();
            });
        });

        describe("Error Propagation", () => {
            it("should throw ClosingError for script on closed client", async () => {
                const tempClient = await GlideClient.createClient({
                    addresses: [parseEndpoint(STANDALONE_PORT)],
                });
                tempClient.close();
                const script = new Script("return 1");
                await expect(tempClient.invokeScript(script)).rejects.toThrow(
                    /closed/i,
                );
                script.release();
            });

            it("should throw ClosingError for updateConnectionPassword on closed client", async () => {
                const tempClient = await GlideClient.createClient({
                    addresses: [parseEndpoint(STANDALONE_PORT)],
                });
                tempClient.close();
                await expect(
                    tempClient.updateConnectionPassword("newpass"),
                ).rejects.toThrow(/closed/i);
            });
        });

        describe("Connection Management", () => {
            it("should handle multiple sequential clients", async () => {
                for (let i = 0; i < 3; i++) {
                    const tempClient = await GlideClient.createClient({
                        addresses: [parseEndpoint(STANDALONE_PORT)],
                    });

                    const result = await tempClient.set(
                        `seq-key-${i}`,
                        `val-${i}`,
                    );
                    expect(result).toBe("OK");

                    tempClient.close();
                }
            });

            it("should handle concurrent requests", async () => {
                const promises = [];

                for (let i = 0; i < 100; i++) {
                    promises.push(client.set(`concurrent-${i}`, `value-${i}`));
                }

                const results = await Promise.all(promises);
                expect(results.every((r) => r === "OK")).toBe(true);

                // Verify all were set
                const getPromises = [];

                for (let i = 0; i < 100; i++) {
                    getPromises.push(client.get(`concurrent-${i}`));
                }

                const values = await Promise.all(getPromises);

                for (let i = 0; i < 100; i++) {
                    expect(values[i]).toBe(`value-${i}`);
                }
            });
        });

        describe("Inflight Limits", () => {
            it("should enforce inflight limit for batches", async () => {
                const limitedClient = await GlideClient.createClient({
                    addresses: [parseEndpoint(STANDALONE_PORT)],
                    inflightRequestsLimit: 1,
                });

                try {
                    // Send commands that should respect the limit
                    // With limit=1, rapid concurrent sends should trigger rejection
                    const promises = [];

                    for (let i = 0; i < 50; i++) {
                        promises.push(
                            limitedClient.set(`inflight-${i}`, `val-${i}`),
                        );
                    }

                    const results = await Promise.allSettled(promises);
                    // Some should succeed, some may be rejected due to inflight limit
                    const fulfilled = results.filter(
                        (r) => r.status === "fulfilled",
                    );
                    const rejected = results.filter(
                        (r) => r.status === "rejected",
                    );

                    // At least some should have been fulfilled
                    expect(fulfilled.length).toBeGreaterThan(0);

                    // If any were rejected, they should have the right error message
                    for (const r of rejected) {
                        if (r.status === "rejected") {
                            expect(r.reason.message).toContain("Inflight");
                        }
                    }
                } finally {
                    limitedClient.close();
                }
            });

            it("should enforce inflight limit for script invocations", async () => {
                const limitedClient = await GlideClient.createClient({
                    addresses: [parseEndpoint(STANDALONE_PORT)],
                    inflightRequestsLimit: 1,
                });

                try {
                    const script = new Script("return ARGV[1]");

                    try {
                        const promises = [];

                        for (let i = 0; i < 50; i++) {
                            promises.push(
                                limitedClient.invokeScript(script, {
                                    args: [`val-${i}`],
                                }),
                            );
                        }

                        const results = await Promise.allSettled(promises);
                        const fulfilled = results.filter(
                            (r) => r.status === "fulfilled",
                        );
                        const rejected = results.filter(
                            (r) => r.status === "rejected",
                        );

                        // At least some should have been fulfilled
                        expect(fulfilled.length).toBeGreaterThan(0);

                        // If any were rejected, they should have the right error message
                        for (const r of rejected) {
                            if (r.status === "rejected") {
                                expect(r.reason.message).toContain("Inflight");
                            }
                        }
                    } finally {
                        script.release();
                    }
                } finally {
                    limitedClient.close();
                }
            });

            it("should enforce inflight limit for batch operations", async () => {
                const limitedClient = await GlideClient.createClient({
                    addresses: [parseEndpoint(STANDALONE_PORT)],
                    inflightRequestsLimit: 1,
                });

                try {
                    // Occupy the single inflight slot with a blocking command
                    const blockingKey = `{nonexisting}:inflight-batch-${Date.now()}`;
                    // Use short timeout and catch the rejection from close()
                    const blockingPromise = limitedClient
                        .blpop([blockingKey], 1)
                        .catch(() => {
                            /* expected to be rejected by close() */
                        });

                    // Now try to send a batch while the slot is occupied
                    const batch = new Batch(false);
                    batch.set("batch-inflight-1", "val1");
                    batch.get("batch-inflight-1");

                    await expect(
                        limitedClient.exec(batch, true),
                    ).rejects.toThrow(RequestError);

                    await blockingPromise;
                } finally {
                    limitedClient.close();
                }
            });
        });

        describe("Protocol Versions", () => {
            it("should work with RESP2", async () => {
                const resp2Client = await GlideClient.createClient({
                    addresses: [parseEndpoint(STANDALONE_PORT)],
                    protocol: ProtocolVersion.RESP2,
                });

                try {
                    const result = await resp2Client.set(
                        "resp2-key",
                        "resp2-value",
                    );
                    expect(result).toBe("OK");

                    const getResult = await resp2Client.get("resp2-key");
                    expect(getResult).toBe("resp2-value");
                } finally {
                    resp2Client.close();
                }
            });

            it("should work with RESP3", async () => {
                const resp3Client = await GlideClient.createClient({
                    addresses: [parseEndpoint(STANDALONE_PORT)],
                    protocol: ProtocolVersion.RESP3,
                });

                try {
                    const result = await resp3Client.set(
                        "resp3-key",
                        "resp3-value",
                    );
                    expect(result).toBe("OK");

                    const getResult = await resp3Client.get("resp3-key");
                    expect(getResult).toBe("resp3-value");
                } finally {
                    resp3Client.close();
                }
            });
        });

        describe("Client Lifecycle", () => {
            it("should reject in-flight requests on close", async () => {
                const tempClient = await GlideClient.createClient({
                    addresses: [parseEndpoint(STANDALONE_PORT)],
                });

                const promises = [];

                for (let i = 0; i < 10; i++) {
                    promises.push(tempClient.get(`lifecycle-key-${i}`));
                }

                tempClient.close();

                // Some may resolve (completed before close), others should reject
                const results = await Promise.allSettled(promises);
                const rejected = results.filter((r) => r.status === "rejected");

                // Any rejected promises should be ClosingError
                for (const result of rejected) {
                    expect(
                        (result as PromiseRejectedResult).reason,
                    ).toBeInstanceOf(ClosingError);
                }

                // At minimum, the client should be closed after close()
                expect(
                    (tempClient as unknown as { isClosed: boolean }).isClosed,
                ).toBe(true);
            });

            it("should handle double close gracefully", async () => {
                const tempClient = await GlideClient.createClient({
                    addresses: [parseEndpoint(STANDALONE_PORT)],
                });

                await tempClient.set("double-close-key", "value");
                tempClient.close();

                // Second close should not throw
                expect(() => tempClient.close()).not.toThrow();
            });

            it("should throw ClosingError for operations after close", async () => {
                const tempClient = await GlideClient.createClient({
                    addresses: [parseEndpoint(STANDALONE_PORT)],
                });

                tempClient.close();

                await expect(tempClient.get("key")).rejects.toThrow(
                    ClosingError,
                );
            });
        });
    });

    describe("GlideClusterClient", () => {
        let clusterClient: GlideClusterClient;

        beforeAll(async () => {
            const endpoints = CLUSTER_PORTS.split(",").map(parseEndpoint);
            clusterClient = await GlideClusterClient.createClient({
                addresses: endpoints,
            });
        });

        afterAll(async () => {
            if (clusterClient) {
                clusterClient.close();
            }
        });

        afterEach(async () => {
            await clusterClient.flushall();
        });

        describe("Cluster Commands", () => {
            it("should run commands across cluster", async () => {
                // Use same hash tag so keys go to same slot
                await clusterClient.set("{test}:key1", "alice");
                await clusterClient.set("{test}:key2", "bob");

                const result1 = await clusterClient.get("{test}:key1");
                const result2 = await clusterClient.get("{test}:key2");

                expect(result1).toBe("alice");
                expect(result2).toBe("bob");
            });

            it("should handle cluster info command", async () => {
                const info = await clusterClient.info({
                    sections: [InfoOptions.Cluster],
                    route: "randomNode",
                });
                // With randomNode route, response is a string from a single node
                expect(typeof info).toBe("string");
                expect(info).toContain("cluster_enabled");
            });
        });

        describe("Cluster Scan", () => {
            it("should scan keys across cluster", async () => {
                // Set some keys
                for (let i = 0; i < 10; i++) {
                    await clusterClient.set(`scan-key-${i}`, `value-${i}`);
                }

                // Scan all keys
                const allKeys: GlideString[] = [];
                let cursor = new ClusterScanCursor();

                do {
                    const [newCursor, keys] = await clusterClient.scan(cursor);
                    cursor = newCursor;
                    allKeys.push(...keys);
                } while (!cursor.isFinished());

                expect(allKeys.length).toBeGreaterThanOrEqual(10);
            });

            it("should scan with pattern match", async () => {
                for (let i = 0; i < 5; i++) {
                    await clusterClient.set(`pattern-a-${i}`, "val");
                    await clusterClient.set(`pattern-b-${i}`, "val");
                }

                const matchedKeys: GlideString[] = [];
                let cursor = new ClusterScanCursor();

                do {
                    const [newCursor, keys] = await clusterClient.scan(cursor, {
                        match: "pattern-a-*",
                    });
                    cursor = newCursor;
                    matchedKeys.push(...keys);
                } while (!cursor.isFinished());

                expect(matchedKeys.length).toBe(5);
                matchedKeys.forEach((key) => {
                    expect(key.toString()).toMatch(/^pattern-a-/);
                });
            });
        });

        describe("Cluster Batch Operations", () => {
            it("should run cluster batch with same slot keys", async () => {
                const batch = new ClusterBatch(false);
                batch.set("{slot}:key1", "val1");
                batch.set("{slot}:key2", "val2");
                batch.get("{slot}:key1");

                const results = await clusterClient.exec(batch, true);
                expect(results).toEqual(["OK", "OK", "val1"]);
            });

            it("should run cluster transaction", async () => {
                const tx = new ClusterTransaction();
                tx.set("{tx-slot}:key1", "val1");
                tx.set("{tx-slot}:key2", "val2");
                tx.mget(["{tx-slot}:key1", "{tx-slot}:key2"]);

                const results = await clusterClient.exec(tx, true);
                expect(results?.[0]).toBe("OK");
                expect(results?.[1]).toBe("OK");
                expect(results?.[2]).toEqual(["val1", "val2"]);
            });
        });

        describe("Cluster Routing", () => {
            it("should route to all primaries", async () => {
                const result = await clusterClient.dbsize({
                    route: "allPrimaries",
                });
                // Result is a map of node -> count or a number
                expect(result).toBeDefined();
            });

            it("should route to random node", async () => {
                const result = await clusterClient.info({
                    sections: [InfoOptions.Server],
                    route: "randomNode",
                });
                // Info with routing returns object mapping
                expect(result).toBeDefined();
            });
        });
    });

    describe("Response Buffering", () => {
        let client: GlideClient;

        beforeAll(async () => {
            client = await GlideClient.createClient({
                addresses: [parseEndpoint(STANDALONE_PORT)],
            });
        });

        afterAll(async () => {
            if (client) {
                client.close();
            }
        });

        it("should handle rapid fire commands", async () => {
            const count = 1000;
            const promises = [];

            for (let i = 0; i < count; i++) {
                promises.push(client.incr("rapid-counter"));
            }

            const results = await Promise.all(promises);

            // All increments should complete
            expect(results.length).toBe(count);

            // Final value should be count
            const finalValue = await client.get("rapid-counter");
            expect(parseInt(finalValue as string, 10)).toBe(count);
        });

        it("should handle mixed command types rapidly", async () => {
            const promises = [];

            for (let i = 0; i < 100; i++) {
                promises.push(client.set(`mixed-${i}`, `val-${i}`));
                promises.push(client.get(`mixed-${i}`));
                promises.push(client.del([`mixed-${i}`]));
            }

            const results = await Promise.all(promises);
            expect(results.length).toBe(300);
        });
    });
});
