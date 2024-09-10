/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { afterAll, afterEach, beforeAll, describe } from "@jest/globals";
import { ClusterScanCursor } from "glide-rs";
import {
    Decoder,
    GlideClusterClient,
    GlideString,
    ObjectType,
    ProtocolVersion
} from "..";
import { RedisCluster } from "../../utils/TestUtils.js";
import {
    flushAndCloseClient,
    getClientConfigurationOption,
    parseCommandLineArgs,
    parseEndpoints,
} from "./TestUtilities";


const TIMEOUT = 50000;

describe("Scan GlideClusterClient", () => {
    const testsFailed = 0;
    let cluster: RedisCluster;
    let client: GlideClusterClient;
    beforeAll(async () => {
        const clusterAddresses = parseCommandLineArgs()["cluster-endpoints"];
        // Connect to cluster or create a new one based on the parsed addresses
        cluster = clusterAddresses
            ? await RedisCluster.initFromExistingCluster(
                  parseEndpoints(clusterAddresses),
              )
            : // setting replicaCount to 1 to facilitate tests routed to replicas
              await RedisCluster.createCluster(true, 3, 1);
    }, 20000);

    afterEach(async () => {
        await flushAndCloseClient(true, cluster.getAddresses(), client);
    });

    afterAll(async () => {
        if (testsFailed === 0) {
            await cluster.close();
        }
    });

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `GlideClusterClient test basic cluster scan_%p`,
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            // Iterate over all keys in the cluster
            await client.mset([
                { key: "key1", value: "value1" },
                { key: "key2", value: "value2" },
                { key: "key3", value: "value3" },
            ]);
            let cursor = new ClusterScanCursor();
            const allKeys: GlideString[] = [];
            let keys: GlideString[] = [];

            while (!cursor.isFinished()) {
                [cursor, keys] = await client.scan(cursor, { count: 10 });
                allKeys.push(...keys);
            }

            expect(allKeys).toHaveLength(3);
            expect(allKeys).toEqual(expect.arrayContaining(["key1", "key2", "key3"]));

            // Iterate over keys matching a pattern
            await client.mset([{key: "key1", value: "value1"}, {key: "key2", value: "value2"}, {key: "notMykey", value: "value3"}, {key: "somethingElse", value: "value4"}]);
            cursor = new ClusterScanCursor();
            const matchedKeys: GlideString[] = [];
            while (!cursor.isFinished()) {
                [cursor, keys] = await client.scan(cursor, { match: "*key*", count: 10 });
                matchedKeys.push(...keys);
            }
            expect(matchedKeys).toEqual(expect.arrayContaining(["key1", "key2", "key3", "notMykey"]));
            expect(matchedKeys).not.toContain("somethingElse");

            // Iterate over keys of a specific type
            await client.mset([{key: "key1", value: "value1"}, {key: "key2", value: "value2"}, {key: "key3", value: "value3"}]);
            await client.sadd("thisIsASet", ["value4"]);
            cursor = new ClusterScanCursor();
            const stringKeys: GlideString[] = [];
            while (!cursor.isFinished()) {
                [cursor, keys] = await client.scan(cursor, { object: ObjectType.STRING });
                stringKeys.push(...keys);
            }
            expect(stringKeys).toEqual(expect.arrayContaining(["key1", "key2", "key3"]));
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
            const expectedKeys = Array.from({length: 100}, (_, i) => `key:${i}`);
            for (const key1 of expectedKeys)
                expect (await client.set(key1, "value")).toEqual("OK");
            const expectedEncodedKeys = expectedKeys.map((key)=> Buffer.from(key));

            let cursor = new ClusterScanCursor();
            let keys: GlideString[] = [];
            const allKeys: GlideString[] = [];
            while(!cursor.isFinished()){
                [cursor, keys] = await client.scan(cursor, {decoder: Decoder.Bytes});
                console.log(keys);
                allKeys.push(...keys);
            } 
            expect(allKeys).toEqual(expect.arrayContaining(expectedEncodedKeys));
        },
        TIMEOUT,
    );

    

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `GlideClusterClient scan with object type and pattern%p`,
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            const expectedKeys = Array.from({length: 100}, (_, i) => `key:${i}`);
            for (const key1 of expectedKeys)
                expect (await client.set(key1, "value")).toEqual("OK");
            const unexpectedTypeKeys = Array.from({length: 100}, (_, i) => `key:${i+100}`);
            for (const key2 of unexpectedTypeKeys)
                expect (await client.sadd(key2, ["value"])).toEqual(1);
            const unexpectedPatternKeys = Array.from({length:100}, (_,i)=>`${i+200}`)
            for (const key3 of unexpectedPatternKeys)
                expect (await client.set(key3, "value")).toEqual("OK");

            let cursor = new ClusterScanCursor();
            let keys: GlideString[] = [];
            const allKeys: GlideString[] = [];
            while(!cursor.isFinished()){
                [cursor, keys] = await client.scan(cursor, {match: "key*", object: ObjectType.STRING});
                allKeys.push(...keys);
            } 
            expect(allKeys).toEqual(expect.arrayContaining(expectedKeys));
            expect(allKeys).toEqual(expect.not.arrayContaining(unexpectedTypeKeys));
            expect(allKeys).toEqual(expect.not.arrayContaining(unexpectedPatternKeys));
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `GlideClusterClient scan with count%p`,
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            
            const expectedKeys = Array.from({length: 100}, (_, i) => `key:${i}`);
            for (const key of expectedKeys)
                expect(await client.set(key, "value")).toEqual("OK");

            let cursor = new ClusterScanCursor();
            let keysOf1: GlideString[] = [];
            let keysOf100: GlideString[] = [];
            const allKeys: GlideString[] = [];
            let successfulComparedScans = 0;
            while (!cursor.isFinished()) {
                [cursor, keysOf1] = await client.scan(cursor, {count: 1});
                console.log(keysOf1);
                allKeys.push(...keysOf1);
                if (cursor.isFinished())
                    break;
                [cursor, keysOf100] = await client.scan(cursor, {count: 100});
                console.log(keysOf100);
                allKeys.push(...keysOf100);
                if (keysOf1.length < keysOf100.length)
                    successfulComparedScans+=1;
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
            
            const expectedKeys = Array.from({length: 100}, (_, i) => `key:${i}`);
            for (const key of expectedKeys)
                expect(await client.set(key, "value")).toEqual("OK");
            const unexpectedKeys = Array.from({length: 100}, (_, i) => `${i}`);
            for (const key of unexpectedKeys)
                expect (await client.set(key, "value")).toEqual("OK");


            let cursor = new ClusterScanCursor();
            let keys: GlideString[] = [];
            const allKeys: GlideString[] = [];
            let successfulComparedScans = 0;
            while (!cursor.isFinished()) {
                [cursor, keys] = await client.scan(cursor, {match: "key*"});
                allKeys.push(...keys);
            } 
            expect(allKeys).toEqual(expect.arrayContaining(expectedKeys));
            expect(allKeys).toEqual(expect.not.arrayContaining(unexpectedKeys))
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `GlideClusterClient scan with different types%p`,
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            
            const stringKeys = Array.from({length: 100}, (_, i) => `key:${i}`);
            for (const key of stringKeys)
                expect(await client.set(key, "value")).toEqual("OK");

            const setKeys = Array.from({length: 100}, (_, i) => `stringKey:${i+100}`);
            for (const key of setKeys)
                expect(await client.sadd(key, ["value"])).toEqual(1);

            const hashKeys = Array.from({length: 100}, (_, i) => `hashKey:${i+200}`);
            for (const key of hashKeys)
                expect(await client.hset(key, {"field": "value"})).toEqual(1);

            const listKeys = Array.from({length: 100}, (_, i) => `listKey:${i+300}`);
            for (const key of listKeys)
                expect(await client.lpush(key, ["value"])).toEqual(1);
            const encodedListKeys = listKeys.map((key)=> Buffer.from(key));
            
            const zsetKeys = Array.from({length: 100}, (_, i) => `key:${i+400}`);
            for (const key of zsetKeys)
                expect(await client.zadd(key, {"value" : 1})).toEqual(1);
            
            const streamKeys = Array.from({length: 100}, (_, i) => `key:${i+500}`);
            for (const key of streamKeys)
                await client.xadd(key, [["field", "value"]]);
        
            let cursor = new ClusterScanCursor();
            let keys: GlideString[] = [];
            const allKeys: GlideString[] = [];
            while (!cursor.isFinished()) {
                [cursor, keys] = await client.scan(cursor, {object: ObjectType.SET});
                allKeys.push(...keys);
            } 
            expect(allKeys).toEqual(expect.arrayContaining(setKeys));
            expect(allKeys).toEqual(expect.not.arrayContaining(listKeys));
            expect(allKeys).toEqual(expect.not.arrayContaining(stringKeys));
            expect(allKeys).toEqual(expect.not.arrayContaining(hashKeys));
            expect(allKeys).toEqual(expect.not.arrayContaining(zsetKeys));
            expect(allKeys).toEqual(expect.not.arrayContaining(streamKeys));
        },
        TIMEOUT,
    );

});
