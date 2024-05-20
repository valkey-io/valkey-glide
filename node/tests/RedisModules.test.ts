/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */

import {
    afterAll,
    afterEach,
    beforeAll,
    describe,
    expect,
    it,
} from "@jest/globals";
import { BaseClientConfiguration, RedisClusterClient } from "../";
import {
    RedisCluster,
    flushallOnPort,
    parseCommandLineArgs,
    parseEndpoints,
} from "./TestUtilities";

type Context = {
    client: RedisClusterClient;
};

const TIMEOUT = 10000;

describe("RedisModules", () => {
    let testsFailed = 0;
    let cluster: RedisCluster;
    beforeAll(async () => {
        const clusterAddresses = parseCommandLineArgs()["cluster-endpoints"];
        // Connect to cluster or create a new one based on the parsed addresses
        cluster = clusterAddresses
            ? RedisCluster.initFromExistingCluster(
                  parseEndpoints(clusterAddresses),
              )
            : await RedisCluster.createCluster(true, 3, 0);
    }, 20000);

    afterEach(async () => {
        await Promise.all(cluster.ports().map((port) => flushallOnPort(port)));
    });

    afterAll(async () => {
        if (testsFailed === 0) {
            await cluster.close();
        }
    });

    const getOptions = (
        addresses: [string, number][],
    ): BaseClientConfiguration => {
        return {
            addresses: addresses.map(([host, port]) => ({
                host,
                port,
            })),
        };
    };

    it("simple json test", async () => {
        const client = await RedisClusterClient.createClient(
            getOptions(cluster.getAddresses()),
        );
        expect(await client.customCommand(["JSON.TYPE", "key"])).toBeNull();
        client.close();
    });
});
