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
import { GlideClient, ProtocolVersion, NodeDiscoveryMode } from "../build-ts";
import {
    getClientConfigurationOption,
    getServerVersion,
    parseEndpoints,
} from "./TestUtilities";

describe("NodeDiscoveryMode", () => {
    let cluster: ValkeyCluster;
    let client: GlideClient | undefined;

    beforeAll(async () => {
        const standaloneAddresses: string =
            global.STAND_ALONE_ENDPOINT as string;
        cluster = standaloneAddresses
            ? await ValkeyCluster.initFromExistingCluster(
                  false,
                  parseEndpoints(standaloneAddresses),
                  getServerVersion,
              )
            : await ValkeyCluster.createCluster(false, 1, 1, getServerVersion);
    }, 20000);

    afterEach(async () => {
        client?.close();
        client = undefined;
    });

    afterAll(async () => {
        await cluster.close();
    });

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "skip info replication connects and reads_%p",
        async (protocol) => {
            client = await GlideClient.createClient({
                ...getClientConfigurationOption(
                    cluster.getAddresses(),
                    protocol,
                ),
                nodeDiscoveryMode: NodeDiscoveryMode.Static,
            });

            const result = await client.get("nonexistent");
            expect(result).toBeNull();
        },
        10000,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "skip info replication allows writes_%p",
        async (protocol) => {
            // Use only the primary address (first address) to avoid connecting
            // to a replica with NodeDiscoveryMode.Static, which would cause
            // ReadOnly errors on write operations.
            const primaryAddress = [cluster.getAddresses()[0]];
            client = await GlideClient.createClient({
                ...getClientConfigurationOption(primaryAddress, protocol),
                nodeDiscoveryMode: NodeDiscoveryMode.Static,
            });

            const key = `skip_write_${Date.now()}`;
            await client.set(key, "value");
            const result = await client.get(key);
            expect(result).toBe("value");
            await client.del([key]);
        },
        10000,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "read only rejects discover replicas_%p",
        async (protocol) => {
            await expect(
                GlideClient.createClient({
                    ...getClientConfigurationOption(
                        cluster.getAddresses(),
                        protocol,
                    ),
                    readOnly: true,
                    nodeDiscoveryMode: NodeDiscoveryMode.DiscoverAll,
                }),
            ).rejects.toThrow();
        },
        10000,
    );

    describe("DiscoverAll", () => {
        let discoveryCluster: ValkeyCluster;

        beforeAll(async () => {
            discoveryCluster = await ValkeyCluster.createCluster(
                false,
                1,
                3,
                getServerVersion,
            );
        }, 40000);

        afterAll(async () => {
            await discoveryCluster.close();
        });

        it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
            "discover replicas from primary_%p",
            async (protocol) => {
                const addresses = discoveryCluster.getAddresses();
                const primaryAddr = addresses[0];
                const replicaAddr = addresses[1];
                const uniqueName = `discovery_t4_${Date.now()}`;

                const discoveryClient = await GlideClient.createClient({
                    addresses: [{ host: primaryAddr[0], port: primaryAddr[1] }],
                    protocol,
                    clientName: uniqueName,
                    nodeDiscoveryMode: NodeDiscoveryMode.DiscoverAll,
                });

                const probe = await GlideClient.createClient({
                    addresses: [{ host: replicaAddr[0], port: replicaAddr[1] }],
                    protocol,
                    readOnly: true,
                });

                const clientList = await probe.customCommand([
                    "CLIENT",
                    "LIST",
                ]);
                expect(clientList?.toString()).toContain(uniqueName);

                probe.close();
                discoveryClient.close();
            },
            30000,
        );

        it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
            "discover replicas from replica_%p",
            async (protocol) => {
                const addresses = discoveryCluster.getAddresses();
                const replicaAddr = addresses[1];
                const uniqueName = `discovery_t5_${Date.now()}`;

                const discoveryClient = await GlideClient.createClient({
                    addresses: [{ host: replicaAddr[0], port: replicaAddr[1] }],
                    protocol,
                    clientName: uniqueName,
                    nodeDiscoveryMode: NodeDiscoveryMode.DiscoverAll,
                });

                const key = `t5_key_${Date.now()}`;
                await discoveryClient.set(key, "value");
                const result = await discoveryClient.get(key);
                expect(result).toBe("value");
                await discoveryClient.del([key]);

                const probe = await GlideClient.createClient({
                    addresses: [{ host: replicaAddr[0], port: replicaAddr[1] }],
                    protocol,
                    readOnly: true,
                });

                const clientList = await probe.customCommand([
                    "CLIENT",
                    "LIST",
                ]);
                expect(clientList?.toString()).toContain(uniqueName);

                probe.close();
                discoveryClient.close();
            },
            30000,
        );

        it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
            "discover replicas partial addresses_%p",
            async (protocol) => {
                const addresses = discoveryCluster.getAddresses();
                const primaryAddr = addresses[0];
                const replica0Addr = addresses[1];
                const replica1Addr = addresses[2];
                const replica2Addr = addresses[3];
                const uniqueName = `discovery_t6_${Date.now()}`;

                const discoveryClient = await GlideClient.createClient({
                    addresses: [
                        { host: primaryAddr[0], port: primaryAddr[1] },
                        { host: replica0Addr[0], port: replica0Addr[1] },
                    ],
                    protocol,
                    clientName: uniqueName,
                    nodeDiscoveryMode: NodeDiscoveryMode.DiscoverAll,
                });

                const probe1 = await GlideClient.createClient({
                    addresses: [
                        { host: replica1Addr[0], port: replica1Addr[1] },
                    ],
                    protocol,
                    readOnly: true,
                });

                const probe2 = await GlideClient.createClient({
                    addresses: [
                        { host: replica2Addr[0], port: replica2Addr[1] },
                    ],
                    protocol,
                    readOnly: true,
                });

                const clientList1 = await probe1.customCommand([
                    "CLIENT",
                    "LIST",
                ]);
                expect(clientList1?.toString()).toContain(uniqueName);

                const clientList2 = await probe2.customCommand([
                    "CLIENT",
                    "LIST",
                ]);
                expect(clientList2?.toString()).toContain(uniqueName);

                probe1.close();
                probe2.close();
                discoveryClient.close();
            },
            30000,
        );
    });
});
