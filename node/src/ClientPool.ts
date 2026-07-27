/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 *
 * Client-Instance Pool for Node.js.
 *
 * Pool clients are real GlideClient/GlideClusterClient instances with the full
 * command API. Commands flow through the standard N-API sendCommand() path
 * (same as standalone clients) with complete type fidelity.
 *
 * State reset (DISCARD + UNWATCH + SELECT) runs on the actual GlideClient
 * connection during release, ensuring the next borrower gets a clean state.
 *
 * Scope commands (WATCH/MULTI/EXEC via IsolatedScope) go through the Rust
 * glide-core::scope module for per-connection state tracking and slot pinning.
 *
 * ## When to use a pool in Node.js
 *
 * - **Blocking commands** (`BLPOP`, `BRPOP`, `XREAD BLOCK`) — holds one
 *   connection, letting other pool clients serve requests unblocked.
 * - **WATCH/MULTI/EXEC transactions** (use `IsolatedScope` for dedicated connections).
 * - **Large response deserialization** — parallel Rust worker threads decode concurrently.
 * - **TCP head-of-line blocking** avoidance under high throughput.
 * - **`worker_threads`** environments where each thread needs its own connection.
 */

import type { BaseClient, BaseClientConfiguration } from "./BaseClient";
import { GlideClient } from "./GlideClient";
import type { GlideClientConfiguration } from "./GlideClient";
import { GlideClusterClient } from "./GlideClusterClient";
import type { GlideClusterClientConfiguration } from "./GlideClusterClient";

/** Re-export the pool client type (full command set). */
export type PoolClient = BaseClient;

/**
 * Configuration for the client-instance pool.
 */
export interface PoolConfig {
    /** Maximum number of clients in the pool. Default: 10. */
    maxSize?: number;
    /** Minimum idle clients to pre-warm at creation. Default: 1. */
    minIdle?: number;
    /** Maximum time to wait when pool is exhausted (seconds). Default: 5. */
    acquireTimeoutS?: number;
    /**
     * Maximum inactivity time for a borrowed client before the pool reclaims it (ms).
     * The timer resets on every command sent. The abandon monitor skips clients
     * executing blocking commands (BLPOP, XREAD BLOCK, etc.).
     * Set to 0 to disable abandon detection. Default: 300000 (5 minutes).
     */
    abandonTimeoutMs?: number;
    /** Whether to create cluster clients. Default: auto-detected from config. */
    clusterMode?: boolean;
}

/**
 * Pool metrics snapshot.
 */
export interface ClientPoolMetrics {
    idle: number;
    active: number;
    total: number;
}

interface PoolEntry {
    client: BaseClient;
    id: number;
}

interface Waiter {
    resolve: (entry: PoolEntry) => void;
    reject: (err: Error) => void;
    timer: ReturnType<typeof setTimeout>;
}

/**
 * Client-instance pool managing real GlideClient / GlideClusterClient instances.
 *
 * Commands go through the standard N-API sendCommand() path — same as
 * standalone clients — with complete type fidelity (Buffer, number, arrays, maps).
 */
export class ClientPool {
    private idle: PoolEntry[] = []; // LIFO stack
    private active = new Map<number, PoolEntry>();
    private closed = false;
    private nextId = 1;
    private readonly maxSize: number;
    private readonly minIdle: number;
    private readonly acquireTimeoutMs: number;
    private readonly abandonTimeoutMs: number;
    private readonly isCluster: boolean;
    private readonly clientConfig: BaseClientConfiguration;
    private waiters: Waiter[] = [];
    /** Tracks last command activity per active entry id. */
    private lastActivity = new Map<number, number>();
    /** Tracks entries currently executing a blocking command. */
    private blockingEntries = new Set<number>();
    /** Abandon monitor interval handle. */
    private abandonMonitor: ReturnType<typeof setInterval> | null = null;

