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
import {
    BaseClientConfiguration,
    ClusterTransaction,
    InfoOptions,
    ProtocolVersion,
    RedisClusterClient,
} from "..";
import { runBaseTests } from "./SharedTests";
import {
    RedisCluster,
    flushallOnPort,
    getFirstResult,
    transactionTest,
} from "./TestUtilities";

type Context = {
    client: RedisClusterClient;
};

const TIMEOUT = 10000;

describe("RedisClusterClient", () => {
    let testsFailed = 0;
    let cluster: RedisCluster;
    beforeAll(async () => {
        cluster = await RedisCluster.createCluster(3, 0);
    }, 20000);

    afterEach(async () => {
        await Promise.all(cluster.ports().map((port) => flushallOnPort(port)));
    });

    afterAll(async () => {
        if (testsFailed === 0) {
            await cluster.close();
        }
    });

    const getOptions = (ports: number[]): BaseClientConfiguration => {
        return {
            addresses: ports.map((port) => ({
                host: "localhost",
                port,
            })),
        };
    };

    runBaseTests<Context>({
        init: async (protocol, clientName?) => {
            const options = getOptions(cluster.ports());
            options.serverProtocol = protocol;
            options.clientName = clientName;
            testsFailed += 1;
            const client = await RedisClusterClient.createClient(options);
            return {
                context: {
                    client,
                },
                client,
            };
        },
        close: (context: Context, testSucceeded: boolean) => {
            if (testSucceeded) {
                testsFailed -= 1;
            }

            context.client.close();
        },
        timeout: TIMEOUT,
    });

    it(
        "info with server and replication",
        async () => {
            const client = await RedisClusterClient.createClient(
                getOptions(cluster.ports())
            );
            const info_server = getFirstResult(
                await client.info([InfoOptions.Server])
            );
            expect(info_server).toEqual(expect.stringContaining("# Server"));

            const result = (await client.info([
                InfoOptions.Replication,
            ])) as Record<string, string>;
            const clusterNodes = await client.customCommand([
                "CLUSTER",
                "NODES",
            ]);
            expect(
                (clusterNodes as string)?.split("master").length - 1
            ).toEqual(Object.keys(result).length);
            Object.values(result).every((item) => {
                expect(item).toEqual(expect.stringContaining("# Replication"));
                expect(item).toEqual(
                    expect.not.stringContaining("# Errorstats")
                );
            });
            client.close();
        },
        TIMEOUT
    );

    it(
        "info with server and randomNode route",
        async () => {
            const client = await RedisClusterClient.createClient(
                getOptions(cluster.ports())
            );
            const result = await client.info(
                [InfoOptions.Server],
                "randomNode"
            );
            expect(typeof result).toEqual("string");
            expect(result).toEqual(expect.stringContaining("# Server"));
            expect(result).toEqual(expect.not.stringContaining("# Errorstats"));
            client.close();
        },
        TIMEOUT
    );

    it(
        "config get and config set transactions test",
        async () => {
            const client = await RedisClusterClient.createClient(
                getOptions(cluster.ports())
            );
            const transaction = new ClusterTransaction();
            transaction.configSet({ timeout: "1000" });
            transaction.configGet(["timeout"]);
            const result = await client.exec(transaction);
            expect(result).toEqual(["OK", { timeout: "1000" }]);
            client.close();
        },
        TIMEOUT
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `can send transactions_%p`,
        async () => {
            const client = await RedisClusterClient.createClient(
                getOptions(cluster.ports())
            );
            const transaction = new ClusterTransaction();
            const expectedRes = transactionTest(transaction);
            const result = await client.exec(transaction);
            expect(result).toEqual(expectedRes);
            client.close();
        },
        TIMEOUT
    );

    it(
        "can return null on WATCH transaction failures",
        async () => {
            const client1 = await RedisClusterClient.createClient(
                getOptions(cluster.ports())
            );
            const client2 = await RedisClusterClient.createClient(
                getOptions(cluster.ports())
            );
            const transaction = new ClusterTransaction();
            transaction.get("key");
            const result1 = await client1.customCommand(["WATCH", "key"]);
            expect(result1).toEqual("OK");

            const result2 = await client2.set("key", "foo");
            expect(result2).toEqual("OK");

            const result3 = await client1.exec(transaction);
            expect(result3).toBeNull();

            client1.close();
            client2.close();
        },
        TIMEOUT
    );
});
