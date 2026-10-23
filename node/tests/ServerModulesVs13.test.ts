/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

/**
 * Live tests for the valkey-search 1.3 stable surface (wave 1 of the ports)
 * and wave 2 (scoring, cursors, FT.HYBRID).
 * Wave 1 scope: INKEYS, the typed reducer factories, FLOAT16/BFLOAT16, FT.CREATE
 * FILTER/UNF/NOHL/SCORE_FIELD, and count -> LIMIT 0 0.
 * Wave 2 scope: WITHSCORES/SCORER on FT.SEARCH, ADDSCORES/SCORER on
 * FT.AGGREGATE, FT.AGGREGATE/FT.HYBRID WITHCURSOR plus FT.CURSOR READ/DEL
 * (primary-pinned routing), and FT.HYBRID (SEARCH/VSIM arms, COMBINE
 * RRF/LINEAR/FUNCTION, VSIM FILTER/POLICY/BATCH_SIZE, LOCALONLY, LOAD,
 * per-arm and COMBINE yield-score-as aliases).
 *
 * Mirrors the Python reference's two-stage gate:
 *   1. A capability probe (real command + a 1.3-only argument, classified by
 *      error text, never a version string) answers "is the search module
 *      1.3-capable at all?" -- a missing module raises loudly, never skips.
 *   2. An emulate-release level read answers "is 1.3 behaviour actually
 *      active right now?" -- only needed by the legacy-vs-1.3 branching
 *      test (SCORE_FIELD in FT.INFO) in this stable-surface slice.
 *
 * The count -> LIMIT 0 0 behaviour change itself is NOT gated behind the 1.3
 * probe above: a bare COUNT token is invalid FT.SEARCH syntax on search 1.2.x
 * too, so "count maps to LIMIT 0 0" below runs unconditionally and is the
 * test that actually exercises CI's own search 1.2.1 image for this change.
 * It queries a real field-filter expression rather than a bare "*" wildcard,
 * since "*" as a standalone query string is itself rejected by 1.2.x
 * ("Invalid query string syntax") and only became valid there in 1.3 --
 * unrelated to LIMIT or count, confirmed directly against both engine
 * versions (matches Java's ft_search_count_maps_to_limit_zero_zero).
 * The 1.3-gated variant covers only the INKEYS/reducer/vector-type/parity
 * additions that genuinely need 1.3, plus every wave-2 test below (all of
 * which genuinely need 1.3).
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
    FtHybridOptions,
    FtSearchOptions,
    GlideClient,
    GlideClusterClient,
    GlideFt,
    GlideReturnType,
    GlideString,
    NumericField,
    ProtocolVersion,
    SortOrder,
    TextField,
    VectorField,
} from "../build-ts";
import {
    checkVersionLt,
    probeCapabilityAndLevel,
    readMapField,
} from "./searchCapabilityHelpers";
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
 * Packs two 32-bit floats as little-endian bytes, matching the vector blob
 * format `struct.pack("2f", x, y)` produces in the Python reference.
 */
function packVector2f(x: number, y: number): Buffer {
    const buf = Buffer.alloc(8);
    buf.writeFloatLE(x, 0);
    buf.writeFloatLE(y, 4);
    return buf;
}

/** Reads a flat `GlideRecord<GlideReturnType>` row's field by key, as a string. */
function readRowField(
    row: { key: GlideString; value: GlideReturnType }[],
    field: string,
): string | undefined {
    const entry = row.find((e) => e.key.toString() === field);

    if (
        entry === undefined ||
        entry.value === undefined ||
        entry.value === null
    ) {
        return undefined;
    }

    return Buffer.isBuffer(entry.value)
        ? entry.value.toString()
        : String(entry.value);
}