    private constructor(
        maxSize: number,
        minIdle: number,
        acquireTimeoutMs: number,
        abandonTimeoutMs: number,
        isCluster: boolean,
        clientConfig: BaseClientConfiguration,
    ) {
        this.maxSize = maxSize;
        this.minIdle = minIdle;
        this.acquireTimeoutMs = acquireTimeoutMs;
        this.abandonTimeoutMs = abandonTimeoutMs;
        this.isCluster = isCluster;
        this.clientConfig = clientConfig;

        // Start abandon monitor if enabled
        if (abandonTimeoutMs > 0) {
            this.abandonMonitor = setInterval(
                () => this.scanForAbandoned(),
                abandonTimeoutMs / 2,
            );
            // Unref so it doesn't prevent Node.js from exiting
            if (this.abandonMonitor.unref) {
                this.abandonMonitor.unref();
            }
        }
    }

    /**
     * Create a new client-instance pool.
     *
     * Warms up `minIdle` real GlideClient instances in the background.
     */
    static async create(
        clientConfig: BaseClientConfiguration,
        poolConfig?: PoolConfig,
    ): Promise<ClientPool> {
        const maxSize = poolConfig?.maxSize ?? 10;
        const minIdle = poolConfig?.minIdle ?? 1;
        const acquireTimeoutS = poolConfig?.acquireTimeoutS ?? 5;

        // Reject pubsub subscriptions
        const cfg = clientConfig as
            GlideClientConfiguration | GlideClusterClientConfiguration;

        if ("pubsubSubscriptions" in cfg && cfg.pubsubSubscriptions) {
            throw new Error(
                "Pool clients cannot have pubsub subscriptions configured. " +
                    "Use the main client's pubsub API for subscriptions.",
            );
        }

        // Cluster mode: must be explicitly specified (no auto-detection).
        // Default is standalone (GlideClient). Pass clusterMode: true for GlideClusterClient.
        const isCluster = poolConfig?.clusterMode ?? false;

        const pool = new ClientPool(
            maxSize,
            minIdle,
            acquireTimeoutS * 1000,
            poolConfig?.abandonTimeoutMs ?? 300_000,
            isCluster,
            clientConfig,
        );

        // Warm up: create the first client synchronously.
        // This validates connectivity — if the config is wrong (bad address,
        // wrong cluster mode), the error propagates to the caller immediately
        // instead of being discovered at acquire() time.
        await pool.createAndAddClient();

        // Remaining warmup is best-effort background
        for (let i = 1; i < minIdle; i++) {
            pool.createAndAddClient().catch(() => {
                /* background warmup — first client already validated connectivity */
            });
        }

        return pool;
    }

    /**
     * Acquire a client from the pool.
     *
     * Returns a real GlideClient or GlideClusterClient with the full command API.
     */
    async acquire(timeout?: number): Promise<BaseClient> {
        if (this.closed) {
            throw new Error("Pool is closed");
        }

        // Fast path: LIFO pop from idle stack
        const entry = this.idle.pop();

        if (entry) {
            this.active.set(entry.id, entry);
            this.lastActivity.set(entry.id, Date.now());
            return this.wrapClient(entry);
        }

        // If below max size, create a new client
        if (this.idle.length + this.active.size < this.maxSize) {
            const newEntry = await this.createEntry();
            this.active.set(newEntry.id, newEntry);
            this.lastActivity.set(newEntry.id, Date.now());
            return this.wrapClient(newEntry);
        }

        // At max size — wait for a release
        const timeoutMs = timeout ? timeout * 1000 : this.acquireTimeoutMs;

        return new Promise<BaseClient>((resolve, reject) => {
            const timer = setTimeout(() => {
                const idx = this.waiters.findIndex((w) => w.reject === reject);

                if (idx >= 0) this.waiters.splice(idx, 1);
                reject(
                    new Error(
                        `Pool exhausted: could not acquire client within ${timeoutMs / 1000}s`,
                    ),
                );
            }, timeoutMs);

            this.waiters.push({
                resolve: (entry: PoolEntry) => {
                    clearTimeout(timer);
                    this.active.set(entry.id, entry);
                    this.lastActivity.set(entry.id, Date.now());
                    resolve(this.wrapClient(entry));
                },
                reject,
                timer,
            });
        });
    }

