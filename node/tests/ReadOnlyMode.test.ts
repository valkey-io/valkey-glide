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
import { GlideClient, ProtocolVersion, RequestError } from "../build-ts";
import {
    flushAndCloseClient,
    getClientConfigurationOption,
    getServerVersion,
    parseEndpoints,
} from "./TestUtilities";

/**
 * Integration tests for read-only mode in standalone client.
 *
 * These tests verify that:
 * - Write commands are blocked in read-only mode
 * - Read commands are allowed in read-only mode
 * - AZAffinity strategies are rejected with read-only mode
 * - PreferReplica strategy is accepted with read-only mode
 */
describe("ReadOnlyMode", () => {
    const testsFailed = 0;
    let cluster: ValkeyCluster;
    let client: GlideClient;

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
        await flushAndCloseClient(false, cluster?.getAddresses(), client);
    });

    afterAll(async () => {
        if (testsFailed === 0) {
            await cluster.close();
        } else {
            await cluster.close(true);
        }
    });

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "read-only mode blocks write commands_%p",
        async (protocol) => {
            client = await GlideClient.createClient({
                ...getClientConfigurationOption(
                    cluster.getAddresses(),
                    protocol,
                ),
                readOnly: true,
            });

            // Attempt to execute a write command - should be blocked
            await expect(client.set("key", "value")).rejects.toThrow(
                RequestError,
            );

            try {
                await client.set("key", "value");
                // Should not reach here
                expect(true).toBe(false);
            } catch (error) {
                expect(error).toBeInstanceOf(RequestError);
                expect((error as RequestError).message.toLowerCase()).toContain(
                    "write commands are not allowed in read-only mode",
                );
            }
        },
        10000,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "read-only mode allows read commands_%p",
        async (protocol) => {
            client = await GlideClient.createClient({
                ...getClientConfigurationOption(
                    cluster.getAddresses(),
                    protocol,
                ),
                readOnly: true,
            });

            // Read commands should work without raising an error
            const result = await client.get("nonexistent_key");
            // The key doesn't exist, so result should be null
            expect(result).toBeNull();
        },
        10000,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "read-only mode rejects AZAffinity strategy_%p",
        async (protocol) => {
            // Test that read-only mode with AZAffinity strategy fails during client creation
            await expect(
                GlideClient.createClient({
                    ...getClientConfigurationOption(
                        cluster.getAddresses(),
                        protocol,
                    ),
                    readOnly: true,
                    readFrom: "AZAffinity",
                    clientAz: "us-east-1a",
                }),
            ).rejects.toThrow();
        },
        10000,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "read-only mode rejects AZAffinityReplicasAndPrimary strategy_%p",
        async (protocol) => {
            // Test that read-only mode with AZAffinityReplicasAndPrimary strategy fails during client creation
            await expect(
                GlideClient.createClient({
                    ...getClientConfigurationOption(
                        cluster.getAddresses(),
                        protocol,
                    ),
                    readOnly: true,
                    readFrom: "AZAffinityReplicasAndPrimary",
                    clientAz: "us-east-1a",
                }),
            ).rejects.toThrow();
        },
        10000,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "read-only mode accepts PreferReplica strategy_%p",
        async (protocol) => {
            client = await GlideClient.createClient({
                ...getClientConfigurationOption(
                    cluster.getAddresses(),
                    protocol,
                ),
                readOnly: true,
                readFrom: "preferReplica",
            });

            // Client should be created successfully and read commands should work
            const result = await client.get("nonexistent_key");
            expect(result).toBeNull();
        },
        10000,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "read-only mode default is false (write commands work)_%p",
        async (protocol) => {
            client = await GlideClient.createClient({
                ...getClientConfigurationOption(
                    cluster.getAddresses(),
                    protocol,
                ),
                // readOnly not set, defaults to false
            });

            // Write commands should work when readOnly is not set (defaults to false)
            const key = `test_key_${Date.now()}`;
            await client.set(key, "value");
            const result = await client.get(key);
            expect(result).toBe("value");

            // Clean up
            await client.del([key]);
        },
        10000,
    );
});
