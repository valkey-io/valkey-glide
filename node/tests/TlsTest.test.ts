/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { afterAll, afterEach, beforeAll, describe } from "@jest/globals";
import { ValkeyCluster } from "../../utils/TestUtils.js";
import { GlideClient, GlideClusterClient, ProtocolVersion } from "../build-ts";
import {
    flushAndCloseClient,
    getClientConfigurationOption,
    getServerVersion,
} from "./TestUtilities";
const TIMEOUT = 50000;
const TLS_OPTIONS = {
    advancedConfiguration: {
        tlsAdvancedConfiguration: { insecure: true },
    },
    useTLS: true,
};

// tls cluster tests
describe("tls GlideClusterClient", () => {
    let cluster: ValkeyCluster;
    let client: GlideClusterClient;

    beforeAll(async () => {
        try {
            cluster = await ValkeyCluster.createCluster(
                true,
                3,
                2,
                getServerVersion,
                true,
                TLS_OPTIONS,
            );
        } catch (error) {
            console.error("Failed to create cluster:", error);
            throw new Error("Unable to create cluster for TLS tests");
        }
    }, 40000);

    afterEach(async () => {
        if (cluster) {
            await flushAndCloseClient(
                true,
                cluster.getAddresses(),
                client,
                TLS_OPTIONS,
            );
        }
    });

    afterAll(async () => {
        if (cluster) {
            await cluster.close();
        }
    });

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "clusterClient connect with insecure TLS (protocol: %p)",
        async (protocol) => {
            if (!cluster) {
                throw new Error("Cluster not available for testing");
            }

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
    let client: GlideClient;

    beforeAll(async () => {
        try {
            cluster = await ValkeyCluster.createCluster(
                false,
                1,
                1,
                getServerVersion,
                true,
                TLS_OPTIONS,
            );
        } catch (error) {
            console.error("Failed to create cluster:", error);
            throw new Error("Unable to create cluster for TLS tests");
        }
    }, 40000);

    afterEach(async () => {
        if (cluster) {
            await flushAndCloseClient(
                false,
                cluster.getAddresses(),
                client,
                TLS_OPTIONS,
            );
        }
    });

    afterAll(async () => {
        if (cluster) {
            await cluster.close();
        }
    });

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "Standalone client connect with insecure TLS (protocol: %p)",
        async (protocol) => {
            if (!cluster) {
                throw new Error("Cluster not available for testing");
            }

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
