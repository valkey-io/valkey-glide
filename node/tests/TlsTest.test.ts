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

/**
 * Retry wrapper for cluster creation. TLS cluster startup can fail transiently
 * in CI due to port contention, resource pressure, or slow topology convergence.
 * Retries up to maxAttempts times before giving up.
 */
async function createClusterWithRetry(
    clusterMode: boolean,
    shardCount: number,
    replicaCount: number,
    maxAttempts: number = 3,
): Promise<ValkeyCluster> {
    let lastError: unknown;

    for (let attempt = 1; attempt <= maxAttempts; attempt++) {
        try {
            return await ValkeyCluster.createCluster(
                clusterMode,
                shardCount,
                replicaCount,
                getServerVersion,
                true,
                TLS_OPTIONS,
            );
        } catch (error) {
            lastError = error;
            Logger.log(
                "warn",
                "TlsTest",
                `Cluster creation attempt ${attempt}/${maxAttempts} failed: ${error}`,
            );

            if (attempt < maxAttempts) {
                // Wait briefly before retrying to allow ports to be released
                await new Promise((resolve) =>
                    setTimeout(resolve, 2000 * attempt),
                );
            }
        }
    }

    throw lastError;
}

// tls cluster tests
describe("tls GlideClusterClient", () => {
    let cluster: ValkeyCluster;
    let client: GlideClusterClient | undefined;

    beforeAll(async () => {
        // Use 3 shards with 0 replicas (3 nodes total) instead of 3 shards
        // with 2 replicas (9 nodes). This test only validates TLS connectivity
        // via ping, so minimal cluster size reduces startup time and resource
        // contention that cause transient CI failures.
        cluster = await createClusterWithRetry(true, 3, 0);
    }, CLUSTER_CREATION_TIMEOUT);

    afterEach(async () => {
        if (cluster) {
            await flushAndCloseClient(
                true,
                cluster.getAddresses(),
                client,
                TLS_OPTIONS,
            );
        }

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

// tls standalone tests
describe("tls GlideClient", () => {
    let cluster: ValkeyCluster;
    let client: GlideClient | undefined;

    beforeAll(async () => {
        cluster = await createClusterWithRetry(false, 1, 1);
    }, CLUSTER_CREATION_TIMEOUT);

    afterEach(async () => {
        if (cluster) {
            await flushAndCloseClient(
                false,
                cluster.getAddresses(),
                client,
                TLS_OPTIONS,
            );
        }

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
