// Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0

import { closeMonitorClient, createMonitorClient } from "../build-ts/native";
import { connection_request } from "../build-ts/ProtobufMessage";
import { BaseClientConfiguration } from "./BaseClient.js";

export interface MonitorLine {
    timestamp: number;
    db: number;
    clientAddr: string;
    command: string;
    args: string[];
}

export class GlideMonitorClient {
    private handleId: number | null = null;
    private closed = false;
    private readonly queue: MonitorLine[] = [];
    private waiters: {
        resolve: (line: MonitorLine) => void;
        reject: (err: Error) => void;
    }[] = [];

    // eslint-disable-next-line @typescript-eslint/no-empty-function
    private constructor() {}

    /**
     * Creates a GlideMonitorClient that streams server-side commands.
     * @param options - Connection configuration.
     * @param callback - Optional callback invoked for each monitor line.
     *   If omitted, use {@link getNextMessage} or {@link tryGetNextMessage} to poll.
     */
    static async create(
        options: BaseClientConfiguration,
        callback?: (line: MonitorLine) => void,
    ): Promise<GlideMonitorClient> {
        const client = new GlideMonitorClient();
        const request = GlideMonitorClient.buildConnectionRequest(options);
        const bytes =
            connection_request.ConnectionRequest.encode(request).finish();
        client.handleId = await createMonitorClient(
            Buffer.from(bytes),
            (timestamp, db, clientAddr, command, args) => {
                const line: MonitorLine = {
                    timestamp,
                    db,
                    clientAddr,
                    command,
                    args,
                };

                if (callback) {
                    callback(line);
                } else {
                    const waiter = client.waiters.shift();

                    if (waiter) {
                        waiter.resolve(line);
                    } else {
                        client.queue.push(line);
                    }
                }
            },
        );
        return client;
    }

    /**
     * Returns the next monitor line, waiting until one arrives.
     * Only usable when no callback was provided to {@link create}.
     * Rejects if the client is already closed.
     */
    getNextMessage(): Promise<MonitorLine> {
        if (this.closed) {
            return Promise.reject(new Error("Monitor is closed"));
        }

        if (this.queue.length > 0) {
            return Promise.resolve(this.queue.shift()!);
        }

        return new Promise((resolve, reject) =>
            this.waiters.push({ resolve, reject }),
        );
    }

    /**
     * Returns the next monitor line immediately, or `undefined` if none is queued.
     * Only usable when no callback was provided to {@link create}.
     */
    tryGetNextMessage(): MonitorLine | undefined {
        return this.queue.shift();
    }

    /** Stops monitoring. Idempotent — safe to call multiple times. */
    async close(): Promise<void> {
        if (this.closed) return;
        this.closed = true;

        // Reject any pending getNextMessage() waiters so callers don't hang.
        for (const { reject } of this.waiters.splice(0)) {
            reject(new Error("Monitor is closed"));
        }

        if (this.handleId !== null) {
            const id = this.handleId;
            this.handleId = null;
            await closeMonitorClient(id);
        }
    }

    private static buildConnectionRequest(
        options: BaseClientConfiguration,
    ): connection_request.IConnectionRequest {
        return connection_request.ConnectionRequest.create({
            addresses: options.addresses?.map((a) => ({
                host: a.host,
                port: a.port,
            })),
            tlsMode: options.useTLS
                ? connection_request.TlsMode.SecureTls
                : connection_request.TlsMode.NoTls,
            authenticationInfo:
                options.credentials != null && "password" in options.credentials
                    ? connection_request.AuthenticationInfo.create({
                          username: options.credentials.username ?? "",
                          password: options.credentials.password,
                      })
                    : undefined,
            databaseId: options.databaseId ?? 0,
            clientName: options.clientName,
        });
    }
}
