/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 *
 * Integration tests for Client-Instance Pooling (Node.js).
 * Parameterized over cluster/standalone for parity with Java/Python/Go.
 */

import { afterAll, beforeAll, describe, expect, it } from "@jest/globals";
import { ClientPool, GlideClient, GlideClientConfiguration } from "..";
import { ValkeyCluster } from "../../utils/TestUtils.js";
import {
    getClientConfigurationOption,
    getServerVersion,
    parseEndpoints,
} from "./TestUtilities";

const TIMEOUT = 30_000;
const CLUSTER_TIMEOUT = 60_000;

function makeKey(clusterMode: boolean, prefix: string): string {
    const uid = Math.random().toString(36).slice(2, 10);
    return clusterMode
        ? `{pool-test}-${prefix}-${uid}`
        : `pool-test-${prefix}-${uid}`;
}

async function waitForIdle(
    pool: ClientPool,
    minIdle = 1,
    timeoutMs = 15_000,
): Promise<void> {
    const deadline = Date.now() + timeoutMs;

    while (pool.idleCount < minIdle && Date.now() < deadline) {
        await new Promise((r) => setTimeout(r, 50));
    }
}

describe("ClientPool", () => {
    let standaloneCluster: ValkeyCluster;

    let standaloneConfig: GlideClientConfiguration;

    beforeAll(async () => {
        const standaloneAddresses = global.STAND_ALONE_ENDPOINT as string;
        standaloneCluster = standaloneAddresses
            ? await ValkeyCluster.initFromExistingCluster(
                  false,
                  parseEndpoints(standaloneAddresses),
                  getServerVersion,
              )
            : await ValkeyCluster.createCluster(false, 1, 1, getServerVersion);

        standaloneConfig = getClientConfigurationOption(
            standaloneCluster.getAddresses(),
            0, // RESP3
            { requestTimeout: 5000 },
        ) as GlideClientConfiguration;
    }, CLUSTER_TIMEOUT);

    afterAll(async () => {
        await standaloneCluster?.close();
    }, TIMEOUT);

    // Negative tests — validate error surfacing
    describe("error handling", () => {
        it(
            "create with bad address rejects immediately",
            async () => {
                const badConfig: GlideClientConfiguration = {
                    addresses: [{ host: "192.0.2.1", port: 1 }], // RFC 5737 TEST-NET
                    requestTimeout: 2000,
                };
                await expect(
                    ClientPool.create(badConfig, { maxSize: 1, minIdle: 1 }),
                ).rejects.toThrow();
            },
            TIMEOUT,
        );

        it(
            "create with cluster mode against standalone rejects",
            async () => {
                await expect(
                    ClientPool.create(standaloneConfig, {
                        maxSize: 1,
                        minIdle: 1,
                        clusterMode: true,
                    }),
                ).rejects.toThrow();
            },
            TIMEOUT,
        );
    });

    // Build parameterized modes dynamically inside the describe
    describe("standalone mode", () => {
        it(
            "create pool and warmup",
            async () => {
                const pool = await ClientPool.create(standaloneConfig, {
                    maxSize: 3,
                    minIdle: 1,
                });
                await waitForIdle(pool);
                expect(pool.idleCount).toBeGreaterThanOrEqual(1);
                expect(pool.totalCount).toBeGreaterThanOrEqual(1);
                expect(pool.isClosed).toBe(false);
                pool.close();
            },
            TIMEOUT,
        );

        it(
            "borrow and execute commands",
            async () => {
                const pool = await ClientPool.create(standaloneConfig, {
                    maxSize: 3,
                    minIdle: 1,
                });
                await waitForIdle(pool);
                const key = makeKey(false, "borrow");

                const result = await pool.borrow(async (client) => {
                    await client.set(key, "hello");
                    return await client.get(key);
                });

                expect(result).toBe("hello");
                await pool.borrow(async (client) => {
                    await client.del([key]);
                });
                pool.close();
            },
            TIMEOUT,
        );

        it(
            "LIFO reuse",
            async () => {
                const pool = await ClientPool.create(standaloneConfig, {
                    maxSize: 3,
                    minIdle: 1,
                });
                await waitForIdle(pool);

                const client1 = await pool.acquire();
                await pool.release(client1);
                await new Promise((r) => setTimeout(r, 50));

                const client2 = await pool.acquire();
                await pool.release(client2);

                // Same client returned (LIFO)
                expect(client1.getClientId()).toBe(client2.getClientId());
                pool.close();
            },
            TIMEOUT,
        );

        it(
            "timeout on exhaustion",
            async () => {
                const pool = await ClientPool.create(standaloneConfig, {
                    maxSize: 1,
                    minIdle: 1,
                    acquireTimeoutS: 1,
                });
                await waitForIdle(pool);

                const client = await pool.acquire();
                await expect(pool.acquire(0.5)).rejects.toThrow(/exhausted/);
                await pool.release(client);
                pool.close();
            },
            TIMEOUT,
        );

        it(
            "close rejects acquire",
            async () => {
                const pool = await ClientPool.create(standaloneConfig, {
                    maxSize: 2,
                    minIdle: 1,
                });
                await waitForIdle(pool);
                pool.close();
                await expect(pool.acquire()).rejects.toThrow(/closed/);
            },
            TIMEOUT,
        );

        it(
            "concurrent access",
            async () => {
                const pool = await ClientPool.create(standaloneConfig, {
                    maxSize: 4,
                    minIdle: 2,
                    acquireTimeoutS: 10,
                });
                await waitForIdle(pool);

                const errors: Error[] = [];
                const tasks = Array.from({ length: 8 }, (_, i) =>
                    pool
                        .borrow(async (client) => {
                            const key = makeKey(false, `concurrent-${i}`);
                            await client.set(key, `task-${i}`);
                            const val = await client.get(key);
                            expect(val).toBe(`task-${i}`);
                            await client.del([key]);
                        })
                        .catch((e: Error) => errors.push(e)),
                );
                await Promise.all(tasks);
                expect(errors).toHaveLength(0);
                pool.close();
            },
            TIMEOUT,
        );

        it(
            "pubsub rejection",
            async () => {
                const badConfig = {
                    ...standaloneConfig,
                    pubsubSubscriptions: {
                        channelsAndPatterns: { 0: new Set(["ch"]) },
                        // eslint-disable-next-line @typescript-eslint/no-empty-function
                        callback: () => {},
                        context: null,
                    },
                };
                await expect(
                    // eslint-disable-next-line @typescript-eslint/no-explicit-any
                    ClientPool.create(badConfig as any, {
                        maxSize: 2,
                        minIdle: 1,
                    }),
                ).rejects.toThrow(/pubsub/);
            },
            TIMEOUT,
        );

        it(
            "metrics reflect state",
            async () => {
                const pool = await ClientPool.create(standaloneConfig, {
                    maxSize: 3,
                    minIdle: 2,
                });
                await waitForIdle(pool, 1);
                expect(pool.getMetrics().idle).toBeGreaterThanOrEqual(1);
                expect(pool.getMetrics().active).toBe(0);

                const client = await pool.acquire();
                expect(pool.getMetrics().active).toBe(1);
                await pool.release(client);
                pool.close();
            },
            TIMEOUT,
        );

        it(
            "release resets database state",
            async () => {
                const pool = await ClientPool.create(standaloneConfig, {
                    maxSize: 1,
                    minIdle: 1,
                });
                await waitForIdle(pool);
                const key = makeKey(false, "db-reset");

                await pool.borrow(async (client) => {
                    await (client as GlideClient).customCommand([
                        "SELECT",
                        "5",
                    ]);
                    await client.set(key, "on-db5");
                });
                await new Promise((r) => setTimeout(r, 50));

                await pool.borrow(async (client) => {
                    const result = await client.get(key);
                    expect(result).toBeNull();
                });

                await pool.borrow(async (client) => {
                    await (client as GlideClient).customCommand([
                        "SELECT",
                        "5",
                    ]);
                    await client.del([key]);
                });
                pool.close();
            },
            TIMEOUT,
        );

        it(
            "pool publish (concurrent)",
            async () => {
                const pool = await ClientPool.create(standaloneConfig, {
                    maxSize: 4,
                    minIdle: 2,
                    acquireTimeoutS: 10,
                });
                await waitForIdle(pool, 1);

                const channel = `pool-pub-${Math.random().toString(36).slice(2, 10)}`;
                const errors: Error[] = [];
                const tasks = Array.from({ length: 4 }, (_, i) =>
                    pool
                        .borrow(async (client) => {
                            for (let j = 0; j < 5; j++) {
                                await (client as GlideClient).customCommand([
                                    "PUBLISH",
                                    channel,
                                    `msg-${i}-${j}`,
                                ]);
                            }
                        })
                        .catch((e: Error) => errors.push(e)),
                );
                await Promise.all(tasks);
                expect(errors).toHaveLength(0);
                pool.close();
            },
            TIMEOUT,
        );

        it(
            "blocking commands don't stall other pool clients",
            async () => {
                const pool = await ClientPool.create(standaloneConfig, {
                    maxSize: 2,
                    minIdle: 2,
                    acquireTimeoutS: 10,
                });
                await waitForIdle(pool, 2);
                const key = makeKey(false, "blocking");

                const blockingPromise = pool.borrow(async (client) => {
                    return await client.blpop([key], 30);
                });
                await new Promise((r) => setTimeout(r, 50));

                const start = Date.now();
                const result = await pool.borrow(async (client) => {
                    const k = makeKey(false, "nonblocking");
                    await client.set(k, "fast");
                    const val = await client.get(k);
                    await client.del([k]);
                    await client.lpush(key, ["unblock"]);
                    return val;
                });
                const elapsed = Date.now() - start;

                expect(result).toBe("fast");
                expect(elapsed).toBeLessThan(1000);
                const blockingResult = await blockingPromise;
                expect(blockingResult).not.toBeNull();
                pool.close();
            },
            TIMEOUT,
        );

        it(
            "blocking customCommand is not reclaimed by abandon monitor",
            async () => {
                // Use a short abandon timeout to prove blocking detection works
                const pool = await ClientPool.create(standaloneConfig, {
                    maxSize: 2,
                    minIdle: 2,
                    acquireTimeoutS: 10,
                    abandonTimeoutMs: 1000, // 1 second
                });
                await waitForIdle(pool, 2);
                const key = makeKey(false, "blpop-custom");

                try {
                    // BLPOP via customCommand — previously invisible to TS Proxy
                    const blockingPromise = pool.borrow(async (client) => {
                        return await (client as GlideClient).customCommand([
                            "BLPOP",
                            key,
                            "3", // 3 second server timeout
                        ]);
                    });

                    // Wait longer than abandon timeout
                    await new Promise((r) => setTimeout(r, 1500));

                    // Unblock by pushing a value from another client
                    await pool.borrow(async (client) => {
                        await client.lpush(key, ["done"]);
                    });

                    // Blocking command should complete successfully (not be killed)
                    const result = await blockingPromise;
                    expect(result).toEqual([key, "done"]);
                } finally {
                    pool.close();
                }
            },
            TIMEOUT,
        );

        it(
            "abandon detection discards inactive clients",
            async () => {
                const pool = await ClientPool.create(standaloneConfig, {
                    maxSize: 2,
                    minIdle: 1,
                    abandonTimeoutMs: 1000, // 1 second
                });
                await waitForIdle(pool, 1);

                try {
                    // Acquire and hold without sending commands
                    const client = await pool.acquire();
                    const originalId = client.getClientId();
                    expect(pool.activeCount).toBe(1);

                    // Wait for abandon monitor to discard (timeout + scan interval)
                    await new Promise((r) => setTimeout(r, 2000));

                    // Trigger drain by acquiring again
                    const client2 = await pool.acquire();

                    // Original client should have been discarded
                    expect(client2.getClientId()).not.toBe(originalId);
                    await pool.release(client2);
                } finally {
                    pool.close();
                }
            },
            TIMEOUT,
        );

        it(
            "does not exceed maxSize during concurrent acquire while release is resetting",
            async () => {
                const pool = await ClientPool.create(standaloneConfig, {
                    maxSize: 1,
                    minIdle: 1,
                });
                await waitForIdle(pool, 1);

                try {
                    const firstClient = await pool.acquire();

                    // release() is async (state reset). Concurrent acquires should
                    // not create extra clients beyond maxSize.
                    const releasePromise = pool.release(firstClient);
                    const secondClient = await pool.acquire();
                    await releasePromise;

                    expect(pool.getMetrics().total).toBeLessThanOrEqual(1);
                    await pool.release(secondClient);
                } finally {
                    pool.close();
                }
            },
            TIMEOUT,
        );
    });
});
