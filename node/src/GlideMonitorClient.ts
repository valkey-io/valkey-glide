// Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0

import { closeMonitorClient, createMonitorClient } from "../build-ts/native";
import { connection_request } from "../build-ts/ProtobufMessage";
import { BaseClientConfiguration } from "./BaseClient.js";

/**
 * Represents a single line received from the MONITOR command.
 */
export interface MonitorLine {
    /** Unix timestamp of when the command was received. */
    timestamp: number;
    /** Database index on which the command was executed. */
    db: number;
    /** Client address (e.g. "127.0.0.1:52345"). */
    clientAddr: string;
    /** Command name (e.g. "set"). */
    command: string;
    /** Command arguments. */
    args: string[];
}

/**
 * A client that listens to all commands processed by the server via the MONITOR command.
 * Standalone-only; cluster mode is not supported.
 *
 * @example
 * ```typescript
 * const monitor = await GlideMonitorClient.create(
 *     { addresses: [{ host: "localhost", port: 6379 }] },
 *     (line) => console.log(line.command, line.args),
 * );
 * // ... use a regular client to run commands ...
 * await monitor.close();
 * ```
 */
export class GlideMonitorClient {
    private handleId: number | null = null;
    private closed = false;

    // eslint-disable-next-line @typescript-eslint/no-empty-function
    private constructor() {}

    /**
     * Creates a new GlideMonitorClient connected to a standalone server.
     *
     * @param options - Connection options. Must target a standalone server.
     * @param callback - Called for each received MonitorLine.
     * @returns A connected GlideMonitorClient.
     */
    static async create(
        options: BaseClientConfiguration,
        callback: (line: MonitorLine) => void,
    ): Promise<GlideMonitorClient> {
        const client = new GlideMonitorClient();
        const request = GlideMonitorClient.buildConnectionRequest(options);
        const bytes =
            connection_request.ConnectionRequest.encode(request).finish();
        client.handleId = await createMonitorClient(
            Buffer.from(bytes),
            (
                timestamp: number,
                db: number,
                clientAddr: string,
                command: string,
                args: string[],
            ) => callback({ timestamp, db, clientAddr, command, args }),
        );
        return client;
    }

    /**
     * Stops the monitor client and releases its resources.
     * Idempotent — safe to call multiple times.
     */
    async close(): Promise<void> {
        if (this.closed) return;
        this.closed = true;

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
        });
    }
}
