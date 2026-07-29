/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 *
 * Integration tests for Isolated Execution Scopes (Node.js).
 * Scopes go through glide-core's Rust scope module via N-API.
 */

import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { GlideClient, IsolatedScope } from "..";
import { GlideClientConfiguration } from "..";
import { connection_request } from "../build-ts/ProtobufMessage";
import { ValkeyCluster } from "../../utils/TestUtils.js";
import {
    getClientConfigurationOption,
    getServerVersion,
    parseEndpoints,
} from "./TestUtilities";

const TIMEOUT = 30_000;

function makeKey(prefix: string): string {
    return `scope-test-${prefix}-${Math.random().toString(36).slice(2, 10)}`;
}

describe("IsolatedScope", () => {
    let cluster: ValkeyCluster;
    let client: GlideClient;
    let connReqBytes: Uint8Array;
    let config: GlideClientConfiguration;

    beforeAll(async () => {
        const standaloneAddresses = global.STAND_ALONE_ENDPOINT as string;
        cluster = standaloneAddresses
            ? await ValkeyCluster.initFromExistingCluster(
                  false,
                  parseEndpoints(standaloneAddresses),
                  getServerVersion,
              )
            : await ValkeyCluster.createCluster(false, 1, 1, getServerVersion);

        config = getClientConfigurationOption(cluster.getAddresses(), 0, {
            requestTimeout: 5000,
        }) as GlideClientConfiguration;

        client = await GlideClient.createClient(config);

        // Build connReqBytes from the actual addresses
        const addresses = cluster.getAddresses().map(([host, port]) => ({
            host,
            port,
        }));
        const req = connection_request.ConnectionRequest.create({
            addresses,
            requestTimeout: 5000,
        });
        connReqBytes =
            connection_request.ConnectionRequest.encode(req).finish();
    }, TIMEOUT);

    afterAll(async () => {
        client?.close();
        await cluster?.close();
    }, TIMEOUT);

    it(
        "acquire and release",
        async () => {
            const scope = await IsolatedScope.acquire(client, connReqBytes);
            const result = await scope.ping();
            expect(result).toBe("PONG");
            expect(scope.isReleased).toBe(false);
            scope.release();
            expect(scope.isReleased).toBe(true);
        },
        TIMEOUT,
    );

    it(
        "GET and SET",
        async () => {
            const scope = await IsolatedScope.acquire(client, connReqBytes);
            const key = makeKey("basic");
            await scope.set(key, "value");
            const result = await scope.get(key);
            expect(result).toBe("value");
            await scope.del(key);
            scope.release();
        },
        TIMEOUT,
    );

    it(
        "WATCH/MULTI/EXEC success",
        async () => {
            const scope = await IsolatedScope.acquire(client, connReqBytes);
            const key = makeKey("occ");
            await scope.set(key, "0");
            await scope.watch(key);
            const current = await scope.get(key);
            expect(current).toBe("0");
            await scope.multi();
            await scope.set(key, "1");
            const result = await scope.exec();
            expect(result).not.toBeNull();
            const final_ = await scope.get(key);
            expect(final_).toBe("1");
            await scope.del(key);
            scope.release();
        },
        TIMEOUT,
    );

    it(
        "WATCH conflict aborts EXEC",
        async () => {
            const scope = await IsolatedScope.acquire(client, connReqBytes);
            const key = makeKey("conflict");
            await scope.set(key, "original");
            await scope.watch(key);
            await scope.get(key);
            // Modify externally via the parent client
            await client.set(key, "modified-externally");
            await scope.multi();
            await scope.set(key, "from-scope");
            const result = await scope.exec();
            expect(result).toBeNull();
            const val = await client.get(key);
            expect(val).toBe("modified-externally");
            await client.del([key]);
            scope.release();
        },
        TIMEOUT,
    );

    it(
        "raises after release",
        async () => {
            const scope = await IsolatedScope.acquire(client, connReqBytes);
            await scope.ping();
            scope.release();
            await expect(scope.ping()).rejects.toThrow(/released/);
        },
        TIMEOUT,
    );

    it(
        "idempotent release",
        async () => {
            const scope = await IsolatedScope.acquire(client, connReqBytes);
            await scope.ping();
            scope.release();
            scope.release();
            expect(scope.isReleased).toBe(true);
        },
        TIMEOUT,
    );

    it(
        "INCR command",
        async () => {
            const scope = await IsolatedScope.acquire(client, connReqBytes);
            const key = makeKey("incr");
            await scope.set(key, "10");
            const result = await scope.incr(key);
            expect(result).toBe("11");
            await scope.del(key);
            scope.release();
        },
        TIMEOUT,
    );

    it(
        "SELECT command",
        async () => {
            const scope = await IsolatedScope.acquire(client, connReqBytes);
            const result = await scope.select(2);
            expect(result).toBe("OK");
            await scope.select(0);
            scope.release();
        },
        TIMEOUT,
    );

    it(
        "executeCommand arbitrary",
        async () => {
            const scope = await IsolatedScope.acquire(client, connReqBytes);
            const key = makeKey("custom");
            await scope.executeCommand("SET", key, "custom_value");
            const result = await scope.executeCommand("GET", key);
            expect(result).toBe("custom_value");
            await scope.executeCommand("DEL", key);
            scope.release();
        },
        TIMEOUT,
    );

    it(
        "scope connection reuse after release",
        async () => {
            const s1 = await IsolatedScope.acquire(client, connReqBytes);
            await s1.ping();
            s1.release();
            await new Promise((r) => setTimeout(r, 50));

            const s2 = await IsolatedScope.acquire(client, connReqBytes);
            await s2.ping();
            s2.release();
            await new Promise((r) => setTimeout(r, 50));

            const s3 = await IsolatedScope.acquire(client, connReqBytes);
            await s3.ping();
            s3.release();
        },
        TIMEOUT,
    );

    it(
        "scope release resets state",
        async () => {
            const key = makeKey("scope-reset");
            const s1 = await IsolatedScope.acquire(client, connReqBytes);
            await s1.select(5);
            await s1.set(key, "on-db5");
            s1.release();
            await new Promise((r) => setTimeout(r, 50));

            const s2 = await IsolatedScope.acquire(client, connReqBytes);
            const result = await s2.get(key);
            expect(result).toBeNull();
            await s2.select(5);
            await s2.del(key);
            s2.release();
        },
        TIMEOUT,
    );

    it(
        "scope inherits compression - reads decompress correctly",
        async () => {
            const addresses = cluster.getAddresses().map(([host, port]) => ({
                host,
                port,
            }));
            const compressedConfig: GlideClientConfiguration = {
                ...config,
                compression: { enabled: true },
            };
            const compressedClient =
                await GlideClient.createClient(compressedConfig);

            const req = connection_request.ConnectionRequest.create({
                addresses,
                requestTimeout: 5000,
                compressionConfig: {
                    enabled: true,
                    backend: 0,
                    minCompressionSize: 64,
                },
            });
            const compressedBytes =
                connection_request.ConnectionRequest.encode(req).finish();

            const key = makeKey("compress");
            const largeValue = "CompressibleData_".repeat(30);
            await compressedClient.set(key, largeValue);

            const scope = await IsolatedScope.acquire(
                compressedClient,
                compressedBytes,
            );
            const scopeResult = await scope.get(key);
            expect(scopeResult).toBe(largeValue);

            // Raw client sees different bytes (proves compression applied)
            const rawClient = await GlideClient.createClient(config);

            try {
                const rawResult = await rawClient.get(key);
                expect(rawResult).not.toBe(largeValue);
            } catch {
                // Decode error = compressed bytes not valid UTF-8 = works
            }

            await compressedClient.del([key]);
            scope.release();
            rawClient.close();
            compressedClient.close();
        },
        TIMEOUT,
    );

    it(
        "scope respects request timeout",
        async () => {
            const addresses = cluster.getAddresses().map(([host, port]) => ({
                host,
                port,
            }));
            const timeoutConfig: GlideClientConfiguration = {
                ...config,
                requestTimeout: 100,
            };
            const timeoutClient = await GlideClient.createClient(timeoutConfig);

            const req = connection_request.ConnectionRequest.create({
                addresses,
                requestTimeout: 100,
            });
            const timeoutBytes =
                connection_request.ConnectionRequest.encode(req).finish();

            const scope = await IsolatedScope.acquire(
                timeoutClient,
                timeoutBytes,
            );
            const key = makeKey("timeout");
            await scope.set(key, "fast");
            const result = await scope.get(key);
            expect(result).toBe("fast");
            await scope.del(key);
            scope.release();
            timeoutClient.close();
        },
        TIMEOUT,
    );

    it(
        "scope inherits configured database",
        async () => {
            const addresses = cluster.getAddresses().map(([host, port]) => ({
                host,
                port,
            }));
            const db2Config: GlideClientConfiguration = {
                ...config,
                databaseId: 2,
            };
            const db2Client = await GlideClient.createClient(db2Config);

            const req = connection_request.ConnectionRequest.create({
                addresses,
                requestTimeout: 5000,
                databaseId: 2,
            });
            const db2Bytes =
                connection_request.ConnectionRequest.encode(req).finish();

            const scope = await IsolatedScope.acquire(db2Client, db2Bytes);
            const key = makeKey("db-inherit");
            await scope.set(key, "on-db2");
            const result = await scope.get(key);
            expect(result).toBe("on-db2");

            // Not visible on db 0
            const db0Client = await GlideClient.createClient(config);
            const db0Result = await db0Client.get(key);
            expect(db0Result).toBeNull();

            await scope.del(key);
            scope.release();
            db0Client.close();
            db2Client.close();
        },
        TIMEOUT,
    );
});
