/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 *
 * Isolated Execution Scope for Node.js.
 *
 * Commands go through glide-core's scope module via N-API:
 * - `scopeTryAcquire` → `glide_core::scope::try_acquire_scope`
 * - `scopeExecute` → `glide_core::scope::execute_scope_command`
 * - `scopeRelease` → `glide_core::scope::release_scope`
 *
 * This gives us state tracking, cluster slot pinning, compression,
 * timeout, and zero-cost release — identical to Java/Python/Go.
 */

import { scopeExecute, scopeRelease } from "../build-ts/native";
import type { GlideString } from "./BaseClient";
import type { BaseClient } from "./BaseClient";
import { hasScopeConnectionRequest, tryAcquireScope } from "./ScopeInternal.js";

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
    private released = false;

    private constructor(scopeId: number, clientId: number) {
        this.scopeId = scopeId;
        this.clientId = clientId;
    }

    /**
     * Acquire an isolated scope for a client.
     *
     * Uses `glide_core::scope::try_acquire_scope` with exponential backoff.
     * Creates new scope connections in the background if the pool is empty.
     *
     * The connection request is taken from the client (captured when it
     * connected), so the one-argument form `IsolatedScope.acquire(client)`
     * works on both a standalone client and a pool-borrowed client. Passing
     * `connectionRequestBytes` explicitly is still supported and overrides the
     * client's captured request.
     *
     * Prefer {@link BaseClient.scopedConnection} for the common case.
     *
     * @param client - A GlideClient or GlideClusterClient instance.
     * @param routingKey - In cluster mode, the key whose hash slot determines which node the scope connects to.
     * @param maxRetries - Maximum retries with backoff. Default: 10.
     * @returns A new IsolatedScope.
     */
    static async acquire(
        client: BaseClient,
        routingKey?: string,
        maxRetries?: number,
    ): Promise<IsolatedScope>;
    /**
     * Acquire an isolated scope for a client using an explicit connection request.
     *
     * @param client - A GlideClient or GlideClusterClient instance.
     * @param connectionRequestBytes - Serialized connection request to use for the scope's connection.
     * @param routingKey - In cluster mode, the key whose hash slot determines which node the scope connects to.
     * @param maxRetries - Maximum retries with backoff. Default: 10.
     * @returns A new IsolatedScope.
     */
    static async acquire(
        client: BaseClient,
        connectionRequestBytes: Uint8Array,
        routingKey?: string,
        maxRetries?: number,
    ): Promise<IsolatedScope>;
    static async acquire(
        client: BaseClient,
        bytesOrRoutingKey?: Uint8Array | string,
        routingKeyOrMaxRetries?: string | number,
        maxRetries = 10,
    ): Promise<IsolatedScope> {
        // Normalize the overloaded arguments. When the second argument is a
        // Uint8Array it is an explicit connection request; otherwise it is the
        // routing key and the bytes are derived from the client.
        let explicitBytes: Uint8Array | undefined;
        let routingKey: string | undefined;

        if (bytesOrRoutingKey instanceof Uint8Array) {
            // acquire(client, bytes, routingKey?, maxRetries?)
            explicitBytes = bytesOrRoutingKey;
            routingKey = routingKeyOrMaxRetries as string | undefined;
        } else {
            // acquire(client, routingKey?, maxRetries?)
            routingKey = bytesOrRoutingKey;

            if (typeof routingKeyOrMaxRetries === "number") {
                maxRetries = routingKeyOrMaxRetries;
            }
        }

        // Without explicit bytes, the scope reuses the client's cached request.
        // It lives in module-private storage, so acquisition happens through a
        // helper that never hands the bytes back here.
        if (!explicitBytes && !hasScopeConnectionRequest(client)) {
            throw new Error(
                "Client has no connection request available for a scope. " +
                    "Ensure it was created via GlideClient.createClient() (or GlideClusterClient.createClient()) and is connected.",
            );
        }

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

        let backoffMs = 10;

        for (let i = 0; i < maxRetries; i++) {
            const scopeId = tryAcquireScope(
                client,
                clientId,
                routingSlot,
                explicitBytes,
            );

            if (scopeId >= 0) {
                return new IsolatedScope(scopeId, clientId);
            }

            // Exponential backoff
            await new Promise((resolve) => setTimeout(resolve, backoffMs));
            backoffMs = Math.min(backoffMs * 2, 500);
        }

        throw new Error(
            "Could not acquire scoped connection after retries. " +
                "All scope connections may be in use or connection creation failed.",
        );
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
