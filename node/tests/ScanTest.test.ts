/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { afterAll, afterEach, beforeAll, describe } from "@jest/globals";
import { ClusterScanCursor } from "glide-rs";
import { GlideClusterClient, GlideString, ProtocolVersion } from "..";
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
        `test basic cluster scan_%p`,
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
            expect(allKeys).toEqual(
                expect.arrayContaining(["key1", "key2", "key3"]),
            );
            console.log(allKeys); // ["key1", "key2", "key3"]
        },
        TIMEOUT,
    );
});
