/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 *
 * Isolated Execution Scope for Node.js.
 *
 * Commands go through glide-core's scope module via N-API:
 * - `scopeAcquire` → `glide_core::scope::acquire_scope`
 * - `scopeExecute` → `glide_core::scope::execute_scope_command`
 * - `scopeRelease` → `glide_core::scope::release_scope`
 *
 * This gives us state tracking, cluster slot pinning, compression,
 * timeout, and zero-cost release — identical to Java/Python/Go.
 */

import { scopeAcquire, scopeExecute, scopeRelease } from "../build-ts/native";
import type { GlideString } from "./BaseClient";
import type { BaseClient } from "./BaseClient";
import { ConnectionError, RequestError, TimeoutError } from "./Errors";

// ─── Wire Format Serialization ───────────────────────────────────────────────

/**
 * Serialize a command into the wire format expected by glide-core::scope::deserialize_command.
 *
 * Wire format (little-endian):
 *   [4:cmd_name_len][cmd_name][4:num_args][4:arg1_len][arg1]...[4:argN_len][argN]
 */
function serializeCommand(cmdName: string, args: GlideString[]): Uint8Array {
    const encoder = new TextEncoder();
    const cmdBytes = encoder.encode(cmdName);

    let totalSize = 4 + cmdBytes.length + 4;
    const argBuffers: Uint8Array[] = [];

    for (const arg of args) {
        const buf =
            arg instanceof Buffer
                ? new Uint8Array(arg)
                : encoder.encode(arg as string);
        argBuffers.push(buf);
        totalSize += 4 + buf.length;
    }

    const buffer = new Uint8Array(totalSize);
    const view = new DataView(buffer.buffer);
    let offset = 0;

    view.setUint32(offset, cmdBytes.length, true);
    offset += 4;
    buffer.set(cmdBytes, offset);
    offset += cmdBytes.length;

    view.setUint32(offset, argBuffers.length, true);
    offset += 4;

    for (const argBuf of argBuffers) {
        view.setUint32(offset, argBuf.length, true);
        offset += 4;
        buffer.set(argBuf, offset);
        offset += argBuf.length;
    }

    return buffer;
}

// ─── Slot Computation ────────────────────────────────────────────────────────

/**
 * Compute the Redis cluster hash slot for a key (CRC16 mod 16384).
 * Handles hash tags: if the key contains {...}, only the content between
 * the first { and first } is hashed.
 */
function slotForKey(key: Buffer): number {
    const start = key.indexOf(0x7b); // '{'

    if (start !== -1) {
        const end = key.indexOf(0x7d, start + 1); // '}'

        if (end !== -1 && end !== start + 1) {
            key = key.subarray(start + 1, end);
        }
    }

    let crc = 0;

    for (const byte of key) {
        crc ^= byte << 8;

        for (let j = 0; j < 8; j++) {
            if (crc & 0x8000) {
                crc = ((crc << 1) ^ 0x1021) & 0xffff;
            } else {
                crc = (crc << 1) & 0xffff;
            }
        }
    }

    return crc % 16384;
}

// ─── Acquire Errors ──────────────────────────────────────────────────────────

/**
 * The native `scopeAcquire` rejection carries the core's `RequestErrorType`
 * name as a `Name: ` prefix on the message (napi has no error code field).
 * Strip it and pick the error class every other command path uses for it.
 */
function toAcquireError(e: unknown): Error {
    const message = e instanceof Error ? e.message : String(e);
    const sep = message.indexOf(": ");

    if (sep < 0) {
        return new RequestError(message);
    }

    const rest = message.slice(sep + 2);

    switch (message.slice(0, sep)) {
        case "Timeout":
            return new TimeoutError(rest);
        case "Disconnect":
            return new ConnectionError(rest);
        default:
            return new RequestError(rest);
    }
}

// ─── IsolatedScope ───────────────────────────────────────────────────────────

/**
 * An isolated execution scope backed by glide-core's scope module.
 *
 * Each scope has a dedicated TCP connection managed by the Rust core.
 * Commands go through `execute_scope_command` which provides:
 * - Per-connection state tracking (WATCH, MULTI, SELECT, subscriptions)
 * - Cluster slot pinning (first keyed command pins the slot)
 * - Cross-slot rejection in cluster mode
 * - Compression/decompression using parent client settings
 * - Timeout handling via parent client's request_timeout
 * - Zero-cost release when state is clean
 *
 * @example
 * ```typescript
 * const scope = await IsolatedScope.acquire(client);
 * try {
 *     await scope.watch("key");
 *     const val = await scope.get("key");
 *     await scope.multi();
 *     await scope.set("key", "new_value");
 *     const result = await scope.exec();
 * } finally {
 *     scope.release();
 * }
 * ```
 */
export class IsolatedScope {
    private scopeId: number;
    private clientId: number;
    private connectionRequestBytes: Uint8Array;
    private released = false;

