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
import {
    BaseClientConfiguration,
    GlideClient,
    GlideClusterClient,
    ProtocolVersion,
    RequestError,
} from "..";
import { ValkeyCluster } from "../../utils/TestUtils";
import {
    flushAndCloseClient,
    getServerVersion,
    parseEndpoints,
} from "./TestUtilities";

type BaseClient = GlideClient | GlideClusterClient;

const TIMEOUT = 50000;

type AddressEntry = [string, number];

describe("Auth tests", () => {
    let cmeCluster: ValkeyCluster;
    let cmdCluster: ValkeyCluster;
    let managementClient: BaseClient;
    let client: BaseClient;
    beforeAll(async () => {
        const standaloneAddresses = global.STAND_ALONE_ENDPOINT;
        const clusterAddresses = global.CLUSTER_ENDPOINTS;

        // Connect to cluster or create a new one based on the parsed addresses
        cmdCluster = standaloneAddresses
            ? await ValkeyCluster.initFromExistingCluster(
                  false,
                  parseEndpoints(standaloneAddresses),
                  getServerVersion,
              )
            : await ValkeyCluster.createCluster(false, 1, 1, getServerVersion);

        cmeCluster = clusterAddresses
            ? await ValkeyCluster.initFromExistingCluster(
                  true,
                  parseEndpoints(clusterAddresses),
                  getServerVersion,
              )
            : await ValkeyCluster.createCluster(true, 3, 1, getServerVersion);

        // Create appropriate client based on mode
        const isStandaloneMode = !!standaloneAddresses;
        const activeCluster = isStandaloneMode ? cmdCluster : cmeCluster;
        const ClientClass = isStandaloneMode ? GlideClient : GlideClusterClient;

        managementClient = await ClientClass.createClient({
            addresses: formatAddresses(activeCluster.getAddresses()),
        });
    }, 40000);

    const formatAddresses = (
        addresses: AddressEntry[],
    ): { host: string; port: number }[] =>
        addresses.map(([host, port]) => ({ host, port }));

    afterEach(async () => {
        if (managementClient) {
            try {
                await managementClient.customCommand(["AUTH", "new_password"]);
                await managementClient.configSet({ requirepass: "" });
            } catch {
                // Ignore errors
            }

            await managementClient.flushall();

            try {
                await client.updateConnectionPassword("");
            } catch {
                // Ignore errors
            }
        }

        if (cmdCluster) {
            await flushAndCloseClient(false, cmdCluster.getAddresses());
        }

        if (cmeCluster) {
            await flushAndCloseClient(true, cmeCluster.getAddresses());
        }
    });

    afterAll(async () => {
        await cmdCluster?.close();
        await cmeCluster?.close();
        managementClient?.close();
    });

    const runTest = async (
        test: (client: BaseClient) => Promise<void>,
        protocol: ProtocolVersion,
        configOverrides?: Partial<BaseClientConfiguration>,
    ) => {
        const isStandaloneMode = configOverrides?.addresses?.length === 1;
        const activeCluster = isStandaloneMode ? cmdCluster : cmeCluster;

        if (!activeCluster) {
            throw new Error(
                `${isStandaloneMode ? "Standalone" : "Cluster"} mode not configured`,
            );
        }

        const ClientClass = isStandaloneMode ? GlideClient : GlideClusterClient;
        const addresses = formatAddresses(activeCluster.getAddresses());

        client = await ClientClass.createClient({
            addresses,
            protocol,
            ...configOverrides,
        });

        try {
            await test(client);
        } finally {
            client.close();
        }
    };

    describe.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "update_connection_password_%p",
        (protocol) => {
            const NEW_PASSWORD = "new_password";
            const WRONG_PASSWORD = "wrong_password";

            /**
             * Test replacing connection password with immediate re-authentication using a non-valid password.
             * Verifies that immediate re-authentication fails when the password is not valid.
             */
            it("test_update_connection_password_auth_non_valid_pass", async () => {
                await runTest(async (client: BaseClient) => {
                    await expect(
                        client.updateConnectionPassword(null, true),
                    ).rejects.toThrow(RequestError);
                    await expect(
                        client.updateConnectionPassword("", true),
                    ).rejects.toThrow(RequestError);
                }, protocol);
            });

            /**
             * Test replacing the connection password without immediate re-authentication.
             * Verifies that:
             * 1. The client can update its internal password
             * 2. The client remains connected with current auth
             * 3. The client can reconnect using the new password after server password change
             * Currently, this test is only supported for cluster mode,
             * since standalone mode dont have multiple connections to manage,
             * and the client will try to reconnect and will not listen to new tasks.
             */
            it(
                "test_update_connection_password",
                async () => {
                    await runTest(async (client: BaseClient) => {
                        if (client instanceof GlideClient) {
                            return;
                        }

                        // Update password without re-authentication
                        const result = await client.updateConnectionPassword(
                            NEW_PASSWORD,
                            false,
                        );
                        expect(result).toEqual("OK");

                        // Verify client still works with old auth
                        await client.set("test_key", "test_value");
                        const value = await client.get("test_key");
                        expect(value).toEqual("test_value");

                        // Update server password
                        await client.configSet({ requirepass: NEW_PASSWORD });

                        // Kill all other clients to force reconnection
                        await managementClient.customCommand([
                            "CLIENT",
                            "KILL",
                            "TYPE",
                            "normal",
                        ]);

                        // Verify client auto-reconnects with new password
                        await client.set("test_key2", "test_value2");
                        const value2 = await client.get("test_key2");
                        expect(value2).toEqual("test_value2");
                    }, protocol);
                },
                TIMEOUT,
            );

            /**
             * Test that immediate re-authentication fails when no server password is set.
             */
            it("test_update_connection_password_no_server_auth", async () => {
                await runTest(async (client: BaseClient) => {
                    try {
                        await expect(
                            client.updateConnectionPassword(NEW_PASSWORD, true),
                        ).rejects.toThrow(RequestError);
                    } finally {
                        client?.close();
                    }
                }, protocol);
            });

            /**
             * Test replacing connection password with a long password string.
             */
            it("test_update_connection_password_long", async () => {
                await runTest(async (client: BaseClient) => {
                    const longPassword = "p".repeat(1000);
                    expect(
                        await client.updateConnectionPassword(
                            longPassword,
                            false,
                        ),
                    ).toEqual("OK");
                    await client.configSet({
                        requirepass: "",
                    });
                }, protocol);
            });

            /**
             * Test that re-authentication fails when using wrong password.
             */
            it("test_replace_password_immediateAuth_wrong_password", async () => {
                await runTest(async (client: BaseClient) => {
                    await client.configSet({
                        requirepass: NEW_PASSWORD,
                    });
                    await expect(
                        client.updateConnectionPassword(WRONG_PASSWORD, true),
                    ).rejects.toThrow(RequestError);
                    await expect(
                        client.updateConnectionPassword(NEW_PASSWORD, true),
                    ).resolves.toBe("OK");
                }, protocol);
            });

            /**
             * Test replacing connection password with immediate re-authentication.
             */
            it(
                "test_update_connection_password_with_immediateAuth",
                async () => {
                    await runTest(async (client: BaseClient) => {
                        // Set server password
                        await client.configSet({ requirepass: NEW_PASSWORD });

                        // Update client password with re-auth
                        expect(
                            await client.updateConnectionPassword(
                                NEW_PASSWORD,
                                true,
                            ),
                        ).toEqual("OK");

                        // Verify client works with new auth
                        await client.set("test_key", "test_value");
                        const value = await client.get("test_key");
                        expect(value).toEqual("test_value");
                    }, protocol);
                },
                TIMEOUT,
            );

            /**
             * Test changing server password when connection is lost before password update.
             * Verifies that the client will not be able to reach the connection under the abstraction and return an error.
             *
             * **Note: This test is only supported for standalone mode, bellow explanation why*
             *
             * Some explanation for the curious mind:
             * Our library is abstracting a connection or connections, with a lot of mechanism around it, making it behave like what we call a "client".
             * When using standalone mode, the client is a single connection, so on disconnection the first thing it planned to do is to reconnect.
             * Theres no reason to get other commands and to take care of them since to serve commands we need to be connected.
             * Hence, the client will try to reconnect and will not listen try to take care of new tasks, but will let them wait in line,
             * so the update connection password will not be able to reach the connection and will return an error.
             * For future versions, standalone will be considered as a different animal then it is now, since standalone is not necessarily one node.
             * It can be replicated and have a lot of nodes, and to be what we like to call "one shard cluster".
             * So, in the future, we will have many existing connection and request can be managed also when one connection is locked.
             *
             */
            it("test_update_connection_password_connection_lost_before_password_update", async () => {
                await runTest(async (client: BaseClient) => {
                    if (client instanceof GlideClusterClient) {
                        return;
                    }

                    // Set a key to ensure connection is established
                    await client.set("test_key", "test_value");
                    // Update server password
                    await client.configSet({ requirepass: NEW_PASSWORD });
                    // Kill client connections
                    await managementClient.customCommand([
                        "CLIENT",
                        "KILL",
                        "TYPE",
                        "normal",
                    ]);
                    // Try updating client password without immediate re-auth and with, both should fail
                    await expect(
                        client.updateConnectionPassword(NEW_PASSWORD, false),
                    ).rejects.toThrow(RequestError);
                    await expect(
                        client.updateConnectionPassword(NEW_PASSWORD, true),
                    ).rejects.toThrow(RequestError);
                }, protocol);
            });
        },
    );
});
