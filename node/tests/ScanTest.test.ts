/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { afterAll, afterEach, beforeAll, describe } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import {
    ClusterScanCursor,
    Decoder,
    GlideClient,
    GlideClusterClient,
    GlideString,
    ObjectType,
    ProtocolVersion,
    GlideClusterClientConfiguration,
} from "..";
import { ValkeyCluster } from "../../utils/TestUtils.js";
import {
    flushAndCloseClient,
    getClientConfigurationOption,
    getServerVersion,
    parseEndpoints,
    waitForClusterReady as isClusterReadyWithExpectedNodeCount,
} from "./TestUtilities";

const TIMEOUT = 50000;

//cluster tests
describe("Scan GlideClusterClient", () => {
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
        `GlideClusterClient test basic cluster scan_%p`,
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            // Iterate over all keys in the cluster
            const [key1, key2, key3] = [
                `key:${uuidv4()}`,
                `key:${uuidv4()}`,
                `key:${uuidv4()}`,
            ];
            await client.mset([
                { key: key1, value: "value1" },
                { key: key2, value: "value2" },
                { key: key3, value: "value3" },
            ]);
            let cursor = new ClusterScanCursor();
            const allKeys: GlideString[] = [];
            let keys: GlideString[] = [];

            while (!cursor.isFinished()) {
                [cursor, keys] = await client.scan(cursor, { count: 10 });
                allKeys.push(...keys);
            }

            expect(allKeys).toHaveLength(3);
            expect(allKeys).toEqual(expect.arrayContaining([key1, key2, key3]));

            // Iterate over keys matching a pattern
            const [key4, key5] = ["notMykey", "somethingElse"];
            await client.mset([
                { key: key4, value: "value4" },
                { key: key5, value: "value5" },
            ]);
            cursor = new ClusterScanCursor();
            const matchedKeys: GlideString[] = [];

            while (!cursor.isFinished()) {
                [cursor, keys] = await client.scan(cursor, {
                    match: "*key*",
                    count: 10,
                });
                matchedKeys.push(...keys);
            }

            expect(matchedKeys).toEqual(
                expect.arrayContaining([key1, key2, key3, key4]),
            );
            expect(matchedKeys).not.toContain("somethingElse");

            // Iterate over keys of a specific type
            await client.sadd("thisIsASet", ["value4"]);
            cursor = new ClusterScanCursor();
            const stringKeys: GlideString[] = [];

            while (!cursor.isFinished()) {
                [cursor, keys] = await client.scan(cursor, {
                    type: ObjectType.STRING,
                });
                stringKeys.push(...keys);
            }

            expect(stringKeys).toEqual(
                expect.arrayContaining([key1, key2, key3, key4, key5]),
            );
            expect(stringKeys).not.toContain("thisIsASet");
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `GlideClusterClient simple scan with encoding %p`,
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            const expectedKeys = Array.from(
                { length: 100 },
                () => `key:${uuidv4()}`,
            );
            for (const key1 of expectedKeys)
                expect(await client.set(key1, "value")).toEqual("OK");
            const expectedEncodedKeys = expectedKeys.map((key) =>
                Buffer.from(key),
            );

            let cursor = new ClusterScanCursor();
            let keys: GlideString[] = [];
            const allKeys: GlideString[] = [];

            while (!cursor.isFinished()) {
                [cursor, keys] = await client.scan(cursor, {
                    decoder: Decoder.Bytes,
                });
                allKeys.push(...keys);
            }

            expect(allKeys).toEqual(
                expect.arrayContaining(expectedEncodedKeys),
            );
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `GlideClusterClient scan with object type and pattern%p`,
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            const expectedKeys = Array.from(
                { length: 100 },
                () => `key:${uuidv4()}`,
            );
            for (const key1 of expectedKeys)
                expect(await client.set(key1, "value")).toEqual("OK");
            const unexpectedTypeKeys = Array.from(
                { length: 100 },
                () => `key:${uuidv4() + 100}`,
            );
            for (const key2 of unexpectedTypeKeys)
                expect(await client.sadd(key2, ["value"])).toEqual(1);
            const unexpectedPatternKeys = Array.from(
                { length: 100 },
                () => `${uuidv4() + 200}`,
            );
            for (const key3 of unexpectedPatternKeys)
                expect(await client.set(key3, "value")).toEqual("OK");

            let cursor = new ClusterScanCursor();
            let keys: GlideString[] = [];
            const allKeys: GlideString[] = [];

            while (!cursor.isFinished()) {
                [cursor, keys] = await client.scan(cursor, {
                    match: "key*",
                    type: ObjectType.STRING,
                });
                allKeys.push(...keys);
            }

            expect(allKeys).toEqual(expect.arrayContaining(expectedKeys));
            expect(allKeys).toEqual(
                expect.not.arrayContaining(unexpectedTypeKeys),
            );
            expect(allKeys).toEqual(
                expect.not.arrayContaining(unexpectedPatternKeys),
            );
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `GlideClusterClient scan with count%p`,
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            const expectedKeys = Array.from(
                { length: 100 },
                () => `key:${uuidv4()}`,
            );
            for (const key of expectedKeys)
                expect(await client.set(key, "value")).toEqual("OK");

            let cursor = new ClusterScanCursor();
            let keysOf1: GlideString[] = [];
            let keysOf100: GlideString[] = [];
            const allKeys: GlideString[] = [];
            let successfulComparedScans = 0;

            while (!cursor.isFinished()) {
                [cursor, keysOf1] = await client.scan(cursor, { count: 1 });
                allKeys.push(...keysOf1);
                if (cursor.isFinished()) break;
                [cursor, keysOf100] = await client.scan(cursor, { count: 100 });
                allKeys.push(...keysOf100);
                if (keysOf1.length < keysOf100.length)
                    successfulComparedScans += 1;
            }

            expect(allKeys).toEqual(expect.arrayContaining(expectedKeys));
            expect(successfulComparedScans).toBeGreaterThan(0);
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `GlideClusterClient scan with match%p`,
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            const expectedKeys = Array.from(
                { length: 100 },
                () => `key:${uuidv4()}`,
            );
            for (const key of expectedKeys)
                expect(await client.set(key, "value")).toEqual("OK");
            const encodedExpectedKeys = expectedKeys.map((key) =>
                Buffer.from(key),
            );
            const unexpectedKeys = Array.from(
                { length: 100 },
                () => `${uuidv4()}`,
            );
            for (const key of unexpectedKeys)
                expect(await client.set(key, "value")).toEqual("OK");
            const encodedUnexpectedKeys = unexpectedKeys.map((key) =>
                Buffer.from(key),
            );

            let cursor = new ClusterScanCursor();
            let keys: GlideString[] = [];
            const allKeys: GlideString[] = [];

            while (!cursor.isFinished()) {
                [cursor, keys] = await client.scan(cursor, {
                    match: "key*",
                    decoder: Decoder.Bytes,
                });
                allKeys.push(...keys);
            }

            expect(allKeys).toEqual(
                expect.arrayContaining(encodedExpectedKeys),
            );
            expect(allKeys).toEqual(
                expect.not.arrayContaining(encodedUnexpectedKeys),
            );
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `GlideClusterClient scan with different types%p`,
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            const stringKeys = Array.from(
                { length: 100 },
                () => `key:${uuidv4()}`,
            );
            for (const key of stringKeys)
                expect(await client.set(key, "value")).toEqual("OK");
            const encodedStringKeys = stringKeys.map((key) => Buffer.from(key));

            const setKeys = Array.from(
                { length: 100 },
                () => `stringKey:${uuidv4() + 100}`,
            );
            for (const key of setKeys)
                expect(await client.sadd(key, ["value"])).toEqual(1);
            const encodedSetKeys = setKeys.map((key) => Buffer.from(key));

            const hashKeys = Array.from(
                { length: 100 },
                () => `hashKey:${uuidv4() + 200}`,
            );
            for (const key of hashKeys)
                expect(await client.hset(key, { field: "value" })).toEqual(1);
            const encodedHashKeys = hashKeys.map((key) => Buffer.from(key));

            const listKeys = Array.from(
                { length: 100 },
                () => `listKey:${uuidv4() + 300}`,
            );
            for (const key of listKeys)
                expect(await client.lpush(key, ["value"])).toEqual(1);
            const encodedListKeys = listKeys.map((key) => Buffer.from(key));

            const zsetKeys = Array.from(
                { length: 100 },
                () => `key:${uuidv4() + 400}`,
            );
            for (const key of zsetKeys)
                expect(await client.zadd(key, { value: 1 })).toEqual(1);
            const encodedZsetKeys = zsetKeys.map((key) => Buffer.from(key));

            const streamKeys = Array.from(
                { length: 100 },
                () => `key:${uuidv4() + 500}`,
            );
            for (const key of streamKeys)
                await client.xadd(key, [["field", "value"]]);
            const encodedStreamKeys = streamKeys.map((key) => Buffer.from(key));

            let cursor = new ClusterScanCursor();
            let keys: GlideString[] = [];
            const allKeys: GlideString[] = [];

            while (!cursor.isFinished()) {
                [cursor, keys] = await client.scan(cursor, {
                    type: ObjectType.SET,
                    decoder: Decoder.Bytes,
                });
                allKeys.push(...keys);
            }

            expect(allKeys).toEqual(expect.arrayContaining(encodedSetKeys));
            expect(allKeys).toEqual(
                expect.not.arrayContaining(encodedListKeys),
            );
            expect(allKeys).toEqual(
                expect.not.arrayContaining(encodedStringKeys),
            );
            expect(allKeys).toEqual(
                expect.not.arrayContaining(encodedHashKeys),
            );
            expect(allKeys).toEqual(
                expect.not.arrayContaining(encodedZsetKeys),
            );
            expect(allKeys).toEqual(
                expect.not.arrayContaining(encodedStreamKeys),
            );
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `GlideClusterClient scan with allowNonCoveredSlots %p`,
        async (protocol) => {
            const testCluster = await ValkeyCluster.createCluster(
                true,
                3,
                0,
                getServerVersion,
            );
            const config: GlideClusterClientConfiguration = {
                addresses: testCluster
                    .getAddresses()
                    .map(([host, port]) => ({ host, port })),
                protocol,
            };
            const testClient = await GlideClusterClient.createClient(config);

            try {
                for (let i = 0; i < 10000; i++) {
                    const result = await testClient.set(`${uuidv4()}`, "value");
                    expect(result).toBe("OK");
                }

                // Perform an initial scan to ensure all works as expected
                let cursor = new ClusterScanCursor();
                let result = await testClient.scan(cursor);
                cursor = result[0];
                expect(cursor.isFinished()).toBe(false);

                // Set 'cluster-require-full-coverage' to 'no' to allow operations with missing slots
                await testClient.configSet({
                    "cluster-require-full-coverage": "no",
                });

                // Forget one server to simulate a node failure
                const addresses = testCluster.getAddresses();
                const addressToForget = addresses[0];
                const allOtherAddresses = addresses.slice(1);
                const idToForget = await testClient.customCommand(
                    ["CLUSTER", "MYID"],
                    {
                        route: {
                            type: "routeByAddress",
                            host: addressToForget[0],
                            port: addressToForget[1],
                        },
                    },
                );

                for (const address of allOtherAddresses) {
                    await testClient.customCommand(
                        ["CLUSTER", "FORGET", idToForget as string],
                        {
                            route: {
                                type: "routeByAddress",
                                host: address[0],
                                port: address[1],
                            },
                        },
                    );
                }

                // Wait for the cluster to stabilize after forgetting a node
                const ready = await isClusterReadyWithExpectedNodeCount(
                    testClient,
                    allOtherAddresses.length,
                );
                expect(ready).toBe(true);

                // Attempt to scan without 'allowNonCoveredSlots', expecting an error
                // Since it might take time for the inner core to forget the missing node,
                // we retry the scan until the expected error is thrown.

                const maxRetries = 10;
                let retries = 0;
                let errorReceived = false;

                while (retries < maxRetries && !errorReceived) {
                    retries++;
                    cursor = new ClusterScanCursor();

                    try {
                        while (!cursor.isFinished()) {
                            result = await testClient.scan(cursor);
                            cursor = result[0];
                        }

                        // If scan completes without error, wait and retry
                        await new Promise((resolve) =>
                            setTimeout(resolve, 1000),
                        );
                    } catch (error) {
                        if (
                            error instanceof Error &&
                            error.message.includes(
                                "Could not find an address covering a slot, SCAN operation cannot continue",
                            )
                        ) {
                            // Expected error occurred
                            errorReceived = true;
                        } else {
                            // Unexpected error, rethrow
                            throw error;
                        }
                    }
                }

                expect(errorReceived).toBe(true);

                // Perform scan with 'allowNonCoveredSlots: true'
                cursor = new ClusterScanCursor();

                while (!cursor.isFinished()) {
                    result = await testClient.scan(cursor, {
                        allowNonCoveredSlots: true,
                    });
                    cursor = result[0];
                }

                expect(cursor.isFinished()).toBe(true);

                // Get keys using 'KEYS *' from the remaining nodes
                const keys: GlideString[] = [];

                for (const address of allOtherAddresses) {
                    const result = await testClient.customCommand(
                        ["KEYS", "*"],
                        {
                            route: {
                                type: "routeByAddress",
                                host: address[0],
                                port: address[1],
                            },
                        },
                    );
                    keys.push(...(result as GlideString[]));
                }

                // Scan again with 'allowNonCoveredSlots: true' and collect results
                cursor = new ClusterScanCursor();
                const results: GlideString[] = [];

                while (!cursor.isFinished()) {
                    result = await testClient.scan(cursor, {
                        allowNonCoveredSlots: true,
                    });
                    results.push(...result[1]);
                    cursor = result[0];
                }

                // Compare the sets of keys obtained from 'KEYS *' and 'SCAN'
                expect(new Set(results)).toEqual(new Set(keys));
            } finally {
                testClient.close();
                await testCluster.close();
            }
        },
        TIMEOUT,
    );
});