    /**
     * Release a client back to the pool.
     *
     * Resets connection state (DISCARD + UNWATCH + SELECT) on the actual
     * GlideClient connection before making it idle again.
     */
    async release(client: BaseClient): Promise<void> {
        // Find entry by client reference
        let entry: PoolEntry | undefined;

        for (const [id, e] of this.active) {
            if (e.client === client) {
                entry = e;
                this.active.delete(id);
                break;
            }
        }

        if (!entry) return;

        this.lastActivity.delete(entry.id);
        this.blockingEntries.delete(entry.id);

        // State reset on the actual connection
        await this.resetClientState(entry.client);

        // If waiters are queued, hand directly to next waiter (FIFO fairness)
        if (this.waiters.length > 0 && !this.closed) {
            const waiter = this.waiters.shift()!;
            waiter.resolve(entry);
            return;
        }

        // Return to idle stack (LIFO for connection reuse locality)
        if (!this.closed) {
            this.idle.push(entry);
        }
    }

    /**
     * Borrow a client, execute callback, then auto-release.
     *
     * The callback receives a real GlideClient/GlideClusterClient with full API.
     */
    async borrow<T>(
        fn: (client: BaseClient) => Promise<T>,
        timeout?: number,
    ): Promise<T> {
        const client = await this.acquire(timeout);

        try {
            return await fn(client);
        } finally {
            await this.release(client);
        }
    }

    /** Get pool metrics. */
    getMetrics(): ClientPoolMetrics {
        return {
            idle: this.idle.length,
            active: this.active.size,
            total: this.idle.length + this.active.size,
        };
    }

    get idleCount(): number {
        return this.idle.length;
    }

    get activeCount(): number {
        return this.active.size;
    }

    get totalCount(): number {
        return this.idle.length + this.active.size;
    }

    get isClosed(): boolean {
        return this.closed;
    }

