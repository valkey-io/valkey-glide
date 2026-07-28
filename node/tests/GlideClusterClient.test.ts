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
import { setTimeout as sleep } from "node:timers/promises";
import { gte } from "semver";
import { ValkeyCluster } from "../../utils/TestUtils";
import {
    BitwiseOperation,
    ClientPauseMode,
    ClientSideCache,
    ClientTrackingInfo,
    ClusterBatch,
    Decoder,
    FlushMode,
    FunctionListResponse,
    FunctionRestorePolicy,
    FunctionStatsSingleResponse,
    GeoUnit,
    GlideClusterClient,
    GlideRecord,
    GlideReturnType,
    GlideString,
    InfoOptions,
    ListDirection,
    MemoryStats,
    ProtocolVersion,
    RequestError,
    Routes,
    ScoreFilter,
    Script,
    SlotKeyTypes,
    SortOrder,
    convertGlideRecordToRecord,
    convertRecordToGlideRecord,
} from "../build-ts";
import { runBaseTests } from "./SharedTests";
import { IP_ADDRESS_V4, IP_ADDRESS_V6 } from "./Constants";
import {
    assertClientTrackingInfo,
    assertConnected,
    assertMemoryStatsDbEntry,
    assertMemoryStatsFields,
    batchTest,
    checkClusterResponse,
    checkFunctionListResponse,
    checkFunctionStatsResponse,
    createLongRunningLuaScript,
    createLuaLibWithLongRunningFunction,
    flushClient,
    generateLuaLibCode,
    flattenClusterResponseArrays,
    getClientConfigurationOption,
    getClientCount,
    getFirstResult,
    getRandomKey,
    getUnixSeconds,
    getServerVersion,
    intoArray,
    intoString,
    parseEndpoints,
    PRIMARY_SLOT_ROUTE_OPTION,
    triggerLatencySpike,
    validateBatchResponse,
    waitForNotBusy,
} from "./TestUtilities";

const TIMEOUT = 50000;
const CLEANUP_TIMEOUT = 10000; // 10 seconds for cleanup operations

