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
import * as fs from "fs";
import { TestTLSConfig, ValkeyCluster } from "../../utils/TestUtils.js";
import {
    GlideClient,
    GlideClusterClient,
    Logger,
    ProtocolVersion,
} from "../build-ts";
import {
    HOSTNAME_NO_TLS,
    HOSTNAME_TLS,
} from "./Constants";
import {
    getClientConfigurationOption,
    getServerVersion,
} from "./TestUtilities";

const TIMEOUT = 50000;
const CLUSTER_CREATION_TIMEOUT = 120000;
const HOSTNAME_INVALID = "nonexistent.invalid";

/*
 * Returns true if DNS tests are enabled.
 */
const isDnsTestsEnabled = () => process.env.VALKEY_GLIDE_DNS_TESTS_ENABLED ? true : false;

(isDnsTestsEnabled() ? describe : describe.skip)("DNS Tests - Non-TLS", () => {
    let standaloneCluster: ValkeyCluster;
    let clusterModeCluster: ValkeyCluster;
    let standaloneClient: GlideClient | undefined;
    let clusterClient: GlideClusterClient | undefined;

    beforeAll(async () => {
        standaloneCluster = await ValkeyCluster.createCluster(false, 1, 1, getServerVersion);
        clusterModeCluster = await ValkeyCluster.createCluster(true, 3, 2, getServerVersion);
    }, CLUSTER_CREATION_TIMEOUT);

    afterEach(async () => {
        if (standaloneClient) standaloneClient.close();
        if (clusterClient) clusterClient.close();

        standaloneClient = undefined;
        clusterClient = undefined;
    });

    it(
        "should connect with valid hostname - standalone",
        async () => {
            const addresses = standaloneCluster.getAddresses();
            const port = addresses[0][1];
            standaloneClient = await GlideClient.createClient({
                ...getClientConfigurationOption(
                    [[HOSTNAME_NO_TLS, port]],
                    ProtocolVersion.RESP3,
                ),
            });

            const result = await standaloneClient.ping();
            expect(result).toBe("PONG");
        },
        TIMEOUT,
    );

    it(
        "should fail with invalid hostname - standalone",
        async () => {
            const addresses = standaloneCluster.getAddresses();
            const port = addresses[0][1];
            await expect(
                GlideClient.createClient({
                    ...getClientConfigurationOption(
                        [[HOSTNAME_INVALID, port]],
                        ProtocolVersion.RESP3,
                    ),
                }),
            ).rejects.toThrow();
        },
        TIMEOUT,
    );

    it(
        "should connect with valid hostname - cluster",
        async () => {
            const addresses = clusterModeCluster.getAddresses();
            const port = addresses[0][1];
            clusterClient = await GlideClusterClient.createClient({
                ...getClientConfigurationOption(
                    [[HOSTNAME_NO_TLS, port]],
                    ProtocolVersion.RESP3,
                ),
            });

            const result = await clusterClient.ping();
            expect(result).toBe("PONG");
        },
        TIMEOUT,
    );

    it(
        "should fail with invalid hostname - cluster",
        async () => {
            const addresses = clusterModeCluster.getAddresses();
            const port = addresses[0][1];
            await expect(
                GlideClusterClient.createClient({
                    ...getClientConfigurationOption(
                        [[HOSTNAME_INVALID, port]],
                        ProtocolVersion.RESP3,
                    ),
                }),
            ).rejects.toThrow();
        },
        TIMEOUT,
    );
});

(isDnsTestsEnabled() ? describe : describe.skip)("DNS Tests - TLS", () => {
    let standaloneCluster: ValkeyCluster;
    let clusterModeCluster: ValkeyCluster;
    let standaloneClient: GlideClient | undefined;
    let clusterClient: GlideClusterClient | undefined;
    let caCertData: Buffer;

    beforeAll(async () => {
        const tlsConfig: TestTLSConfig = {
            useTLS: true,
            advancedConfiguration: {
                tlsAdvancedConfiguration: {
                    insecure: true,
                },
            },
        };

        standaloneCluster = await ValkeyCluster.createCluster(false,1,1,getServerVersion,true,tlsConfig);
        clusterModeCluster = await ValkeyCluster.createCluster(true, 3, 2, getServerVersion, true, tlsConfig);

        const glideHomeDir = process.env.GLIDE_HOME_DIR || process.cwd() + "/..";
        const caCertPath = `${glideHomeDir}/utils/tls_crts/ca.crt`;
        caCertData = fs.readFileSync(caCertPath);
    }, CLUSTER_CREATION_TIMEOUT);

    afterEach(async () => {
        if (standaloneClient) standaloneClient.close();
        if (clusterClient) clusterClient.close();

        standaloneClient = undefined;
        clusterClient = undefined;
    });

    it(
        "should connect with hostname in certificate SAN - standalone",
        async () => {
            const addresses = standaloneCluster.getAddresses();
            const port = addresses[0][1];
            standaloneClient = await GlideClient.createClient({
                ...getClientConfigurationOption(
                    [[HOSTNAME_TLS, port]],
                    ProtocolVersion.RESP3,
                ),
                useTLS: true,
                advancedConfiguration: {
                    tlsAdvancedConfiguration: {
                        rootCertificates: caCertData,
                    },
                },
            });

            const result = await standaloneClient.ping();
            expect(result).toBe("PONG");
        },
        TIMEOUT,
    );

    it(
        "should fail with hostname NOT in certificate SAN - standalone",
        async () => {
            const addresses = standaloneCluster.getAddresses();
            const port = addresses[0][1];
            await expect(
                GlideClient.createClient({
                    ...getClientConfigurationOption(
                        [[HOSTNAME_NO_TLS, port]],
                        ProtocolVersion.RESP3,
                    ),
                    useTLS: true,
                    advancedConfiguration: {
                        tlsAdvancedConfiguration: {
                            rootCertificates: caCertData,
                        },
                    },
                }),
            ).rejects.toThrow();
        },
        TIMEOUT,
    );

    it(
        "should connect with hostname in certificate SAN - cluster",
        async () => {
            const addresses = clusterModeCluster.getAddresses();
            const port = addresses[0][1];
            clusterClient = await GlideClusterClient.createClient({
                ...getClientConfigurationOption(
                    [[HOSTNAME_TLS, port]],
                    ProtocolVersion.RESP3,
                ),
                useTLS: true,
                advancedConfiguration: {
                    tlsAdvancedConfiguration: {
                        rootCertificates: caCertData,
                    },
                },
            });

            const result = await clusterClient.ping();
            expect(result).toBe("PONG");
        },
        TIMEOUT,
    );

    it(
        "should fail with hostname NOT in certificate SAN - cluster",
        async () => {
            const addresses = clusterModeCluster.getAddresses();
            await expect(
                GlideClusterClient.createClient({
                    ...getClientConfigurationOption(
                        [[HOSTNAME_NO_TLS, addresses[0][1]]],
                        ProtocolVersion.RESP3,
                    ),
                    useTLS: true,
                    advancedConfiguration: {
                        tlsAdvancedConfiguration: {
                            rootCertificates: caCertData,
                        },
                    },
                }),
            ).rejects.toThrow();
        },
        TIMEOUT,
    );
});