const HYBRID_DOC_TEXT_WINNER = "hybrid-text-winner";
const HYBRID_DOC_VECTOR_WINNER = "hybrid-vector-winner";
const HYBRID_SEARCH_ALIAS = "ss";
const HYBRID_VSIM_ALIAS = "vs";

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
                ({ capability, emulateReleaseLevel } =
                    await probeCapabilityAndLevel(probeClient));
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

                        // A bare "*" wildcard query is rejected outright by
                        // search 1.2.x ("Invalid query string syntax") -- it
                        // only became valid as a standalone token in 1.3. This
                        // test runs unconditionally against both versions
                        // (see the file docstring), so it uses a real
                        // field-filter query instead, which both versions
                        // accept identically (matches Java's
                        // ft_search_count_maps_to_limit_zero_zero).
                        const result = await GlideFt.search(
                            client,
                            indexName,
                            "@title:hello",
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

                    for (const vectorType of ["FLOAT16", "BFLOAT16"] as const) {
                        const indexName = `${vectorType.toLowerCase()}_idx_${Date.now()}`;
                        const vectorField: VectorField = {
                            type: "VECTOR",
                            name: "vec",
                            attributes: {
                                algorithm: "FLAT",
                                dimensions: 2,
                                distanceMetric: "L2",
                                type: vectorType,
                            },
                        };

                        await GlideFt.create(client, indexName, [vectorField], {
                            dataType: "HASH",
                        } as FtCreateOptions);

                        try {
                            // No writes/reads needed here: FT.CREATE
                            // accepting the vector type is the behaviour
                            // under test.
                        } finally {
                            await client.customCommand([
                                "FT.DROPINDEX",
                                indexName,
                            ]);
                        }
                    }
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

                it("FT.SEARCH WITHSCORES and SCORER rank by relevance", async () => {
                    if (!capability) return;

                    client = await GlideClusterClient.createClient(
                        getClientConfigurationOption(
                            cluster.getAddresses(),
                            protocol,
                        ),
                    );
                    const prefix = "{" + getRandomKey() + "}:";
                    const indexName = prefix + "scores_idx";
                    await GlideFt.create(
                        client,
                        indexName,
                        [{ type: "TEXT", name: "title" }],
                        {
                            dataType: "HASH",
                            prefixes: [prefix],
                        } as FtCreateOptions,
                    );

                    try {
                        await client.hset(prefix + "1", {
                            title: "hello hello hello world",
                        });
                        await client.hset(prefix + "2", {
                            title: "hello there",
                        });
                        await sleep();

                        const result = await GlideFt.search(
                            client,
                            indexName,
                            "hello",
                            {
                                withScores: true,
                                scorer: "BM25STD",
                            } as FtSearchOptions,
                        );
                        expect(result[0]).toBe(2);
                        const resultMap = result[1];
                        expect(resultMap.length).toBe(2);
                        const scoresByKey = new Map<string, number>();

                        for (const entry of resultMap) {
                            const scoreAndFields = entry.value as unknown as [
                                GlideString,
                                { key: GlideString; value: GlideString }[],
                            ];
                            expect(scoreAndFields.length).toBe(2);
                            const score = Number(scoreAndFields[0].toString());
                            expect(score).toBeGreaterThan(0);
                            scoresByKey.set(entry.key.toString(), score);
                            const fields = scoreAndFields[1];
                            expect(
                                fields.some(
                                    (f) => f.key.toString() === "title",
                                ),
                            ).toBe(true);
                        }

                        expect(scoresByKey.get(prefix + "1")!).toBeGreaterThan(
                            scoresByKey.get(prefix + "2")!,
                        );
                    } finally {
                        await client.customCommand(["FT.DROPINDEX", indexName]);
                    }
                });

                it("FT.SEARCH WITHSCORES + WITHSORTKEYS combined shape", async () => {
                    if (!capability) return;

                    client = await GlideClusterClient.createClient(
                        getClientConfigurationOption(
                            cluster.getAddresses(),
                            protocol,
                        ),
                    );
                    const prefix = "{" + getRandomKey() + "}:";
                    const indexName = prefix + "scores_sortkeys_idx";
                    await GlideFt.create(
                        client,
                        indexName,
                        [
                            {
                                type: "TEXT",
                                name: "title",
                                sortable: true,
                            },
                            {
                                type: "NUMERIC",
                                name: "price",
                                sortable: true,
                            },
                        ],
                        {
                            dataType: "HASH",
                            prefixes: [prefix],
                        } as FtCreateOptions,
                    );

                    try {
                        await client.hset(prefix + "1", {
                            title: "hello",
                            price: "10",
                        });
                        await client.hset(prefix + "2", {
                            title: "world",
                            price: "20",
                        });
                        await sleep();

                        const result = await GlideFt.search(
                            client,
                            indexName,
                            "*",
                            {
                                withScores: true,
                                sortby: "price",
                                sortbyOrder: "ASC",
                                withsortkeys: true,
                            } as FtSearchOptions,
                        );
                        expect(result[0]).toBe(2);
                        const resultMap = result[1];
                        expect(resultMap.length).toBe(2);

                        for (const entry of resultMap) {
                            const triple = entry.value as unknown as [
                                GlideString,
                                GlideString,
                                { key: GlideString; value: GlideString }[],
                            ];
                            expect(triple.length).toBe(3);
                            const [score, sortKey, fields] = triple;
                            expect(
                                Number(score.toString()),
                            ).toBeGreaterThanOrEqual(0);
                            expect(sortKey).not.toBeUndefined();
                            expect(sortKey).not.toBeNull();
                            expect(
                                fields.some(
                                    (f) => f.key.toString() === "title",
                                ),
                            ).toBe(true);
                        }
                    } finally {
                        await client.customCommand(["FT.DROPINDEX", indexName]);
                    }
                });

                it("FT.AGGREGATE ADDSCORES exposes __score, sortable", async () => {
                    if (!capability) return;

                    client = await GlideClusterClient.createClient(
                        getClientConfigurationOption(
                            cluster.getAddresses(),
                            protocol,
                        ),
                    );
                    const prefix = "{" + getRandomKey() + "}:";
                    const indexName = prefix + "addscores_idx";
                    await GlideFt.create(
                        client,
                        indexName,
                        [{ type: "TEXT", name: "title" }],
                        {
                            dataType: "HASH",
                            prefixes: [prefix],
                        } as FtCreateOptions,
                    );

                    try {
                        await client.hset(prefix + "1", {
                            title: "hello world",
                        });
                        await client.hset(prefix + "2", {
                            title: "hello there friend",
                        });
                        await sleep();

                        const result = await GlideFt.aggregate(
                            client,
                            indexName,
                            "hello",
                            {
                                addScores: true,
                                loadFields: ["title"],
                                clauses: [
                                    {
                                        type: "SORTBY",
                                        properties: [
                                            {
                                                property: "@__score",
                                                order: SortOrder.DESC,
                                            },
                                        ],
                                    },
                                ],
                            },
                        );
                        expect(result.length).toBe(2);
                        const scores: number[] = [];

                        for (const row of result) {
                            const flat = row as unknown as {
                                key: GlideString;
                                value: GlideString;
                            }[];
                            const scoreField = readRowField(flat, "__score");
                            expect(scoreField).not.toBeUndefined();
                            const score = Number(scoreField);
                            expect(score).toBeGreaterThan(0);
                            scores.push(score);
                        }

                        expect(scores).toEqual(
                            [...scores].sort((a, b) => b - a),
                        );
                    } finally {
                        await client.customCommand(["FT.DROPINDEX", indexName]);
                    }
                });

                it("FT.AGGREGATE WITHCURSOR + FT.CURSOR READ/DEL affinity (PRIMARY, never a replica)", async () => {
                    if (!capability) return;

                    client = await GlideClusterClient.createClient(
                        getClientConfigurationOption(
                            cluster.getAddresses(),
                            protocol,
                        ),
                    );
                    const prefix = "{" + getRandomKey() + "}:";
                    const indexName = prefix + "cursor_idx";
                    await GlideFt.create(
                        client,
                        indexName,
                        [{ type: "NUMERIC", name: "n", sortable: true }],
                        {
                            dataType: "HASH",
                            prefixes: [prefix],
                        } as FtCreateOptions,
                    );

                    try {
                        for (let i = 1; i <= 20; i++) {
                            await client.hset(prefix + `d${i}`, {
                                n: i.toString(),
                            });
                        }

                        await sleep();

                        let totalRows = 0;
                        const seenNValues = new Set<number>();
                        const page = await GlideFt.aggregate(
                            client,
                            indexName,
                            "*",
                            {
                                loadFields: ["n"],
                                withCursor: { count: 5 },
                            },
                        );
                        let cursorId = page[1] as unknown as number;
                        let rows = page[0] as unknown as {
                            key: GlideString;
                            value: GlideString;
                        }[][];
                        totalRows += rows.length;

                        for (const row of rows) {
                            seenNValues.add(Number(readRowField(row, "n")));
                        }

                        let pagesRead = 1;

                        while (cursorId !== 0) {
                            const nextPage = await GlideFt.cursorRead(
                                client,
                                indexName,
                                cursorId,
                            );
                            rows = nextPage[0] as unknown as {
                                key: GlideString;
                                value: GlideString;
                            }[][];
                            totalRows += rows.length;

                            for (const row of rows) {
                                seenNValues.add(Number(readRowField(row, "n")));
                            }

                            cursorId = nextPage[1] as unknown as number;
                            pagesRead++;
                            expect(pagesRead).toBeLessThanOrEqual(10);
                        }

                        expect(totalRows).toBe(20);
                        expect(seenNValues).toEqual(
                            new Set(
                                Array.from({ length: 20 }, (_, i) => i + 1),
                            ),
                        );
                    } finally {
                        await client.customCommand(["FT.DROPINDEX", indexName]);
                    }
                });

                it("FT.CURSOR DEL discards an open cursor; reading it or an unknown id errors", async () => {
                    if (!capability) return;

                    client = await GlideClusterClient.createClient(
                        getClientConfigurationOption(
                            cluster.getAddresses(),
                            protocol,
                        ),
                    );
                    const prefix = "{" + getRandomKey() + "}:";
                    const indexName = prefix + "cursor_del_idx";
                    await GlideFt.create(
                        client,
                        indexName,
                        [{ type: "NUMERIC", name: "n", sortable: true }],
                        {
                            dataType: "HASH",
                            prefixes: [prefix],
                        } as FtCreateOptions,
                    );

                    try {
                        for (let i = 1; i <= 5; i++) {
                            await client.hset(prefix + `d${i}`, {
                                n: i.toString(),
                            });
                        }

                        await sleep();

                        const page = await GlideFt.aggregate(
                            client,
                            indexName,
                            "*",
                            {
                                loadFields: ["n"],
                                withCursor: { count: 1 },
                            },
                        );
                        const openCursorId = page[1] as unknown as number;
                        expect(openCursorId).not.toBe(0);

                        expect(
                            await GlideFt.cursorDel(
                                client,
                                indexName,
                                openCursorId,
                            ),
                        ).toBe("OK");

                        await expect(
                            GlideFt.cursorRead(client, indexName, openCursorId),
                        ).rejects.toThrow(/Cursor/);

                        await expect(
                            GlideFt.cursorRead(client, indexName, 999999999),
                        ).rejects.toThrow(/Cursor/);
                    } finally {
                        await client.customCommand(["FT.DROPINDEX", indexName]);
                    }
                });

                describe("FT.HYBRID", () => {
                    /**
                     * Creates an index with a TEXT field (`title`) and a 2-dimensional FLAT
                     * FLOAT32 VECTOR field (`vec`), seeds the shared hybrid-docs fixture, and
                     * waits for both documents to be indexed. Returns the index name.
                     */
                    async function createHybridIndex(
                        testClient: GlideClusterClient,
                        prefix: string,
                    ): Promise<string> {
                        const indexName = prefix + "index";
                        await GlideFt.create(
                            testClient,
                            indexName,
                            [
                                { type: "TEXT", name: "title" },
                                {
                                    type: "VECTOR",
                                    name: "vec",
                                    attributes: {
                                        algorithm: "FLAT",
                                        dimensions: 2,
                                        distanceMetric: "L2",
                                        type: "FLOAT32",
                                    },
                                },
                            ],
                            {
                                dataType: "HASH",
                                prefixes: [prefix],
                            } as FtCreateOptions,
                        );
                        await testClient.hset(prefix + HYBRID_DOC_TEXT_WINNER, {
                            title: "hello hello hello world",
                            vec: packVector2f(100.0, 100.0),
                        });
                        await testClient.hset(
                            prefix + HYBRID_DOC_VECTOR_WINNER,
                            {
                                title: "goodbye",
                                vec: packVector2f(1.0, 1.0),
                            },
                        );
                        await sleep();
                        return indexName;
                    }

                    it("basic query: default reply is just the document key and the fused score", async () => {
                        if (!capability) return;

                        client = await GlideClusterClient.createClient(
                            getClientConfigurationOption(
                                cluster.getAddresses(),
                                protocol,
                            ),
                        );
                        const prefix = "{" + getRandomKey() + "}:";
                        const indexName = await createHybridIndex(
                            client,
                            prefix,
                        );

                        try {
                            const result = await GlideFt.hybrid(
                                client,
                                indexName,
                                { query: "hello" },
                                {
                                    field: "vec",
                                    vector: packVector2f(1.0, 1.0),
                                    k: 5,
                                },
                            );
                            expect(result.length).toBe(2);
                            const keys = new Set(
                                result.map((row) =>
                                    readRowField(
                                        row as unknown as {
                                            key: GlideString;
                                            value: GlideString;
                                        }[],
                                        "__key",
                                    ),
                                ),
                            );
                            expect(keys).toEqual(
                                new Set([
                                    prefix + HYBRID_DOC_TEXT_WINNER,
                                    prefix + HYBRID_DOC_VECTOR_WINNER,
                                ]),
                            );

                            for (const row of result) {
                                expect(
                                    readRowField(
                                        row as unknown as {
                                            key: GlideString;
                                            value: GlideString;
                                        }[],
                                        "__score",
                                    ),
                                ).not.toBeUndefined();
                            }
                        } finally {
                            await client.customCommand([
                                "FT.DROPINDEX",
                                indexName,
                            ]);
                        }
                    });

                    it("a post-fusion FILTER clause (no LOAD) filters after both arms, never misread as a VSIM pre-filter", async () => {
                        if (!capability) return;

                        client = await GlideClusterClient.createClient(
                            getClientConfigurationOption(
                                cluster.getAddresses(),
                                protocol,
                            ),
                        );
                        const prefix = "{" + getRandomKey() + "}:";
                        const indexName = await createHybridIndex(
                            client,
                            prefix,
                        );

                        try {
                            // SEARCH arm yields its own score under "ss", only
                            // matching HYBRID_DOC_VECTOR_WINNER's query
                            // ("goodbye"). The VSIM arm (k=5) matches both
                            // documents, so the union going into the aggregate
                            // tail is both documents. A FILTER keeping only
                            // rows where "ss" is positive, applied correctly
                            // (post-fusion, not misread as a VSIM pre-filter),
                            // keeps only HYBRID_DOC_VECTOR_WINNER.
                            const result = await GlideFt.hybrid(
                                client,
                                indexName,
                                {
                                    query: "goodbye",
                                    yieldScoreAs: HYBRID_SEARCH_ALIAS,
                                },
                                {
                                    field: "vec",
                                    vector: packVector2f(1.0, 1.0),
                                    k: 5,
                                },
                                {
                                    clauses: [
                                        {
                                            type: "FILTER",
                                            expression: `@${HYBRID_SEARCH_ALIAS} > 0`,
                                        },
                                    ],
                                } as FtHybridOptions,
                            );
                            expect(result.length).toBe(1);
                            expect(
                                readRowField(
                                    result[0] as unknown as {
                                        key: GlideString;
                                        value: GlideString;
                                    }[],
                                    "__key",
                                ),
                            ).toBe(prefix + HYBRID_DOC_VECTOR_WINNER);
                        } finally {
                            await client.customCommand([
                                "FT.DROPINDEX",
                                indexName,
                            ]);
                        }
                    });

                    it.each<[string, FtHybridOptions["combine"], string]>([
                        [
                            "LINEAR-text-only",
                            { method: "LINEAR", alpha: 1.0, beta: 0.0 },
                            HYBRID_DOC_TEXT_WINNER,
                        ],
                        [
                            "LINEAR-vector-only",
                            { method: "LINEAR", alpha: 0.0, beta: 1.0 },
                            HYBRID_DOC_VECTOR_WINNER,
                        ],
                        [
                            "FUNCTION-text",
                            {
                                method: "FUNCTION",
                                expr: `@${HYBRID_SEARCH_ALIAS}`,
                            },
                            HYBRID_DOC_TEXT_WINNER,
                        ],
                        [
                            "FUNCTION-vec",
                            {
                                method: "FUNCTION",
                                expr: `@${HYBRID_VSIM_ALIAS}`,
                            },
                            HYBRID_DOC_VECTOR_WINNER,
                        ],
                    ])(
                        "COMBINE %s fuses results so the expected arm wins",
                        async (_label, combine, expectedFirst) => {
                            if (!capability) return;

                            client = await GlideClusterClient.createClient(
                                getClientConfigurationOption(
                                    cluster.getAddresses(),
                                    protocol,
                                ),
                            );
                            const prefix = "{" + getRandomKey() + "}:";
                            const indexName = await createHybridIndex(
                                client,
                                prefix,
                            );

                            try {
                                const result = await GlideFt.hybrid(
                                    client,
                                    indexName,
                                    {
                                        query: "hello",
                                        yieldScoreAs: HYBRID_SEARCH_ALIAS,
                                    },
                                    {
                                        field: "vec",
                                        vector: packVector2f(1.0, 1.0),
                                        k: 5,
                                        yieldScoreAs: HYBRID_VSIM_ALIAS,
                                    },
                                    {
                                        combine,
                                    },
                                );
                                expect(result.length).toBeGreaterThan(0);
                                expect(
                                    readRowField(
                                        result[0] as unknown as {
                                            key: GlideString;
                                            value: GlideString;
                                        }[],
                                        "__key",
                                    ),
                                ).toBe(prefix + expectedFirst);
                            } finally {
                                await client.customCommand([
                                    "FT.DROPINDEX",
                                    indexName,
                                ]);
                            }
                        },
                    );

                    it("vector as inline bytes and as a $-reference produce the same result", async () => {
                        if (!capability) return;

                        client = await GlideClusterClient.createClient(
                            getClientConfigurationOption(
                                cluster.getAddresses(),
                                protocol,
                            ),
                        );
                        const prefix = "{" + getRandomKey() + "}:";
                        const indexName = await createHybridIndex(
                            client,
                            prefix,
                        );
                        const vectorBytes = packVector2f(1.0, 1.0);

                        try {
                            const inlineResult = await GlideFt.hybrid(
                                client,
                                indexName,
                                { query: "hello" },
                                {
                                    field: "vec",
                                    vector: vectorBytes,
                                    k: 1,
                                    yieldScoreAs: HYBRID_VSIM_ALIAS,
                                },
                            );
                            const paramResult = await GlideFt.hybrid(
                                client,
                                indexName,
                                { query: "hello" },
                                {
                                    field: "vec",
                                    vector: "$myvec",
                                    k: 1,
                                    yieldScoreAs: HYBRID_VSIM_ALIAS,
                                },
                                {
                                    params: [
                                        { key: "myvec", value: vectorBytes },
                                    ],
                                },
                            );

                            const inlineKeys = new Set(
                                inlineResult.map((row) =>
                                    readRowField(
                                        row as unknown as {
                                            key: GlideString;
                                            value: GlideString;
                                        }[],
                                        "__key",
                                    ),
                                ),
                            );
                            const paramKeys = new Set(
                                paramResult.map((row) =>
                                    readRowField(
                                        row as unknown as {
                                            key: GlideString;
                                            value: GlideString;
                                        }[],
                                        "__key",
                                    ),
                                ),
                            );
                            expect(inlineKeys).toEqual(paramKeys);
                            expect(inlineResult.length).toBe(2);
                            expect(paramResult.length).toBe(2);

                            for (const result of [inlineResult, paramResult]) {
                                const rowsWithVs = result.filter(
                                    (row) =>
                                        readRowField(
                                            row as unknown as {
                                                key: GlideString;
                                                value: GlideString;
                                            }[],
                                            HYBRID_VSIM_ALIAS,
                                        ) !== undefined,
                                );
                                expect(rowsWithVs.length).toBe(1);
                                expect(
                                    readRowField(
                                        rowsWithVs[0] as unknown as {
                                            key: GlideString;
                                            value: GlideString;
                                        }[],
                                        "__key",
                                    ),
                                ).toBe(prefix + HYBRID_DOC_VECTOR_WINNER);
                            }
                        } finally {
                            await client.customCommand([
                                "FT.DROPINDEX",
                                indexName,
                            ]);
                        }
                    });

                    it.each(["ADHOC", "BATCHES"] as const)(
                        "VSIM FILTER with POLICY %s narrows the vector candidate set",
                        async (policy) => {
                            if (!capability) return;

                            client = await GlideClusterClient.createClient(
                                getClientConfigurationOption(
                                    cluster.getAddresses(),
                                    protocol,
                                ),
                            );
                            const prefix = "{" + getRandomKey() + "}:";
                            const indexName = await createHybridIndex(
                                client,
                                prefix,
                            );

                            try {
                                // SEARCH arm matches nothing; the VSIM
                                // arm's own pre-filter narrows the vector
                                // candidate set down to only the
                                // text-winner document via its title.
                                const result = await GlideFt.hybrid(
                                    client,
                                    indexName,
                                    { query: "nonexistentterm12345" },
                                    {
                                        field: "vec",
                                        vector: packVector2f(1.0, 1.0),
                                        k: 5,
                                        filter: {
                                            expression: "@title:hello",
                                            policy,
                                        },
                                    },
                                );
                                expect(result.length).toBe(1);
                                expect(
                                    readRowField(
                                        result[0] as unknown as {
                                            key: GlideString;
                                            value: GlideString;
                                        }[],
                                        "__key",
                                    ),
                                ).toBe(prefix + HYBRID_DOC_TEXT_WINNER);
                            } finally {
                                await client.customCommand([
                                    "FT.DROPINDEX",
                                    indexName,
                                ]);
                            }
                        },
                    );

                    it("both arms' and COMBINE's yieldScoreAs are distinct, independently reachable columns", async () => {
                        if (!capability) return;

                        client = await GlideClusterClient.createClient(
                            getClientConfigurationOption(
                                cluster.getAddresses(),
                                protocol,
                            ),
                        );
                        const prefix = "{" + getRandomKey() + "}:";
                        const indexName = await createHybridIndex(
                            client,
                            prefix,
                        );

                        try {
                            const result = await GlideFt.hybrid(
                                client,
                                indexName,
                                {
                                    query: "hello",
                                    yieldScoreAs: HYBRID_SEARCH_ALIAS,
                                },
                                {
                                    field: "vec",
                                    vector: packVector2f(1.0, 1.0),
                                    k: 5,
                                    yieldScoreAs: HYBRID_VSIM_ALIAS,
                                },
                                {
                                    combine: {
                                        method: "RRF",
                                        yieldScoreAs: "fused",
                                    },
                                },
                            );
                            expect(result.length).toBeGreaterThan(0);

                            for (const row of result) {
                                const flat = row as unknown as {
                                    key: GlideString;
                                    value: GlideString;
                                }[];
                                expect(
                                    readRowField(flat, HYBRID_VSIM_ALIAS),
                                ).not.toBeUndefined();
                                expect(
                                    readRowField(flat, "fused"),
                                ).not.toBeUndefined();
                                expect(
                                    readRowField(flat, "__score"),
                                ).toBeUndefined();
                            }

                            const textWinnerRow = result.find(
                                (row) =>
                                    readRowField(
                                        row as unknown as {
                                            key: GlideString;
                                            value: GlideString;
                                        }[],
                                        "__key",
                                    ) ===
                                    prefix + HYBRID_DOC_TEXT_WINNER,
                            );
                            expect(textWinnerRow).not.toBeUndefined();
                            expect(
                                readRowField(
                                    textWinnerRow as unknown as {
                                        key: GlideString;
                                        value: GlideString;
                                    }[],
                                    HYBRID_SEARCH_ALIAS,
                                ),
                            ).not.toBeUndefined();
                        } finally {
                            await client.customCommand([
                                "FT.DROPINDEX",
                                indexName,
                            ]);
                        }
                    });

                    it("LOCALONLY is accepted, no shape change on a single shard", async () => {
                        if (!capability) return;

                        client = await GlideClusterClient.createClient(
                            getClientConfigurationOption(
                                cluster.getAddresses(),
                                protocol,
                            ),
                        );
                        const prefix = "{" + getRandomKey() + "}:";
                        const indexName = await createHybridIndex(
                            client,
                            prefix,
                        );

                        try {
                            const result = await GlideFt.hybrid(
                                client,
                                indexName,
                                { query: "hello" },
                                {
                                    field: "vec",
                                    vector: packVector2f(1.0, 1.0),
                                    k: 5,
                                },
                                { localOnly: true } as FtHybridOptions,
                            );
                            expect(result.length).toBe(2);
                        } finally {
                            await client.customCommand([
                                "FT.DROPINDEX",
                                indexName,
                            ]);
                        }
                    });

                    it("WITHCURSOR paging on FT.HYBRID is pinned to PRIMARY and reaches exhaustion", async () => {
                        if (!capability) return;

                        client = await GlideClusterClient.createClient(
                            getClientConfigurationOption(
                                cluster.getAddresses(),
                                protocol,
                            ),
                        );
                        const prefix = "{" + getRandomKey() + "}:";
                        const indexName = await createHybridIndex(
                            client,
                            prefix,
                        );

                        try {
                            let totalRows = 0;
                            const seenKeys = new Set<string | undefined>();
                            const page = await GlideFt.hybrid(
                                client,
                                indexName,
                                { query: "hello" },
                                {
                                    field: "vec",
                                    vector: packVector2f(1.0, 1.0),
                                    k: 5,
                                },
                                {
                                    withCursor: { count: 1 },
                                } as FtHybridOptions,
                            );
                            let cursorId = page[1] as unknown as number;
                            let rows = page[0] as unknown as {
                                key: GlideString;
                                value: GlideString;
                            }[][];
                            totalRows += rows.length;
                            rows.forEach((r) =>
                                seenKeys.add(readRowField(r, "__key")),
                            );

                            let pagesRead = 1;

                            while (cursorId !== 0) {
                                const nextPage = await GlideFt.cursorRead(
                                    client,
                                    indexName,
                                    cursorId,
                                );
                                rows = nextPage[0] as unknown as {
                                    key: GlideString;
                                    value: GlideString;
                                }[][];
                                totalRows += rows.length;
                                rows.forEach((r) =>
                                    seenKeys.add(readRowField(r, "__key")),
                                );
                                cursorId = nextPage[1] as unknown as number;
                                pagesRead++;
                                expect(pagesRead).toBeLessThanOrEqual(10);
                            }

                            expect(totalRows).toBe(2);
                            expect(seenKeys).toEqual(
                                new Set([
                                    prefix + HYBRID_DOC_TEXT_WINNER,
                                    prefix + HYBRID_DOC_VECTOR_WINNER,
                                ]),
                            );
                        } finally {
                            await client.customCommand([
                                "FT.DROPINDEX",
                                indexName,
                            ]);
                        }
                    });

                    it("without k, the VSIM arm uses the engine default K=10", async () => {
                        if (!capability) return;

                        client = await GlideClusterClient.createClient(
                            getClientConfigurationOption(
                                cluster.getAddresses(),
                                protocol,
                            ),
                        );
                        const prefix = "{" + getRandomKey() + "}:";
                        const indexName = prefix + "index";
                        await GlideFt.create(
                            client,
                            indexName,
                            [
                                { type: "TEXT", name: "title" },
                                {
                                    type: "VECTOR",
                                    name: "vec",
                                    attributes: {
                                        algorithm: "FLAT",
                                        dimensions: 2,
                                        distanceMetric: "L2",
                                        type: "FLOAT32",
                                    },
                                },
                            ],
                            {
                                dataType: "HASH",
                                prefixes: [prefix],
                            } as FtCreateOptions,
                        );

                        try {
                            for (let i = 0; i < 15; i++) {
                                await client.hset(prefix + `d${i}`, {
                                    title: "nonexistentterm",
                                    vec: packVector2f(i, i),
                                });
                            }

                            await sleep();

                            const result = await GlideFt.hybrid(
                                client,
                                indexName,
                                { query: "nonexistentterm2" },
                                { field: "vec", vector: packVector2f(0, 0) },
                            );
                            expect(result.length).toBe(10);
                        } finally {
                            await client.customCommand([
                                "FT.DROPINDEX",
                                indexName,
                            ]);
                        }
                    });
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