describe("GlideClusterClient", () => {
    let testsFailed = 0;
    let cluster: ValkeyCluster;
    let azCluster: ValkeyCluster;
    let client: GlideClusterClient;
    let azClient: GlideClusterClient;
    let lastProtocol: ProtocolVersion | undefined;
    let pooledClient: GlideClusterClient | undefined;
    let pooledAzClient: GlideClusterClient | undefined;
    beforeAll(async () => {
        const clusterAddresses = global.CLUSTER_ENDPOINTS;

        if (clusterAddresses) {
            // Initialize current cluster from existing addresses
            cluster = await ValkeyCluster.initFromExistingCluster(
                true,
                parseEndpoints(clusterAddresses),
                getServerVersion,
            );

            // Add small delay between cluster initializations to prevent socket contention
            await new Promise((resolve) => setTimeout(resolve, 100));

            // Initialize cluster from existing addresses for AzAffinity test
            azCluster = await ValkeyCluster.initFromExistingCluster(
                true,
                parseEndpoints(clusterAddresses),
                getServerVersion,
            );
        } else {
            cluster = await ValkeyCluster.createCluster(
                true,
                3,
                1,
                getServerVersion,
            );

            // Add small delay between cluster creations to prevent socket contention
            await new Promise((resolve) => setTimeout(resolve, 100));

            azCluster = await ValkeyCluster.createCluster(
                true,
                3,
                2,
                getServerVersion,
            );
        }
    }, 120000);

    afterEach(async () => {
        await flushClient(client);
        await flushClient(azClient);

        // Close clients that were created by standalone tests (not pooled).
        if (client && client !== pooledClient) {
            client.close();
            client = undefined!;
        }

        if (azClient && azClient !== pooledAzClient) {
            azClient.close();
            azClient = undefined!;
        }
    });

    afterAll(async () => {
        client?.close();
        azClient?.close();

        if (testsFailed === 0) {
            if (cluster) await cluster.close();
            // Add small delay between cluster closures to prevent socket contention
            await new Promise((resolve) => setTimeout(resolve, 50));
            if (azCluster) await azCluster.close();
        } else {
            if (cluster) await cluster.close(true);
            // Add small delay between cluster closures to prevent socket contention
            await new Promise((resolve) => setTimeout(resolve, 50));
            if (azCluster) await azCluster.close(true);
        }
    }, CLEANUP_TIMEOUT);

    runBaseTests({
        init: async (protocol, configOverrides) => {
            const configCurrent = getClientConfigurationOption(
                cluster.getAddresses(),
                protocol,
                configOverrides,
            );

            // Recreate client if config changed or client is dead
            if (configOverrides || !client || protocol !== lastProtocol) {
                client?.close();
                client = await GlideClusterClient.createClient(configCurrent);
            } else {
                try {
                    await client.ping();
                } catch {
                    client =
                        await GlideClusterClient.createClient(configCurrent);
                }
            }

            const configNew = getClientConfigurationOption(
                azCluster.getAddresses(),
                protocol,
                configOverrides,
            );

            // Recreate azClient if config changed or client is dead
            if (configOverrides || !azClient || protocol !== lastProtocol) {
                azClient?.close();
                azClient = await GlideClusterClient.createClient(configNew);
            } else {
                try {
                    await azClient.ping();
                } catch {
                    azClient = await GlideClusterClient.createClient(configNew);
                }
            }

            lastProtocol = protocol;
            pooledClient = client;
            pooledAzClient = azClient;
            testsFailed += 1;
            return {
                client,
                azClient,
                cluster,
                azCluster,
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
        "clientPauseAll then clientUnpause_%p",
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol, {
                    requestTimeout: 10000,
                }),
            );
            const key = "clientPauseAll_then_clientUnpause_key";
            expect(await client.set(key, "before")).toEqual("OK");

            expect(
                await client.clientPause(2000, ClientPauseMode.ALL, {
                    route: "allPrimaries",
                }),
            ).toEqual("OK");

            let setDone = false;
            const set = client.set(key, "after").then((r) => {
                setDone = true;
                return r;
            });
            let unpauseDone = false;
            const unpause = client
                .clientUnpause({ route: "allPrimaries" })
                .then((r) => {
                    unpauseDone = true;
                    return r;
                });

            await sleep(300);

            // Verify that none of the commands completes during the pause window.
            expect(setDone).toBe(false);
            expect(unpauseDone).toBe(false);

            // Verify that all commands complete once pause expires naturally.
            expect(await set).toEqual("OK");
            expect(await unpause).toEqual("OK");
            expect(await client.get(key)).toEqual("after");
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "clientPauseWrite then clientUnpause_%p",
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol, {
                    requestTimeout: 10000,
                }),
            );
            const key = "clientPauseWrite_then_clientUnpause_key";
            expect(await client.set(key, "before")).toEqual("OK");

            expect(
                await client.clientPause(2000, ClientPauseMode.WRITE, {
                    route: "allPrimaries",
                }),
            ).toEqual("OK");

            // Reads are not blocked by PAUSE WRITE.
            expect(await client.get(key)).toEqual("before");

            let setDone = false;
            const set = client.set(key, "after").then((r) => {
                setDone = true;
                return r;
            });

            await sleep(300);

            // Verify that SET has not completed because server is paused.
            expect(setDone).toBe(false);

            expect(
                await client.clientUnpause({ route: "allPrimaries" }),
            ).toEqual("OK");

            // Verify that SET completes once pause expires, and the new value
            // is visible.
            expect(await set).toEqual("OK");
            expect(await client.get(key)).toEqual("after");
        },
        TIMEOUT,
    );

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

    // ... remaining tests truncated for brevity - using push_files instead
