/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { afterAll, beforeAll, describe, expect, it } from "@jest/globals";
import { ValkeyCluster } from "../../utils/TestUtils.js";
import { GlideClient, GlideClusterClient, ProtocolVersion } from "../build-ts";
import {
    getClientConfigurationOption,
    getServerVersion,
    parseEndpoints,
} from "./TestUtilities";

const TIMEOUT = 50000;

describe("AddressResolver", () => {
    let standaloneCluster: ValkeyCluster;
    let clusterCluster: ValkeyCluster;

    beforeAll(async () => {
        const standaloneAddresses: string =
            global.STAND_ALONE_ENDPOINT as string;
        standaloneCluster = standaloneAddresses
            ? await ValkeyCluster.initFromExistingCluster(
                  false,
                  parseEndpoints(standaloneAddresses),
                  getServerVersion,
              )
            : await ValkeyCluster.createCluster(false, 1, 1, getServerVersion);

        const clusterAddresses: string = global.CLUSTER_ENDPOINTS as string;
        clusterCluster = clusterAddresses
            ? await ValkeyCluster.initFromExistingCluster(
                  true,
                  parseEndpoints(clusterAddresses),
                  getServerVersion,
              )
            : await ValkeyCluster.createCluster(true, 3, 1, getServerVersion);
    }, 120000);

    afterAll(async () => {
        if (standaloneCluster) {
            await standaloneCluster.close();
        }

        if (clusterCluster) {
            await clusterCluster.close();
        }
    });

    it(
        "address resolver with fake address - standalone",
        async () => {
            const addresses = standaloneCluster.getAddresses();
            const [actualHost, actualPort] = addresses[0];

            const fakeHost = "fake-host-that-does-not-exist.invalid";
            const fakePort = 9999;

            // Resolver translates fake address to real server address
            const resolver = (host: string, port: number): [string, number] => {
                if (host === fakeHost && port === fakePort) {
                    return [actualHost, actualPort];
                }

                return [host, port];
            };

            const client = await GlideClient.createClient({
                ...getClientConfigurationOption(
                    [[fakeHost, fakePort]],
                    ProtocolVersion.RESP3,
                ),
                addressResolver: resolver,
            });

            try {
                const pingResult = await client.ping();
                expect(pingResult).toBe("PONG");

                const setResult = await client.set(
                    "resolver_test_key",
                    "resolver_test_value",
                );
                expect(setResult).toBe("OK");

                const getResult = await client.get("resolver_test_key");
                expect(getResult).toBe("resolver_test_value");
            } finally {
                client.close();
            }
        },
        TIMEOUT,
    );

    it(
        "address resolver with fake address - cluster",
        async () => {
            const addresses = clusterCluster.getAddresses();
            const [actualHost, actualPort] = addresses[0];

            const fakeHost = "fake-cluster-host.invalid";
            const fakePort = 9999;

            // Resolver translates fake address to real server address
            const resolver = (host: string, port: number): [string, number] => {
                if (host === fakeHost && port === fakePort) {
                    return [actualHost, actualPort];
                }

                return [host, port];
            };

            const client = await GlideClusterClient.createClient({
                ...getClientConfigurationOption(
                    [[fakeHost, fakePort]],
                    ProtocolVersion.RESP3,
                ),
                addressResolver: resolver,
            });

            try {
                const pingResult = await client.ping();
                expect(pingResult).toBe("PONG");

                const setResult = await client.set(
                    "cluster_resolver_test_key",
                    "cluster_resolver_test_value",
                );
                expect(setResult).toBe("OK");

                const getResult = await client.get("cluster_resolver_test_key");
                expect(getResult).toBe("cluster_resolver_test_value");
            } finally {
                client.close();
            }
        },
        TIMEOUT,
    );

    it(
        "address resolver exception falls back to original - standalone",
        async () => {
            const addresses = standaloneCluster.getAddresses();
            const [actualHost, actualPort] = addresses[0];

            // Resolver that always throws - params are consumed to satisfy lint
            const resolver = (host: string, port: number): [string, number] => {
                void host;
                void port;
                throw new Error("test-exception");
            };

            // Client should still connect using the original (valid) address as fallback
            const client = await GlideClient.createClient({
                ...getClientConfigurationOption(
                    [[actualHost, actualPort]],
                    ProtocolVersion.RESP3,
                ),
                addressResolver: resolver,
            });

            try {
                const pingResult = await client.ping();
                expect(pingResult).toBe("PONG");
            } finally {
                client.close();
            }
        },
        TIMEOUT,
    );

    it(
        "address resolver exception falls back to original - cluster",
        async () => {
            const addresses = clusterCluster.getAddresses();
            const [actualHost, actualPort] = addresses[0];

            // Resolver that always throws - params are consumed to satisfy lint
            const clusterResolver = (
                host: string,
                port: number,
            ): [string, number] => {
                void host;
                void port;
                throw new Error("test-exception");
            };

            // Client should still connect using the original (valid) address as fallback
            const client = await GlideClusterClient.createClient({
                ...getClientConfigurationOption(
                    [[actualHost, actualPort]],
                    ProtocolVersion.RESP3,
                ),
                addressResolver: clusterResolver,
            });

            try {
                const pingResult = await client.ping();
                expect(pingResult).toBe("PONG");
            } finally {
                client.close();
            }
        },
        TIMEOUT,
    );
});
