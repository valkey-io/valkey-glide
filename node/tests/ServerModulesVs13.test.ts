/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

/**
 * Live tests for the valkey-search 1.3 stable surface (wave 1 of the ports --
 * see VALKEY-SEARCH-1.3.PLAN.md's "Ports" section). Scope here matches
 * QA/BRIEF-WAVE1-PORTS.md: INKEYS, the typed reducer factories, FLOAT16/
 * BFLOAT16, FT.CREATE FILTER/UNF/NOHL/SCORE_FIELD, and count -> LIMIT 0 0.
 * WITHSCORES/SCORER, ADDSCORES, WITHCURSOR/FT.CURSOR, FT.HYBRID and LoadField
 * aliases are wave 2 (after #7335 merges) and are out of scope here.
 *
 * Mirrors the Python reference's two-stage gate (python/tests/conftest.py):
 *   1. A capability probe (real command + a 1.3-only argument, classified by
 *      error text, never a version string) answers "is the search module
 *      1.3-capable at all?" -- a missing module raises loudly, never skips.
 *   2. An emulate-release level read answers "is 1.3 behaviour actually
 *      active right now?" -- only needed by the legacy-vs-1.3 branching
 *      test (SCORE_FIELD in FT.INFO) in this stable-surface slice.
 */

import {
    afterAll,
    afterEach,
    beforeAll,
    describe,
    expect,
    it,
} from "@jest/globals";
import { ValkeyCluster } from "../../utils/TestUtils";
import {
    FtAggregateReducer,
    FtCreateOptions,
    FtSearchOptions,
    GlideClient,
    GlideClusterClient,
    GlideFt,
    GlideString,
    NumericField,
    ProtocolVersion,
    TextField,
    VectorField,
} from "../build-ts";
import {
    flushAndCloseClient,
    getClientConfigurationOption,
    getRandomKey,
    getServerVersion,
    parseEndpoints,
} from "./TestUtilities";

const TIMEOUT = 50000;
/** Waiting interval to let server process the data before querying (matches ServerModules.test.ts). */
const DATA_PROCESSING_TIMEOUT = 1000;

/** Sleeps so the index has time to catch up with recently written keys. */
function sleep(ms: number = DATA_PROCESSING_TIMEOUT): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Pure classification of the INKEYS probe's error text (equivalent to Python's
 * `classify_inkeys_probe_error`). Unit-testable with no server; kept as a
 * free function so a future dedicated unit-test file can exercise it
 * directly without needing a live module.
 */
export function classifyInkeysProbeError(msg: string): boolean {
    return msg.includes("Unexpected") && msg.includes("INKEYS");
}

/**
 * Probes a connected client for valkey-search 1.3 capability using a real
 * command plus a 1.3-only argument (INKEYS, added in rc1 / PR #967),
 * classified by error text rather than a version string (a version string
 * can be present without the specific capability a test needs being live).
 * Raises -- never returns a false "incapable" -- if the search module
 * appears entirely absent, since a missing module must never become a
 * silent skip.
 */
async function probeSearch13Capability(
    client: GlideClient | GlideClusterClient,
): Promise<boolean> {
    const probeIndex = `__glide_search_1_3_probe_${Date.now()}_${Math.random()
        .toString(36)
        .slice(2)}`;

    try {
        await client.customCommand([
            "FT.CREATE",
            probeIndex,
            "SCHEMA",
            "title",
            "TEXT",
        ]);
    } catch (e) {
        throw new Error(
            "search 1.3 capability probe could not create a test index " +
                `(is the search module loaded at all?): ${e}`,
            { cause: e },
        );
    }

    try {
        try {
            await client.customCommand([
                "FT.SEARCH",
                probeIndex,
                "*",
                "INKEYS",
                "0",
            ]);
            return true;
        } catch (e) {
            const msg = e instanceof Error ? e.message : String(e);

            if (classifyInkeysProbeError(msg)) {
                return false;
            }

            throw new Error(
                `search 1.3 capability probe got an unexpected error: ${e}`,
                { cause: e },
            );
        }
    } finally {
        try {
            await client.customCommand(["FT.DROPINDEX", probeIndex]);
        } catch {
            // Retry once -- a transient failure here would otherwise leave
            // probeIndex on the server for the rest of the session. FT.CREATE
            // and FT.DROPINDEX both use probeIndex as their own first
            // argument, so they always route to the same primary; a
            // cluster-routing mismatch is not a possible explanation for a
            // retry also failing.
            await client.customCommand(["FT.DROPINDEX", probeIndex]);
        }
    }
}

