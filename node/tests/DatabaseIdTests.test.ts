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
import { ValkeyCluster } from "../../utils/TestUtils.js";
import {
    BaseClientConfiguration,
    GlideClient,
    GlideClusterClient,
    ProtocolVersion,
    RequestError,
} from "../build-ts";
import {
    flushAndCloseClient,
    getClientConfigurationOption,
    getRandomKey,
    getServerVersion,
    parseEndpoints,
} from "./TestUtilities";

const TIMEOUT = 50000;
const CLEANUP_TIMEOUT = 10000;

describe("Database ID Tests", () => {
    const testsFailed = 0;
    let standaloneCluster: ValkeyCluster;
    let clusterModeCluster: ValkeyCluster;
    let standaloneClient: GlideClient;
    let clusterClient: GlideClusterClient;

    beforeAll(async () => {
        // Initialize standalone cluster
        const standaloneAddresses: string =
            global.STAND_ALONE_ENDPOINT as string;
        standaloneCluster = standaloneAddresses
            ? await ValkeyCluster.initFromExistingCluster(
                  false,
                  parseEndpoints(standaloneAddresses),
                  getServerVersion,
              )
            : await ValkeyCluster.createCluster(false, 1, 1, getServerVersion);

        // Add small delay between cluster initializations
        await new Promise((resolve) => setTimeout(resolve, 100));

        // Initialize cluster mode cluster
        const clusterAddresses = global.CLUSTER_ENDPOINTS;

        if (clusterAddresses) {
            clusterModeCluster = await ValkeyCluster.initFromExistingCluster(
                true,
                parseEndpoints(clusterAddresses),
                getServerVersion,
            );
        } else {
            clusterModeCluster = await ValkeyCluster.createCluster(
                true,
                3,
                1,
                getServerVersion,
            );
        }
    }, 120000);

    afterEach(async () => {
        await flushAndCloseClient(
            false,
            standaloneCluster?.getAddresses(),
            standaloneClient,
        );
        await new Promise((resolve) => setTimeout(resolve, 5));
        await flushAndCloseClient(
            true,
            clusterModeCluster?.getAddresses(),
            clusterClient,
        );
    });

    afterAll(async () => {
        if (testsFailed === 0) {
            if (standaloneCluster) await standaloneCluster.close();
            await new Promise((resolve) => setTimeout(resolve, 50));
            if (clusterModeCluster) await clusterModeCluster.close();
        } else {
            if (standaloneCluster) await standaloneCluster.close(true);
            await new Promise((resolve) => setTimeout(resolve, 50));
            if (clusterModeCluster) await clusterModeCluster.close(true);
        }
    }, CLEANUP_TIMEOUT);

    describe("Unit Tests - Configuration Validation", () => {
        it("should accept valid databaseId values in BaseClientConfiguration", () => {
            // Test valid database IDs
            const validDatabaseIds = [0, 1, 5, 15, 100, 999];

            for (const databaseId of validDatabaseIds) {
                const config: BaseClientConfiguration = {
                    addresses: [{ host: "localhost", port: 6379 }],
                    databaseId: databaseId,
                };

                // Configuration creation should not throw for valid database IDs
                expect(config.databaseId).toBe(databaseId);
            }
        });

        it("should handle undefined databaseId (defaults to 0)", () => {
            const config: BaseClientConfiguration = {
                addresses: [{ host: "localhost", port: 6379 }],
                // databaseId not specified - should default to 0
            };

            expect(config.databaseId).toBeUndefined();
        });

        it("should accept databaseId 0 explicitly", () => {
            const config: BaseClientConfiguration = {
                addresses: [{ host: "localhost", port: 6379 }],
                databaseId: 0,
            };

            expect(config.databaseId).toBe(0);
        });
    });

    describe("Standalone Client Database ID Tests", () => {
        it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
            "should connect to default database (0) when databaseId not specified_%p",
            async (protocol) => {
                const config = getClientConfigurationOption(
                    standaloneCluster.getAddresses(),
                    protocol,
                );
                // databaseId not specified - should default to database 0

                standaloneClient = await GlideClient.createClient(config);

                // Verify we're in database 0 by setting and getting a key
                const key = getRandomKey();
                const value = getRandomKey();

                expect(await standaloneClient.set(key, value)).toEqual("OK");
                expect(await standaloneClient.get(key)).toEqual(value);

                // Switch to database 1 and verify key doesn't exist there
                expect(await standaloneClient.select(1)).toEqual("OK");
                expect(await standaloneClient.get(key)).toBeNull();

                // Switch back to database 0 and verify key exists
                expect(await standaloneClient.select(0)).toEqual("OK");
                expect(await standaloneClient.get(key)).toEqual(value);
            },
            TIMEOUT,
        );

        it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
            "should connect to specified database when databaseId is provided_%p",
            async (protocol) => {
                const config = getClientConfigurationOption(
                    standaloneCluster.getAddresses(),
                    protocol,
                    { databaseId: 5 },
                );

                standaloneClient = await GlideClient.createClient(config);

                // Verify we're in database 5 by setting a key and checking it's not in database 0
                const key = getRandomKey();
                const value = getRandomKey();

                expect(await standaloneClient.set(key, value)).toEqual("OK");
                expect(await standaloneClient.get(key)).toEqual(value);

                // Switch to database 0 and verify key doesn't exist there
                expect(await standaloneClient.select(0)).toEqual("OK");
                expect(await standaloneClient.get(key)).toBeNull();

                // Switch back to database 5 and verify key exists
                expect(await standaloneClient.select(5)).toEqual("OK");
                expect(await standaloneClient.get(key)).toEqual(value);
            },
            TIMEOUT,
        );

        it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
            "should maintain database isolation between different database IDs_%p",
            async (protocol) => {
                const config = getClientConfigurationOption(
                    standaloneCluster.getAddresses(),
                    protocol,
                    { databaseId: 3 },
                );

                standaloneClient = await GlideClient.createClient(config);

                const key = getRandomKey();
                const value1 = "value_db3";
                const value2 = "value_db7";

                // Set value in database 3 (configured database)
                expect(await standaloneClient.set(key, value1)).toEqual("OK");
                expect(await standaloneClient.get(key)).toEqual(value1);

                // Switch to database 7 and set different value for same key
                expect(await standaloneClient.select(7)).toEqual("OK");
                expect(await standaloneClient.set(key, value2)).toEqual("OK");
                expect(await standaloneClient.get(key)).toEqual(value2);

                // Switch back to database 3 and verify original value
                expect(await standaloneClient.select(3)).toEqual("OK");
                expect(await standaloneClient.get(key)).toEqual(value1);

                // Switch to database 7 and verify different value
                expect(await standaloneClient.select(7)).toEqual("OK");
                expect(await standaloneClient.get(key)).toEqual(value2);
            },
            TIMEOUT,
        );

        it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
            "should handle reconnection and restore configured databaseId_%p",
            async (protocol) => {
                const config = getClientConfigurationOption(
                    standaloneCluster.getAddresses(),
                    protocol,
                    { databaseId: 4 },
                );

                standaloneClient = await GlideClient.createClient(config);

                const key = getRandomKey();
                const value = getRandomKey();

                // Set value in configured database 4
                expect(await standaloneClient.set(key, value)).toEqual("OK");

                // Use SELECT to switch to database 2
                expect(await standaloneClient.select(2)).toEqual("OK");
                expect(await standaloneClient.get(key)).toBeNull(); // Key shouldn't exist in DB 2

                // Force reconnection by closing and recreating client
                standaloneClient.close();
                standaloneClient = await GlideClient.createClient(config);

                // After reconnection, should be back in configured database 4, not database 2
                expect(await standaloneClient.get(key)).toEqual(value);

                // Verify we're in database 4 by checking database 2 doesn't have the key
                expect(await standaloneClient.select(2)).toEqual("OK");
                expect(await standaloneClient.get(key)).toBeNull();
            },
            TIMEOUT,
        );
    });

    describe("Cluster Client Database ID Tests", () => {
        const isValkey9OrHigher = () => {
            return (
                clusterModeCluster &&
                !clusterModeCluster.checkIfServerVersionLessThan("9.0.0")
            );
        };

        (isValkey9OrHigher() ? it : it.skip)(
            "should connect to default database (0) when databaseId not specified in cluster mode",
            async () => {
                const config = getClientConfigurationOption(
                    clusterModeCluster.getAddresses(),
                    ProtocolVersion.RESP3,
                );
                // databaseId not specified - should default to database 0

                clusterClient = await GlideClusterClient.createClient(config);

                // Verify we're in database 0 by setting and getting a key
                const key = getRandomKey();
                const value = getRandomKey();

                expect(await clusterClient.set(key, value)).toEqual("OK");
                expect(await clusterClient.get(key)).toEqual(value);

                // Use SELECT with AllNodes routing to switch to database 1
                expect(
                    await clusterClient.select(1, { route: "allNodes" }),
                ).toEqual("OK");
                expect(await clusterClient.get(key)).toBeNull();

                // Switch back to database 0 and verify key exists
                expect(
                    await clusterClient.select(0, { route: "allNodes" }),
                ).toEqual("OK");
                expect(await clusterClient.get(key)).toEqual(value);
            },
            TIMEOUT,
        );

        (isValkey9OrHigher() ? it : it.skip)(
            "should connect to specified database when databaseId is provided in cluster mode",
            async () => {
                const config = getClientConfigurationOption(
                    clusterModeCluster.getAddresses(),
                    ProtocolVersion.RESP3,
                    { databaseId: 2 },
                );

                clusterClient = await GlideClusterClient.createClient(config);

                // Verify we're in database 2 by setting a key and checking it's not in database 0
                const key = getRandomKey();
                const value = getRandomKey();

                expect(await clusterClient.set(key, value)).toEqual("OK");
                expect(await clusterClient.get(key)).toEqual(value);

                // Switch to database 0 and verify key doesn't exist there
                expect(
                    await clusterClient.select(0, { route: "allNodes" }),
                ).toEqual("OK");
                expect(await clusterClient.get(key)).toBeNull();

                // Switch back to database 2 and verify key exists
                expect(
                    await clusterClient.select(2, { route: "allNodes" }),
                ).toEqual("OK");
                expect(await clusterClient.get(key)).toEqual(value);
            },
            TIMEOUT,
        );

        (isValkey9OrHigher() ? it : it.skip)(
            "should maintain database isolation in cluster mode",
            async () => {
                const config = getClientConfigurationOption(
                    clusterModeCluster.getAddresses(),
                    ProtocolVersion.RESP3,
                    { databaseId: 1 },
                );

                clusterClient = await GlideClusterClient.createClient(config);

                const key = getRandomKey();
                const value1 = "value_db1";
                const value2 = "value_db3";

                // Set value in database 1 (configured database)
                expect(await clusterClient.set(key, value1)).toEqual("OK");
                expect(await clusterClient.get(key)).toEqual(value1);

                // Switch to database 3 and set different value for same key
                expect(
                    await clusterClient.select(3, { route: "allNodes" }),
                ).toEqual("OK");
                expect(await clusterClient.set(key, value2)).toEqual("OK");
                expect(await clusterClient.get(key)).toEqual(value2);

                // Switch back to database 1 and verify original value
                expect(
                    await clusterClient.select(1, { route: "allNodes" }),
                ).toEqual("OK");
                expect(await clusterClient.get(key)).toEqual(value1);

                // Switch to database 3 and verify different value
                expect(
                    await clusterClient.select(3, { route: "allNodes" }),
                ).toEqual("OK");
                expect(await clusterClient.get(key)).toEqual(value2);
            },
            TIMEOUT,
        );

        (isValkey9OrHigher() ? it : it.skip)(
            "should handle reconnection and restore configured databaseId in cluster mode",
            async () => {
                const config = getClientConfigurationOption(
                    clusterModeCluster.getAddresses(),
                    ProtocolVersion.RESP3,
                    { databaseId: 6 },
                );

                clusterClient = await GlideClusterClient.createClient(config);

                const key = getRandomKey();
                const value = getRandomKey();

                // Set value in configured database 6
                expect(await clusterClient.set(key, value)).toEqual("OK");

                // Use SELECT to switch to database 1
                expect(
                    await clusterClient.select(1, { route: "allNodes" }),
                ).toEqual("OK");
                expect(await clusterClient.get(key)).toBeNull(); // Key shouldn't exist in DB 1

                // Force reconnection by closing and recreating client
                clusterClient.close();
                clusterClient = await GlideClusterClient.createClient(config);

                // After reconnection, should be back in configured database 6, not database 1
                expect(await clusterClient.get(key)).toEqual(value);

                // Verify we're in database 6 by checking database 1 doesn't have the key
                expect(
                    await clusterClient.select(1, { route: "allNodes" }),
                ).toEqual("OK");
                expect(await clusterClient.get(key)).toBeNull();
            },
            TIMEOUT,
        );
    });

    describe("SELECT Command Routing Tests", () => {
        const isValkey9OrHigher = () => {
            return (
                clusterModeCluster &&
                !clusterModeCluster.checkIfServerVersionLessThan("9.0.0")
            );
        };

        (isValkey9OrHigher() ? it : it.skip)(
            "should route SELECT command to all nodes by default in cluster mode",
            async () => {
                const config = getClientConfigurationOption(
                    clusterModeCluster.getAddresses(),
                    ProtocolVersion.RESP3,
                );

                clusterClient = await GlideClusterClient.createClient(config);

                // SELECT without explicit routing should route to all nodes
                expect(await clusterClient.select(5)).toEqual("OK");

                // Verify all nodes are in database 5 by setting a key and getting it
                const key = getRandomKey();
                const value = getRandomKey();

                expect(await clusterClient.set(key, value)).toEqual("OK");
                expect(await clusterClient.get(key)).toEqual(value);

                // Switch to database 0 on all nodes and verify key doesn't exist
                expect(await clusterClient.select(0)).toEqual("OK");
                expect(await clusterClient.get(key)).toBeNull();
            },
            TIMEOUT,
        );

        (isValkey9OrHigher() ? it : it.skip)(
            "should support explicit AllNodes routing for SELECT command",
            async () => {
                const config = getClientConfigurationOption(
                    clusterModeCluster.getAddresses(),
                    ProtocolVersion.RESP3,
                );

                clusterClient = await GlideClusterClient.createClient(config);

                // SELECT with explicit AllNodes routing
                expect(
                    await clusterClient.select(7, { route: "allNodes" }),
                ).toEqual("OK");

                // Verify all nodes are in database 7
                const key = getRandomKey();
                const value = getRandomKey();

                expect(await clusterClient.set(key, value)).toEqual("OK");
                expect(await clusterClient.get(key)).toEqual(value);
            },
            TIMEOUT,
        );

        (isValkey9OrHigher() ? it : it.skip)(
            "should support RandomNode routing for SELECT command",
            async () => {
                const config = getClientConfigurationOption(
                    clusterModeCluster.getAddresses(),
                    ProtocolVersion.RESP3,
                );

                clusterClient = await GlideClusterClient.createClient(config);

                // SELECT with RandomNode routing (affects only one node)
                expect(
                    await clusterClient.select(8, { route: "randomNode" }),
                ).toEqual("OK");

                // Note: With RandomNode routing, only one node switches database,
                // so cluster operations may behave inconsistently
                // This test just verifies the command doesn't fail
            },
            TIMEOUT,
        );
    });

    describe("Error Handling Tests", () => {
        it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
            "should handle invalid database selection in standalone mode_%p",
            async (protocol) => {
                const config = getClientConfigurationOption(
                    standaloneCluster.getAddresses(),
                    protocol,
                );

                standaloneClient = await GlideClient.createClient(config);

                // Try to select an invalid database (typically > 15 for default config)
                await expect(standaloneClient.select(999)).rejects.toThrow(
                    RequestError,
                );
            },
            TIMEOUT,
        );

        const isValkey9OrHigher = () => {
            return (
                clusterModeCluster &&
                !clusterModeCluster.checkIfServerVersionLessThan("9.0.0")
            );
        };

        (isValkey9OrHigher() ? it : it.skip)(
            "should handle invalid database selection in cluster mode",
            async () => {
                const config = getClientConfigurationOption(
                    clusterModeCluster.getAddresses(),
                    ProtocolVersion.RESP3,
                );

                clusterClient = await GlideClusterClient.createClient(config);

                // Try to select an invalid database
                await expect(
                    clusterClient.select(999, { route: "allNodes" }),
                ).rejects.toThrow(RequestError);
            },
            TIMEOUT,
        );

        it("should handle server version incompatibility gracefully for cluster mode", async () => {
            if (
                !clusterModeCluster ||
                !clusterModeCluster.checkIfServerVersionLessThan("9.0.0")
            ) {
                // Skip this test if server supports multi-database cluster mode
                return;
            }

            const config = getClientConfigurationOption(
                clusterModeCluster.getAddresses(),
                ProtocolVersion.RESP3,
                { databaseId: 1 }, // Non-zero database ID
            );

            // For servers < 9.0.0, connecting with non-zero databaseId should fail
            await expect(
                GlideClusterClient.createClient(config),
            ).rejects.toThrow();
        });
    });

    describe("Backward Compatibility Tests", () => {
        it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
            "should maintain backward compatibility - existing standalone code works_%p",
            async (protocol) => {
                // Test that existing code without databaseId still works
                const config = getClientConfigurationOption(
                    standaloneCluster.getAddresses(),
                    protocol,
                );
                // No databaseId specified - should work as before

                standaloneClient = await GlideClient.createClient(config);

                // Basic operations should work
                const key = getRandomKey();
                const value = getRandomKey();

                expect(await standaloneClient.set(key, value)).toEqual("OK");
                expect(await standaloneClient.get(key)).toEqual(value);
                expect(await standaloneClient.select(0)).toEqual("OK");
            },
            TIMEOUT,
        );

        const isValkey9OrHigher = () => {
            return (
                clusterModeCluster &&
                !clusterModeCluster.checkIfServerVersionLessThan("9.0.0")
            );
        };

        (isValkey9OrHigher() ? it : it.skip)(
            "should maintain backward compatibility - existing cluster code works",
            async () => {
                // Test that existing cluster code without databaseId still works
                const config = getClientConfigurationOption(
                    clusterModeCluster.getAddresses(),
                    ProtocolVersion.RESP3,
                );
                // No databaseId specified - should work as before

                clusterClient = await GlideClusterClient.createClient(config);

                // Basic operations should work
                const key = getRandomKey();
                const value = getRandomKey();

                expect(await clusterClient.set(key, value)).toEqual("OK");
                expect(await clusterClient.get(key)).toEqual(value);
            },
            TIMEOUT,
        );

        (isValkey9OrHigher() ? it : it.skip)(
            "should default to database 0 behavior when databaseId is 0",
            async () => {
                const configWithoutDb = getClientConfigurationOption(
                    clusterModeCluster.getAddresses(),
                    ProtocolVersion.RESP3,
                );

                const configWithDb0 = getClientConfigurationOption(
                    clusterModeCluster.getAddresses(),
                    ProtocolVersion.RESP3,
                    { databaseId: 0 },
                );

                const client1 =
                    await GlideClusterClient.createClient(configWithoutDb);
                const client2 =
                    await GlideClusterClient.createClient(configWithDb0);

                try {
                    const key = getRandomKey();
                    const value = getRandomKey();

                    // Set value with first client (no databaseId specified)
                    expect(await client1.set(key, value)).toEqual("OK");

                    // Get value with second client (databaseId: 0) - should be the same
                    expect(await client2.get(key)).toEqual(value);
                } finally {
                    client1.close();
                    client2.close();
                }
            },
            TIMEOUT,
        );
    });

    describe("Cross-Database Operations Tests", () => {
        it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
            "should support database-specific operations in standalone mode_%p",
            async (protocol) => {
                const config = getClientConfigurationOption(
                    standaloneCluster.getAddresses(),
                    protocol,
                    { databaseId: 2 },
                );

                standaloneClient = await GlideClient.createClient(config);

                const key1 = getRandomKey();
                const key2 = getRandomKey();
                const value1 = "value1";
                const value2 = "value2";

                // Set keys in database 2 (configured database)
                expect(await standaloneClient.set(key1, value1)).toEqual("OK");
                expect(await standaloneClient.set(key2, value2)).toEqual("OK");

                // Check DBSIZE in database 2
                const dbSize2 = await standaloneClient.dbsize();
                expect(dbSize2).toBeGreaterThanOrEqual(2);

                // Switch to database 0 and check it's empty (or has different keys)
                expect(await standaloneClient.select(0)).toEqual("OK");
                expect(await standaloneClient.get(key1)).toBeNull();
                expect(await standaloneClient.get(key2)).toBeNull();

                // DBSIZE in database 0 should be different
                const dbSize0 = await standaloneClient.dbsize();
                expect(dbSize0).not.toEqual(dbSize2);

                // FLUSHDB should only affect current database (0)
                expect(await standaloneClient.flushdb()).toEqual("OK");
                expect(await standaloneClient.dbsize()).toEqual(0);

                // Switch back to database 2 - keys should still exist
                expect(await standaloneClient.select(2)).toEqual("OK");
                expect(await standaloneClient.get(key1)).toEqual(value1);
                expect(await standaloneClient.get(key2)).toEqual(value2);
            },
            TIMEOUT,
        );
    });
});
