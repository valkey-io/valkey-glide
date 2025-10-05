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
import { ValkeyCluster } from "../../utils/TestUtils";
import {
    BaseClientConfiguration,
    GlideClient,
    GlideClusterClient,
    ProtocolVersion,
    RequestError,
    ServiceType,
    IamAuthConfig,
    Logger,
} from "../build-ts";
import {
    flushAndCloseClient,
    getServerVersion,
    parseEndpoints,
} from "./TestUtilities";

type BaseClient = GlideClient | GlideClusterClient;

const USERNAME = "username";
const INITIAL_PASSWORD = "initial_password";
const NEW_PASSWORD = "new_password";
const WRONG_PASSWORD = "wrong_password";
const TIMEOUT = 50000;

type AddressEntry = [string, number];

describe("Auth tests", () => {
    let cmeCluster: ValkeyCluster;
    let cmdCluster: ValkeyCluster;
    let managementClient: BaseClient;
    let client: BaseClient;
    let managementClientCMD: GlideClient;
    let managementClientCME: GlideClusterClient;
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

        managementClientCMD = await GlideClient.createClient({
            addresses: formatAddresses(cmdCluster.getAddresses()),
        });
        managementClientCME = await GlideClusterClient.createClient({
            addresses: formatAddresses(cmeCluster.getAddresses()),
        });
    }, 40000);

    const formatAddresses = (
        addresses: AddressEntry[],
    ): { host: string; port: number }[] =>
        addresses.map(([host, port]) => ({ host, port }));

    async function setNewAclUsernameWithPassword(
        client: BaseClient,
        username: string,
        password: string,
    ) {
        const result = await client.customCommand([
            "ACL",
            "SETUSER",
            username,
            "on",
            `>${password}`,
            "~*",
            "&*",
            "+@all",
        ]);
        expect(result).toEqual("OK");
    }

    async function deleteAclUsernameAndPassword(
        client: BaseClient,
        username: string,
    ) {
        const result = await client.customCommand(["ACL", "DELUSER", username]);
        expect(result).toEqual(1);
    }

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

        await deleteAclUsernameAndPassword(managementClient, USERNAME);

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
        managementClientCME?.close();
        managementClientCMD?.close();
    });

    const runTest = async (
        test: (client: BaseClient) => Promise<void>,
        protocol: ProtocolVersion,
        clusterMode: boolean,
        configOverrides?: Partial<BaseClientConfiguration>,
    ) => {
        const activeCluster = clusterMode ? cmeCluster : cmdCluster;

        managementClient = clusterMode
            ? managementClientCME
            : managementClientCMD;

        if (!activeCluster) {
            throw new Error(
                `${clusterMode ? "Cluster" : "Standalone"} mode not configured`,
            );
        }

        await setNewAclUsernameWithPassword(
            managementClient,
            USERNAME,
            INITIAL_PASSWORD,
        );

        const ClientClass = clusterMode ? GlideClusterClient : GlideClient;
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

    describe.each([
        { clusterMode: false, protocol: ProtocolVersion.RESP2 },
        { clusterMode: false, protocol: ProtocolVersion.RESP3 },
        { clusterMode: true, protocol: ProtocolVersion.RESP2 },
        { clusterMode: true, protocol: ProtocolVersion.RESP3 },
    ])(
        "update_connection_password_cluster$clusterMode_$protocol",
        ({ clusterMode, protocol }) => {
            /**
             * Test replacing connection password with immediate re-authentication using a non-valid password.
             * Verifies that immediate re-authentication fails when the password is not valid.
             */
            it("test_update_connection_password_auth_non_valid_pass", async () => {
                await runTest(
                    async (client: BaseClient) => {
                        await expect(
                            client.updateConnectionPassword(null, true),
                        ).rejects.toThrow(RequestError);
                        await expect(
                            client.updateConnectionPassword("", true),
                        ).rejects.toThrow(RequestError);
                    },
                    protocol,
                    clusterMode,
                );
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
                    await runTest(
                        async (client: BaseClient) => {
                            // Update password without re-authentication
                            const result =
                                await client.updateConnectionPassword(
                                    NEW_PASSWORD,
                                    false,
                                );
                            expect(result).toEqual("OK");

                            // Verify client still works with old auth
                            await client.set("test_key", "test_value");
                            const value = await client.get("test_key");
                            expect(value).toEqual("test_value");

                            // Update server password
                            await client.configSet({
                                requirepass: NEW_PASSWORD,
                            });

                            // Kill all other clients to force reconnection
                            await managementClient.customCommand([
                                "CLIENT",
                                "KILL",
                                "TYPE",
                                "normal",
                            ]);

                            // Sleep to ensure disconnection
                            await new Promise((resolve) =>
                                setTimeout(resolve, 1000),
                            );

                            // Verify client auto-reconnects with new password
                            await client.set("test_key2", "test_value2");
                            const value2 = await client.get("test_key2");
                            expect(value2).toEqual("test_value2");
                        },
                        protocol,
                        clusterMode,
                    );
                },
                TIMEOUT,
            );

            /**
             * Test that immediate re-authentication fails when no server password is set.
             */
            it("test_update_connection_password_no_server_auth", async () => {
                await runTest(
                    async (client: BaseClient) => {
                        try {
                            await expect(
                                client.updateConnectionPassword(
                                    NEW_PASSWORD,
                                    true,
                                ),
                            ).rejects.toThrow(RequestError);
                        } finally {
                            client?.close();
                        }
                    },
                    protocol,
                    clusterMode,
                );
            });

            /**
             * Test replacing connection password with a long password string.
             */
            it("test_update_connection_password_long", async () => {
                await runTest(
                    async (client: BaseClient) => {
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
                    },
                    protocol,
                    clusterMode,
                );
            });

            /**
             * Test that re-authentication fails when using wrong password.
             */
            it("test_replace_password_immediateAuth_wrong_password", async () => {
                await runTest(
                    async (client: BaseClient) => {
                        await client.configSet({
                            requirepass: NEW_PASSWORD,
                        });
                        await expect(
                            client.updateConnectionPassword(
                                WRONG_PASSWORD,
                                true,
                            ),
                        ).rejects.toThrow(RequestError);
                        await expect(
                            client.updateConnectionPassword(NEW_PASSWORD, true),
                        ).resolves.toBe("OK");
                    },
                    protocol,
                    clusterMode,
                );
            });

            /**
             * Test replacing connection password with immediate re-authentication.
             */
            it(
                "test_update_connection_password_with_immediateAuth",
                async () => {
                    await runTest(
                        async (client: BaseClient) => {
                            // Set server password
                            await client.configSet({
                                requirepass: NEW_PASSWORD,
                            });

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
                        },
                        protocol,
                        clusterMode,
                    );
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
             * However, it will try to reconnect with the wrong password, and thus will fail to reconnect and won't have valid connection
             * to server. Hence, authenticating with non-immediate auth will succeed, since it doesn't require an active connection
             * to the server (it's an internal update), while immediate auth will fail (as would any command that requires an active server connection).
             * For future versions, standalone will be considered as a different animal then it is now, since standalone is not necessarily one node.
             * It can be replicated and have a lot of nodes, and to be what we like to call "one shard cluster".
             * So, in the future, we will have many existing connection and request can be managed also when one connection is locked.
             *
             */
            it("test_update_connection_password_connection_lost_before_password_update", async () => {
                await runTest(
                    async (client: BaseClient) => {
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
                        // Sleep to ensure disconnection
                        await new Promise((resolve) =>
                            setTimeout(resolve, 1000),
                        );

                        // Try updating client password without immediate re-auth and with - non immediate should succeed,
                        // immediate auth should fail (failing to reconnect)

                        const result = await client.updateConnectionPassword(
                            NEW_PASSWORD,
                            false,
                        );
                        expect(result).toEqual("OK");
                        await expect(
                            client.updateConnectionPassword(NEW_PASSWORD, true),
                        ).rejects.toThrow(RequestError);
                    },
                    protocol,
                    clusterMode,
                );
            });

            /*
             * Test replacing the connection password without immediate re-authentication, when the client is pre-authenticated as an acl user.
             * Verifies that:
             * 1. The client can update its internal password
             * 2. The client remains connected with current auth after non-immediate password update.
             * 3. The client can reconnect using the new password after the user was deleted and reset with the new password on the server side (which causes the server to kill connections).
             * Currently, this test is only supported for cluster mode,
             * since standalone mode dont have multiple connections to manage,
             * and the client will try to reconnect and will not listen to new tasks.
             */
            it(
                "test_update_connection_password_with_acl_user",
                async () => {
                    await runTest(
                        async (client: BaseClient) => {
                            if (client instanceof GlideClient) {
                                return;
                            }

                            // Update password without re-authentication
                            const result =
                                await client.updateConnectionPassword(
                                    NEW_PASSWORD,
                                    false,
                                );
                            expect(result).toEqual("OK");

                            // Verify client still works with old auth
                            await client.set("test_key", "test_value");
                            const value = await client.get("test_key");
                            expect(value).toEqual("test_value");

                            // Update server password - this also kills the connection
                            await deleteAclUsernameAndPassword(
                                managementClient,
                                USERNAME,
                            );
                            await setNewAclUsernameWithPassword(
                                managementClient,
                                USERNAME,
                                NEW_PASSWORD,
                            );

                            // Sleep to ensure disconnection
                            await new Promise((resolve) =>
                                setTimeout(resolve, 1000),
                            );

                            // Verify client auto-reconnects with new password
                            await client.set("test_key2", "test_value2");
                            const value2 = await client.get("test_key2");
                            expect(value2).toEqual("test_value2");
                        },
                        protocol,
                        clusterMode,
                        {
                            credentials: {
                                username: USERNAME,
                                password: INITIAL_PASSWORD,
                            },
                        },
                    );
                },
                TIMEOUT,
            );

            /**
             * Test replacing connection password with immediate re-authentication using a non-valid password, with an acl user.
             * Verifies that immediate re-authentication fails when the password is not valid.
             */
            it("test_update_connection_password_auth_non_valid_pass_acl_user", async () => {
                await runTest(
                    async (client: BaseClient) => {
                        await expect(
                            client.updateConnectionPassword(null, true),
                        ).rejects.toThrow(RequestError);
                        await expect(
                            client.updateConnectionPassword("", true),
                        ).rejects.toThrow(RequestError);
                    },
                    protocol,
                    clusterMode,
                    {
                        credentials: {
                            username: USERNAME,
                            password: INITIAL_PASSWORD,
                        },
                    },
                );
            });

            /**
             * Test that re-authentication with a new password succeeds.
             */
            it("test_replace_password_immediateAuth_acl_user", async () => {
                await runTest(
                    async (client: BaseClient) => {
                        await setNewAclUsernameWithPassword(
                            managementClient,
                            USERNAME,
                            NEW_PASSWORD,
                        );

                        await expect(
                            client.updateConnectionPassword(NEW_PASSWORD, true),
                        ).resolves.toBe("OK");
                    },
                    protocol,
                    clusterMode,
                    {
                        credentials: {
                            username: USERNAME,
                            password: INITIAL_PASSWORD,
                        },
                    },
                );
            });
            /**
             * Test changing server password when connection is lost before password update.
             * Verifies that the client will not be able to reach the connection under the abstraction and return an error.
             *
             * **Note: This test is only supported for standalone mode, see explanation at the parallel test above*
             */

            it("test_update_connection_password_connection_lost_before_password_update_acl_user", async () => {
                await runTest(
                    async (client: BaseClient) => {
                        if (client instanceof GlideClusterClient) {
                            return;
                        }

                        // Set a key to ensure connection is established
                        await client.set("test_key", "test_value");

                        // Delete user and reset it with new password. This also kills the server conneciton.
                        await deleteAclUsernameAndPassword(
                            managementClient,
                            USERNAME,
                        );
                        await setNewAclUsernameWithPassword(
                            managementClient,
                            USERNAME,
                            NEW_PASSWORD,
                        );

                        // Sleep to ensure disconnection
                        await new Promise((resolve) =>
                            setTimeout(resolve, 1000),
                        );

                        // Try updating client password without immediate re-auth and with - non immediate should succeed,
                        // immediate auth should fail (failing to reconnect)
                        expect(
                            await client.updateConnectionPassword(
                                NEW_PASSWORD,
                                false,
                            ),
                        ).toEqual("OK");
                        await expect(
                            client.updateConnectionPassword(NEW_PASSWORD, true),
                        ).rejects.toThrow(RequestError);
                    },
                    protocol,
                    clusterMode,
                    {
                        credentials: {
                            username: USERNAME,
                            password: INITIAL_PASSWORD,
                        },
                    },
                );
            });
        },
    );
});

// Skip IAM Auth tests in CI/CD environments
const describeIamTests =
    process.env.CI || process.env.GITHUB_ACTIONS || process.env.JENKINS_URL
        ? describe.skip
        : describe;

describeIamTests("IAM Auth: Elasticache Cluster", () => {
    it("test_iam_authentication_elasticache_cluster", async () => {
        // Use debug level to see detailed logs about the iam auth process
        Logger.setLoggerConfig("debug");

        // Replace these values with your actual cluster info and region
        const clusterName = "iam-auth-cluster";
        const username = "iam-auth";
        const region = "us-east-1";
        const endpoint =
            "clustercfg.iam-auth-cluster.nra7gl.use1.cache.amazonaws.com";
        const iamConfig: IamAuthConfig = {
            clusterName: clusterName,
            service: ServiceType.Elasticache,
            region: region,
            // refreshIntervalSeconds: 10, // optional
        };

        const client = await GlideClusterClient.createClient({
            addresses: [{ host: endpoint, port: 6379 }],
            credentials: { username: username, iamConfig: iamConfig },
            useTLS: true,
        });

        // Basic ping test to verify connection
        const result = await client.ping();
        expect(result).toBe("PONG");

        Logger.log("info", "IAM test", "Refreshing the token manually");

        // Should see in the logs ""send_immediate_auth - Using IAM token for authentication`
        await client.refreshIamToken();

        await client.close();
    });
});