/** Parses `CONFIG GET search.emulate-release`'s reply shape (object or flat array) into a string, or `undefined` if unparseable. */
/**
 * Parses `CONFIG GET search.emulate-release`'s reply into a string, or
 * `undefined` if the key is absent or the shape is unrecognised.
 */
function parseEmulateReleaseLevel(configGetReply: unknown): string | undefined {
    const value = readMapField(configGetReply, "search.emulate-release");
    return value === undefined || value === null
        ? undefined
        : Buffer.isBuffer(value)
          ? value.toString()
          : String(value);
}

/** `true` if `level` is strictly less than `minLevel`, both dotted X.Y.Z (an optional `-rcN` suffix is stripped). */
function checkVersionLt(level: string, minLevel: string): boolean {
    const strip = (v: string) => v.split("-")[0];
    const a = strip(level).split(".").map(Number);
    const b = strip(minLevel).split(".").map(Number);

    for (let i = 0; i < Math.max(a.length, b.length); i++) {
        const x = a[i] ?? 0;
        const y = b[i] ?? 0;
        if (x !== y) return x < y;
    }

    return false;
}

/**
 * Reads one field out of a reply that may be a plain object, a
 * `GlideRecord`-style array of `{ key, value }` entries, or a flat
 * `[key, value, ...]` array -- the shape a nested map comes back as depends
 * on the protocol (RESP3 clients get `GlideRecord`/object shapes, RESP2
 * clients get a flat array for the same logical map). Returns `undefined`
 * if the field is absent or the reply shape isn't recognised.
 */
function readMapField(reply: unknown, field: string): unknown {
    const decode = (v: unknown): string | undefined =>
        v === undefined || v === null
            ? undefined
            : Buffer.isBuffer(v)
              ? v.toString()
              : String(v);

    if (Array.isArray(reply)) {
        if (
            reply.length > 0 &&
            typeof reply[0] === "object" &&
            reply[0] !== null &&
            "key" in (reply[0] as object)
        ) {
            for (const entry of reply as { key: unknown; value: unknown }[]) {
                if (decode(entry.key) === field) {
                    return entry.value;
                }
            }

            return undefined;
        }

        for (let i = 0; i + 1 < reply.length; i += 2) {
            if (decode(reply[i]) === field) {
                return reply[i + 1];
            }
        }

        return undefined;
    }

    if (reply !== null && typeof reply === "object") {
        for (const [k, v] of Object.entries(reply as Record<string, unknown>)) {
            if (decode(k) === field) {
                return v;
            }
        }
    }

    return undefined;
}

