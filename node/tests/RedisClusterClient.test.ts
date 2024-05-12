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
import { v4 as uuidv4 } from "uuid";

import {
    BaseClientConfiguration,
    ClosingError,
    ClusterTransaction,
    InfoOptions,
    ProtocolVersion,
    RedisClusterClient,
} from "..";
import { runBaseTests } from "./SharedTests";
import {
    RedisCluster,
    getFirstResult,
    parseCommandLineArgs,
    parseEndpoints,
    transactionTest,
} from "./TestUtilities";

type Context = {
    client: RedisClusterClient;
};

const TIMEOUT = 10000;

describe("RedisClusterClient", () => {
    let testsFailed = 0;
    let cluster: RedisCluster;
    let client: RedisClusterClient;
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
        protocol: ProtocolVersion,
    ): BaseClientConfiguration => {
        return {
            addresses: addresses.map(([host, port]) => ({
                host,
                port,
            })),
            protocol,
        };
    };

    runBaseTests<Context>({
        init: async (protocol, clientName?) => {
            const options = getOptions(cluster.getAddresses(), protocol);
            options.protocol = protocol;
            options.clientName = clientName;
            testsFailed += 1;
            client = await RedisClusterClient.createClient(options);
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
        },
        timeout: TIMEOUT,
    });

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `info with server and replication_%p`,
        async (protocol) => {
            client = await RedisClusterClient.createClient(
                getOptions(cluster.getAddresses(), protocol),
            );
            const info_server = getFirstResult(
                await client.info([InfoOptions.Server]),
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
                (clusterNodes as string)?.split("master").length - 1,
            ).toEqual(Object.keys(result).length);
            Object.values(result).every((item) => {
                expect(item).toEqual(expect.stringContaining("# Replication"));
                expect(item).toEqual(
                    expect.not.stringContaining("# Errorstats"),
                );
            });
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `info with server and randomNode route_%p`,
        async (protocol) => {
            client = await RedisClusterClient.createClient(
                getOptions(cluster.getAddresses(), protocol),
            );
            const result = await client.info(
                [InfoOptions.Server],
                "randomNode",
            );
            expect(typeof result).toEqual("string");
            expect(result).toEqual(expect.stringContaining("# Server"));
            expect(result).toEqual(expect.not.stringContaining("# Errorstats"));
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `route by address reaches correct node_%p`,
        async (protocol) => {
            // returns the line that contains the word "myself", up to that point. This is done because the values after it might change with time.
            const cleanResult = (value: string) => {
                return (
                    value
                        .split("\n")
                        .find((line: string) => line.includes("myself"))
                        ?.split("myself")[0] ?? ""
                );
            };

            client = await RedisClusterClient.createClient(
                getOptions(cluster.getAddresses(), protocol),
            );
            const result = cleanResult(
                (await client.customCommand(
                    ["cluster", "nodes"],
                    "randomNode",
                )) as string,
            );

            // check that routing without explicit port works
            const host = result.split(" ")[1].split("@")[0] ?? "";

            if (!host) {
                throw new Error("No host could be parsed");
            }

            const secondResult = cleanResult(
                (await client.customCommand(["cluster", "nodes"], {
                    type: "routeByAddress",
                    host,
                })) as string,
            );

            expect(result).toEqual(secondResult);

            const [host2, port] = host.split(":");

            // check that routing with explicit port works
            const thirdResult = cleanResult(
                (await client.customCommand(["cluster", "nodes"], {
                    type: "routeByAddress",
                    host: host2,
                    port: Number(port),
                })) as string,
            );

            expect(result).toEqual(thirdResult);
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `fail routing by address if no port is provided_%p`,
        async (protocol) => {
            client = await RedisClusterClient.createClient(
                getOptions(cluster.getAddresses(), protocol),
            );
            expect(() =>
                client.info(undefined, {
                    type: "routeByAddress",
                    host: "foo",
                }),
            ).toThrowError();
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `config get and config set transactions test_%p`,
        async (protocol) => {
            client = await RedisClusterClient.createClient(
                getOptions(cluster.getAddresses(), protocol),
            );
            const transaction = new ClusterTransaction();
            transaction.configSet({ timeout: "1000" });
            transaction.configGet(["timeout"]);
            const result = await client.exec(transaction);
            expect(result).toEqual(["OK", { timeout: "1000" }]);
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `can send transactions_%p`,
        async (protocol) => {
            client = await RedisClusterClient.createClient(
                getOptions(cluster.getAddresses(), protocol),
            );
            const transaction = new ClusterTransaction();
            const expectedRes = await transactionTest(transaction);
            const result = await client.exec(transaction);
            expect(result).toEqual(expectedRes);
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `can return null on WATCH transaction failures_%p`,
        async (protocol) => {
            const client1 = await RedisClusterClient.createClient(
                getOptions(cluster.getAddresses(), protocol),
            );
            const client2 = await RedisClusterClient.createClient(
                getOptions(cluster.getAddresses(), protocol),
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
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `echo with all nodes routing_%p`,
        async (protocol) => {
            client = await RedisClusterClient.createClient(
                getOptions(cluster.getAddresses(), protocol),
            );
            const message = uuidv4();
            const echoDict = await client.echo(message, "allNodes");

            expect(typeof echoDict).toBe("object");
            expect(Object.values(echoDict)).toEqual(
                expect.arrayContaining([message]),
            );
        },
        TIMEOUT,
    );
});
