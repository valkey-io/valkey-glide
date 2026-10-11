/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { afterEach, beforeEach, describe, expect, it } from "@jest/globals";
import { ChildProcess, spawn } from "node:child_process";
import { once } from "node:events";
import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import { createConnection } from "node:net";
import { join } from "node:path";
import {
    ClientPool,
    GlideClient,
    GlideClientConfiguration,
    GlideMonitorClient,
    MonitorLine,
    NodeAddress,
} from "../build-ts";
import {
    checkWhichCommandAvailable,
    getClientInfoField,
    waitFor,
} from "./TestUtilities";

const describeUnix = process.platform === "win32" ? describe.skip : describe;

function pingSocket(socketPath: string): Promise<boolean> {
    return new Promise((resolve) => {
        const socket = createConnection({ path: socketPath });
        socket.setTimeout(1000);
        socket.once("connect", () => socket.write("PING\r\n"));
        socket.once("data", (data) => {
            socket.destroy();
            resolve(data.toString().startsWith("+PONG"));
        });
        socket.once("error", () => resolve(false));
        socket.once("close", () => resolve(false));
        socket.once("timeout", () => {
            socket.destroy();
            resolve(false);
        });
    });
}

/** Listens only on a Unix domain socket (`--port 0`), so a connected client must use it. */
class UnixSocketServer {
    readonly socketPath: string;
    private readonly directory: string;
    private readonly process: ChildProcess;
    private failure: string | undefined;

    private constructor(serverBinary: string) {
        // Unix socket paths are limited to ~104-108 bytes, so avoid long TMPDIRs.
        this.directory = mkdtempSync("/tmp/glide-uds-");
        this.socketPath = join(this.directory, "valkey.sock");
        this.process = spawn(
            serverBinary,
            [
                "--port",
                "0",
                "--unixsocket",
                this.socketPath,
                "--unixsocketperm",
                "700",
                "--dir",
                this.directory,
                "--logfile",
                join(this.directory, "server.log"),
                "--save",
                "",
                "--appendonly",
                "no",
            ],
            { stdio: "ignore" },
        );
        this.process.once("error", (error) => {
            this.failure = error.message;
        });
        this.process.once("exit", (code, signal) => {
            this.failure ??= `server exited (${signal ?? code})`;
        });
    }

    static async start(): Promise<UnixSocketServer> {
        const server = new UnixSocketServer(
            await checkWhichCommandAvailable("valkey-server", "redis-server"),
        );

        try {
            await waitFor(async () => {
                if (server.failure !== undefined) {
                    throw new Error(server.failure);
                }

                return pingSocket(server.socketPath);
            }, `server did not listen on ${server.socketPath} in time`);
        } catch (error) {
            const log = readFileSync(join(server.directory, "server.log"), {
                encoding: "utf8",
                flag: "a+",
            });
            await server.stop();
            throw new Error(`${(error as Error).message}:\n${log}`, {
                cause: error,
            });
        }

        return server;
    }

    async stop(): Promise<void> {
        if (
            this.process.exitCode === null &&
            this.process.signalCode === null
        ) {
            const exited = once(this.process, "exit");
            this.process.kill("SIGKILL");
            await exited;
        }

        rmSync(this.directory, { recursive: true, force: true });
    }

    config(
        overrides: Partial<GlideClientConfiguration> = {},
    ): GlideClientConfiguration {
        return {
            addresses: [{ unixSocketPath: this.socketPath }],
            requestTimeout: 2000,
            ...overrides,
        };
    }
}

/** Asserts that the server flags the client's connection as a Unix socket client (`U`). */
async function expectUnixSocketClient(client: GlideClient): Promise<string> {
    const info = String(await client.customCommand(["CLIENT", "INFO"]));
    expect(getClientInfoField(info, "flags")).toContain("U");
    return info;
}

describeUnix("Unix domain socket connections", () => {
    let server: UnixSocketServer;

    beforeEach(async () => {
        server = await UnixSocketServer.start();
    });

    afterEach(async () => {
        await server?.stop();
    });

    it("connects over the socket", async () => {
        const client = await GlideClient.createClient(
            server.config({ databaseId: 2 }),
        );

        try {
            expect(await client.set("uds-key", "uds-value")).toEqual("OK");
            expect(await client.get("uds-key")).toEqual("uds-value");
            const info = await expectUnixSocketClient(client);
            expect(getClientInfoField(info, "db")).toEqual("2");
        } finally {
            client.close();
        }
    });

    it("serves pooled clients over the socket", async () => {
        const pool = await ClientPool.create(server.config(), {
            maxSize: 1,
            minIdle: 1,
        });

        try {
            const pooled = (await pool.acquire()) as GlideClient;
            expect(await pooled.set("uds-pool-key", "pooled")).toEqual("OK");
            await expectUnixSocketClient(pooled);

            const pooledScope = await pooled.scopedConnection();

            try {
                expect(await pooledScope.get("uds-pool-key")).toEqual("pooled");
            } finally {
                pooledScope.release();
            }

            await pool.release(pooled);
        } finally {
            pool.close();
        }
    });

    it("opens isolated scopes over the socket", async () => {
        const client = await GlideClient.createClient(server.config());

        try {
            const scope = await client.scopedConnection();

            try {
                await scope.set("uds-scope-key", "scoped");
                expect(await scope.get("uds-scope-key")).toEqual("scoped");
            } finally {
                scope.release();
            }

            expect(await client.get("uds-scope-key")).toEqual("scoped");
        } finally {
            client.close();
        }
    });

    it("streams MONITOR output over the socket", async () => {
        const received: MonitorLine[] = [];
        const monitor = await GlideMonitorClient.create(
            server.config(),
            (line) => received.push(line),
        );

        try {
            const client = await GlideClient.createClient(server.config());

            try {
                await client.set("uds-monitor-key", "value");
                await waitFor(
                    async () =>
                        received.some(
                            (line) =>
                                line.command.toUpperCase() === "SET" &&
                                line.clientAddr === `unix:${server.socketPath}`,
                        ),
                    "SET over the socket not seen by MONITOR",
                );
            } finally {
                client.close();
            }
        } finally {
            await monitor.close();
        }
    });

    it("surfaces the core's rejection of a host given with a socket path", async () => {
        await expect(
            GlideClient.createClient({
                // Only reachable from untyped callers; the type forbids it.
                addresses: [
                    {
                        host: "localhost",
                        unixSocketPath: server.socketPath,
                    } as unknown as NodeAddress,
                ],
            }),
        ).rejects.toThrow("must not also set a host or port");
    });
});