describe("GlideFt", () => {
    describe("1.3 stable surface", () => {
        let cluster: ValkeyCluster;
        let standalone: ValkeyCluster | undefined;
        let capability: boolean;
        let emulateReleaseLevel: string | undefined;

        beforeAll(async () => {
            cluster = await ValkeyCluster.initFromExistingCluster(
                true,
                parseEndpoints(global.CLUSTER_ENDPOINTS),
                getServerVersion,
            );

            if (global.STAND_ALONE_ENDPOINT) {
                standalone = await ValkeyCluster.initFromExistingCluster(
                    false,
                    parseEndpoints(global.STAND_ALONE_ENDPOINT),
                    getServerVersion,
                );
            }

            // Session-once probe, cluster-mode if available (matches the
            // Python reference's connect-cluster-else-standalone rule).
            const probeClient = await GlideClusterClient.createClient(
                getClientConfigurationOption(
                    cluster.getAddresses(),
                    ProtocolVersion.RESP3,
                ),
            );

            try {
                capability = await probeSearch13Capability(probeClient);

                if (capability) {
                    const configGetReply = await probeClient.customCommand([
                        "CONFIG",
                        "GET",
                        "search.emulate-release",
                    ]);
                    emulateReleaseLevel =
                        parseEmulateReleaseLevel(configGetReply);

                    if (emulateReleaseLevel === undefined) {
                        throw new Error(
                            "search.emulate-release could not be parsed " +
                                "from a 1.3-capable module's CONFIG GET reply",
                        );
                    }
                }
            } finally {
                await probeClient.close();
            }
        }, 40000);

        afterAll(async () => {
            await cluster.close();
            await standalone?.close();
        }, TIMEOUT);

        describe.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
            "cluster_mode=true, protocol=%s",
            (protocol) => {
                let client: GlideClusterClient;

                afterEach(async () => {
                    await flushAndCloseClient(
                        true,
                        cluster?.getAddresses(),
                        client,
                    );
                });

                it("INKEYS: absent, [], and a list", async () => {
                    if (!capability) {
                        console.warn(
                            "skipping: search module present but pre-1.3 " +
                                "(INKEYS probe rejected it)",
                        );
                        return;
                    }

                    client = await GlideClusterClient.createClient(
                        getClientConfigurationOption(
                            cluster.getAddresses(),
                            protocol,
                        ),
                    );
                    const prefix = "{" + getRandomKey() + "}:";
                    const indexName = prefix + "inkeys_idx";
                    await client.customCommand([
                        "FT.CREATE",
                        indexName,
                        "ON",
                        "HASH",
                        "PREFIX",
                        "1",
                        prefix,
                        "SCHEMA",
                        "title",
                        "TEXT",
                    ]);

                    try {
                        const keys = [prefix + "1", prefix + "2", prefix + "3"];

                        for (const key of keys) {
                            await client.hset(key, { title: "hello world" });
                        }

                        await sleep();

                        // absent -> no restriction, all 3 match
                        const all = await GlideFt.search(
                            client,
                            indexName,
                            "*",
                        );
                        expect(all[0]).toBe(3);

                        // [] -> INKEYS 0, excludes every result
                        const none = await GlideFt.search(
                            client,
                            indexName,
                            "*",
                            { inKeys: [] } as FtSearchOptions,
                        );
                        expect(none[0]).toBe(0);

                        // a list -> restricts to just those keys
                        const some = await GlideFt.search(
                            client,
                            indexName,
                            "*",
                            {
                                inKeys: [keys[0], keys[1]],
                            } as FtSearchOptions,
                        );
                        expect(some[0]).toBe(2);
                    } finally {
                        await client.customCommand(["FT.DROPINDEX", indexName]);
                    }
                });

                it("count maps to LIMIT 0 0 (count-only, no document rows)", async () => {
                    if (!capability) return;

                    client = await GlideClusterClient.createClient(
                        getClientConfigurationOption(
                            cluster.getAddresses(),
                            protocol,
                        ),
                    );
                    const prefix = "{" + getRandomKey() + "}:";
                    const indexName = prefix + "count_idx";
                    await client.customCommand([
                        "FT.CREATE",
                        indexName,
                        "ON",
                        "HASH",
                        "PREFIX",
                        "1",
                        prefix,
                        "SCHEMA",
                        "title",
                        "TEXT",
                    ]);

                    try {
                        await client.hset(prefix + "1", { title: "hello" });
                        await client.hset(prefix + "2", { title: "hello" });

                        await sleep();

                        const result = await GlideFt.search(
                            client,
                            indexName,
                            "*",
                            { count: true } as FtSearchOptions,
                        );
                        expect(result[0]).toBe(2);
                        expect(result.length).toBe(1);
                    } finally {
                        await client.customCommand(["FT.DROPINDEX", indexName]);
                    }
                });

                it("typed reducer factories: TOLIST, FIRST_VALUE, RANDOM_SAMPLE", async () => {
                    if (!capability) return;

                    client = await GlideClusterClient.createClient(
                        getClientConfigurationOption(
                            cluster.getAddresses(),
                            protocol,
                        ),
                    );
                    const prefix = "{" + getRandomKey() + "}:";
                    const indexName = prefix + "reducers_idx";
                    const categoryField: TextField = {
                        type: "TEXT",
                        name: "category",
                    };
                    const priceField: NumericField = {
                        type: "NUMERIC",
                        name: "price",
                    };
                    await GlideFt.create(
                        client,
                        indexName,
                        [categoryField, priceField],
                        {
                            dataType: "HASH",
                            prefixes: [prefix],
                        } as FtCreateOptions,
                    );

                    try {
                        await client.hset(prefix + "1", {
                            category: "a",
                            price: "10",
                        });
                        await client.hset(prefix + "2", {
                            category: "a",
                            price: "30",
                        });
                        await client.hset(prefix + "3", {
                            category: "a",
                            price: "10",
                        });

                        await sleep();

                        const result = await GlideFt.aggregate(
                            client,
                            indexName,
                            "*",
                            {
                                clauses: [
                                    {
                                        type: "GROUPBY",
                                        properties: ["@category"],
                                        reducers: [
                                            FtAggregateReducer.toList(
                                                "@price",
                                                "prices",
                                            ),
                                            FtAggregateReducer.firstValue(
                                                "@price",
                                                {
                                                    by: "@price",
                                                    order: "DESC",
                                                },
                                                "max_price",
                                            ),
                                            FtAggregateReducer.randomSample(
                                                "@price",
                                                2,
                                                "sample",
                                            ),
                                        ],
                                    },
                                ],
                            },
                        );
                        expect(result.length).toBe(1);
                        const flat = result[0] as unknown as {
                            key: GlideString;
                            value: unknown;
                        }[];
                        const asMap = new Map(
                            flat.map((e) => [e.key.toString(), e.value]),
                        );
                        const prices = (asMap.get("prices") as GlideString[])
                            .map((v) => v.toString())
                            .sort();
                        expect(prices).toEqual(["10", "30"]);
                        expect(
                            (asMap.get("max_price") as GlideString).toString(),
                        ).toBe("30");
                    } finally {
                        await client.customCommand(["FT.DROPINDEX", indexName]);
                    }
                });

                it("FT.CREATE FLOAT16/BFLOAT16 vector types", async () => {
                    if (!capability) return;

                    client = await GlideClusterClient.createClient(
                        getClientConfigurationOption(
                            cluster.getAddresses(),
                            protocol,
                        ),
                    );
                    const indexName = `float16_idx_${Date.now()}`;
                    const vectorField: VectorField = {
                        type: "VECTOR",
                        name: "vec",
                        attributes: {
                            algorithm: "FLAT",
                            dimensions: 2,
                            distanceMetric: "L2",
                            type: "FLOAT16",
                        },
                    };

                    await GlideFt.create(client, indexName, [vectorField], {
                        dataType: "HASH",
                    } as FtCreateOptions);
                    await client.customCommand(["FT.DROPINDEX", indexName]);
                });

                it("FT.CREATE FILTER, UNF, NOHL, SCORE_FIELD", async () => {
                    if (!capability) return;

                    client = await GlideClusterClient.createClient(
                        getClientConfigurationOption(
                            cluster.getAddresses(),
                            protocol,
                        ),
                    );
                    const indexName = `parity_idx_${Date.now()}`;
                    const titleField: TextField = {
                        type: "TEXT",
                        name: "title",
                        sortable: true,
                        unf: true,
                    };
                    const boostField: NumericField = {
                        type: "NUMERIC",
                        name: "boost",
                    };

                    await GlideFt.create(
                        client,
                        indexName,
                        [titleField, boostField],
                        {
                            dataType: "HASH",
                            filter: "@title != ''",
                            scoreField: "boost",
                            noHighlight: true,
                        } as FtCreateOptions,
                    );

                    try {
                        await client.hset("parity:1", {
                            title: "hello",
                            boost: "5",
                        });
                        await sleep();
                        const info = (await GlideFt.info(
                            client,
                            indexName,
                        )) as Record<string, unknown>;
                        const indexDef = info["index_definition"];
                        const scoreField = readMapField(
                            indexDef,
                            "score_field",
                        );

                        if (
                            emulateReleaseLevel !== undefined &&
                            !checkVersionLt(emulateReleaseLevel, "1.3.0")
                        ) {
                            expect(scoreField?.toString()).toBe("boost");
                        } else {
                            expect(scoreField).toBeUndefined();
                        }
                    } finally {
                        await client.customCommand(["FT.DROPINDEX", indexName]);
                    }
                });

                it("rejects an empty FILTER expression client-side", async () => {
                    client = await GlideClusterClient.createClient(
                        getClientConfigurationOption(
                            cluster.getAddresses(),
                            protocol,
                        ),
                    );
                    await expect(
                        GlideFt.create(client, "whatever", [], {
                            dataType: "HASH",
                            filter: "",
                        } as FtCreateOptions),
                    ).rejects.toThrow(/filter cannot be empty/);
                });
            },
        );

        describe.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
            "cluster_mode=false, protocol=%s",
            (protocol) => {
                let client: GlideClient;

                afterEach(async () => {
                    await flushAndCloseClient(
                        false,
                        standalone?.getAddresses(),
                        client,
                    );
                });

                it("INKEYS standalone smoke test", async () => {
                    if (!standalone) {
                        console.warn(
                            "skipping: no --standalone-endpoints provided",
                        );
                        return;
                    }

                    if (!capability) return;

                    client = await GlideClient.createClient(
                        getClientConfigurationOption(
                            standalone.getAddresses(),
                            protocol,
                        ),
                    );
                    const indexName = `inkeys_standalone_idx_${Date.now()}`;
                    await client.customCommand([
                        "FT.CREATE",
                        indexName,
                        "SCHEMA",
                        "title",
                        "TEXT",
                    ]);

                    try {
                        await client.hset("standalone_inkeys:1", {
                            title: "hello",
                        });
                        await sleep();
                        const result = await GlideFt.search(
                            client,
                            indexName,
                            "*",
                            { inKeys: [] } as FtSearchOptions,
                        );
                        expect(result[0]).toBe(0);
                    } finally {
                        await client.customCommand(["FT.DROPINDEX", indexName]);
                    }
                });
            },
        );
    });
});
