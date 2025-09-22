/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

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
} from "../src";
import { flushAndCloseClient, parseEndpoints } from "./TestUtilities";
import ValkeyCluster from "../../utils/TestUtils";

const unlinkAsync = promisify(fs.unlink);
const accessAsync = promisify(fs.access);

Logger.setLevel("error");

const SOCKET_GLOB_PREFIX = "glide-socket-";

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
    const standaloneEndpoints = process.env.STAND_ALONE_ENDPOINT as
        | string
        | undefined;
    const addresses = standaloneEndpoints
        ? parseEndpoints(standaloneEndpoints)
        : undefined;

    const config: GlideClientConfiguration = {
        addresses: addresses ?? [{ host: "127.0.0.1", port: 6379 }],
    };
    return GlideClient.create(config);
}

async function createClusterClient(): Promise<GlideClusterClient> {
    const clusterEndpoints = process.env.CLUSTER_ENDPOINT as string | undefined;
    const addresses = clusterEndpoints
        ? parseEndpoints(clusterEndpoints)
        : undefined;

    const config: GlideClusterClientConfiguration = {
        addresses: addresses ?? [{ host: "127.0.0.1", port: 6379 }],
    };
    return GlideClusterClient.create(config);
}

describe("Socket lifecycle contracts", () => {
    let standaloneCluster: ValkeyCluster | undefined;
    let clusterCluster: ValkeyCluster | undefined;

    beforeAll(async () => {
        if (!process.env.STAND_ALONE_ENDPOINT) {
            standaloneCluster = await ValkeyCluster.createCluster(false, 1, 1);
            process.env.STAND_ALONE_ENDPOINT = standaloneCluster
                .getAddresses()
                .join(",");
        }

        if (!process.env.CLUSTER_ENDPOINT) {
            clusterCluster = await ValkeyCluster.createCluster(true, 2, 1);
            process.env.CLUSTER_ENDPOINT = clusterCluster
                .getAddresses()
                .join(",");
        }
    }, 60000);

    afterAll(async () => {
        await standaloneCluster?.close();
        await clusterCluster?.close();
    }, 30000);

    it("removes socket on standalone close", async () => {
        const client = await createStandaloneClient();
        const socketPath = (client as unknown as { socketPath?: string })
            .socketPath;
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
        const seedSocketPath = (
            seedClient as unknown as { socketPath?: string }
        ).socketPath;
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
            const socketPath = (client as unknown as { socketPath?: string })
                .socketPath;

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

        const socketPathA = (clientA as unknown as { socketPath?: string })
            .socketPath;
        const socketPathB = (clientB as unknown as { socketPath?: string })
            .socketPath;

        expect(socketPathA && socketPathB).toBeTruthy();
        expect(socketPathA).toEqual(socketPathB);

        await clientA.ping();
        await clientB.ping();

        await clientA.close();
        await sleep(0);
        await expect(clientB.ping()).resolves.toEqual("PONG");

        await flushAndCloseClient(
            false,
            (process.env.STAND_ALONE_ENDPOINT as string).split(","),
            clientB,
        );

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
        const socketPath = (client as unknown as { socketPath?: string })
            .socketPath;
        expect(typeof socketPath).toBe("string");
        await client.ping();

        if (socketPath) {
            await removeIfExists(socketPath);
        }

        await expect(client.ping()).rejects.toThrow();

        await flushAndCloseClient(
            true,
            (process.env.CLUSTER_ENDPOINT as string).split(","),
            client,
        );
    }, 30000);
});
