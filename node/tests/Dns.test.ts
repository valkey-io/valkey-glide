/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { afterEach, beforeAll, describe, expect, it } from "@jest/globals";
import { TestTLSConfig, ValkeyCluster } from "../../utils/TestUtils.js";
import { GlideClient, GlideClusterClient, ProtocolVersion } from "../build-ts";
import { HOSTNAME_NO_TLS, HOSTNAME_TLS } from "./Constants";
import {
    getCaCertificateData,
    getClientConfigurationOption,
    getServerVersion,
} from "./TestUtilities";

const TIMEOUT = 50000;
const CLUSTER_CREATION_TIMEOUT = 120000;
const HOSTNAME_INVALID = "nonexistent.invalid";

/**
 * Returns true if DNS tests are enabled via the VALKEY_GLIDE_DNS_TESTS_ENABLED environment variable.
 */
const isDnsTestsEnabled = () =>
    process.env.VALKEY_GLIDE_DNS_TESTS_ENABLED ? true : false;

/**
 * Creates a standalone client connected to the given server with the specified hostname.
 *
 * @param server - The ValkeyCluster server instance
 * @param hostname - The hostname to connect to
 * @returns A connected GlideClient instance
 */
async function createClient(
    server: ValkeyCluster,
    hostname: string,
): Promise<GlideClient> {
    const port = server.ports()[0];
    const address = [hostname, port] as [string, number];
    const baseConfig = {
        ...getClientConfigurationOption([address], ProtocolVersion.RESP3),
    };

    if (server.isTls()) {
        return await GlideClient.createClient({
            ...baseConfig,
            useTLS: true,
            advancedConfiguration: {
                tlsAdvancedConfiguration: {
                    rootCertificates: getCaCertificateData(),
                },
            },
        });
    }

    return await GlideClient.createClient(baseConfig);
}

/**
 * Creates a cluster client connected to the given server with the specified hostname.
 *
 * @param server - The ValkeyCluster server instance
 * @param hostname - The hostname to connect to
 * @returns A connected GlideClusterClient instance
 */
async function createClusterClient(
    server: ValkeyCluster,
    hostname: string,
): Promise<GlideClusterClient> {
    const port = server.ports()[0];
    const address = [hostname, port] as [string, number];
    const baseConfig = {
        ...getClientConfigurationOption([address], ProtocolVersion.RESP3),
    };

    if (server.isTls()) {
        return await GlideClusterClient.createClient({
            ...baseConfig,
            useTLS: true,
            advancedConfiguration: {
                tlsAdvancedConfiguration: {
                    rootCertificates: getCaCertificateData(),
                },
            },
        });
    }

    return await GlideClusterClient.createClient(baseConfig);
}

(isDnsTestsEnabled() ? describe : describe.skip)("DNS Tests - Non-TLS", () => {
    let standaloneServer: ValkeyCluster;
    let clusterServer: ValkeyCluster;
    let standaloneClient: GlideClient | undefined;
    let clusterClient: GlideClusterClient | undefined;

    beforeAll(async () => {
        standaloneServer = await ValkeyCluster.createCluster(
            false,
            1,
            1,
            getServerVersion,
        );
        clusterServer = await ValkeyCluster.createCluster(
            true,
            3,
            2,
            getServerVersion,
        );
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
            standaloneClient = await createClient(
                standaloneServer,
                HOSTNAME_NO_TLS,
            );

            const result = await standaloneClient.ping();
            expect(result).toBe("PONG");
        },
        TIMEOUT,
    );

    it(
        "should fail with invalid hostname - standalone",
        async () => {
            await expect(
                createClient(standaloneServer, HOSTNAME_INVALID),
            ).rejects.toThrow();
        },
        TIMEOUT,
    );

    it(
        "should connect with valid hostname - cluster",
        async () => {
            clusterClient = await createClusterClient(
                clusterServer,
                HOSTNAME_NO_TLS,
            );

            const result = await clusterClient.ping();
            expect(result).toBe("PONG");
        },
        TIMEOUT,
    );

    it(
        "should fail with invalid hostname - cluster",
        async () => {
            await expect(
                createClusterClient(clusterServer, HOSTNAME_INVALID),
            ).rejects.toThrow();
        },
        TIMEOUT,
    );
});

(isDnsTestsEnabled() ? describe : describe.skip)("DNS Tests - TLS", () => {
    let standaloneServer: ValkeyCluster;
    let clusterServer: ValkeyCluster;
    let standaloneClient: GlideClient | undefined;
    let clusterClient: GlideClusterClient | undefined;

    beforeAll(async () => {
        const tlsConfig: TestTLSConfig = {
            useTLS: true,
            advancedConfiguration: {
                tlsAdvancedConfiguration: {
                    insecure: true,
                },
            },
        };

        standaloneServer = await ValkeyCluster.createCluster(
            false,
            1,
            1,
            getServerVersion,
            true,
            tlsConfig,
        );
        clusterServer = await ValkeyCluster.createCluster(
            true,
            3,
            2,
            getServerVersion,
            true,
            tlsConfig,
        );
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
            standaloneClient = await createClient(
                standaloneServer,
                HOSTNAME_TLS,
            );

            const result = await standaloneClient.ping();
            expect(result).toBe("PONG");
        },
        TIMEOUT,
    );

    it(
        "should fail with hostname NOT in certificate SAN - standalone",
        async () => {
            await expect(
                createClient(standaloneServer, HOSTNAME_NO_TLS),
            ).rejects.toThrow();
        },
        TIMEOUT,
    );

    it(
        "should connect with hostname in certificate SAN - cluster",
        async () => {
            clusterClient = await createClusterClient(
                clusterServer,
                HOSTNAME_TLS,
            );

            const result = await clusterClient.ping();
            expect(result).toBe("PONG");
        },
        TIMEOUT,
    );

    it(
        "should fail with hostname NOT in certificate SAN - cluster",
        async () => {
            await expect(
                createClusterClient(clusterServer, HOSTNAME_NO_TLS),
            ).rejects.toThrow();
        },
        TIMEOUT,
    );
});
