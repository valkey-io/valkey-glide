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
    ClusterClientConfiguration,
    ClusterTransaction,
    GlideClusterClient,
    InfoOptions,
    ProtocolVersion,
    Routes,
    ScoreFilter,
} from "..";
import { RedisCluster } from "../../utils/TestUtils.js";
import { FlushMode } from "../build-ts/src/commands/FlushMode";
import { runBaseTests } from "./SharedTests";
import {
    checkClusterResponse,
    checkSimple,
    flushAndCloseClient,
    generateLuaLibCode,
    getClientConfigurationOption,
    getFirstResult,
    intoArray,
    intoString,
    parseCommandLineArgs,
    parseEndpoints,
    transactionTest,
} from "./TestUtilities";
type Context = {
    client: GlideClusterClient;
};

const TIMEOUT = 50000;

describe("GlideClusterClient", () => {
    let testsFailed = 0;
    let cluster: RedisCluster;
    let client: GlideClusterClient;
    beforeAll(async () => {
        const clusterAddresses = parseCommandLineArgs()["cluster-endpoints"];
        // Connect to cluster or create a new one based on the parsed addresses
        cluster = clusterAddresses
            ? RedisCluster.initFromExistingCluster(
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

    runBaseTests<Context>({
        init: async (protocol, clientName?) => {
            const options = getClientConfigurationOption(
                cluster.getAddresses(),
                protocol,
            );
            options.protocol = protocol;
            options.clientName = clientName;
            testsFailed += 1;
            client = await GlideClusterClient.createClient(options);
            return {
                context: {
                    client,
                },
                client,
                cluster,
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
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            const info_server = getFirstResult(
                await client.info([InfoOptions.Server]),
            );
            expect(intoString(info_server)).toEqual(
                expect.stringContaining("# Server"),
            );

            const infoReplicationValues = Object.values(
                await client.info([InfoOptions.Replication]),
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
            const result = await client.info(
                [InfoOptions.Server],
                "randomNode",
            );
            expect(intoString(result)).toEqual(
                expect.stringContaining("# Server"),
            );
            expect(intoString(result)).toEqual(
                expect.not.stringContaining("# Errorstats"),
            );
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
                    await client.customCommand(
                        ["cluster", "nodes"],
                        "randomNode",
                    ),
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
                        type: "routeByAddress",
                        host,
                    }),
                ),
            );

            expect(result).toEqual(secondResult);

            const [host2, port] = host.split(":");

            // check that routing with explicit port works
            const thirdResult = cleanResult(
                intoString(
                    await client.customCommand(["cluster", "nodes"], {
                        type: "routeByAddress",
                        host: host2,
                        port: Number(port),
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
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            const transaction = new ClusterTransaction();
            transaction.configSet({ timeout: "1000" });
            transaction.configGet(["timeout"]);
            const result = await client.exec(transaction);
            expect(intoString(result)).toEqual(
                intoString(["OK", { timeout: "1000" }]),
            );
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
            const result = await client.exec(transaction);
            expect(intoString(result)).toEqual(intoString(expectedRes));
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
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            const message = uuidv4();
            const echoDict = await client.echo(message, "allNodes");

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
                client.brpop(["abc", "zxy", "lkn"], 0.1),
                client.bitop(BitwiseOperation.AND, "abc", ["zxy", "lkn"]),
                client.smove("abc", "zxy", "value"),
                client.renamenx("abc", "zxy"),
                client.sinter(["abc", "zxy", "lkn"]),
                client.sinterstore("abc", ["zxy", "lkn"]),
                client.zinterstore("abc", ["zxy", "lkn"]),
                client.sunionstore("abc", ["zxy", "lkn"]),
                client.sunion(["abc", "zxy", "lkn"]),
                client.pfcount(["abc", "zxy", "lkn"]),
                client.sdiff(["abc", "zxy", "lkn"]),
                client.sdiffstore("abc", ["zxy", "lkn"]),
            ];

            if (gte(cluster.getVersion(), "6.2.0")) {
                promises.push(
                    client.zdiff(["abc", "zxy", "lkn"]),
                    client.zdiffWithScores(["abc", "zxy", "lkn"]),
                    client.zdiffstore("abc", ["zxy", "lkn"]),
                );
            }

            if (gte(cluster.getVersion(), "7.0.0")) {
                promises.push(
                    client.sintercard(["abc", "zxy", "lkn"]),
                    client.zintercard(["abc", "zxy", "lkn"]),
                    client.zmpop(["abc", "zxy", "lkn"], ScoreFilter.MAX),
                );
            }

            for (const promise of promises) {
                try {
                    await promise;
                } catch (e) {
                    expect((e as Error).message.toLowerCase()).toContain(
                        "crossslot",
                    );
                }
            }

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
            // TODO touch
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
            const maxmemoryPolicy = String(config[maxmemoryPolicyKey]);

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
            const maxmemoryPolicy = String(config[maxmemoryPolicyKey]);

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
            const result1 = await client.lolwut({}, "allNodes");
            expect(intoString(result1)).toEqual(
                expect.stringContaining("Redis ver. "),
            );

            const result2 = await client.lolwut(
                { version: 2, parameters: [10, 20] },
                "allNodes",
            );
            expect(intoString(result2)).toEqual(
                expect.stringContaining("Redis ver. "),
            );

            // test with single-node route
            const result3 = await client.lolwut({}, "randomNode");
            expect(intoString(result3)).toEqual(
                expect.stringContaining("Redis ver. "),
            );

            const result4 = await client.lolwut(
                { version: 2, parameters: [10, 20] },
                "randomNode",
            );
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
        "flushdb flushall dbsize test_%p",
        async (protocol) => {
            const client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            expect(await client.dbsize()).toBeGreaterThanOrEqual(0);
            checkSimple(await client.set(uuidv4(), uuidv4())).toEqual("OK");
            expect(await client.dbsize()).toBeGreaterThan(0);

            checkSimple(await client.flushall()).toEqual("OK");
            expect(await client.dbsize()).toEqual(0);

            checkSimple(await client.set(uuidv4(), uuidv4())).toEqual("OK");
            expect(await client.dbsize()).toEqual(1);
            checkSimple(await client.flushdb(FlushMode.ASYNC)).toEqual("OK");
            expect(await client.dbsize()).toEqual(0);

            checkSimple(await client.set(uuidv4(), uuidv4())).toEqual("OK");
            expect(await client.dbsize()).toEqual(1);
            checkSimple(await client.flushdb(FlushMode.SYNC)).toEqual("OK");
            expect(await client.dbsize()).toEqual(0);

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
                        "function load",
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
                                // TODO use commands instead of customCommand once implemented
                                // verify function does not yet exist
                                const functionList = await client.customCommand(
                                    [
                                        "FUNCTION",
                                        "LIST",
                                        "LIBRARYNAME",
                                        libName,
                                    ],
                                );
                                checkClusterResponse(
                                    functionList as object,
                                    singleNodeRoute,
                                    (value) => expect(value).toEqual([]),
                                );
                                // load the library
                                checkSimple(
                                    await client.functionLoad(code),
                                ).toEqual(libName);
                                // call functions from that library to confirm that it works
                                let fcall = await client.customCommand(
                                    ["FCALL", funcName, "0", "one", "two"],
                                    route,
                                );
                                checkClusterResponse(
                                    fcall as object,
                                    singleNodeRoute,
                                    (value) =>
                                        checkSimple(value).toEqual("one"),
                                );

                                fcall = await client.customCommand(
                                    ["FCALL_RO", funcName, "0", "one", "two"],
                                    route,
                                );
                                checkClusterResponse(
                                    fcall as object,
                                    singleNodeRoute,
                                    (value) =>
                                        checkSimple(value).toEqual("one"),
                                );

                                // re-load library without replace
                                await expect(
                                    client.functionLoad(code),
                                ).rejects.toThrow(
                                    `Library '${libName}' already exists`,
                                );

                                // re-load library with replace
                                checkSimple(
                                    await client.functionLoad(code, true),
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
                                checkSimple(
                                    await client.functionLoad(newCode, true),
                                ).toEqual(libName);

                                fcall = await client.customCommand(
                                    ["FCALL", func2Name, "0", "one", "two"],
                                    route,
                                );
                                checkClusterResponse(
                                    fcall as object,
                                    singleNodeRoute,
                                    (value) => expect(value).toEqual(2),
                                );

                                fcall = await client.customCommand(
                                    ["FCALL_RO", func2Name, "0", "one", "two"],
                                    route,
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
                },
            );
        },
    );

    describe.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "Protocol is RESP2 = %s",
        (protocol) => {
            describe.each([true, false])(
                "Single node route = %s",
                (singleNodeRoute) => {
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

                                // TODO use commands instead of customCommand once implemented
                                // verify function does not yet exist
                                const functionList1 =
                                    await client.customCommand([
                                        "FUNCTION",
                                        "LIST",
                                        "LIBRARYNAME",
                                        libName,
                                    ]);
                                checkClusterResponse(
                                    functionList1 as object,
                                    singleNodeRoute,
                                    (value) => expect(value).toEqual([]),
                                );

                                // load the library
                                checkSimple(
                                    await client.functionLoad(
                                        code,
                                        undefined,
                                        route,
                                    ),
                                ).toEqual(libName);

                                // flush functions
                                expect(
                                    await client.functionFlush(
                                        FlushMode.SYNC,
                                        route,
                                    ),
                                ).toEqual("OK");
                                expect(
                                    await client.functionFlush(
                                        FlushMode.ASYNC,
                                        route,
                                    ),
                                ).toEqual("OK");

                                // TODO use commands instead of customCommand once implemented
                                // verify function does not exist
                                const functionList2 =
                                    await client.customCommand([
                                        "FUNCTION",
                                        "LIST",
                                        "LIBRARYNAME",
                                        libName,
                                    ]);
                                checkClusterResponse(
                                    functionList2 as object,
                                    singleNodeRoute,
                                    (value) => expect(value).toEqual([]),
                                );

                                // Attempt to re-load library without overwriting to ensure FLUSH was effective
                                checkSimple(
                                    await client.functionLoad(
                                        code,
                                        undefined,
                                        route,
                                    ),
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
                },
            );
        },
    );

    describe.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "Protocol is RESP2 = %s",
        (protocol) => {
            describe.each([true, false])(
                "Single node route = %s",
                (singleNodeRoute) => {
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
                                // TODO use commands instead of customCommand once implemented
                                // verify function does not yet exist
                                let functionList = await client.customCommand([
                                    "FUNCTION",
                                    "LIST",
                                    "LIBRARYNAME",
                                    libName,
                                ]);
                                checkClusterResponse(
                                    functionList as object,
                                    singleNodeRoute,
                                    (value) => expect(value).toEqual([]),
                                );
                                // load the library
                                checkSimple(
                                    await client.functionLoad(
                                        code,
                                        undefined,
                                        route,
                                    ),
                                ).toEqual(libName);

                                // Delete the function
                                expect(
                                    await client.functionDelete(libName, route),
                                ).toEqual("OK");

                                // TODO use commands instead of customCommand once implemented
                                // verify function does not exist
                                functionList = await client.customCommand([
                                    "FUNCTION",
                                    "LIST",
                                    "LIBRARYNAME",
                                    libName,
                                ]);
                                checkClusterResponse(
                                    functionList as object,
                                    singleNodeRoute,
                                    (value) => expect(value).toEqual([]),
                                );

                                // Delete a non-existing library
                                await expect(
                                    client.functionDelete(libName, route),
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
                },
            );
        },
    );

    it.each([
        [true, ProtocolVersion.RESP3],
        [false, ProtocolVersion.RESP3],
    ])("simple pubsub test", async (sharded, protocol) => {
        if (sharded && cluster.checkIfServerVersionLessThan("7.2.0")) {
            return;
        }

        const channel = "test-channel";
        const shardedChannel = "test-channel-sharded";
        const channelsAndPatterns: Partial<
            Record<ClusterClientConfiguration.PubSubChannelModes, Set<string>>
        > = {
            [ClusterClientConfiguration.PubSubChannelModes.Exact]: new Set([
                channel,
            ]),
        };

        if (sharded) {
            channelsAndPatterns[
                ClusterClientConfiguration.PubSubChannelModes.Sharded
            ] = new Set([shardedChannel]);
        }

        const config: ClusterClientConfiguration = getClientConfigurationOption(
            cluster.getAddresses(),
            protocol,
        );
        config.pubsubSubscriptions = {
            channelsAndPatterns: channelsAndPatterns,
        };
        client = await GlideClusterClient.createClient(config);
        const message = uuidv4();

        await client.publish(message, channel);
        const sleep = new Promise((resolve) => setTimeout(resolve, 1000));
        await sleep;

        let pubsubMsg = await client.getPubSubMessage();
        expect(pubsubMsg.channel.toString()).toBe(channel);
        expect(pubsubMsg.message.toString()).toBe(message);
        expect(pubsubMsg.pattern).toBeNull();

        if (sharded) {
            await client.publish(message, shardedChannel, true);
            await sleep;
            pubsubMsg = await client.getPubSubMessage();
            console.log(pubsubMsg);
            expect(pubsubMsg.channel.toString()).toBe(shardedChannel);
            expect(pubsubMsg.message.toString()).toBe(message);
            expect(pubsubMsg.pattern).toBeNull();
        }

        client.close();
    });
});
