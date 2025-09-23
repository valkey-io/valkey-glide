/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { afterAll, beforeAll, describe, expect, it } from "@jest/globals";
import * as fs from "fs";
import * as os from "os";
import * as path from "path";
import { promisify } from "util";
import { setTimeout as sleep } from "timers/promises";

import {
    GlideClient,
    GlideClientConfiguration,
    GlideClusterClient,
    GlideClusterClientConfiguration,
    Logger,
} from "../build-ts";
import {
    flushAndCloseClient,
    getServerVersion,
    parseEndpoints,
} from "./TestUtilities";
import ValkeyCluster from "../../utils/TestUtils";

const unlinkAsync = promisify(fs.unlink);
const accessAsync = promisify(fs.access);

Logger.setLoggerConfig("error");

const SOCKET_GLOB_PREFIX = "glide-socket-";

type EndpointTuple = [string, number];

/**
 * Helper function to extract socketPath from client objects.
 * Reduces repetitive type casting and improves maintainability.
 */
function getSocketPath(
    client: GlideClient | GlideClusterClient,
): string | undefined {
    return (client as unknown as { socketPath?: string }).socketPath;
}

function mapToClientAddresses(
    addresses?: EndpointTuple[],
): { host: string; port: number }[] {
    return (addresses ?? [["127.0.0.1", 6379]]).map(([host, port]) => ({
        host,
        port,
    }));
}

function parseEnvEndpoints(name: string): EndpointTuple[] | undefined {
    const value = process.env[name];
    return value ? parseEndpoints(value) : undefined;
}

function formatAddressesForEnv(addresses: EndpointTuple[]): string {
    return addresses.map(([host, port]) => `${host}:${port}`).join(",");
}

async function collectSockets(directory: string): Promise<string[]> {
    try {
        const entries = await fs.promises.readdir(directory, {
            withFileTypes: true,
        });
        return entries
            .filter(
                (entry) =>
                    entry.isFile() && entry.name.startsWith(SOCKET_GLOB_PREFIX),
            )
            .map((entry) => path.join(directory, entry.name));
    } catch (err) {
        if ((err as NodeJS.ErrnoException).code === "ENOENT") {
            return [];
        }

        throw err;
    }
}

async function assertFileDoesNotExist(filePath: string, message: string) {
    try {
        await accessAsync(filePath, fs.constants.F_OK);
        throw new Error(message);
    } catch (err) {
        if ((err as NodeJS.ErrnoException).code !== "ENOENT") {
            throw err;
        }
    }
}

async function removeIfExists(filePath: string) {
    try {
        await unlinkAsync(filePath);
    } catch (err) {
        if ((err as NodeJS.ErrnoException).code !== "ENOENT") {
            throw err;
        }
    }
}

async function createStandaloneClient(): Promise<GlideClient> {
    const addresses = parseEnvEndpoints("STAND_ALONE_ENDPOINT");

    const config: GlideClientConfiguration = {
        addresses: mapToClientAddresses(addresses),
    };
    return GlideClient.createClient(config);
}

async function createClusterClient(): Promise<GlideClusterClient> {
    const addresses = parseEnvEndpoints("CLUSTER_ENDPOINT");

    const config: GlideClusterClientConfiguration = {
        addresses: mapToClientAddresses(addresses),
    };
    return GlideClusterClient.createClient(config);
}

describe("Socket lifecycle contracts", () => {
    let standaloneCluster: ValkeyCluster | undefined;
    let clusterCluster: ValkeyCluster | undefined;

    beforeAll(async () => {
        if (!process.env.STAND_ALONE_ENDPOINT) {
            standaloneCluster = await ValkeyCluster.createCluster(
                false,
                1,
                1,
                getServerVersion,
            );
            process.env.STAND_ALONE_ENDPOINT = formatAddressesForEnv(
                standaloneCluster.getAddresses(),
            );
        }

        if (!process.env.CLUSTER_ENDPOINT) {
            clusterCluster = await ValkeyCluster.createCluster(
                true,
                3,
                1,
                getServerVersion,
            );
            process.env.CLUSTER_ENDPOINT = formatAddressesForEnv(
                clusterCluster.getAddresses(),
            );
        }
    }, 60000);

    afterAll(async () => {
        await standaloneCluster?.close();
        await clusterCluster?.close();
    }, 30000);

    it("removes socket on standalone close", async () => {
        const client = await createStandaloneClient();
        const socketPath = getSocketPath(client);
        expect(typeof socketPath).toBe("string");
        await client.ping();
        await client.close();

        if (socketPath) {
            await sleep(0);
            await assertFileDoesNotExist(
                socketPath,
                "Expected standalone socket to be removed after close",
            );
        }
    }, 20000);

    it("does not leak sockets across sequential clients", async () => {
        const seedClient = await createStandaloneClient();
        const seedSocketPath = getSocketPath(seedClient);
        expect(typeof seedSocketPath).toBe("string");
        const socketDir = seedSocketPath
            ? path.dirname(seedSocketPath)
            : os.tmpdir();
        await seedClient.close();
        await sleep(0);

        const baseline = await collectSockets(socketDir);
        const created: string[] = [];

        for (let i = 0; i < 3; i++) {
            const client = await createStandaloneClient();
            const socketPath = getSocketPath(client);

            if (socketPath) {
                created.push(socketPath);
            }

            await client.ping();
            await client.close();
            await sleep(0);
        }

        const current = await collectSockets(socketDir);
        const leaked = current.filter((entry) => !baseline.includes(entry));
        expect(leaked).toHaveLength(0);

        await Promise.all(created.map(removeIfExists));
    }, 30000);

    it("closing one client keeps shared socket alive", async () => {
        const clientA = await createStandaloneClient();
        const clientB = await createStandaloneClient();

        const socketPathA = getSocketPath(clientA);
        const socketPathB = getSocketPath(clientB);

        expect(socketPathA && socketPathB).toBeTruthy();
        expect(socketPathA).toEqual(socketPathB);

        await clientA.ping();
        await clientB.ping();

        await clientA.close();
        await sleep(0);
        await expect(clientB.ping()).resolves.toEqual("PONG");

        const envStandalone = parseEnvEndpoints("STAND_ALONE_ENDPOINT");
        await flushAndCloseClient(false, envStandalone, clientB);

        if (socketPathA) {
            await sleep(0);
            await assertFileDoesNotExist(
                socketPathA,
                "Socket should be removed once all standalone clients close",
            );
        }
    }, 30000);

    it("cluster client recreates listener after cleanup", async () => {
        const client = await createClusterClient();
        const socketPath = getSocketPath(client);
        expect(typeof socketPath).toBe("string");
        await client.ping();

        if (socketPath) {
            await removeIfExists(socketPath);
            await assertFileDoesNotExist(
                socketPath,
                "Expected cluster socket to be removed before recreation",
            );
        }

        await expect(client.ping()).resolves.toEqual("PONG");

        const envCluster = parseEnvEndpoints("CLUSTER_ENDPOINT");
        await flushAndCloseClient(true, envCluster, client);
    }, 30000);
});
