/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import {
    afterAll,
    afterEach,
    beforeAll,
    beforeEach,
    describe,
    expect,
    it,
} from "@jest/globals";
import { ValkeyCluster } from "../../utils/TestUtils";
import {
    BaseClient as BaseClientClass,
    BaseClientConfiguration,
    AwsCredentials,
    ConfigurationError,
    GlideClient,
    GlideClusterClient,
    GlideCredentialProvider,
    ProtocolVersion,
    RequestError,
    ServiceType,
    IamAuthConfig,
} from "../build-ts";
import {
    assertConnected,
    flushAndCloseClient,
    getServerVersion,
    parseEndpoints,
} from "./TestUtilities";
import {
    CreateDirectClient,
    registerCredentialProvider,
    removeCredentialProvider,
} from "../build-ts/native";
import { connection_request } from "../build-ts/ProtobufMessage";
import {
    IAM_TEST_CLUSTER_NAME,
    IAM_TEST_REGION_US_EAST_1,
    IAM_USERNAME,
} from "./Constants";

type BaseClient = GlideClient | GlideClusterClient;

const USERNAME = "username";
const INITIAL_PASSWORD = "initial_password";
const NEW_PASSWORD = "new_password";
const IAM_TESTS_ENABLED = Boolean(
    process.env.AWS_ACCESS_KEY_ID && process.env.AWS_SECRET_ACCESS_KEY,
);
const iamIt = IAM_TESTS_ENABLED ? it : it.skip;
const WRONG_PASSWORD = "wrong_password";
const TIMEOUT = 50000;

type AddressEntry = [string, number];

/**
 * Creates a test IAM authentication configuration.
 * @param refreshIntervalSeconds - Token refresh interval in seconds
 * @returns IamAuthConfig for testing
 */