    private constructor(
        scopeId: number,
        clientId: number,
        connectionRequestBytes: Uint8Array,
    ) {
        this.scopeId = scopeId;
        this.clientId = clientId;
        this.connectionRequestBytes = connectionRequestBytes;
    }

    /**
     * Acquire an isolated scope for a client.
     *
     * The core owns the wait: it returns once a connection is available, the
     * timeout passes, or a cause that waiting cannot fix is found.
     *
     * @param client - A GlideClient or GlideClusterClient instance.
     * @param connectionRequestBytes - Serialized connection request (from pool or client config).
     * @param routingKey - In cluster mode, the key whose hash slot determines which node the scope connects to.
     * @param timeoutMs - Maximum time to wait for a scope, in milliseconds. Default: 5000.
     * @returns A new IsolatedScope.
     * @throws {TimeoutError} if no scope became available within `timeoutMs`.
     * @throws {ConnectionError} if the client was closed while waiting.
     * @throws {RequestError} if the client's configuration cannot produce a scoped connection.
     */
    static async acquire(
        client: BaseClient,
        connectionRequestBytes: Uint8Array,
        routingKey?: string,
        timeoutMs = 5000,
    ): Promise<IsolatedScope> {
        // Get the client_id from the handle (registered in Rust scope registry)
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const clientId = (client as any).clientHandle?.clientId;

        if (clientId === undefined || clientId === null) {
            throw new Error(
                "Client does not have a valid handle. Ensure it was created via GlideClient.createClient().",
            );
        }

        const routingSlot = routingKey
            ? slotForKey(Buffer.from(routingKey))
            : 0;

        let scopeId: number;

        try {
            scopeId = await scopeAcquire(
                clientId,
                connectionRequestBytes,
                routingSlot,
                timeoutMs,
            );
        } catch (e) {
            throw toAcquireError(e);
        }

        return new IsolatedScope(scopeId, clientId, connectionRequestBytes);
    }

    /** Whether this scope has been released. */
    get isReleased(): boolean {
        return this.released;
    }

    /**
     * Execute a command on this scope via glide-core.
     *
     * @param command - Command name (e.g., "GET", "SET", "WATCH").
     * @param args - Command arguments.
     * @returns Result as string, or null for nil.
     */
    async executeCommand(
        command: string,
        ...args: GlideString[]
    ): Promise<string | null> {
        if (this.released) {
            throw new Error("Scope has been released");
        }

        const cmdBytes = serializeCommand(command, args);
        return scopeExecute(this.scopeId, this.clientId, cmdBytes);
    }

    // ─── WATCH/MULTI/EXEC ────────────────────────────────────────────────────

    /** WATCH one or more keys for optimistic locking. */
    async watch(...keys: string[]): Promise<string | null> {
        return this.executeCommand("WATCH", ...keys);
    }

    /** UNWATCH all watched keys. */
    async unwatch(): Promise<string | null> {
        return this.executeCommand("UNWATCH");
    }

    /** Begin a MULTI transaction block. */
    async multi(): Promise<string | null> {
        return this.executeCommand("MULTI");
    }

    /**
     * Execute the MULTI transaction.
     * Returns null if WATCH detected a conflict (transaction aborted).
     */
    async exec(): Promise<string | null> {
        return this.executeCommand("EXEC");
    }

    /** DISCARD the current transaction. */
    async discard(): Promise<string | null> {
        return this.executeCommand("DISCARD");
    }

    // ─── Data Commands ───────────────────────────────────────────────────────

    /** GET a key's value. */
    async get(key: string): Promise<string | null> {
        return this.executeCommand("GET", key);
    }

    /** SET a key to a value. */
    async set(key: string, value: string): Promise<string | null> {
        return this.executeCommand("SET", key, value);
    }

    /** INCREMENT a key's integer value by 1. */
    async incr(key: string): Promise<string | null> {
        return this.executeCommand("INCR", key);
    }

    /** DEL one or more keys. */
    async del(...keys: string[]): Promise<string | null> {
        return this.executeCommand("DEL", ...keys);
    }

    // ─── Server Commands ─────────────────────────────────────────────────────

    /** PING the server. */
    async ping(): Promise<string | null> {
        return this.executeCommand("PING");
    }

    /** SELECT a database by index. */
    async select(db: number): Promise<string | null> {
        return this.executeCommand("SELECT", db.toString());
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    /**
     * Release this scope back to the connection pool.
     *
     * Goes through `glide_core::scope::release_scope` which handles:
     * - Zero-cost release if state is clean
     * - Async cleanup (DISCARD + UNWATCH + SELECT) if dirty
     * - Connection returned to pool for reuse
     *
     * Safe to call multiple times (idempotent).
     */
    release(): void {
        if (!this.released) {
            this.released = true;
            scopeRelease(this.scopeId, this.clientId);
        }
    }

    /** Alias for release(). */
    close(): void {
        this.release();
    }
}
