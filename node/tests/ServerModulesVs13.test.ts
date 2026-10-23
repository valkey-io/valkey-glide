/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

/**
 * Live tests for the valkey-search 1.3 stable surface (wave 1 of the ports).
 * Scope: INKEYS, the typed reducer factories, FLOAT16/BFLOAT16, FT.CREATE
 * FILTER/UNF/NOHL/SCORE_FIELD, and count -> LIMIT 0 0. WITHSCORES/SCORER,
 * ADDSCORES, WITHCURSOR/FT.CURSOR, FT.HYBRID and LoadField aliases are wave 2
 * (after the Python reference PR merges) and are out of scope here.
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
 * additions that genuinely need 1.3.
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
