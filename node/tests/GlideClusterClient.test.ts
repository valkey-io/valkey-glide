/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import {
    afterAll,
    afterEach,
    beforeAll,
    describe,
    expect,
    it,
} from "@jest/globals";
import { gte } from "semver";
import { v4 as uuidv4 } from "uuid";
import {
    BitwiseOperation,
    ClusterTransaction,
    Decoder,
    FlushMode,
    FunctionListResponse,
    FunctionRestorePolicy,
    FunctionStatsSingleResponse,
    GeoUnit,
    GlideClusterClient,
    GlideReturnType,
    GlideString,
    InfoOptions,
    ListDirection,
    ProtocolVersion,
    RequestError,
    Routes,
    ScoreFilter,
    Script,
    SlotKeyTypes,
    SortOrder,
    convertRecordToGlideRecord,
} from "..";
import { ValkeyCluster } from "../../utils/TestUtils.js";
import { runBaseTests } from "./SharedTests";
import {
    checkClusterResponse,
    checkFunctionListResponse,
    checkFunctionStatsResponse,
    createLongRunningLuaScript,
    createLuaLibWithLongRunningFunction,
    flushAndCloseClient,
    generateLuaLibCode,
    getClientConfigurationOption,
    getFirstResult,
    getServerVersion,
    intoArray,
    intoString,
    parseEndpoints,
    transactionTest,
    validateTransactionResponse,
    waitForNotBusy,
} from "./TestUtilities";

const TIMEOUT = 50000;