//standalone tests
describe("Scan GlideClient", () => {
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
        `GlideClient test basic scan_%p`,
        async (protocol) => {
            client = await GlideClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            // Iterate over all keys in the cluster
            const [key1, key2, key3] = [
                `key:${uuidv4()}`,
                `key:${uuidv4()}`,
                `key:${uuidv4()}`,
            ];
            await client.mset([
                { key: key1, value: "value1" },
                { key: key2, value: "value2" },
                { key: key3, value: "value3" },
            ]);
            let cursor: GlideString = "0";
            const allKeys: GlideString[] = [];
            let keys: GlideString[] = [];

            do {
                [cursor, keys] = await client.scan(cursor, { count: 10 });
                allKeys.push(...keys);
            } while (cursor !== "0");

            expect(allKeys).toHaveLength(3);
            expect(allKeys).toEqual(expect.arrayContaining([key1, key2, key3]));

            // Iterate over keys matching a pattern
            const key4 = "notMykey";
            const key5 = "somethingElse";
            await client.mset([
                { key: key4, value: "value4" },
                { key: key5, value: "value5" },
            ]);
            cursor = "0";
            const matchedKeys: GlideString[] = [];

            do {
                [cursor, keys] = await client.scan(cursor, {
                    match: "*key*",
                    count: 10,
                });
                matchedKeys.push(...keys);
            } while (cursor !== "0");

            expect(matchedKeys).toEqual(
                expect.arrayContaining([key1, key2, key3, key4]),
            );
            expect(matchedKeys).not.toContain("somethingElse");

            // Iterate over keys of a specific type
            await client.sadd("thisIsASet", ["value4"]);
            cursor = "0";
            const stringKeys: GlideString[] = [];

            do {
                [cursor, keys] = await client.scan(cursor, {
                    type: ObjectType.STRING,
                });
                stringKeys.push(...keys);
            } while (cursor !== "0");

            expect(stringKeys).toEqual(
                expect.arrayContaining([key1, key2, key3, key4, key5]),
            );
            expect(stringKeys).not.toContain("thisIsASet");
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `GlideClient simple scan with encoding %p`,
        async (protocol) => {
            client = await GlideClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            const expectedKeys = Array.from(
                { length: 100 },
                () => `key:${uuidv4()}`,
            );
            for (const key1 of expectedKeys)
                expect(await client.set(key1, "value")).toEqual("OK");
            const expectedEncodedKeys = expectedKeys.map((key) =>
                Buffer.from(key),
            );

            let cursor: GlideString = "0";
            let keys: GlideString[] = [];
            const allKeys: GlideString[] = [];

            do {
                [cursor, keys] = await client.scan(cursor, {
                    decoder: Decoder.Bytes,
                });
                allKeys.push(...keys);
            } while (!(cursor as Buffer).equals(Buffer.from("0")));

            expect(allKeys).toEqual(
                expect.arrayContaining(expectedEncodedKeys),
            );
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `GlideClient scan with object type and pattern%p`,
        async (protocol) => {
            client = await GlideClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            const expectedKeys = Array.from(
                { length: 100 },
                () => `key:${uuidv4()}`,
            );
            for (const key1 of expectedKeys)
                expect(await client.set(key1, "value")).toEqual("OK");
            const unexpectedTypeKeys = Array.from(
                { length: 100 },
                () => `key:${uuidv4() + 100}`,
            );
            for (const key2 of unexpectedTypeKeys)
                expect(await client.sadd(key2, ["value"])).toEqual(1);
            const unexpectedPatternKeys = Array.from(
                { length: 100 },
                () => `${uuidv4() + 200}`,
            );
            for (const key3 of unexpectedPatternKeys)
                expect(await client.set(key3, "value")).toEqual("OK");

            let cursor: GlideString = "0";
            let keys: GlideString[] = [];
            const allKeys: GlideString[] = [];

            do {
                [cursor, keys] = await client.scan(cursor, {
                    match: "key*",
                    type: ObjectType.STRING,
                });
                allKeys.push(...keys);
            } while (cursor !== "0");

            expect(allKeys).toEqual(expect.arrayContaining(expectedKeys));
            expect(allKeys).toEqual(
                expect.not.arrayContaining(unexpectedTypeKeys),
            );
            expect(allKeys).toEqual(
                expect.not.arrayContaining(unexpectedPatternKeys),
            );
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `GlideClient scan with count%p`,
        async (protocol) => {
            client = await GlideClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            const expectedKeys = Array.from(
                { length: 100 },
                () => `key:${uuidv4()}`,
            );
            for (const key of expectedKeys)
                expect(await client.set(key, "value")).toEqual("OK");

            let cursor: GlideString = "0";
            let keysOf1: GlideString[] = [];
            let keysOf100: GlideString[] = [];
            const allKeys: GlideString[] = [];
            let successfulComparedScans = 0;

            do {
                [cursor, keysOf1] = await client.scan(cursor, { count: 1 });
                allKeys.push(...keysOf1);
                if (cursor == "0") break;
                [cursor, keysOf100] = await client.scan(cursor, { count: 100 });
                allKeys.push(...keysOf100);
                if (keysOf1.length < keysOf100.length)
                    successfulComparedScans += 1;
            } while (cursor !== "0");

            expect(allKeys).toEqual(expect.arrayContaining(expectedKeys));
            expect(successfulComparedScans).toBeGreaterThan(0);
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `GlideClient scan with match%p`,
        async (protocol) => {
            client = await GlideClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            const expectedKeys = Array.from(
                { length: 100 },
                () => `key:${uuidv4()}`,
            );
            for (const key of expectedKeys)
                expect(await client.set(key, "value")).toEqual("OK");
            const encodedExpectedKeys = expectedKeys.map((key) =>
                Buffer.from(key),
            );
            const unexpectedKeys = Array.from(
                { length: 100 },
                () => `${uuidv4()}`,
            );
            for (const key of unexpectedKeys)
                expect(await client.set(key, "value")).toEqual("OK");
            const encodedUnexpectedKeys = unexpectedKeys.map((key) =>
                Buffer.from(key),
            );

            let cursor: GlideString = "0";
            let keys: GlideString[] = [];
            const allKeysEncoded: GlideString[] = [];

            do {
                [cursor, keys] = await client.scan(cursor, {
                    match: "key*",
                    decoder: Decoder.Bytes,
                });
                allKeysEncoded.push(...keys);
            } while (!(cursor as Buffer).equals(Buffer.from("0")));

            expect(allKeysEncoded).toEqual(
                expect.arrayContaining(encodedExpectedKeys),
            );
            expect(allKeysEncoded).toEqual(
                expect.not.arrayContaining(encodedUnexpectedKeys),
            );
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `GlideClient scan with different types%p`,
        async (protocol) => {
            client = await GlideClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            const stringKeys = Array.from(
                { length: 100 },
                () => `key:${uuidv4()}`,
            );
            for (const key of stringKeys)
                expect(await client.set(key, "value")).toEqual("OK");
            const encodedStringKeys = stringKeys.map((key) => Buffer.from(key));

            const setKeys = Array.from(
                { length: 100 },
                () => `stringKey:${uuidv4() + 100}`,
            );
            for (const key of setKeys)
                expect(await client.sadd(key, ["value"])).toEqual(1);
            const encodedSetKeys = setKeys.map((key) => Buffer.from(key));

            const hashKeys = Array.from(
                { length: 100 },
                () => `hashKey:${uuidv4() + 200}`,
            );
            for (const key of hashKeys)
                expect(await client.hset(key, { field: "value" })).toEqual(1);
            const encodedHashKeys = hashKeys.map((key) => Buffer.from(key));

            const listKeys = Array.from(
                { length: 100 },
                () => `listKey:${uuidv4() + 300}`,
            );
            for (const key of listKeys)
                expect(await client.lpush(key, ["value"])).toEqual(1);
            const encodedListKeys = listKeys.map((key) => Buffer.from(key));

            const zsetKeys = Array.from(
                { length: 100 },
                () => `key:${uuidv4() + 400}`,
            );
            for (const key of zsetKeys)
                expect(await client.zadd(key, { value: 1 })).toEqual(1);
            const encodedZsetKeys = zsetKeys.map((key) => Buffer.from(key));

            const streamKeys = Array.from(
                { length: 100 },
                () => `key:${uuidv4() + 500}`,
            );
            for (const key of streamKeys)
                await client.xadd(key, [["field", "value"]]);
            const encodedStreamKeys = streamKeys.map((key) => Buffer.from(key));

            let cursor: GlideString = "0";
            let keys: GlideString[] = [];
            const allKeys: GlideString[] = [];

            do {
                [cursor, keys] = await client.scan(cursor, {
                    type: ObjectType.SET,
                    decoder: Decoder.Bytes,
                });
                allKeys.push(...keys);
            } while (!(cursor as Buffer).equals(Buffer.from("0")));

            expect(allKeys).toEqual(expect.arrayContaining(encodedSetKeys));
            expect(allKeys).toEqual(
                expect.not.arrayContaining(encodedListKeys),
            );
            expect(allKeys).toEqual(
                expect.not.arrayContaining(encodedStringKeys),
            );
            expect(allKeys).toEqual(
                expect.not.arrayContaining(encodedHashKeys),
            );
            expect(allKeys).toEqual(
                expect.not.arrayContaining(encodedZsetKeys),
            );
            expect(allKeys).toEqual(
                expect.not.arrayContaining(encodedStreamKeys),
            );
        },
        TIMEOUT,
    );
});
