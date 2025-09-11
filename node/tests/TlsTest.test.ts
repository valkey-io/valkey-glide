/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { afterAll, afterEach, beforeAll, describe } from "@jest/globals";
import { ValkeyCluster } from "../../utils/TestUtils.js";
import {
    GlideClient,
    GlideClusterClient,
    Logger,
    ProtocolVersion,
} from "../build-ts";
import {
    flushAndCloseClient,
    getClientConfigurationOption,
    getServerVersion,
} from "./TestUtilities";
const TIMEOUT = 50000;
const CLUSTER_CREATION_TIMEOUT = 120000; // Increased timeout for TLS cluster creation
const TLS_OPTIONS = {
    advancedConfiguration: {
        tlsAdvancedConfiguration: { insecure: true },
    },
    useTLS: true,
};

// tls cluster tests
describe("tls GlideClusterClient", () => {
    let cluster: ValkeyCluster;
    let client: GlideClusterClient | undefined;

    beforeAll(async () => {
        cluster = await ValkeyCluster.createCluster(
            true,
            3,
            2,
            getServerVersion,
            true,
            TLS_OPTIONS,
        );
        // Small delay to ensure cluster is fully ready after TLS setup
        await new Promise((resolve) => setTimeout(resolve, 1000));
    }, CLUSTER_CREATION_TIMEOUT);

    afterEach(async () => {
        await flushAndCloseClient(
            true,
            cluster?.getAddresses(),
            client,
            TLS_OPTIONS,
        );
        client = undefined;
    });

    afterAll(async () => {
        try {
            if (cluster) {
                await cluster.close();
            }
        } catch (error) {
            // Log the error but don't throw to avoid masking test results
            Logger.log(
                "warn",
                "TlsTest",
                "Error closing cluster",
                error as Error,
            );
        }

        // Additional delay to ensure proper TLS cleanup
        await new Promise((resolve) => setTimeout(resolve, 100));
    });

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "clusterClient connect with insecure TLS (protocol: %p)",
        async (protocol) => {
            const config = {
                ...getClientConfigurationOption(
                    cluster.getAddresses(),
                    protocol,
                ),
                ...TLS_OPTIONS,
            };

            client = await GlideClusterClient.createClient(config);

            const result = await client.ping();
            expect(result.toString()).toBe("PONG");
        },
        TIMEOUT,
    );
});

// tls cluster tests
describe("tls GlideClient", () => {
    let cluster: ValkeyCluster;
    let client: GlideClient | undefined;

    beforeAll(async () => {
        cluster = await ValkeyCluster.createCluster(
            false,
            1,
            1,
            getServerVersion,
            true,
            TLS_OPTIONS,
        );
        // Small delay to ensure cluster is fully ready after TLS setup
        await new Promise((resolve) => setTimeout(resolve, 1000));
    }, CLUSTER_CREATION_TIMEOUT);

    afterEach(async () => {
        await flushAndCloseClient(
            false,
            cluster?.getAddresses(),
            client,
            TLS_OPTIONS,
        );
        client = undefined;
    });

    afterAll(async () => {
        try {
            if (cluster) {
                await cluster.close();
            }
        } catch (error) {
            // Log the error but don't throw to avoid masking test results
            Logger.log(
                "warn",
                "TlsTest",
                "Error closing cluster",
                error as Error,
            );
        }

        // Additional delay to ensure proper TLS cleanup
        await new Promise((resolve) => setTimeout(resolve, 100));
    });

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "Standalone client connect with insecure TLS (protocol: %p)",
        async (protocol) => {
            const config = {
                ...getClientConfigurationOption(
                    cluster.getAddresses(),
                    protocol,
                ),
                ...TLS_OPTIONS,
            };

            client = await GlideClient.createClient(config);

            const result = await client.ping();
            expect(result.toString()).toBe("PONG");
        },
        TIMEOUT,
    );
});