describe("GlideClusterClient", () => {
    let testsFailed = 0;
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

    runBaseTests({
        init: async (protocol, configOverrides) => {
            const config = getClientConfigurationOption(
                cluster.getAddresses(),
                protocol,
                configOverrides,
            );

            testsFailed += 1;
            client = await GlideClusterClient.createClient(config);
            return {
                client,
                cluster,
            };
        },
        close: (testSucceeded: boolean) => {
            if (testSucceeded) {
                testsFailed -= 1;
            }
        },
        timeout: TIMEOUT,
    });

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `info with server and replication_%p`,
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            const info_server = getFirstResult(
                await client.info({ sections: [InfoOptions.Server] }),
            );
            expect(info_server).toEqual(expect.stringContaining("# Server"));

            const infoReplicationValues = Object.values(
                await client.info({ sections: [InfoOptions.Replication] }),
            );

            const replicationInfo = intoArray(infoReplicationValues);

            for (const item of replicationInfo) {
                expect(item).toContain("role:master");
                expect(item).toContain("# Replication");
            }
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `info with server and randomNode route_%p`,
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            const result = await client.info({
                sections: [InfoOptions.Server],
                route: "randomNode",
            });
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

            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            const result = cleanResult(
                intoString(
                    await client.customCommand(["cluster", "nodes"], {
                        route: "randomNode",
                    }),
                ),
            );

            // check that routing without explicit port works
            const host = result.split(" ")[1].split("@")[0] ?? "";

            if (!host) {
                throw new Error("No host could be parsed");
            }

            const secondResult = cleanResult(
                intoString(
                    await client.customCommand(["cluster", "nodes"], {
                        route: {
                            type: "routeByAddress",
                            host,
                        },
                    }),
                ),
            );

            expect(result).toEqual(secondResult);

            const [host2, port] = host.split(":");

            // check that routing with explicit port works
            const thirdResult = cleanResult(
                intoString(
                    await client.customCommand(["cluster", "nodes"], {
                        route: {
                            type: "routeByAddress",
                            host: host2,
                            port: Number(port),
                        },
                    }),
                ),
            );

            expect(result).toEqual(thirdResult);
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `fail routing by address if no port is provided_%p`,
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            await expect(
                client.info({
                    route: {
                        type: "routeByAddress",
                        host: "foo",
                    },
                }),
            ).rejects.toThrowError(RequestError);
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `dump and restore custom command_%p`,
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            const key = "key";
            const value = "value";
            const valueEncoded = Buffer.from(value);
            expect(await client.set(key, value)).toEqual("OK");
            // Since DUMP gets binary results, we cannot use the default decoder (string) here, so we expected to get an error.
            await expect(client.customCommand(["DUMP", key])).rejects.toThrow(
                "invalid utf-8 sequence",
            );

            const dumpResult = await client.customCommand(["DUMP", key], {
                decoder: Decoder.Bytes,
            });

            expect(await client.del([key])).toEqual(1);

            if (dumpResult instanceof Buffer) {
                // check the delete
                expect(await client.get(key)).toEqual(null);
                expect(
                    await client.customCommand(
                        ["RESTORE", key, "0", dumpResult],
                        { decoder: Decoder.Bytes },
                    ),
                ).toEqual("OK");
                // check the restore
                expect(await client.get(key)).toEqual(value);
                expect(
                    await client.get(key, { decoder: Decoder.Bytes }),
                ).toEqual(valueEncoded);
            }
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `config get and config set transactions test_%p`,
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            const transaction = new ClusterTransaction()
                .configSet({ timeout: "1000" })
                .configGet(["timeout"]);
            const result = await client.exec(transaction);
            expect(result).toEqual([
                "OK",
                convertRecordToGlideRecord({ timeout: "1000" }),
            ]);
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `can send transactions_%p`,
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            const transaction = new ClusterTransaction();

            const expectedRes = await transactionTest(
                transaction,
                cluster.getVersion(),
            );

            if (!cluster.checkIfServerVersionLessThan("7.0.0")) {
                transaction.publish("message", "key", true);
                expectedRes.push(['publish("message", "key", true)', 0]);

                transaction.pubsubShardChannels();
                expectedRes.push(["pubsubShardChannels()", []]);
                transaction.pubsubShardNumSub([]);
                expectedRes.push(["pubsubShardNumSub()", []]);
            }

            const result = await client.exec(transaction);
            validateTransactionResponse(result, expectedRes);
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `can return null on WATCH transaction failures_%p`,
        async (protocol) => {
            const client1 = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            const client2 = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            const transaction = new ClusterTransaction();
            transaction.get("key");
            const result1 = await client1.watch(["key"]);
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
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            const message = uuidv4();
            const echoDict = await client.echo(message, { route: "allNodes" });

            expect(typeof echoDict).toBe("object");
            expect(intoArray(echoDict)).toEqual(
                expect.arrayContaining(intoArray([message])),
            );
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `check that multi key command returns a cross slot error`,
        async (protocol) => {
            const client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            const promises: Promise<unknown>[] = [
                client.blpop(["abc", "zxy", "lkn"], 0.1),
                client.rename("abc", "zxy"),
                client.msetnx({ abc: "xyz", def: "abc", hij: "def" }),
                client.brpop(["abc", "zxy", "lkn"], 0.1),
                client.bitop(BitwiseOperation.AND, "abc", ["zxy", "lkn"]),
                client.smove("abc", "zxy", "value"),
                client.renamenx("abc", "zxy"),
                client.sinter(["abc", "zxy", "lkn"]),
                client.sinterstore("abc", ["zxy", "lkn"]),
                client.zinterstore("abc", ["zxy", "lkn"]),
                client.zunionstore("abc", ["zxy", "lkn"]),
                client.sunionstore("abc", ["zxy", "lkn"]),
                client.sunion(["abc", "zxy", "lkn"]),
                client.pfcount(["abc", "zxy", "lkn"]),
                client.pfmerge("abc", ["def", "ghi"]),
                client.sdiff(["abc", "zxy", "lkn"]),
                client.sdiffstore("abc", ["zxy", "lkn"]),
                client.sortStore("abc", "zyx"),
                client.sortStore("abc", "zyx", { isAlpha: true }),
                client.bzpopmax(["abc", "def"], 0.5),
                client.bzpopmin(["abc", "def"], 0.5),
                client.xread({ abc: "0-0", zxy: "0-0", lkn: "0-0" }),
                client.xreadgroup("_", "_", { abc: ">", zxy: ">", lkn: ">" }),
            ];

            if (gte(cluster.getVersion(), "6.2.0")) {
                promises.push(
                    client.blmove(
                        "abc",
                        "def",
                        ListDirection.LEFT,
                        ListDirection.LEFT,
                        0.2,
                    ),
                    client.zdiff(["abc", "zxy", "lkn"]),
                    client.zdiffWithScores(["abc", "zxy", "lkn"]),
                    client.zdiffstore("abc", ["zxy", "lkn"]),
                    client.copy("abc", "zxy", { replace: true }),
                    client.geosearchstore(
                        "abc",
                        "zxy",
                        { member: "_" },
                        { radius: 5, unit: GeoUnit.METERS },
                    ),
                    client.zrangeStore("abc", "zyx", { start: 0, end: -1 }),
                    client.zinter(["abc", "zxy", "lkn"]),
                    client.zinterWithScores(["abc", "zxy", "lkn"]),
                    client.zunion(["abc", "zxy", "lkn"]),
                    client.zunionWithScores(["abc", "zxy", "lkn"]),
                );
            }

            if (gte(cluster.getVersion(), "7.0.0")) {
                promises.push(
                    client.sintercard(["abc", "zxy", "lkn"]),
                    client.zintercard(["abc", "zxy", "lkn"]),
                    client.zmpop(["abc", "zxy", "lkn"], ScoreFilter.MAX),
                    client.bzmpop(["abc", "zxy", "lkn"], ScoreFilter.MAX, 0.1),
                    client.lcs("abc", "xyz"),
                    client.lcsLen("abc", "xyz"),
                    client.lcsIdx("abc", "xyz"),
                    client.lmpop(["abc", "def"], ListDirection.LEFT, {
                        count: 1,
                    }),
                    client.blmpop(["abc", "def"], ListDirection.RIGHT, 0.1, {
                        count: 1,
                    }),
                );
            }

            await Promise.allSettled(promises).then((results) => {
                results.forEach((result) => {
                    expect(result.status).toBe("rejected");

                    if (result.status === "rejected") {
                        expect(result.reason.message).toContain("CrossSlot");
                    }
                });
            });

            client.close();
        },
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `check that multi key command routed to multiple nodes`,
        async (protocol) => {
            const client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            await client.exists(["abc", "zxy", "lkn"]);
            await client.unlink(["abc", "zxy", "lkn"]);
            await client.del(["abc", "zxy", "lkn"]);
            await client.mget(["abc", "zxy", "lkn"]);
            await client.mset({ abc: "1", zxy: "2", lkn: "3" });
            await client.touch(["abc", "zxy", "lkn"]);
            await client.watch(["ghi", "zxy", "lkn"]);
            client.close();
        },
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "object freq transaction test_%p",
        async (protocol) => {
            const client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            const key = uuidv4();
            const maxmemoryPolicyKey = "maxmemory-policy";
            const config = await client.configGet([maxmemoryPolicyKey]);
            const maxmemoryPolicy = config[maxmemoryPolicyKey] as string;

            try {
                const transaction = new ClusterTransaction();
                transaction.configSet({
                    [maxmemoryPolicyKey]: "allkeys-lfu",
                });
                transaction.set(key, "foo");
                transaction.objectFreq(key);

                const response = await client.exec(transaction);
                expect(response).not.toBeNull();

                if (response != null) {
                    expect(response.length).toEqual(3);
                    expect(response[0]).toEqual("OK");
                    expect(response[1]).toEqual("OK");
                    expect(response[2]).toBeGreaterThanOrEqual(0);
                }
            } finally {
                expect(
                    await client.configSet({
                        [maxmemoryPolicyKey]: maxmemoryPolicy,
                    }),
                ).toEqual("OK");
            }

            client.close();
        },
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "object idletime transaction test_%p",
        async (protocol) => {
            const client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            const key = uuidv4();
            const maxmemoryPolicyKey = "maxmemory-policy";
            const config = await client.configGet([maxmemoryPolicyKey]);
            const maxmemoryPolicy = config[maxmemoryPolicyKey] as string;

            try {
                const transaction = new ClusterTransaction();
                transaction.configSet({
                    // OBJECT IDLETIME requires a non-LFU maxmemory-policy
                    [maxmemoryPolicyKey]: "allkeys-random",
                });
                transaction.set(key, "foo");
                transaction.objectIdletime(key);

                const response = await client.exec(transaction);
                expect(response).not.toBeNull();

                if (response != null) {
                    expect(response.length).toEqual(3);
                    // transaction.configSet({[maxmemoryPolicyKey]: "allkeys-random"});
                    expect(response[0]).toEqual("OK");
                    // transaction.set(key, "foo");
                    expect(response[1]).toEqual("OK");
                    // transaction.objectIdletime(key);
                    expect(response[2]).toBeGreaterThanOrEqual(0);
                }
            } finally {
                expect(
                    await client.configSet({
                        [maxmemoryPolicyKey]: maxmemoryPolicy,
                    }),
                ).toEqual("OK");
            }

            client.close();
        },
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "object refcount transaction test_%p",
        async (protocol) => {
            const client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            const key = uuidv4();
            const transaction = new ClusterTransaction();
            transaction.set(key, "foo");
            transaction.objectRefcount(key);

            const response = await client.exec(transaction);
            expect(response).not.toBeNull();

            if (response != null) {
                expect(response.length).toEqual(2);
                expect(response[0]).toEqual("OK"); // transaction.set(key, "foo");
                expect(response[1]).toBeGreaterThanOrEqual(1); // transaction.objectRefcount(key);
            }

            client.close();
        },
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `lolwut test_%p`,
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            // test with multi-node route
            const result1 = await client.lolwut({ route: "allNodes" });
            expect(intoString(result1)).toEqual(
                expect.stringContaining("Redis ver. "),
            );

            const result2 = await client.lolwut({
                version: 2,
                parameters: [10, 20],
                route: "allNodes",
            });
            expect(intoString(result2)).toEqual(
                expect.stringContaining("Redis ver. "),
            );

            // test with single-node route
            const result3 = await client.lolwut({ route: "randomNode" });
            expect(intoString(result3)).toEqual(
                expect.stringContaining("Redis ver. "),
            );

            const result4 = await client.lolwut({
                version: 2,
                parameters: [10, 20],
                route: "randomNode",
            });
            expect(intoString(result4)).toEqual(
                expect.stringContaining("Redis ver. "),
            );

            // transaction tests
            const transaction = new ClusterTransaction();
            transaction.lolwut();
            transaction.lolwut({ version: 5 });
            transaction.lolwut({ parameters: [1, 2] });
            transaction.lolwut({ version: 6, parameters: [42] });
            const results = await client.exec(transaction);

            if (results) {
                for (const element of results) {
                    expect(intoString(element)).toEqual(
                        expect.stringContaining("Redis ver. "),
                    );
                }
            } else {
                throw new Error("Invalid LOLWUT transaction test results.");
            }

            client.close();
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "copy test_%p",
        async (protocol) => {
            const client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            if (cluster.checkIfServerVersionLessThan("6.2.0")) return;

            const source = `{key}-${uuidv4()}`;
            const destination = `{key}-${uuidv4()}`;
            const value1 = uuidv4();
            const value2 = uuidv4();

            // neither key exists
            expect(
                await client.copy(source, destination, { replace: true }),
            ).toEqual(false);
            expect(await client.copy(Buffer.from(source), destination)).toEqual(
                false,
            );

            // source exists, destination does not
            expect(await client.set(source, value1)).toEqual("OK");
            expect(
                await client.copy(source, Buffer.from(destination), {
                    replace: false,
                }),
            ).toEqual(true);
            expect(await client.get(destination)).toEqual(value1);

            // new value for source key
            expect(await client.set(source, value2)).toEqual("OK");

            // both exists, no REPLACE
            expect(
                await client.copy(
                    Buffer.from(source),
                    Buffer.from(destination),
                ),
            ).toEqual(false);
            expect(
                await client.copy(source, destination, { replace: false }),
            ).toEqual(false);
            expect(await client.get(destination)).toEqual(value1);

            // both exists, with REPLACE
            expect(
                await client.copy(source, Buffer.from(destination), {
                    replace: true,
                }),
            ).toEqual(true);
            expect(await client.get(destination)).toEqual(value2);

            //transaction tests
            const transaction = new ClusterTransaction();
            transaction.set(source, value1);
            transaction.copy(source, destination, { replace: true });
            transaction.get(destination);
            const results = await client.exec(transaction);

            expect(results).toEqual(["OK", true, value1]);

            client.close();
        },
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "flushdb flushall dbsize test_%p",
        async (protocol) => {
            const client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            expect(await client.dbsize()).toBeGreaterThanOrEqual(0);
            expect(await client.set(uuidv4(), uuidv4())).toEqual("OK");
            expect(await client.dbsize()).toBeGreaterThan(0);

            expect(await client.flushall()).toEqual("OK");
            expect(await client.dbsize()).toEqual(0);

            expect(await client.set(uuidv4(), uuidv4())).toEqual("OK");
            expect(await client.dbsize()).toEqual(1);
            expect(await client.flushdb({ mode: FlushMode.ASYNC })).toEqual(
                "OK",
            );
            expect(await client.dbsize()).toEqual(0);

            expect(await client.set(uuidv4(), uuidv4())).toEqual("OK");
            expect(await client.dbsize()).toEqual(1);
            expect(await client.flushdb({ mode: FlushMode.SYNC })).toEqual(
                "OK",
            );
            expect(await client.dbsize()).toEqual(0);

            client.close();
        },
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "sort sortstore sort_store sortro sort_ro sortreadonly test_%p",
        async (protocol) => {
            const client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            const key1 = "{sort}" + uuidv4();
            const key2 = "{sort}" + uuidv4();
            const key3 = "{sort}" + uuidv4();
            const key4 = "{sort}" + uuidv4();
            const key5 = "{sort}" + uuidv4();

            expect(await client.sort(key3)).toEqual([]);
            expect(await client.lpush(key1, ["2", "1", "4", "3"])).toEqual(4);
            expect(await client.sort(Buffer.from(key1))).toEqual([
                "1",
                "2",
                "3",
                "4",
            ]);
            // test binary decoder
            expect(await client.sort(key1, { decoder: Decoder.Bytes })).toEqual(
                [
                    Buffer.from("1"),
                    Buffer.from("2"),
                    Buffer.from("3"),
                    Buffer.from("4"),
                ],
            );

            // sort RO
            if (!cluster.checkIfServerVersionLessThan("7.0.0")) {
                expect(await client.sortReadOnly(key3)).toEqual([]);
                expect(await client.sortReadOnly(Buffer.from(key3))).toEqual(
                    [],
                );
                // test binary decoder
                expect(
                    await client.sortReadOnly(key1, { decoder: Decoder.Bytes }),
                ).toEqual([
                    Buffer.from("1"),
                    Buffer.from("2"),
                    Buffer.from("3"),
                    Buffer.from("4"),
                ]);
            }

            // sort with store
            expect(await client.sortStore(key1, key2)).toEqual(4);
            expect(
                await client.sortStore(Buffer.from(key1), Buffer.from(key2)),
            ).toEqual(4);
            expect(await client.lrange(key2, 0, -1)).toEqual([
                "1",
                "2",
                "3",
                "4",
            ]);

            // SORT with strings require ALPHA
            expect(
                await client.rpush(key3, ["2", "1", "a", "x", "c", "4", "3"]),
            ).toEqual(7);
            await expect(client.sort(key3)).rejects.toThrow(RequestError);
            expect(await client.sort(key3, { isAlpha: true })).toEqual([
                "1",
                "2",
                "3",
                "4",
                "a",
                "c",
                "x",
            ]);

            // check transaction and options
            const transaction = new ClusterTransaction()
                .lpush(key4, ["3", "1", "2"])
                .sort(key4, {
                    orderBy: SortOrder.DESC,
                    limit: { count: 2, offset: 0 },
                })
                .sortStore(key4, key5, {
                    orderBy: SortOrder.ASC,
                    limit: { count: 100, offset: 1 },
                })
                .lrange(key5, 0, -1);

            if (!cluster.checkIfServerVersionLessThan("7.0.0")) {
                transaction.sortReadOnly(key4, {
                    orderBy: SortOrder.DESC,
                    limit: { count: 2, offset: 0 },
                });
            }

            const result = await client.exec(transaction);
            const expectedResult = [3, ["3", "2"], 2, ["2", "3"]];

            if (!cluster.checkIfServerVersionLessThan("7.0.0")) {
                expectedResult.push(["3", "2"]);
            }

            expect(result).toEqual(expectedResult);

            client.close();
        },
    );

    describe.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "Protocol is RESP2 = %s",
        (protocol) => {
            describe.each([true, false])(
                "Single node route = %s",
                (singleNodeRoute) => {
                    it(
                        "function load function list function stats",
                        async () => {
                            if (cluster.checkIfServerVersionLessThan("7.0.0"))
                                return;

                            const client =
                                await GlideClusterClient.createClient(
                                    getClientConfigurationOption(
                                        cluster.getAddresses(),
                                        protocol,
                                    ),
                                );

                            try {
                                const libName =
                                    "mylib1C" + uuidv4().replaceAll("-", "");
                                const funcName =
                                    "myfunc1c" + uuidv4().replaceAll("-", "");
                                const code = generateLuaLibCode(
                                    libName,
                                    new Map([[funcName, "return args[1]"]]),
                                    true,
                                );
                                const route: Routes = singleNodeRoute
                                    ? { type: "primarySlotKey", key: "1" }
                                    : "allPrimaries";

                                let functionList = await client.functionList({
                                    libNamePattern: libName,
                                    route: route,
                                });
                                checkClusterResponse(
                                    functionList as object,
                                    singleNodeRoute,
                                    (value) => expect(value).toEqual([]),
                                );

                                let functionStats = await client.functionStats({
                                    route: route,
                                });
                                checkClusterResponse(
                                    functionStats as object,
                                    singleNodeRoute,
                                    (value) =>
                                        checkFunctionStatsResponse(
                                            value as FunctionStatsSingleResponse,
                                            [],
                                            0,
                                            0,
                                        ),
                                );

                                // load the library
                                expect(await client.functionLoad(code)).toEqual(
                                    libName,
                                );

                                functionList = await client.functionList({
                                    libNamePattern: libName,
                                    route: route,
                                });
                                let expectedDescription = new Map<
                                    string,
                                    string | null
                                >([[funcName, null]]);
                                let expectedFlags = new Map<string, string[]>([
                                    [funcName, ["no-writes"]],
                                ]);

                                checkClusterResponse(
                                    functionList,
                                    singleNodeRoute,
                                    (value) =>
                                        checkFunctionListResponse(
                                            value as FunctionListResponse,
                                            libName,
                                            expectedDescription,
                                            expectedFlags,
                                        ),
                                );
                                functionStats = await client.functionStats({
                                    route: route,
                                });
                                checkClusterResponse(
                                    functionStats as object,
                                    singleNodeRoute,
                                    (value) =>
                                        checkFunctionStatsResponse(
                                            value as FunctionStatsSingleResponse,
                                            [],
                                            1,
                                            1,
                                        ),
                                );

                                // call functions from that library to confirm that it works
                                let fcall = await client.fcallWithRoute(
                                    funcName,
                                    ["one", "two"],
                                    { route: route },
                                );
                                checkClusterResponse(
                                    fcall as object,
                                    singleNodeRoute,
                                    (value) => expect(value).toEqual("one"),
                                );
                                fcall = await client.fcallReadonlyWithRoute(
                                    funcName,
                                    ["one", "two"],
                                    { route: route },
                                );
                                checkClusterResponse(
                                    fcall as object,
                                    singleNodeRoute,
                                    (value) => expect(value).toEqual("one"),
                                );

                                // re-load library without replace
                                await expect(
                                    client.functionLoad(code),
                                ).rejects.toThrow(
                                    `Library '${libName}' already exists`,
                                );

                                // re-load library with replace
                                expect(
                                    await client.functionLoad(code, {
                                        replace: true,
                                    }),
                                ).toEqual(libName);

                                // overwrite lib with new code
                                const func2Name =
                                    "myfunc2c" + uuidv4().replaceAll("-", "");
                                const newCode = generateLuaLibCode(
                                    libName,
                                    new Map([
                                        [funcName, "return args[1]"],
                                        [func2Name, "return #args"],
                                    ]),
                                    true,
                                );
                                expect(
                                    await client.functionLoad(newCode, {
                                        replace: true,
                                    }),
                                ).toEqual(libName);

                                functionList = await client.functionList({
                                    libNamePattern: libName,
                                    withCode: true,
                                    route: route,
                                });
                                expectedDescription = new Map<
                                    string,
                                    string | null
                                >([
                                    [funcName, null],
                                    [func2Name, null],
                                ]);
                                expectedFlags = new Map<string, string[]>([
                                    [funcName, ["no-writes"]],
                                    [func2Name, ["no-writes"]],
                                ]);

                                checkClusterResponse(
                                    functionList,
                                    singleNodeRoute,
                                    (value) =>
                                        checkFunctionListResponse(
                                            value as FunctionListResponse,
                                            libName,
                                            expectedDescription,
                                            expectedFlags,
                                            newCode,
                                        ),
                                );
                                functionStats = await client.functionStats({
                                    route: route,
                                });
                                checkClusterResponse(
                                    functionStats as object,
                                    singleNodeRoute,
                                    (value) =>
                                        checkFunctionStatsResponse(
                                            value as FunctionStatsSingleResponse,
                                            [],
                                            1,
                                            2,
                                        ),
                                );

                                fcall = await client.fcallWithRoute(
                                    func2Name,
                                    ["one", "two"],
                                    { route: route },
                                );
                                checkClusterResponse(
                                    fcall as object,
                                    singleNodeRoute,
                                    (value) => expect(value).toEqual(2),
                                );

                                fcall = await client.fcallReadonlyWithRoute(
                                    func2Name,
                                    ["one", "two"],
                                    { route: route },
                                );
                                checkClusterResponse(
                                    fcall as object,
                                    singleNodeRoute,
                                    (value) => expect(value).toEqual(2),
                                );
                            } finally {
                                expect(await client.functionFlush()).toEqual(
                                    "OK",
                                );
                                client.close();
                            }
                        },
                        TIMEOUT,
                    );
                    it(
                        "function flush",
                        async () => {
                            if (cluster.checkIfServerVersionLessThan("7.0.0"))
                                return;

                            const client =
                                await GlideClusterClient.createClient(
                                    getClientConfigurationOption(
                                        cluster.getAddresses(),
                                        protocol,
                                    ),
                                );

                            try {
                                const libName =
                                    "mylib1C" + uuidv4().replaceAll("-", "");
                                const funcName =
                                    "myfunc1c" + uuidv4().replaceAll("-", "");
                                const code = generateLuaLibCode(
                                    libName,
                                    new Map([[funcName, "return args[1]"]]),
                                    true,
                                );
                                const route: Routes = singleNodeRoute
                                    ? { type: "primarySlotKey", key: "1" }
                                    : "allPrimaries";

                                const functionList1 = await client.functionList(
                                    { route: route },
                                );
                                checkClusterResponse(
                                    functionList1 as object,
                                    singleNodeRoute,
                                    (value) => expect(value).toEqual([]),
                                );

                                // load the library
                                expect(
                                    await client.functionLoad(code, {
                                        route: route,
                                    }),
                                ).toEqual(libName);

                                // flush functions
                                expect(
                                    await client.functionFlush({
                                        mode: FlushMode.SYNC,
                                        route: route,
                                    }),
                                ).toEqual("OK");
                                expect(
                                    await client.functionFlush({
                                        mode: FlushMode.ASYNC,
                                        route: route,
                                    }),
                                ).toEqual("OK");

                                const functionList2 =
                                    await client.functionList();
                                checkClusterResponse(
                                    functionList2 as object,
                                    singleNodeRoute,
                                    (value) => expect(value).toEqual([]),
                                );

                                // Attempt to re-load library without overwriting to ensure FLUSH was effective
                                expect(
                                    await client.functionLoad(code, {
                                        route: route,
                                    }),
                                ).toEqual(libName);
                            } finally {
                                expect(await client.functionFlush()).toEqual(
                                    "OK",
                                );
                                client.close();
                            }
                        },
                        TIMEOUT,
                    );
                    it(
                        "function delete",
                        async () => {
                            if (cluster.checkIfServerVersionLessThan("7.0.0"))
                                return;

                            const client =
                                await GlideClusterClient.createClient(
                                    getClientConfigurationOption(
                                        cluster.getAddresses(),
                                        protocol,
                                    ),
                                );

                            try {
                                const libName =
                                    "mylib1C" + uuidv4().replaceAll("-", "");
                                const funcName =
                                    "myfunc1c" + uuidv4().replaceAll("-", "");
                                const code = generateLuaLibCode(
                                    libName,
                                    new Map([[funcName, "return args[1]"]]),
                                    true,
                                );
                                const route: Routes = singleNodeRoute
                                    ? { type: "primarySlotKey", key: "1" }
                                    : "allPrimaries";
                                let functionList = await client.functionList({
                                    route: route,
                                });
                                checkClusterResponse(
                                    functionList as object,
                                    singleNodeRoute,
                                    (value) => expect(value).toEqual([]),
                                );
                                // load the library
                                expect(
                                    await client.functionLoad(code, {
                                        route: route,
                                    }),
                                ).toEqual(libName);

                                // Delete the function
                                expect(
                                    await client.functionDelete(libName, {
                                        route,
                                    }),
                                ).toEqual("OK");

                                functionList = await client.functionList({
                                    libNamePattern: libName,
                                    withCode: true,
                                    route: route,
                                });
                                checkClusterResponse(
                                    functionList as object,
                                    singleNodeRoute,
                                    (value) => expect(value).toEqual([]),
                                );

                                // Delete a non-existing library
                                await expect(
                                    client.functionDelete(libName, { route }),
                                ).rejects.toThrow(`Library not found`);
                            } finally {
                                expect(await client.functionFlush()).toEqual(
                                    "OK",
                                );
                                client.close();
                            }
                        },
                        TIMEOUT,
                    );
                    it(
                        "function kill with route",
                        async () => {
                            if (cluster.checkIfServerVersionLessThan("7.0.0"))
                                return;

                            const config = getClientConfigurationOption(
                                cluster.getAddresses(),
                                protocol,
                                { requestTimeout: 10000 },
                            );
                            const client =
                                await GlideClusterClient.createClient(config);
                            const testClient =
                                await GlideClusterClient.createClient(config);

                            try {
                                const libName =
                                    "function_kill_no_write_with_route_" +
                                    singleNodeRoute;
                                const funcName =
                                    "deadlock_with_route_" + singleNodeRoute;
                                const code =
                                    createLuaLibWithLongRunningFunction(
                                        libName,
                                        funcName,
                                        6,
                                        true,
                                    );
                                const route: Routes = singleNodeRoute
                                    ? { type: "primarySlotKey", key: "1" }
                                    : "allPrimaries";
                                expect(await client.functionFlush()).toEqual(
                                    "OK",
                                );

                                // nothing to kill
                                await expect(
                                    client.functionKill({ route }),
                                ).rejects.toThrow(/notbusy/i);

                                // load the lib
                                expect(
                                    await client.functionLoad(code, {
                                        replace: true,
                                        route: route,
                                    }),
                                ).toEqual(libName);

                                try {
                                    // call the function without await
                                    const promise = testClient
                                        .fcallWithRoute(funcName, [], {
                                            route: route,
                                        })
                                        .catch((e) =>
                                            expect(
                                                (e as Error).message,
                                            ).toContain("Script killed"),
                                        );

                                    let killed = false;
                                    let timeout = 4000;
                                    await new Promise((resolve) =>
                                        setTimeout(resolve, 1000),
                                    );

                                    while (timeout >= 0) {
                                        try {
                                            expect(
                                                await client.functionKill({
                                                    route,
                                                }),
                                            ).toEqual("OK");
                                            killed = true;
                                            break;
                                        } catch {
                                            // do nothing
                                        }

                                        await new Promise((resolve) =>
                                            setTimeout(resolve, 500),
                                        );
                                        timeout -= 500;
                                    }

                                    expect(killed).toBeTruthy();
                                    await promise;
                                } finally {
                                    await waitForNotBusy(client);
                                }
                            } finally {
                                expect(await client.functionFlush()).toEqual(
                                    "OK",
                                );
                                client.close();
                                testClient.close();
                            }
                        },
                        TIMEOUT,
                    );

                    it("function dump function restore", async () => {
                        if (cluster.checkIfServerVersionLessThan("7.0.0"))
                            return;

                        const config = getClientConfigurationOption(
                            cluster.getAddresses(),
                            protocol,
                        );
                        const client =
                            await GlideClusterClient.createClient(config);
                        const route: Routes = singleNodeRoute
                            ? { type: "primarySlotKey", key: "1" }
                            : "allPrimaries";
                        expect(
                            await client.functionFlush({
                                mode: FlushMode.SYNC,
                                route: route,
                            }),
                        ).toEqual("OK");

                        try {
                            // dumping an empty lib
                            let response = await client.functionDump({ route });

                            if (singleNodeRoute) {
                                expect(response.byteLength).toBeGreaterThan(0);
                            } else {
                                Object.values(response).forEach((d: Buffer) =>
                                    expect(d.byteLength).toBeGreaterThan(0),
                                );
                            }

                            const name1 = "Foster";
                            const name2 = "Dogster";
                            // function $name1 returns first argument
                            // function $name2 returns argument array len
                            let code = generateLuaLibCode(
                                name1,
                                new Map([
                                    [name1, "return args[1]"],
                                    [name2, "return #args"],
                                ]),
                                false,
                            );
                            expect(
                                await client.functionLoad(code, {
                                    route: route,
                                }),
                            ).toEqual(name1);

                            const flist = await client.functionList({
                                withCode: true,
                                route: route,
                            });
                            response = await client.functionDump({ route });
                            const dump = (
                                singleNodeRoute
                                    ? response
                                    : Object.values(response)[0]
                            ) as Buffer;

                            // restore without cleaning the lib and/or overwrite option causes an error
                            await expect(
                                client.functionRestore(dump, { route: route }),
                            ).rejects.toThrow(
                                `Library ${name1} already exists`,
                            );

                            // APPEND policy also fails for the same reason (name collision)
                            await expect(
                                client.functionRestore(dump, {
                                    policy: FunctionRestorePolicy.APPEND,
                                    route: route,
                                }),
                            ).rejects.toThrow(
                                `Library ${name1} already exists`,
                            );

                            // REPLACE policy succeeds
                            expect(
                                await client.functionRestore(dump, {
                                    policy: FunctionRestorePolicy.REPLACE,
                                    route: route,
                                }),
                            ).toEqual("OK");
                            // but nothing changed - all code overwritten
                            expect(
                                await client.functionList({
                                    withCode: true,
                                    route: route,
                                }),
                            ).toEqual(flist);

                            // create lib with another name, but with the same function names
                            expect(
                                await client.functionFlush({
                                    mode: FlushMode.SYNC,
                                    route: route,
                                }),
                            ).toEqual("OK");
                            code = generateLuaLibCode(
                                name2,
                                new Map([
                                    [name1, "return args[1]"],
                                    [name2, "return #args"],
                                ]),
                                false,
                            );
                            expect(
                                await client.functionLoad(code, {
                                    route: route,
                                }),
                            ).toEqual(name2);

                            // REPLACE policy now fails due to a name collision
                            await expect(
                                client.functionRestore(dump, { route: route }),
                            ).rejects.toThrow(
                                new RegExp(
                                    `Function ${name1}|${name2} already exists`,
                                ),
                            );

                            // FLUSH policy succeeds, but deletes the second lib
                            expect(
                                await client.functionRestore(dump, {
                                    policy: FunctionRestorePolicy.FLUSH,
                                    route: route,
                                }),
                            ).toEqual("OK");
                            expect(
                                await client.functionList({
                                    withCode: true,
                                    route: route,
                                }),
                            ).toEqual(flist);

                            // call restored functions
                            let res = await client.fcallWithRoute(
                                name1,
                                ["meow", "woem"],
                                { route: route },
                            );

                            if (singleNodeRoute) {
                                expect(res).toEqual("meow");
                            } else {
                                Object.values(
                                    res as Record<string, GlideReturnType>,
                                ).forEach((r) => expect(r).toEqual("meow"));
                            }

                            res = await client.fcallWithRoute(
                                name2,
                                ["meow", "woem"],
                                { route: route },
                            );

                            if (singleNodeRoute) {
                                expect(res).toEqual(2);
                            } else {
                                Object.values(
                                    res as Record<string, GlideReturnType>,
                                ).forEach((r) => expect(r).toEqual(2));
                            }
                        } finally {
                            expect(await client.functionFlush()).toEqual("OK");
                            client.close();
                        }
                    });

                    it(
                        "invoke script with route invokeScriptWithRoute %p",
                        async () => {
                            const client =
                                await GlideClusterClient.createClient(
                                    getClientConfigurationOption(
                                        cluster.getAddresses(),
                                        protocol,
                                    ),
                                );
                            const route: Routes = singleNodeRoute
                                ? { type: "primarySlotKey", key: "1" }
                                : "allPrimaries";

                            try {
                                const arg = uuidv4();
                                const script = new Script(
                                    Buffer.from("return {ARGV[1]}"),
                                );
                                let res = await client.invokeScriptWithRoute(
                                    script,
                                    { args: [Buffer.from(arg)], route },
                                );

                                if (singleNodeRoute) {
                                    expect(res).toEqual([arg]);
                                } else {
                                    Object.values(
                                        res as Record<string, GlideReturnType>,
                                    ).forEach((value) =>
                                        expect(value).toEqual([arg]),
                                    );
                                }

                                res = await client.invokeScriptWithRoute(
                                    script,
                                    {
                                        args: [arg],
                                        route,
                                        decoder: Decoder.Bytes,
                                    },
                                );

                                if (singleNodeRoute) {
                                    expect(res).toEqual([Buffer.from(arg)]);
                                } else {
                                    Object.values(
                                        res as Record<string, GlideReturnType>,
                                    ).forEach((value) =>
                                        expect(value).toEqual([
                                            Buffer.from(arg),
                                        ]),
                                    );
                                }
                            } finally {
                                client.close();
                            }
                        },
                        TIMEOUT,
                    );
                },
            );
            it(
                "function kill key based write function",
                async () => {
                    if (cluster.checkIfServerVersionLessThan("7.0.0")) return;

                    const config = getClientConfigurationOption(
                        cluster.getAddresses(),
                        protocol,
                        { requestTimeout: 10000 },
                    );
                    const client =
                        await GlideClusterClient.createClient(config);
                    const testClient =
                        await GlideClusterClient.createClient(config);

                    try {
                        const libName =
                            "function_kill_key_based_write_function";
                        const funcName =
                            "deadlock_write_function_with_key_based_route";
                        const key = libName;
                        const code = createLuaLibWithLongRunningFunction(
                            libName,
                            funcName,
                            6,
                            false,
                        );

                        const route: Routes = {
                            type: "primarySlotKey",
                            key: key,
                        };
                        expect(await client.functionFlush()).toEqual("OK");

                        // nothing to kill
                        await expect(
                            client.functionKill({ route }),
                        ).rejects.toThrow(/notbusy/i);

                        // load the lib
                        expect(
                            await client.functionLoad(code, {
                                replace: true,
                                route: route,
                            }),
                        ).toEqual(libName);

                        let promise = null;

                        try {
                            // call the function without await
                            promise = testClient.fcall(funcName, [key], []);

                            let foundUnkillable = false;
                            let timeout = 4000;
                            await new Promise((resolve) =>
                                setTimeout(resolve, 1000),
                            );

                            while (timeout >= 0) {
                                try {
                                    // valkey kills a function with 5 sec delay
                                    // but this will always throw an error in the test
                                    await client.functionKill({ route });
                                } catch (err) {
                                    // looking for an error with "unkillable" in the message
                                    // at that point we can break the loop
                                    if (
                                        (err as Error).message
                                            .toLowerCase()
                                            .includes("unkillable")
                                    ) {
                                        foundUnkillable = true;
                                        break;
                                    }
                                }

                                await new Promise((resolve) =>
                                    setTimeout(resolve, 500),
                                );
                                timeout -= 500;
                            }

                            expect(foundUnkillable).toBeTruthy();
                        } finally {
                            // If function wasn't killed, and it didn't time out - it blocks the server and cause rest
                            // test to fail. Wait for the function to complete (we cannot kill it)
                            expect(await promise).toContain("Timed out");
                        }
                    } finally {
                        expect(await client.functionFlush()).toEqual("OK");
                        client.close();
                        testClient.close();
                    }
                },
                TIMEOUT,
            );
            it("function dump function restore in transaction", async () => {
                if (cluster.checkIfServerVersionLessThan("7.0.0")) return;

                const config = getClientConfigurationOption(
                    cluster.getAddresses(),
                    protocol,
                );
                const client = await GlideClusterClient.createClient(config);
                const route: SlotKeyTypes = {
                    key: uuidv4(),
                    type: "primarySlotKey",
                };
                expect(await client.functionFlush()).toEqual("OK");

                try {
                    const name1 = "Foster";
                    const name2 = "Dogster";
                    // function returns first argument
                    const code = generateLuaLibCode(
                        name1,
                        new Map([[name2, "return args[1]"]]),
                        false,
                    );
                    expect(
                        await client.functionLoad(code, {
                            replace: true,
                            route: route,
                        }),
                    ).toEqual(name1);

                    // Verify functionDump
                    let transaction = new ClusterTransaction().functionDump();
                    const result = await client.exec(transaction, {
                        decoder: Decoder.Bytes,
                        route: route,
                    });
                    const data = result?.[0] as Buffer;

                    // Verify functionRestore
                    transaction = new ClusterTransaction()
                        .functionRestore(data, FunctionRestorePolicy.REPLACE)
                        .fcall(name2, [], ["meow"]);
                    expect(
                        await client.exec(transaction, { route: route }),
                    ).toEqual(["OK", "meow"]);
                } finally {
                    expect(await client.functionFlush()).toEqual("OK");
                    client.close();
                }
            });
        },
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `randomKey test_%p`,
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            const key = uuidv4();

            // setup: delete all keys
            expect(await client.flushall({ mode: FlushMode.SYNC })).toEqual(
                "OK",
            );

            // no keys exist so randomKey returns null
            expect(await client.randomKey()).toBeNull();

            expect(await client.set(key, "foo")).toEqual("OK");
            // `key` should be the only existing key, so randomKey should return `key`
            expect(await client.randomKey({ decoder: Decoder.Bytes })).toEqual(
                Buffer.from(key),
            );
            expect(await client.randomKey({ route: "allPrimaries" })).toEqual(
                key,
            );

            client.close();
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "watch test_%p",
        async (protocol) => {
            const client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            const key1 = "{key}-1" + uuidv4();
            const key2 = "{key}-2" + uuidv4();
            const key3 = "{key}-3" + uuidv4();
            const key4 = "{key}-4" + uuidv4();
            const setFoobarTransaction = new ClusterTransaction();
            const setHelloTransaction = new ClusterTransaction();

            // Returns null when a watched key is modified before it is executed in a transaction command.
            // Transaction commands are not performed.
            expect(await client.watch([key1, key2, key3])).toEqual("OK");
            expect(await client.set(key2, "hello")).toEqual("OK");
            setFoobarTransaction
                .set(key1, "foobar")
                .set(key2, "foobar")
                .set(key3, "foobar");
            let results = await client.exec(setFoobarTransaction);
            expect(results).toEqual(null);
            // sanity check
            expect(await client.get(key1)).toEqual(null);
            expect(await client.get(key2)).toEqual("hello");
            expect(await client.get(key3)).toEqual(null);

            // Transaction executes command successfully with a read command on the watch key before
            // transaction is executed.
            expect(await client.watch([key1, key2, Buffer.from(key3)])).toEqual(
                "OK",
            );
            expect(await client.get(key2)).toEqual("hello");
            results = await client.exec(setFoobarTransaction);
            expect(results).toEqual(["OK", "OK", "OK"]);
            // sanity check
            expect(await client.get(key1)).toEqual("foobar");
            expect(await client.get(key2)).toEqual("foobar");
            expect(await client.get(key3)).toEqual("foobar");

            // Transaction executes command successfully with unmodified watched keys
            expect(await client.watch([key1, key2, key3])).toEqual("OK");
            results = await client.exec(setFoobarTransaction);
            expect(results).toEqual(["OK", "OK", "OK"]);
            // sanity check
            expect(await client.get(key1)).toEqual("foobar");
            expect(await client.get(key2)).toEqual("foobar");
            expect(await client.get(key3)).toEqual("foobar");

            // Transaction executes command successfully with a modified watched key but is not in the
            // transaction.
            expect(await client.watch([key4])).toEqual("OK");
            setHelloTransaction
                .set(key1, "hello")
                .set(key2, "hello")
                .set(key3, "hello");
            results = await client.exec(setHelloTransaction);
            expect(results).toEqual(["OK", "OK", "OK"]);
            // sanity check
            expect(await client.get(key1)).toEqual("hello");
            expect(await client.get(key2)).toEqual("hello");
            expect(await client.get(key3)).toEqual("hello");

            // WATCH can not have an empty String array parameter
            await expect(client.watch([])).rejects.toThrow(RequestError);

            client.close();
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "unwatch test_%p",
        async (protocol) => {
            const client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            const key1 = "{key}-1" + uuidv4();
            const key2 = "{key}-2" + uuidv4();
            const setFoobarTransaction = new ClusterTransaction();

            // UNWATCH returns OK when there no watched keys
            expect(await client.unwatch()).toEqual("OK");

            // Transaction executes successfully after modifying a watched key then calling UNWATCH
            expect(await client.watch([key1, key2])).toEqual("OK");
            expect(await client.set(key2, "hello")).toEqual("OK");
            expect(await client.unwatch()).toEqual("OK");
            expect(await client.unwatch({ route: "allPrimaries" })).toEqual(
                "OK",
            );
            setFoobarTransaction.set(key1, "foobar").set(key2, "foobar");
            const results = await client.exec(setFoobarTransaction);
            expect(results).toEqual(["OK", "OK"]);
            // sanity check
            expect(await client.get(key1)).toEqual("foobar");
            expect(await client.get(key2)).toEqual("foobar");

            client.close();
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "script kill unkillable test_%p",
        async (protocol) => {
            const config = getClientConfigurationOption(
                cluster.getAddresses(),
                protocol,
                { requestTimeout: 10000 },
            );
            const client1 = await GlideClusterClient.createClient(config);
            const client2 = await GlideClusterClient.createClient(config);

            // Verify that script kill raises an error when no script is running
            await expect(client1.scriptKill()).rejects.toThrow(
                "No scripts in execution right now",
            );

            // Create a long-running script
            const longScript = new Script(createLongRunningLuaScript(5, true));
            let promise = null;

            try {
                // call the script without await
                promise = client2.invokeScript(longScript, {
                    keys: ["{key}-" + uuidv4()],
                });

                let foundUnkillable = false;
                let timeout = 4000;
                await new Promise((resolve) => setTimeout(resolve, 1000));

                while (timeout >= 0) {
                    try {
                        // keep trying to kill until we get an "OK"
                        await client1.scriptKill();
                    } catch (err) {
                        // a RequestError may occur if the script is not yet running
                        // sleep and try again
                        if (
                            (err as Error).message
                                .toLowerCase()
                                .includes("unkillable")
                        ) {
                            foundUnkillable = true;
                            break;
                        }
                    }

                    await new Promise((resolve) => setTimeout(resolve, 500));
                    timeout -= 500;
                }

                expect(foundUnkillable).toBeTruthy();
            } finally {
                // If script wasn't killed, and it didn't time out - it blocks the server and cause the
                // test to fail. Wait for the script to complete (we cannot kill it)
                expect(await promise).toContain("Timed out");
                client1.close();
                client2.close();
            }
        },
        TIMEOUT,
    );

    it.each([
        [ProtocolVersion.RESP2, 5],
        [ProtocolVersion.RESP2, 100],
        [ProtocolVersion.RESP2, 1500],
        [ProtocolVersion.RESP3, 5],
        [ProtocolVersion.RESP3, 100],
        [ProtocolVersion.RESP3, 1500],
    ])(
        "test inflight requests limit of %p with protocol %p",
        async (protocol, inflightRequestsLimit) => {
            const config = getClientConfigurationOption(
                cluster.getAddresses(),
                protocol,
                { inflightRequestsLimit },
            );
            const client = await GlideClusterClient.createClient(config);

            try {
                const key1 = `{nonexistinglist}:1-${uuidv4()}`;
                const tasks: Promise<[GlideString, GlideString] | null>[] = [];

                // Start inflightRequestsLimit blocking tasks
                for (let i = 0; i < inflightRequestsLimit; i++) {
                    tasks.push(client.blpop([key1], 0));
                }

                // This task should immediately fail due to reaching the limit
                await expect(client.blpop([key1], 0)).rejects.toThrow(
                    RequestError,
                );

                // Verify that all previous tasks are still pending
                const timeoutPromise = new Promise((resolve) =>
                    setTimeout(resolve, 100),
                );
                const allTasksStatus = await Promise.race([
                    Promise.any(
                        tasks.map((task) => task.then(() => "resolved")),
                    ),
                    timeoutPromise.then(() => "pending"),
                ]);
                expect(allTasksStatus).toBe("pending");
            } finally {
                await client.close();
            }
        },
    );
    describe("GlideClusterClient - AZAffinity Read Strategy Test", () => {
        it.each([ProtocolVersion.RESP2,ProtocolVersion.RESP3])(
            'should route GET commands to all replicas with the same AZ using protocol %p',
            async (protocol) => {
                const az = "us-east-1a";
                const n_replicas = 6;
                const GET_CALLS = 3 * n_replicas;
                const get_cmdstat = `calls=${GET_CALLS / n_replicas}`;
                
                let client_for_config_set;
                let client_for_testing_az;
                
                try {
                    // Stage 1: Configure nodes
                    client_for_config_set = await GlideClusterClient.createClient(
                        getClientConfigurationOption(cluster.getAddresses(), protocol)
                    );
                    // Skip test if version is below 8.0.0
                    if (cluster.checkIfServerVersionLessThan("8.0.0")) {
                        console.log("Skipping test: requires Valkey 8.0.0 or higher");
                        return;
                    }
                    // const info = await client_for_config_set.customCommand(["INFO", "SERVER"]);
                    await client_for_config_set.customCommand(["CONFIG", "RESETSTAT"]);
                    await client_for_config_set.customCommand(
                        ["CONFIG", "SET", "availability-zone", az],
                        { route: "allNodes" }
                    );                    
                    // Stage 2: Create AZ affinity client and verify configuration
                    client_for_testing_az = await GlideClusterClient.createClient(
                        getClientConfigurationOption(
                            cluster.getAddresses(),
                            protocol,
                            {
                                readFrom: "AZAffinity",
                                clientAz: az
                            }
                        )
                    );
    
                    const azs = await client_for_testing_az.customCommand(
                        ["CONFIG", "GET", "availability-zone"],
                        { route: "allNodes" }
                    );
                    
                    if (Array.isArray(azs)) {
                        const allAZsMatch = azs.every(node => {
                            const nodeResponse = node as { key: string; value: any };
                            
                            if (protocol === ProtocolVersion.RESP2) {
                                // RESP2: Direct array format ["availability-zone", "us-east-1a"]
                                return Array.isArray(nodeResponse.value) && 
                                       nodeResponse.value[1] === az;
                            } else {
                                // RESP3: Nested object format [{ key: "availability-zone", value: "us-east-1a" }]
                                return Array.isArray(nodeResponse.value) && 
                                       nodeResponse.value[0]?.key === "availability-zone" &&
                                       nodeResponse.value[0]?.value === az;
                            }
                        });
                        expect(allAZsMatch).toBe(true);
                    } else {
                        throw new Error("Unexpected response format from CONFIG GET command");
                    }
                    
    
                    // Stage 3: Set test data and perform GET operations
                    await client_for_testing_az.set("foo", "testvalue");
                    for (let i = 0; i < GET_CALLS; i++) {
                        await client_for_testing_az.get("foo");
                    }

                    // Stage 4: Verify GET commands were routed correctly
                    const info_result = await client_for_testing_az.customCommand(
                        ["INFO", "COMMANDSTATS"],
                        { route: "allNodes" }
                    );


                    if (Array.isArray(info_result)) {
                        const matching_entries_count = info_result.filter(node => {
                            const nodeInfo = node as { key: string; value: string | string[] | null };
                            const infoStr = nodeInfo.value?.toString() || '';
                            return infoStr.includes(get_cmdstat);
                        }).length;
                    
                        expect(matching_entries_count).toBe(n_replicas);
                    } else {
                        throw new Error("Unexpected response format from INFO command");
                    }
                } finally {
                    // Cleanup
                    await client_for_config_set?.close();
                    await client_for_testing_az?.close();
                }
            });
        });
    describe("GlideClusterClient - AZAffinity Routing to 1 replica", () => {
        it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
            'should route commands to single replica with AZ using protocol %p',
            async (protocol) => {
                const az = "us-east-1a";
                const GET_CALLS = 3;
                const get_cmdstat = `calls=${GET_CALLS}`;
                let client_for_config_set;
                let client_for_testing_az;

                try {
                    // Stage 1: Configure nodes
                    client_for_config_set = await GlideClusterClient.createClient(
                        getClientConfigurationOption(cluster.getAddresses(), protocol)
                    );
                    
                    // Skip test if version is below 8.0.0
                    if (cluster.checkIfServerVersionLessThan("8.0.0")) {
                        console.log("Skipping test: requires Valkey 8.0.0 or higher");
                        return;
                    }
                    
                    await client_for_config_set.customCommand(
                        ["CONFIG", "SET", "availability-zone", ""],
                        { route: "allNodes" }
                    );           
                    
                    await client_for_config_set.customCommand(["CONFIG", "RESETSTAT"]);
                    
                    await client_for_config_set.customCommand(
                        ["CONFIG", "SET", "availability-zone", az],
                        { route: { type: "replicaSlotId", id: 12182 } }
                    );
                    // Stage 2: Create AZ affinity client and verify configuration
                    client_for_testing_az = await GlideClusterClient.createClient(
                        getClientConfigurationOption(
                            cluster.getAddresses(),
                            protocol,
                            {
                                readFrom: "AZAffinity",
                                clientAz: az
                            }
                        )
                    );
                    await client_for_testing_az.set("foo", "testvalue");
                    for (let i = 0; i < GET_CALLS; i++) {
                        await client_for_testing_az.get("foo");
                    }      
                    // Stage 4: Verify GET commands were routed correctly
                    const info_result = await client_for_testing_az.customCommand(
                        ["INFO", "ALL"],
                        { route: "allNodes" }
                    );
                    // Process the info_result to check that only one replica has the GET calls
                    if (Array.isArray(info_result)) {
                        // Count the number of nodes where both get_cmdstat and az are present
                        const matching_entries_count = info_result.filter(node => {
                            const nodeInfo = node as { key: string; value: string | string[] | null };
                            const infoStr = nodeInfo.value?.toString() || '';
                            return infoStr.includes(get_cmdstat) && infoStr.includes(`availability_zone:${az}`);
                        }).length;

                        expect(matching_entries_count).toBe(1);

                        // Check that only one node has the availability zone set to az
                        const changed_az_count = info_result.filter(node => {
                            const nodeInfo = node as { key: string; value: string | string[] | null };
                            const infoStr = nodeInfo.value?.toString() || '';
                            return infoStr.includes(`availability_zone:${az}`);
                        }).length;

                        expect(changed_az_count).toBe(1);
                    } else {
                        throw new Error("Unexpected response format from INFO command");
                    }
                } finally {
                        await client_for_config_set?.close();
                        await client_for_testing_az?.close();
                    }
            }
        );
    });
    describe("GlideClusterClient - AZAffinity with Non-existing AZ", () => {
        it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
            'should route commands to a replica when AZ does not exist using protocol %p',
            async (protocol) => {
                const GET_CALLS = 4;
                const get_cmdstat = `cmdstat_get:calls=${GET_CALLS}`;
                let client_for_testing_az;
    
                try {
                    if (cluster.checkIfServerVersionLessThan("8.0.0")) {
                        console.log("Skipping test: requires Valkey 8.0.0 or higher");
                        return;
                    }
    
                    client_for_testing_az = await GlideClusterClient.createClient(
                        getClientConfigurationOption(
                            cluster.getAddresses(),
                            protocol,
                            {
                                readFrom: "AZAffinity",
                                clientAz: "non-existing-az",
                                requestTimeout: 2000
                            }
                        )
                    );
    
                    await client_for_testing_az.customCommand(["CONFIG", "RESETSTAT"]);
    
                    for (let i = 0; i < GET_CALLS; i++) {
                        await client_for_testing_az.get("foo");
                    }
    
                    const info_result = await client_for_testing_az.customCommand(
                        ["INFO", "COMMANDSTATS"],
                        { route: "allNodes" }
                    );
    
                    if (typeof info_result === 'object' && info_result !== null) {
                        const values = Object.values(info_result);
                        const matching_entries_count = values.filter(value => {
                            if (!value || typeof value !== 'object' || !('value' in value)) return false;
                            return value.value.includes(get_cmdstat);
                        }).length;
    
                        expect(matching_entries_count).toBe(1);
                    } else {
                        throw new Error("Unexpected response format from INFO command");
                    }
                } finally {
                    await client_for_testing_az?.close();
                }
            }
        );
    });
    
});










    // describe("GlideClusterClient - AZAffinity Read Strategy Test", () => {
    //     const protocols = [ProtocolVersion.RESP2, ProtocolVersion.RESP3];
    //     protocols.forEach(protocol => {
    //         it(`should route GET commands to all replicas with the same AZ using protocol ${protocol}`, async () => {
    //             const az = "us-east-1a";
    //             const n_replicas = 4;
    //             const GET_CALLS = 3 * n_replicas;
    //             const get_cmdstat = `calls=${GET_CALLS / n_replicas}`;
    //             // Create client for all nodes to perform configuration
    //             const config = getClientConfigurationOption(
    //                 cluster.getAddresses(),
    //                 protocol,
    //             );
    //             const client_for_config_set = await GlideClusterClient.createClient(config);
    //             await client_for_config_set.customCommand(["CONFIG", "RESETSTAT"]);
    //             await client_for_config_set.customCommand(
    //                 ["CONFIG", "SET", "availability-zone", az],
    //                 { route: "allNodes" }  // This routes the command to all nodes in the cluster
    //             );
    //             await client_for_config_set.close();
    //             // Create client for testing AZAffinity
    //             const config2 = getClientConfigurationOption(
    //                 cluster.getAddresses(),
    //                 protocol,
    //                 {readFrom:"AZAffinity",
    //                  client_az:az
    //                 }
    //             );
    //             const client_for_testing_az = await GlideClusterClient.createClient(config2);
    //             // First, check if azs is a record (object with node responses)
    //             const azs = await client_for_testing_az.customCommand(["CONFIG", "GET","availability-zone"],
    //                 { route: "allNodes" }  // This routes the command to all nodes in the cluster
    //                 );
    //                 // First, check if azs is a record (object with node responses)
    //                 if (typeof azs === 'object' && azs !== null && !Array.isArray(azs)) {
    //                     const allAZsMatch = Object.values(azs as Record<string, GlideReturnType>).every(node => {
    //                         if (Array.isArray(node) && node.length > 1 && node[1] !== null) {
    //                             // If node response is an array format, check the second element (index 1)
    //                             return node[1].toString() === az;
    //                         } else if (node && typeof node === 'object' && 'availability-zone' in node && node["availability-zone"] !== null) {
    //                             // If node response is an object format, check the 'availability-zone' key
    //                             return node["availability-zone"].toString() === az;
    //                         }
    //                         return false; // Handle case where node format is invalid
    //                     });
    //                     expect(allAZsMatch).toBe(true);
    //                 } else {
    //                     throw new Error("Unexpected response format from CONFIG GET command");
    //                 }
    //                 // Perform GET_CALLS number of GET operations
    //                 for (let i = 0; i < GET_CALLS; i++) {
    //                     await client_for_testing_az.get("foo");
    //                 }

    //                 // Get INFO from all nodes
    //                 const info_result = await client_for_testing_az.customCommand(
    //                     ["INFO", "ALL"],
    //                     { route: "allNodes" }
    //                 );

    //                 // Check that all replicas have the same number of GET calls
    //                 if (typeof info_result === 'object' && info_result !== null && !Array.isArray(info_result)) {
    //                     const matching_entries_count = Object.values(info_result as Record<string, GlideReturnType>)
    //                         .filter(value => {
    //                             if (value === null) return false;
    //                             const valueStr = value.toString();
    //                             return valueStr.includes(get_cmdstat) && valueStr.includes(az);
    //                         })
    //                         .length;

    //                     expect(matching_entries_count).toBe(n_replicas);
    //                 } else {
    //                     throw new Error("Unexpected response format from INFO command");
    //                 }
    //                 // Close the client
    //                 await client_for_testing_az.close();
    //         });
    //     });
    // });



    
    // describe("GlideClient - AZAffinity Read Strategy Test", () => {
    //     let testsFailed = 0;
    //     let replicaAddresses: [string, number][] = [];
    //     let azAffinityClient: GlideClusterClient;
        
    //     beforeAll(async () => {
    //         // Retrieve all addresses from the existing cluster
    //         const clusterAddresses = cluster.getAddresses();
    //         expect(clusterAddresses.length).toBe(6); // 3 Primaries + 3 Replicas
    
    //         // Wait for the cluster to be in a healthy state
    //         const [firstHost, firstPort] = clusterAddresses[0];
    //         const clusterClient = await GlideClusterClient.createClient(
    //             getClientConfigurationOption(
    //                 [[firstHost, firstPort]],
    //                 ProtocolVersion.RESP3,
    //             ),
    //         );
    
    //         let clusterStateOk = false;
    //         let retries = 10;
    //         while (!clusterStateOk && retries > 0) {
    //             const clusterInfo: GlideReturnType = await clusterClient.customCommand(["CLUSTER", "INFO"]);
    //             if (typeof clusterInfo === 'string' && clusterInfo.includes("cluster_state:ok")) {
    //                 clusterStateOk = true;
    //             } else {
    //                 console.log("Waiting for cluster to become healthy...");
    //                 await new Promise(resolve => setTimeout(resolve, 1000));
    //                 retries--;
    //             }
    //         }
    //         expect(clusterStateOk).toBe(true);
    
    //         // Use CLUSTER NODES to identify replica nodes
    //         const clusterNodesOutput: GlideReturnType = await clusterClient.customCommand(["CLUSTER", "NODES"]);
    //         if (typeof clusterNodesOutput !== 'string') {
    //             throw new Error("Unexpected CLUSTER NODES output");
    //         }
    
    //         const nodesInfo = parseClusterNodes(clusterNodesOutput);
    
    //         // Collect replica addresses
    //         replicaAddresses = nodesInfo
    //             .filter(node => node.role === 'replica')
    //             .map(node => [node.ip, node.port]);
    
    //         // Ensure we have found 3 replicas
    //         expect(replicaAddresses.length).toBe(3);
    
    //         // Assign Availability Zones to Replicas using CONFIG SET
    //         for (let i = 0; i < replicaAddresses.length; i++) {
    //             const [host, port] = replicaAddresses[i];
    //             const client = await GlideClusterClient.createClient(
    //                 getClientConfigurationOption(
    //                     [[host, port]],
    //                     ProtocolVersion.RESP3,
    //                 ),
    //             );
    //             // Assign AZs using CONFIG SET
    //             const az = `us-east1a${i}`; // us-east1a0, us-east1a1, us-east1a2
    //             await client.customCommand(["CONFIG", "SET", "availability-zone", az]);
    //             await client.close();
    //         }

    //         // Logging for verification
    //         replicaAddresses.forEach(([host, port], index) => {
    //             console.log(`Replica ${index + 1} assigned to AZ${index}: ${host}:${port}`);
    //         });

    //         await clusterClient.close();
    //     }, TIMEOUT);

    
    //     afterEach(async () => {
    //         await flushAndCloseClient(true, cluster.getAddresses(), azAffinityClient);
    //     });
    
    //     afterAll(async () => {
    //         if (testsFailed === 0) {
    //             await cluster.close();
    //         } else {
    //             await cluster.close(true);
    //         }
    //     }, TIMEOUT);
    
    //     it("should route GET commands to the replica in 'us-east1a'", async () => {
    //         try {
    //             // Step 1: Configure the GlideClient for cluster interactions
    //             azAffinityClient = await GlideClusterClient.createClient(
    //                 getClientConfigurationOption(
    //                     cluster.getAddresses(),
    //                     ProtocolVersion.RESP3,
    //                     { 
    //                         readFrom: "AZAffinity", // Use AZAffinity read strategy
    //                     },
    //                 ),
    //             );
        
    //             // Step 2: Perform SET operations to populate keys
    //             const totalKeys = 4;
    //             for (let i = 0; i < totalKeys; i++) {
    //                 const key = `foo${i}`;
    //                 const value = `bar${i}`;
    //                 await azAffinityClient.set(key, value);
    //             }
        
    //             // Step 3: Perform GET operations and track execution
    //             for (let i = 0; i < totalKeys; i++) {
    //                 const key = `foo${i}`;
    //                 const value = `bar${i}`;
    //                 const result: GlideReturnType = await azAffinityClient.get(key);
        
    //                 if (typeof result === 'string') {
    //                     expect(result).toBe(value);
    //                 } else {
    //                     throw new Error(`Expected string but received ${typeof result}`);
    //                 }
    //             }
        
    //             // Step 4: Retrieve Command Statistics from All Replicas
    //             const replicaClients = await Promise.all(replicaAddresses.map(([host, port]) =>
    //                 GlideClusterClient.createClient(
    //                     getClientConfigurationOption(
    //                         [[host, port]],
    //                         ProtocolVersion.RESP3,
    //                     ),
    //                 )
    //             ));
        
    //             const infoResponses: GlideReturnType[] = await Promise.all(replicaClients.map(client =>
    //                 client.customCommand(["INFO", "commandstats"])
    //             ));
        
    //             // Close all replica clients after fetching stats
    //             await Promise.all(replicaClients.map(client => client.close()));
        
    //             const parseGetCalls = (info: GlideReturnType): number => {
    //                 if (isInfoResponse(info)) {
    //                     const commandstats = info['commandstats'];
    //                     if (isCommandStatsInfo(commandstats)) {
    //                         const cmdstat_get = commandstats['cmdstat_get'];
    //                         if (cmdstat_get && typeof cmdstat_get === 'object') {
    //                             const calls = cmdstat_get['calls'];
    //                             if (typeof calls === 'string' || typeof calls === 'number') {
    //                                 return parseInt(calls.toString(), 10);
    //                             }
    //                         }
    //                     }
    //                 }
    //                 return 0;
    //             };
    //             // Aggregate GET calls across all replicas
    //             let totalGetCalls = 0;
    //             const replicaGetCalls = replicaAddresses.map((address, index) => {
    //                 const info = infoResponses[index];
    //                 const calls = parseGetCalls(info);
    //                 totalGetCalls += calls;
    //                 return calls;
    //             });

    //             // Logging for debugging
    //             console.log(`Total GET calls: ${totalGetCalls}`);
    //             replicaGetCalls.forEach((calls, index) => {
    //                 console.log(`Replica ${index + 1} at ${replicaAddresses[index][0]}:${replicaAddresses[index][1]} handled ${calls} GET calls`);
    //             });
        
    //             // Step 5: Assertions
    //             expect(totalGetCalls).toBe(totalKeys); // All GETs should be handled by replicas
        
    //             // Optionally, check that a specific replica handled all GETs
    //             const specificReplicaIndex = 0; // Index of 'us-east1a0' replica
    //             expect(replicaGetCalls[specificReplicaIndex]).toBe(totalKeys);
    //             replicaGetCalls.forEach((calls, index) => {
    //                 if (index !== specificReplicaIndex) {
    //                     expect(calls).toBe(0);
    //                 }
    //             });
    //         } catch (error) {
    //             testsFailed++;
    //             console.error("Test failed with error:", error);
    //             throw error; // Re-throw to fail the test
    //         } finally {
    //             // Ensure cleanup
    //             if (azAffinityClient) {
    //                 await azAffinityClient.close();
    //             }
    //         }
    //     });
    // });    