    /** Close the pool and all managed clients. */
    close(): void {
        if (!this.closed) {
            this.closed = true;

            // Stop abandon monitor
            if (this.abandonMonitor) {
                clearInterval(this.abandonMonitor);
                this.abandonMonitor = null;
            }

            // Reject all waiters
            for (const waiter of this.waiters) {
                clearTimeout(waiter.timer);
                waiter.reject(new Error("Pool is closed"));
            }

            this.waiters = [];

            // Close all clients
            for (const entry of this.idle) {
                entry.client.close();
            }

            for (const entry of this.active.values()) {
                entry.client.close();
            }

            this.idle = [];
            this.active.clear();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INTERNAL
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Commands that block on the server — abandon monitor skips these.
     * Keep in sync with is_blocking_command() in glide-core/src/client/mod.rs.
     */
    private static readonly BLOCKING_COMMANDS = new Set([
        "blpop",
        "brpop",
        "blmove",
        "bzpopmax",
        "bzpopmin",
        "brpoplpush",
        "blmpop",
        "bzmpop",
        "wait",
        "waitaof",
        "xread",
        "xreadgroup",
    ]);

    /**
     * Wrap a client with a Proxy that refreshes activity on every method call
     * and marks blocking commands so the abandon monitor skips them.
     */
    private wrapClient(entry: PoolEntry): BaseClient {
        const pool = this;

        return new Proxy(entry.client, {
            get(target, prop, receiver) {
                const value = Reflect.get(target, prop, receiver);

                if (typeof value !== "function" || typeof prop !== "string") {
                    return value;
                }

                // Return a wrapper that refreshes activity and tracks blocking
                return (...args: unknown[]) => {
                    pool.lastActivity.set(entry.id, Date.now());

                    const isBlocking =
                        ClientPool.BLOCKING_COMMANDS.has(prop.toLowerCase());

                    if (isBlocking) {
                        pool.blockingEntries.add(entry.id);
                    }

                    const result = (value as Function).apply(target, args);

                    // If result is a Promise, unmark blocking when it resolves
                    if (
                        isBlocking &&
                        result &&
                        typeof result.then === "function"
                    ) {
                        result.then(
                            () => pool.blockingEntries.delete(entry.id),
                            () => pool.blockingEntries.delete(entry.id),
                        );
                    } else if (isBlocking) {
                        pool.blockingEntries.delete(entry.id);
                    }

                    return result;
                };
            },
        });
    }

    private async createEntry(): Promise<PoolEntry> {
        const client = this.isCluster
            ? await GlideClusterClient.createClient(
                  this.clientConfig as GlideClusterClientConfiguration,
              )
            : await GlideClient.createClient(
                  this.clientConfig as GlideClientConfiguration,
              );

        return { client, id: this.nextId++ };
    }

    private async createAndAddClient(): Promise<void> {
        const entry = await this.createEntry();

        if (this.closed) {
            entry.client.close();
            return;
        }

        // If a waiter is queued, deliver directly
        if (this.waiters.length > 0) {
            const waiter = this.waiters.shift()!;
            waiter.resolve(entry);
        } else {
            this.idle.push(entry);
        }
    }

    /** Reset connection state: DISCARD + UNWATCH + SELECT <configured_db>. */
    private async resetClientState(client: BaseClient): Promise<void> {
        try {
            // Cast to access customCommand (available on GlideClient/GlideClusterClient)
            const c = client as unknown as {
                customCommand: (args: string[]) => Promise<unknown>;
            };

            const noop = () => {
                /* ignore errors from commands that aren't applicable */
            };

            await c.customCommand(["DISCARD"]).catch(noop);
            await c.customCommand(["UNWATCH"]).catch(noop);

            const configuredDb = this.clientConfig.databaseId ?? 0;
            await c
                .customCommand(["SELECT", configuredDb.toString()])
                .catch(noop);
        } catch {
            // Connection broken — client will reconnect on next use
        }
    }

    /**
     * Notify the pool that a command was sent on a borrowed client.
     * Resets the abandon inactivity timer for this client.
     * Call this from application code if using raw acquire() with long-held clients.
     */
    notifyActivity(client: BaseClient): void {
        for (const [id, entry] of this.active) {
            if (entry.client === client) {
                this.lastActivity.set(id, Date.now());
                return;
            }
        }
    }

    /** Scan active entries and force-release any that exceed abandon timeout. */
    private scanForAbandoned(): void {
        if (this.closed) return;

        const now = Date.now();
        const abandoned: PoolEntry[] = [];

        for (const [id, entry] of this.active) {
            // Skip clients currently executing a blocking command
            if (this.blockingEntries.has(id)) continue;

            const lastActive = this.lastActivity.get(id) ?? 0;

            if (now - lastActive > this.abandonTimeoutMs) {
                abandoned.push(entry);
            }
        }

        for (const entry of abandoned) {
            console.warn(
                `[valkey-glide pool] Abandon detection: client ${entry.id} exceeded ` +
                    `inactivity timeout (${this.abandonTimeoutMs}ms) — force-releasing`,
            );
            this.active.delete(entry.id);
            this.lastActivity.delete(entry.id);

            // Async release (state reset + return to idle)
            this.resetClientState(entry.client)
                .then(() => {
                    if (this.closed) {
                        entry.client.close();
                        return;
                    }

                    // Hand to waiter or return to idle
                    if (this.waiters.length > 0) {
                        const waiter = this.waiters.shift()!;
                        waiter.resolve(entry);
                    } else {
                        this.idle.push(entry);
                    }
                })
                .catch(() => {
                    // Reset failed — discard the client
                    entry.client.close();
                });
        }
    }
}