function createTestIamConfig(refreshIntervalSeconds: number): IamAuthConfig {
    return {
        clusterName: IAM_TEST_CLUSTER_NAME,
        service: ServiceType.Elasticache,
        region: IAM_TEST_REGION_US_EAST_1,
        refreshIntervalSeconds: refreshIntervalSeconds,
    };
}

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

        if (managementClient) {
            await deleteAclUsernameAndPassword(managementClient, USERNAME);
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
                        await client.configSet({
                            requirepass: NEW_PASSWORD,
                        });
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

// IAM Auth tests with mock credentials
describe("IAM Auth: Mock Credentials", () => {
    iamIt(
        "test_iam_authentication_with_mock_credentials",
        async () => {
            // See DEVELOPER.md for instructions on running IAM authentication tests

            // Skip test if AWS credentials are not set in OS environment
            if (!process.env.AWS_ACCESS_KEY_ID) {
                return; // IAM tests require AWS credentials — see DEVELOPER.md
            }

            const username = IAM_USERNAME; // Use default user
            const iamConfig = createTestIamConfig(5);

            // Use existing cluster from global setup
            const clusterAddresses = global.CLUSTER_ENDPOINTS;
            const cluster = clusterAddresses
                ? await ValkeyCluster.initFromExistingCluster(
                      true,
                      parseEndpoints(clusterAddresses),
                      getServerVersion,
                  )
                : await ValkeyCluster.createCluster(
                      true,
                      3,
                      1,
                      getServerVersion,
                  );

            const addresses = cluster
                .getAddresses()
                .map(([host, port]) => ({ host, port }));

            try {
                const client = await GlideClusterClient.createClient({
                    addresses: addresses,
                    credentials: {
                        username: username,
                        iamConfig: iamConfig,
                    },
                    useTLS: global.TLS,
                });

                // Basic ping test to verify connection
                await assertConnected(client);

                // Test basic operations
                await client.set("iam_test_key", "iam_test_value");
                const value = await client.get("iam_test_key");
                expect(value).toBe("iam_test_value");

                // Test manual token refresh
                await client.refreshIamToken();

                // Verify client still works after token refresh
                await client.set("iam_test_key2", "iam_test_value2");
                const value2 = await client.get("iam_test_key2");
                expect(value2).toBe("iam_test_value2");

                client.close();
            } finally {
                await cluster.close();
            }
        },
        TIMEOUT,
    );

    iamIt(
        "test_iam_authentication_automatic_token_refresh",
        async () => {
            // See DEVELOPER.md for instructions on running IAM authentication tests

            // Skip test if AWS credentials are not set in OS environment
            if (!process.env.AWS_ACCESS_KEY_ID) {
                return; // IAM tests require AWS credentials — see DEVELOPER.md
            }

            const username = IAM_USERNAME;
            const iamConfig = createTestIamConfig(2); // Short interval for automatic refresh

            // Use existing cluster from global setup
            const clusterAddresses = global.CLUSTER_ENDPOINTS;
            const cluster = clusterAddresses
                ? await ValkeyCluster.initFromExistingCluster(
                      true,
                      parseEndpoints(clusterAddresses),
                      getServerVersion,
                  )
                : await ValkeyCluster.createCluster(
                      true,
                      3,
                      1,
                      getServerVersion,
                  );

            const addresses = cluster
                .getAddresses()
                .map(([host, port]) => ({ host, port }));

            try {
                const client = await GlideClusterClient.createClient({
                    addresses: addresses,
                    credentials: {
                        username: username,
                        iamConfig: iamConfig,
                    },
                    useTLS: global.TLS,
                });

                // Verify initial connection
                await assertConnected(client);

                // Wait for automatic token refresh to occur
                await new Promise((resolve) => setTimeout(resolve, 3000));

                // Verify client still works after automatic refresh
                await client.set(
                    "iam_auto_refresh_key",
                    "iam_auto_refresh_value",
                );
                const value = await client.get("iam_auto_refresh_key");
                expect(value).toBe("iam_auto_refresh_value");

                client.close();
            } finally {
                await cluster.close();
            }
        },
        TIMEOUT,
    );

    iamIt(
        "test_iam_authentication_with_mock_credentials_standalone",
        async () => {
            // See DEVELOPER.md for instructions on running IAM authentication tests

            // Skip test if AWS credentials are not set in OS environment
            if (!process.env.AWS_ACCESS_KEY_ID) {
                return; // IAM tests require AWS credentials — see DEVELOPER.md
            }

            const username = IAM_USERNAME;
            const iamConfig = createTestIamConfig(5);

            // Use existing standalone server from global setup
            const standaloneAddresses = global.STAND_ALONE_ENDPOINT;
            const server = standaloneAddresses
                ? await ValkeyCluster.initFromExistingCluster(
                      false,
                      parseEndpoints(standaloneAddresses),
                      getServerVersion,
                  )
                : await ValkeyCluster.createCluster(
                      false,
                      1,
                      0,
                      getServerVersion,
                  );

            const addresses = server
                .getAddresses()
                .map(([host, port]) => ({ host, port }));

            try {
                const client = await GlideClient.createClient({
                    addresses: addresses,
                    credentials: {
                        username: username,
                        iamConfig: iamConfig,
                    },
                    useTLS: global.TLS,
                });

                // Basic ping test to verify connection
                await assertConnected(client);

                // Test basic operations
                await client.set("iam_test_key", "iam_test_value");
                const value = await client.get("iam_test_key");
                expect(value).toBe("iam_test_value");

                // Test manual token refresh
                await client.refreshIamToken();

                // Verify client still works after token refresh
                await client.set("iam_test_key2", "iam_test_value2");
                const value2 = await client.get("iam_test_key2");
                expect(value2).toBe("iam_test_value2");

                client.close();
            } finally {
                await server.close();
            }
        },
        TIMEOUT,
    );

    iamIt(
        "test_iam_authentication_automatic_token_refresh_standalone",
        async () => {
            // See DEVELOPER.md for instructions on running IAM authentication tests

            // Skip test if AWS credentials are not set in OS environment
            if (!process.env.AWS_ACCESS_KEY_ID) {
                return; // IAM tests require AWS credentials — see DEVELOPER.md
            }

            const username = IAM_USERNAME;
            const iamConfig = createTestIamConfig(2);

            // Use existing standalone server from global setup
            const standaloneAddresses = global.STAND_ALONE_ENDPOINT;
            const server = standaloneAddresses
                ? await ValkeyCluster.initFromExistingCluster(
                      false,
                      parseEndpoints(standaloneAddresses),
                      getServerVersion,
                  )
                : await ValkeyCluster.createCluster(
                      false,
                      1,
                      0,
                      getServerVersion,
                  );

            const addresses = server
                .getAddresses()
                .map(([host, port]) => ({ host, port }));

            try {
                const client = await GlideClient.createClient({
                    addresses: addresses,
                    credentials: {
                        username: username,
                        iamConfig: iamConfig,
                    },
                    useTLS: global.TLS,
                });

                // Verify initial connection
                await assertConnected(client);

                // Wait for automatic token refresh to occur
                await new Promise((resolve) => setTimeout(resolve, 3000));

                // Verify client still works after automatic refresh
                await client.set(
                    "iam_auto_refresh_key",
                    "iam_auto_refresh_value",
                );
                const value = await client.get("iam_auto_refresh_key");
                expect(value).toBe("iam_auto_refresh_value");

                client.close();
            } finally {
                await server.close();
            }
        },
        TIMEOUT,
    );
});

describe("Direct client credential registration lifecycle", () => {
    const nativeSeams = BaseClientClass as unknown as {
        nativeCreateDirectClient: typeof CreateDirectClient;
        nativeRegisterAddressResolver: (
            resolver: (host: string, port: number) => [string, number],
        ) => string;
        nativeRemoveAddressResolver: (key: string) => void;
        nativeRegisterCredentialProvider: typeof registerCredentialProvider;
        nativeRemoveCredentialProvider: typeof removeCredentialProvider;
    };
    const originals = {
        createDirectClient: nativeSeams.nativeCreateDirectClient,
        registerAddressResolver: nativeSeams.nativeRegisterAddressResolver,
        removeAddressResolver: nativeSeams.nativeRemoveAddressResolver,
        registerCredentialProvider:
            nativeSeams.nativeRegisterCredentialProvider,
        removeCredentialProvider: nativeSeams.nativeRemoveCredentialProvider,
    };

    const clientConfig = (credentialProvider: unknown) =>
        ({
            addresses: [{ host: "unused.example", port: 1 }],
            addressResolver: (host: string, port: number) => [host, port],
            credentials: {
                username: IAM_USERNAME,
                iamConfig: {
                    clusterName: IAM_TEST_CLUSTER_NAME,
                    service: ServiceType.Elasticache,
                    region: IAM_TEST_REGION_US_EAST_1,
                    credentialProvider,
                },
            },
        }) as Parameters<typeof GlideClient.createClient>[0];

    const installNativeMocks = () => {
        const createDirectClient = jest.fn<
            ReturnType<typeof nativeSeams.nativeCreateDirectClient>,
            Parameters<typeof nativeSeams.nativeCreateDirectClient>
        >();
        const registerAddressResolver = jest.fn<
            ReturnType<typeof nativeSeams.nativeRegisterAddressResolver>,
            Parameters<typeof nativeSeams.nativeRegisterAddressResolver>
        >(() => "resolver-key");
        const removeAddressResolver = jest.fn<
            ReturnType<typeof nativeSeams.nativeRemoveAddressResolver>,
            Parameters<typeof nativeSeams.nativeRemoveAddressResolver>
        >();
        const registerCredentialProvider = jest.fn<
            ReturnType<typeof nativeSeams.nativeRegisterCredentialProvider>,
            Parameters<typeof nativeSeams.nativeRegisterCredentialProvider>
        >(() => "provider-key");
        const removeCredentialProvider = jest.fn<
            ReturnType<typeof nativeSeams.nativeRemoveCredentialProvider>,
            Parameters<typeof nativeSeams.nativeRemoveCredentialProvider>
        >();

        nativeSeams.nativeCreateDirectClient = createDirectClient;
        nativeSeams.nativeRegisterAddressResolver = registerAddressResolver;
        nativeSeams.nativeRemoveAddressResolver = removeAddressResolver;
        nativeSeams.nativeRegisterCredentialProvider =
            registerCredentialProvider;
        nativeSeams.nativeRemoveCredentialProvider = removeCredentialProvider;

        return {
            createDirectClient,
            registerAddressResolver,
            removeAddressResolver,
            registerCredentialProvider,
            removeCredentialProvider,
        };
    };

    beforeEach(() => {
        nativeSeams.nativeCreateDirectClient = originals.createDirectClient;
        nativeSeams.nativeRegisterAddressResolver =
            originals.registerAddressResolver;
        nativeSeams.nativeRemoveAddressResolver =
            originals.removeAddressResolver;
        nativeSeams.nativeRegisterCredentialProvider =
            originals.registerCredentialProvider;
        nativeSeams.nativeRemoveCredentialProvider =
            originals.removeCredentialProvider;
    });

    afterEach(() => {
        nativeSeams.nativeCreateDirectClient = originals.createDirectClient;
        nativeSeams.nativeRegisterAddressResolver =
            originals.registerAddressResolver;
        nativeSeams.nativeRemoveAddressResolver =
            originals.removeAddressResolver;
        nativeSeams.nativeRegisterCredentialProvider =
            originals.registerCredentialProvider;
        nativeSeams.nativeRemoveCredentialProvider =
            originals.removeCredentialProvider;
    });

    it("rejects a non-function provider before registering the resolver", async () => {
        const mocks = installNativeMocks();

        await expect(
            GlideClient.createClient(clientConfig("not-a-function")),
        ).rejects.toThrow(
            new ConfigurationError(
                "credentialProvider must be a function or undefined.",
            ),
        );

        expect(mocks.registerAddressResolver).not.toHaveBeenCalled();
        expect(mocks.registerCredentialProvider).not.toHaveBeenCalled();
        expect(mocks.removeAddressResolver).not.toHaveBeenCalled();
        expect(mocks.removeCredentialProvider).not.toHaveBeenCalled();
        expect(mocks.createDirectClient).not.toHaveBeenCalled();
    });

    it("evaluates a throwing provider getter before registering the resolver", async () => {
        const mocks = installNativeMocks();
        const config = clientConfig(undefined);
        const iamConfig = (config.credentials as { iamConfig: IamAuthConfig })
            .iamConfig;
        Object.defineProperty(iamConfig, "credentialProvider", {
            get: () => {
                throw new Error("provider getter failure");
            },
        });

        await expect(GlideClient.createClient(config)).rejects.toThrow(
            "provider getter failure",
        );

        expect(mocks.registerAddressResolver).not.toHaveBeenCalled();
        expect(mocks.registerCredentialProvider).not.toHaveBeenCalled();
        expect(mocks.removeAddressResolver).not.toHaveBeenCalled();
        expect(mocks.removeCredentialProvider).not.toHaveBeenCalled();
        expect(mocks.createDirectClient).not.toHaveBeenCalled();
    });

    it("cleans up the resolver when provider registration throws", async () => {
        const mocks = installNativeMocks();
        mocks.registerCredentialProvider.mockImplementation(() => {
            throw new Error("provider registration failure");
        });

        await expect(
            GlideClient.createClient(
                clientConfig(() => ({
                    accessKeyId: "access",
                    secretAccessKey: "secret",
                })),
            ),
        ).rejects.toThrow("provider registration failure");

        expect(mocks.registerAddressResolver).toHaveBeenCalledTimes(1);
        expect(mocks.registerCredentialProvider).toHaveBeenCalledTimes(1);
        expect(mocks.removeAddressResolver).toHaveBeenCalledTimes(1);
        expect(mocks.removeAddressResolver).toHaveBeenCalledWith(
            "resolver-key",
        );
        expect(mocks.removeCredentialProvider).not.toHaveBeenCalled();
        expect(mocks.createDirectClient).not.toHaveBeenCalled();
    });

    it.each(["synchronous throw", "async rejection"])(
        "cleans up both registrations exactly once after native %s",
        async (failureMode) => {
            const mocks = installNativeMocks();

            if (failureMode === "synchronous throw") {
                mocks.createDirectClient.mockImplementation(() => {
                    throw new Error("native setup failure");
                });
            } else {
                mocks.createDirectClient.mockRejectedValue(
                    new Error("native setup failure"),
                );
            }

            await expect(
                GlideClient.createClient(
                    clientConfig(() => ({
                        accessKeyId: "access",
                        secretAccessKey: "secret",
                    })),
                ),
            ).rejects.toThrow("native setup failure");

            expect(mocks.createDirectClient).toHaveBeenCalledTimes(1);
            expect(mocks.removeAddressResolver).toHaveBeenCalledTimes(1);
            expect(mocks.removeAddressResolver).toHaveBeenCalledWith(
                "resolver-key",
            );
            expect(mocks.removeCredentialProvider).toHaveBeenCalledTimes(1);
            expect(mocks.removeCredentialProvider).toHaveBeenCalledWith(
                "provider-key",
            );
        },
    );

    it("preserves successful handoff ownership and cleans the resolver on close", async () => {
        const mocks = installNativeMocks();
        const close = jest.fn();
        mocks.createDirectClient.mockResolvedValue({
            close,
        } as unknown as Awaited<ReturnType<typeof CreateDirectClient>>);

        const client = await GlideClient.createClient(
            clientConfig(() => ({
                accessKeyId: "access",
                secretAccessKey: "secret",
            })),
        );

        const request = connection_request.ConnectionRequest.decode(
            mocks.createDirectClient.mock.calls[0][0],
        );
        expect(request.addressResolverKey).toBe("resolver-key");
        expect(request.credentialProviderKey).toBe("provider-key");
        expect(mocks.removeAddressResolver).not.toHaveBeenCalled();
        expect(mocks.removeCredentialProvider).not.toHaveBeenCalled();

        client.close();

        expect(mocks.removeAddressResolver).toHaveBeenCalledTimes(1);
        expect(mocks.removeAddressResolver).toHaveBeenCalledWith(
            "resolver-key",
        );
        expect(mocks.removeCredentialProvider).not.toHaveBeenCalled();
        expect(close).toHaveBeenCalledTimes(1);
    });
});

describe("IAM Auth: Direct Custom Credential Providers", () => {
    const iamEnabled = Boolean(
        process.env.AWS_ACCESS_KEY_ID && process.env.AWS_SECRET_ACCESS_KEY,
    );
    let standaloneServer: ValkeyCluster | undefined;
    let clusterServer: ValkeyCluster | undefined;

    const environmentCredentials = (
        overrides: Partial<AwsCredentials> = {},
    ): AwsCredentials => ({
        accessKeyId: process.env.AWS_ACCESS_KEY_ID!,
        secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY!,
        sessionToken: process.env.AWS_SESSION_TOKEN,
        ...overrides,
    });

    const providerFor = (
        credentials: AwsCredentials,
        promiseProvider: boolean,
    ): GlideCredentialProvider =>
        promiseProvider
            ? async () => Promise.resolve(credentials)
            : () => credentials;

    const addressesFor = (clusterMode: boolean) =>
        (clusterMode ? clusterServer : standaloneServer)!
            .getAddresses()
            .map(([host, port]) => ({ host, port }));

    const createDirectClient = (
        clusterMode: boolean,
        credentialProvider: GlideCredentialProvider,
        refreshIntervalSeconds = 300,
        connectionTimeout?: number,
    ): Promise<BaseClient> => {
        const options: BaseClientConfiguration = {
            addresses: addressesFor(clusterMode),
            credentials: {
                username: IAM_USERNAME,
                iamConfig: {
                    clusterName: IAM_TEST_CLUSTER_NAME,
                    service: ServiceType.Elasticache,
                    region: IAM_TEST_REGION_US_EAST_1,
                    refreshIntervalSeconds,
                    credentialProvider,
                },
            },
            useTLS: global.TLS,
        };

        const advancedConfiguration =
            connectionTimeout === undefined ? undefined : { connectionTimeout };

        return clusterMode
            ? GlideClusterClient.createClient({
                  ...options,
                  advancedConfiguration,
              })
            : GlideClient.createClient({ ...options, advancedConfiguration });
    };

    beforeAll(async () => {
        if (!iamEnabled) return;

        standaloneServer = global.STAND_ALONE_ENDPOINT
            ? await ValkeyCluster.initFromExistingCluster(
                  false,
                  parseEndpoints(global.STAND_ALONE_ENDPOINT),
                  getServerVersion,
              )
            : await ValkeyCluster.createCluster(false, 1, 0, getServerVersion);
        clusterServer = global.CLUSTER_ENDPOINTS
            ? await ValkeyCluster.initFromExistingCluster(
                  true,
                  parseEndpoints(global.CLUSTER_ENDPOINTS),
                  getServerVersion,
              )
            : await ValkeyCluster.createCluster(true, 3, 1, getServerVersion);
    }, TIMEOUT);

    afterAll(async () => {
        await standaloneServer?.close();
        await clusterServer?.close();
    }, TIMEOUT);

    iamIt.each([
        ["standalone", "sync", false, false],
        ["standalone", "Promise", false, true],
        ["cluster", "sync", true, false],
        ["cluster", "Promise", true, true],
    ])(
        "%s direct client authenticates and manually refreshes with a %s provider",
        async (_mode, _providerKind, clusterMode, promiseProvider) => {
            if (!iamEnabled) return;

            let invocations = 0;

            const credentialProvider: GlideCredentialProvider = () => {
                invocations++;
                const credentials = environmentCredentials({
                    expiresAtEpochMillis: Date.now() + 60_000,
                });
                return promiseProvider
                    ? Promise.resolve(credentials)
                    : credentials;
            };

            const directClient = await createDirectClient(
                clusterMode,
                credentialProvider,
            );

            try {
                await assertConnected(directClient);
                const afterInitialAuth = invocations;
                expect(afterInitialAuth).toBeGreaterThan(0);

                await directClient.refreshIamToken();
                expect(invocations).toBeGreaterThan(afterInitialAuth);
                await assertConnected(directClient);
            } finally {
                directClient.close();
            }
        },
        TIMEOUT,
    );

    iamIt.each([
        ["standalone", false, false],
        ["cluster", true, true],
    ])(
        "%s direct client automatically refreshes custom credentials",
        async (_mode, clusterMode, promiseProvider) => {
            if (!iamEnabled) return;

            let invocations = 0;

            const credentialProvider: GlideCredentialProvider = () => {
                invocations++;
                const credentials = environmentCredentials();
                return promiseProvider
                    ? Promise.resolve(credentials)
                    : credentials;
            };

            const directClient = await createDirectClient(
                clusterMode,
                credentialProvider,
                2,
            );

            try {
                await assertConnected(directClient);
                const afterInitialAuth = invocations;
                await new Promise((resolve) => setTimeout(resolve, 3000));
                expect(invocations).toBeGreaterThan(afterInitialAuth);
                await assertConnected(directClient);
            } finally {
                directClient.close();
            }
        },
        TIMEOUT,
    );

    iamIt.each([
        [
            "sync throw",
            () => {
                throw new Error("sync provider failure");
            },
        ],
        [
            "Promise rejection",
            () => Promise.reject(new Error("Promise provider failure")),
        ],
    ] as [string, GlideCredentialProvider][])(
        "surfaces %s during initial direct authentication",
        async (_caseName, credentialProvider) => {
            if (!iamEnabled) return;

            await expect(
                createDirectClient(false, credentialProvider),
            ).rejects.toThrow(/provider failure/u);
        },
        TIMEOUT,
    );

    iamIt.each([
        [
            "empty access key from sync provider",
            "",
            "secret",
            false,
            "accessKeyId",
        ],
        [
            "ASCII blank access key from Promise provider",
            " \t\r\n",
            "secret",
            true,
            "accessKeyId",
        ],
        [
            "Unicode blank access key from sync provider",
            "\u2003\u3000",
            "secret",
            false,
            "accessKeyId",
        ],
        [
            "empty secret key from Promise provider",
            "access",
            "",
            true,
            "secretAccessKey",
        ],
        [
            "ASCII blank secret key from sync provider",
            "access",
            " \t\r\n",
            false,
            "secretAccessKey",
        ],
        [
            "Unicode blank secret key from Promise provider",
            "access",
            "\u2003\u3000",
            true,
            "secretAccessKey",
        ],
    ])(
        "rejects %s",
        async (
            _caseName,
            accessKeyId,
            secretAccessKey,
            promiseProvider,
            expectedField,
        ) => {
            if (!iamEnabled) return;

            const provider = providerFor(
                environmentCredentials({ accessKeyId, secretAccessKey }),
                promiseProvider,
            );
            await expect(createDirectClient(false, provider)).rejects.toThrow(
                new RegExp(`blank ${expectedField}`, "u"),
            );
        },
        TIMEOUT,
    );

    iamIt.each([
        ["omitted", undefined, false],
        ["zero", 0, true],
        ["negative", -1, false],
        ["valid", Date.now() + 60_000, true],
        ["maximum safe integer", Number.MAX_SAFE_INTEGER, false],
    ])(
        "accepts %s expiresAtEpochMillis from a direct provider",
        async (_caseName, expiresAtEpochMillis, promiseProvider) => {
            if (!iamEnabled) return;

            const directClient = await createDirectClient(
                false,
                providerFor(
                    environmentCredentials({ expiresAtEpochMillis }),
                    promiseProvider,
                ),
            );

            try {
                await assertConnected(directClient);
            } finally {
                directClient.close();
            }
        },
        TIMEOUT,
    );

    iamIt.each([
        ["NaN", Number.NaN, false],
        ["positive infinity", Number.POSITIVE_INFINITY, true],
        ["negative infinity", Number.NEGATIVE_INFINITY, false],
        ["fractional", Date.now() + 0.5, true],
        ["above the safe-integer range", Number.MAX_SAFE_INTEGER + 1, false],
    ])(
        "rejects %s expiresAtEpochMillis from a direct provider",
        async (_caseName, expiresAtEpochMillis, promiseProvider) => {
            if (!iamEnabled) return;

            await expect(
                createDirectClient(
                    false,
                    providerFor(
                        environmentCredentials({ expiresAtEpochMillis }),
                        promiseProvider,
                    ),
                ),
            ).rejects.toThrow(/finite safe integer/u);
        },
        TIMEOUT,
    );

    iamIt(
        "surfaces a custom provider failure from manual refresh",
        async () => {
            let failRefresh = false;

            const credentialProvider: GlideCredentialProvider = () => {
                if (failRefresh) {
                    throw new Error("manual refresh provider failure");
                }

                return environmentCredentials();
            };

            const directClient = await createDirectClient(
                false,
                credentialProvider,
            );

            try {
                await assertConnected(directClient);
                failRefresh = true;
                await expect(directClient.refreshIamToken()).rejects.toThrow(
                    /manual refresh provider failure/u,
                );
            } finally {
                directClient.close();
            }
        },
    );

    iamIt(
        "restores a failed Rust claim before JS rejection cleanup",
        async () => {
            const providerKey = registerCredentialProvider(() =>
                Promise.reject(new Error("handoff provider failure")),
            );
            const request = connection_request.ConnectionRequest.create({
                addresses: addressesFor(false),
                tlsMode: global.TLS
                    ? connection_request.TlsMode.SecureTls
                    : connection_request.TlsMode.NoTls,
                authenticationInfo: {
                    username: IAM_USERNAME,
                    iamCredentials: {
                        clusterName: IAM_TEST_CLUSTER_NAME,
                        region: IAM_TEST_REGION_US_EAST_1,
                        serviceType: connection_request.ServiceType.ELASTICACHE,
                        refreshIntervalSeconds: 300,
                    },
                },
                credentialProviderKey: providerKey,
            });
            const requestBytes =
                connection_request.ConnectionRequest.encode(request).finish();

            let handoffError: unknown;

            try {
                await CreateDirectClient(requestBytes, () => undefined);
            } catch (error) {
                handoffError = error;
                // This is the same immediate cleanup performed by BaseClient's catch.
                removeCredentialProvider(providerKey);
            }

            expect(String(handoffError)).toMatch(/handoff provider failure/u);

            // The same key must remain absent: no Rust claim may restore it after the
            // JavaScript rejection handler has removed it.
            await expect(
                Promise.resolve().then(() =>
                    CreateDirectClient(requestBytes, () => undefined),
                ),
            ).rejects.toThrow(/was not found/u);
        },
    );

    iamIt(
        "times out a synchronous provider and safely drops its late return",
        async () => {
            const credentialProvider: GlideCredentialProvider = () => {
                Atomics.wait(
                    new Int32Array(
                        new SharedArrayBuffer(Int32Array.BYTES_PER_ELEMENT),
                    ),
                    0,
                    0,
                    9_250,
                );
                return environmentCredentials();
            };

            const startedAt = Date.now();

            await expect(
                createDirectClient(false, credentialProvider, 300, 15_000),
            ).rejects.toThrow(/did not return within 9s/u);
            const elapsed = Date.now() - startedAt;
            expect(elapsed).toBeGreaterThanOrEqual(9_000);
            expect(elapsed).toBeLessThan(10_000);

            const healthyClient = await createDirectClient(false, () =>
                environmentCredentials(),
            );

            try {
                await assertConnected(healthyClient);
            } finally {
                healthyClient.close();
            }
        },
        20_000,
    );

    iamIt(
        "times out an unresolved Promise provider and safely drops late completion",
        async () => {
            if (!iamEnabled) return;

            let resolveLate:
                ((credentials: AwsCredentials) => void) | undefined;
            const credentialProvider: GlideCredentialProvider = () =>
                new Promise((resolve) => {
                    resolveLate = resolve;
                });
            const startedAt = Date.now();

            await expect(
                createDirectClient(false, credentialProvider, 300, 15_000),
            ).rejects.toThrow(/did not return within 9s/u);
            const elapsed = Date.now() - startedAt;
            expect(elapsed).toBeGreaterThanOrEqual(8_500);
            expect(elapsed).toBeLessThan(10_000);

            resolveLate!(environmentCredentials());
            await new Promise((resolve) => setTimeout(resolve, 200));

            const healthyClient = await createDirectClient(false, () =>
                environmentCredentials(),
            );

            try {
                await assertConnected(healthyClient);
            } finally {
                healthyClient.close();
            }
        },
        20_000,
    );

    iamIt(
        "creates standalone and cluster custom-provider clients concurrently",
        async () => {
            if (!iamEnabled) return;

            const [standaloneClient, clusterClient] = await Promise.all([
                createDirectClient(false, () => environmentCredentials()),
                createDirectClient(true, async () => environmentCredentials()),
            ]);

            try {
                await Promise.all([
                    assertConnected(standaloneClient),
                    assertConnected(clusterClient),
                ]);
            } finally {
                standaloneClient.close();
                clusterClient.close();
            }
        },
    );
});