// // Helper function to parse CLUSTER NODES output
// function parseClusterNodes(clusterNodesOutput: string): Array<{id: string, ip: string, port: number, role: string}> {
//     const nodesInfo = [];
//     const lines = clusterNodesOutput.trim().split('\n');
//     for (const line of lines) {
//         if (line.trim() === '') continue;
//         const parts = line.split(' ');
//         const id = parts[0];
//         const addr = parts[1];
//         const flags = parts[2].split(',');
//         const role = flags.includes('master') ? 'master' : (flags.includes('slave') || flags.includes('replica') ? 'replica' : 'unknown');
//         const [ip, portStr] = addr.split(':');
//         const port = parseInt(portStr.split('@')[0], 10);
//         nodesInfo.push({ id, ip, port, role });
//     }
//     return nodesInfo;
// }
// // Interface representing the structure of cmdstat_get
// interface CmdStatGet {
//     [statName: string]: string | number;
// }

// // Interface representing the commandstats section
// interface CommandStatsInfo {
//     [commandName: string]: CmdStatGet;
// }

// // Interface representing the entire INFO command response
// interface InfoResponse {
//     [section: string]: any;
//     commandstats?: CommandStatsInfo;
// }

// // Type guard to check if an object is an InfoResponse
// function isInfoResponse(obj: any): obj is InfoResponse {
//     return typeof obj === 'object' && obj !== null;
// }

// // Type guard to check if an object is CommandStatsInfo
// function isCommandStatsInfo(obj: any): obj is CommandStatsInfo {
//     return typeof obj === 'object' && obj !== null;
// }
