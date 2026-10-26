/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

/**
 * Pure argv-serialization tests for the valkey-search 1.3 port: the wave-1
 * stable surface (INKEYS, typed reducer factories, FLOAT16/BFLOAT16,
 * FILTER/UNF/NOHL/SCORE_FIELD, count -> LIMIT 0 0) plus wave 2's scoring
 * (WITHSCORES/SCORER/ADDSCORES), cursors (FT.AGGREGATE WITHCURSOR, FT.CURSOR
 * READ/DEL) and FT.HYBRID. No live server is needed: a mock client captures
 * the argv array `GlideFt` builds and passes to `customCommand`, mirroring
 * the Python reference's `test_options.py`, which calls `to_args()` directly
 * on each option object. Node's option-serialization helpers are internal
 * module functions rather than public methods, so a mock `customCommand` is
 * the equivalent seam here.
 */

import { describe, expect, it, jest } from "@jest/globals";
import {
    FtAggregateReducer,
    FtCreateOptions,
    FtSearchOptions,
    GlideClusterClient,
    GlideFt,
    TextField,
    VectorField,
} from "../build-ts";

/** A minimal mock satisfying only the `customCommand` surface `GlideFt` calls. */
function mockClient() {
    const customCommand = jest
        .fn<(...args: unknown[]) => Promise<unknown>>()
        .mockResolvedValue("OK");
    return {
        client: {
            customCommand,
        } as unknown as GlideClusterClient,
        customCommand,
    };
}

describe("GlideFt 1.3 argv serialization", () => {
    describe("FT.SEARCH", () => {
        it("omits INKEYS when inKeys is undefined", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.search(client, "idx", "*");
            const args = customCommand.mock.calls[0][0] as string[];
            expect(args).not.toContain("INKEYS");
        });

        it("sends INKEYS 0 for an explicit empty array", async () => {
            const { client, customCommand } = mockClient();
            const options: FtSearchOptions = { inKeys: [] };
            await GlideFt.search(client, "idx", "*", options);
            const args = customCommand.mock.calls[0][0] as string[];
            expect(args).toContain("INKEYS");
            expect(args[args.indexOf("INKEYS") + 1]).toBe("0");
        });

        it("sends INKEYS with a count and the listed keys", async () => {
            const { client, customCommand } = mockClient();
            const options: FtSearchOptions = { inKeys: ["key1", "key2"] };
            await GlideFt.search(client, "idx", "*", options);
            const args = customCommand.mock.calls[0][0] as string[];
            const i = args.indexOf("INKEYS");
            expect(args.slice(i, i + 4)).toEqual([
                "INKEYS",
                "2",
                "key1",
                "key2",
            ]);
        });

        it("maps count to LIMIT 0 0, not a bare COUNT token", async () => {
            const { client, customCommand } = mockClient();
            const options: FtSearchOptions = { count: true };
            await GlideFt.search(client, "idx", "*", options);
            const args = customCommand.mock.calls[0][0] as string[];
            expect(args).not.toContain("COUNT");
            const i = args.indexOf("LIMIT");
            expect(args.slice(i, i + 3)).toEqual(["LIMIT", "0", "0"]);
        });

        it("still emits an explicit limit normally", async () => {
            const { client, customCommand } = mockClient();
            const options: FtSearchOptions = {
                limit: { offset: 5, count: 10 },
            };
            await GlideFt.search(client, "idx", "*", options);
            const args = customCommand.mock.calls[0][0] as string[];
            const i = args.indexOf("LIMIT");
            expect(args.slice(i, i + 3)).toEqual(["LIMIT", "5", "10"]);
        });

        it("omits WITHSCORES and SCORER by default", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.search(client, "idx", "*");
            const args = customCommand.mock.calls[0][0] as string[];
            expect(args).not.toContain("WITHSCORES");
            expect(args).not.toContain("SCORER");
        });

        it("emits WITHSCORES when withScores is set", async () => {
            const { client, customCommand } = mockClient();
            const options: FtSearchOptions = { withScores: true };
            await GlideFt.search(client, "idx", "*", options);
            const args = customCommand.mock.calls[0][0] as string[];
            expect(args).toContain("WITHSCORES");
        });

        it("emits SCORER with its value", async () => {
            const { client, customCommand } = mockClient();
            const options: FtSearchOptions = { scorer: "BM25STD" };
            await GlideFt.search(client, "idx", "*", options);
            const args = customCommand.mock.calls[0][0] as string[];
            const i = args.indexOf("SCORER");
            expect(args.slice(i, i + 2)).toEqual(["SCORER", "BM25STD"]);
        });

        it("emits WITHSCORES before SCORER when both are set", async () => {
            const { client, customCommand } = mockClient();
            const options: FtSearchOptions = {
                withScores: true,
                scorer: "BM25STD",
            };
            await GlideFt.search(client, "idx", "*", options);
            const args = customCommand.mock.calls[0][0] as string[];
            const withScoresIdx = args.indexOf("WITHSCORES");
            const scorerIdx = args.indexOf("SCORER");
            expect(withScoresIdx).toBeGreaterThanOrEqual(0);
            expect(scorerIdx).toBeGreaterThan(withScoresIdx);
        });
    });

    describe("FT.AGGREGATE scoring and cursor", () => {
        it("omits ADDSCORES, SCORER, and WITHCURSOR by default", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.aggregate(client, "idx", "*");
            const args = customCommand.mock.calls[0][0] as string[];
            expect(args).not.toContain("ADDSCORES");
            expect(args).not.toContain("SCORER");
            expect(args).not.toContain("WITHCURSOR");
        });

        it("emits ADDSCORES when addScores is set", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.aggregate(client, "idx", "*", { addScores: true });
            const args = customCommand.mock.calls[0][0] as string[];
            expect(args).toContain("ADDSCORES");
        });

        it("emits SCORER with its value, after ADDSCORES and other clauses", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.aggregate(client, "idx", "*", {
                loadFields: ["price"],
                addScores: true,
                scorer: "BM25STD",
            });
            const args = customCommand.mock.calls[0][0] as string[];
            expect(args.slice(3)).toEqual([
                "LOAD",
                "1",
                "price",
                "ADDSCORES",
                "SCORER",
                "BM25STD",
            ]);
        });

        it("emits LOAD with a LoadField alias as a 3-token budget entry (field AS alias)", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.aggregate(client, "idx", "*", {
                loadFields: ["price", { fieldIdentifier: "title", alias: "t" }],
            });
            const args = customCommand.mock.calls[0][0] as string[];
            expect(args.slice(3, 10)).toEqual([
                "LOAD",
                "4",
                "price",
                "title",
                "AS",
                "t",
            ]);
        });

        it("omits LOAD entirely for an empty loadFields array (never LOAD 0)", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.aggregate(client, "idx", "*", { loadFields: [] });
            const args = customCommand.mock.calls[0][0] as string[];
            expect(args).not.toContain("LOAD");
        });

        it("emits a bare WITHCURSOR when FtWithCursor has no fields set", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.aggregate(client, "idx", "*", { withCursor: {} });
            const args = customCommand.mock.calls[0][0] as string[];
            expect(args).toContain("WITHCURSOR");
            expect(args).not.toContain("COUNT");
            expect(args).not.toContain("MAXIDLE");
        });

        it("emits WITHCURSOR COUNT n MAXIDLE ms in that order", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.aggregate(client, "idx", "*", {
                withCursor: { count: 10, maxidle: 5000 },
            });
            const args = customCommand.mock.calls[0][0] as string[];
            const i = args.indexOf("WITHCURSOR");
            expect(args.slice(i, i + 6)).toEqual([
                "WITHCURSOR",
                "COUNT",
                "10",
                "MAXIDLE",
                "5000",
            ]);
        });

        it("emits WITHCURSOR after LOAD and ADDSCORES", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.aggregate(client, "idx", "*", {
                loadFields: ["price"],
                addScores: true,
                withCursor: { count: 5 },
            });
            const args = customCommand.mock.calls[0][0] as string[];
            expect(args.slice(3)).toEqual([
                "LOAD",
                "1",
                "price",
                "ADDSCORES",
                "WITHCURSOR",
                "COUNT",
                "5",
            ]);
        });
    });

    describe("FT.CURSOR", () => {
        it("cursorRead builds FT.CURSOR READ with index and cursor id", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.cursorRead(client, "idx", 42);
            const args = customCommand.mock.calls[0][0] as string[];
            expect(args).toEqual(["FT.CURSOR", "READ", "idx", "42"]);
        });

        it("cursorRead appends COUNT when an override count is given", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.cursorRead(client, "idx", 42, { count: 3 });
            const args = customCommand.mock.calls[0][0] as string[];
            expect(args).toEqual([
                "FT.CURSOR",
                "READ",
                "idx",
                "42",
                "COUNT",
                "3",
            ]);
        });

        it("cursorDel builds FT.CURSOR DEL with index and cursor id", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.cursorDel(client, "idx", 42);
            const args = customCommand.mock.calls[0][0] as string[];
            expect(args).toEqual(["FT.CURSOR", "DEL", "idx", "42"]);
        });
    });

    describe("FT.HYBRID", () => {
        it("builds a bare SEARCH + VSIM query with a $-reference vector", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.hybrid(
                client,
                "idx",
                { query: "hello" },
                { field: "vec", vector: "$myvec" },
            );
            const args = customCommand.mock.calls[0][0] as string[];
            expect(args).toEqual([
                "FT.HYBRID",
                "idx",
                "SEARCH",
                "hello",
                "VSIM",
                "@vec",
                "$myvec",
            ]);
        });

        it("generates an internal $-param for a raw bytes vector, never treating it as a reference", async () => {
            const { client, customCommand } = mockClient();
            const vectorBytes = Buffer.from([0x24, 0x01, 0x02]); // starts with '$' byte, must still be raw data
            await GlideFt.hybrid(
                client,
                "idx",
                { query: "hello" },
                { field: "vec", vector: vectorBytes },
            );
            const args = customCommand.mock.calls[0][0] as string[];
            const vsimIdx = args.indexOf("VSIM");
            const generatedParamName = args[vsimIdx + 2] as string;
            expect(generatedParamName.startsWith("$")).toBe(true);
            expect(generatedParamName).not.toBe("$myvec");
            const paramsIdx = args.indexOf("PARAMS");
            expect(args[paramsIdx + 1]).toBe("2");
            expect(args[paramsIdx + 2]).toBe(generatedParamName.slice(1));
            expect(args[paramsIdx + 3]).toBe(vectorBytes);
        });

        it("a string vector not starting with $ also gets a generated param, never sent inline", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.hybrid(
                client,
                "idx",
                { query: "hello" },
                { field: "vec", vector: "rawvectorstring" },
            );
            const args = customCommand.mock.calls[0][0] as string[];
            const vsimIdx = args.indexOf("VSIM");
            const generatedParamName = args[vsimIdx + 2] as string;
            expect(generatedParamName.startsWith("$")).toBe(true);
            const paramsIdx = args.indexOf("PARAMS");
            expect(args[paramsIdx + 3]).toBe("rawvectorstring");
        });

        it("merges a generated VSIM param with caller-supplied params", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.hybrid(
                client,
                "idx",
                { query: "hello" },
                { field: "vec", vector: Buffer.from([1, 2]) },
                { params: [{ key: "q", value: "value" }] },
            );
            const args = customCommand.mock.calls[0][0] as string[];
            const paramsIdx = args.indexOf("PARAMS");
            expect(args[paramsIdx + 1]).toBe("4");
            const entries = args.slice(paramsIdx + 2, paramsIdx + 6);
            expect(entries).toContain("q");
            expect(entries).toContain("value");
        });

        it("throws if a generated VSIM param name collides with a caller-supplied one", async () => {
            jest.resetModules();
            const fixedUuid = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
            jest.doMock("crypto", () => ({
                randomUUID: () => "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            }));

            // Re-require after mocking "crypto" so GlideFt picks up the
            // fixed UUID (the module caches `randomUUID` on import).
            const { GlideFt: MockedGlideFt } = await import("../build-ts");
            const { client } = mockClient();

            try {
                await expect(
                    MockedGlideFt.hybrid(
                        client,
                        "idx",
                        { query: "hello" },
                        { field: "vec", vector: Buffer.from([1, 2]) },
                        { params: [{ key: fixedUuid, value: "explicit" }] },
                    ),
                ).rejects.toThrow(/collides/);
            } finally {
                jest.dontMock("crypto");
                jest.resetModules();
            }
        });

        it("emits SEARCH arm scorer and yieldScoreAs in order", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.hybrid(
                client,
                "idx",
                { query: "hello", scorer: "BM25STD", yieldScoreAs: "ss" },
                { field: "vec", vector: "$v" },
            );
            const args = customCommand.mock.calls[0][0] as string[];
            expect(args.slice(2, 8)).toEqual([
                "SEARCH",
                "hello",
                "SCORER",
                "BM25STD",
                "YIELD_SCORE_AS",
                "ss",
            ]);
        });

        it("emits the VSIM arm's KNN block only when k/efRuntime/shardKRatio are set", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.hybrid(
                client,
                "idx",
                { query: "hello" },
                { field: "vec", vector: "$v" },
            );
            const args = customCommand.mock.calls[0][0] as string[];
            expect(args).not.toContain("KNN");
        });

        it("emits K inside a 2-token KNN block", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.hybrid(
                client,
                "idx",
                { query: "hello" },
                { field: "vec", vector: "$v", k: 5 },
            );
            const args = customCommand.mock.calls[0][0] as string[];
            const i = args.indexOf("KNN");
            expect(args.slice(i, i + 4)).toEqual(["KNN", "2", "K", "5"]);
        });

        it("emits K, EF_RUNTIME, SHARD_K_RATIO inside a 6-token KNN block", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.hybrid(
                client,
                "idx",
                { query: "hello" },
                {
                    field: "vec",
                    vector: "$v",
                    k: 5,
                    efRuntime: 100,
                    shardKRatio: 0.5,
                },
            );
            const args = customCommand.mock.calls[0][0] as string[];
            const i = args.indexOf("KNN");
            expect(args.slice(i, i + 8)).toEqual([
                "KNN",
                "6",
                "K",
                "5",
                "EF_RUNTIME",
                "100",
                "SHARD_K_RATIO",
                "0.5",
            ]);
        });

        it("emits YIELD_SCORE_AS on the VSIM arm after the KNN block", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.hybrid(
                client,
                "idx",
                { query: "hello" },
                { field: "vec", vector: "$v", k: 5, yieldScoreAs: "vs" },
            );
            const args = customCommand.mock.calls[0][0] as string[];
            const i = args.indexOf("VSIM");
            expect(args.slice(i, i + 8)).toEqual([
                "VSIM",
                "@vec",
                "$v",
                "KNN",
                "2",
                "K",
                "5",
                "YIELD_SCORE_AS",
            ]);
        });

        it("emits a bare FILTER (uncounted extension) when no policy/batchSize is set", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.hybrid(
                client,
                "idx",
                { query: "hello" },
                {
                    field: "vec",
                    vector: "$v",
                    filter: { expression: "@title:hello" },
                },
            );
            const args = customCommand.mock.calls[0][0] as string[];
            const i = args.indexOf("FILTER");
            expect(args.slice(i, i + 2)).toEqual(["FILTER", "@title:hello"]);
        });

        it("forces the counted FILTER form when policy is set", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.hybrid(
                client,
                "idx",
                { query: "hello" },
                {
                    field: "vec",
                    vector: "$v",
                    filter: { expression: "@title:hello", policy: "ADHOC" },
                },
            );
            const args = customCommand.mock.calls[0][0] as string[];
            const i = args.indexOf("FILTER");
            expect(args.slice(i, i + 5)).toEqual([
                "FILTER",
                "3",
                "@title:hello",
                "POLICY",
                "ADHOC",
            ]);
        });

        it("emits BATCH_SIZE after POLICY BATCHES in the counted FILTER form", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.hybrid(
                client,
                "idx",
                { query: "hello" },
                {
                    field: "vec",
                    vector: "$v",
                    filter: {
                        expression: "@title:hello",
                        policy: "BATCHES",
                        batchSize: 10,
                    },
                },
            );
            const args = customCommand.mock.calls[0][0] as string[];
            const i = args.indexOf("FILTER");
            expect(args.slice(i, i + 7)).toEqual([
                "FILTER",
                "5",
                "@title:hello",
                "POLICY",
                "BATCHES",
                "BATCH_SIZE",
                "10",
            ]);
        });

        it("throws if efRuntime is set together with filter.policy ADHOC", async () => {
            const { client } = mockClient();
            await expect(
                GlideFt.hybrid(
                    client,
                    "idx",
                    { query: "hello" },
                    {
                        field: "vec",
                        vector: "$v",
                        efRuntime: 100,
                        filter: { expression: "@title:hello", policy: "ADHOC" },
                    },
                ),
            ).rejects.toThrow(/efRuntime/);
        });

        it("does not throw when efRuntime is set with policy BATCHES (only ADHOC is rejected)", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.hybrid(
                client,
                "idx",
                { query: "hello" },
                {
                    field: "vec",
                    vector: "$v",
                    efRuntime: 100,
                    filter: { expression: "@title:hello", policy: "BATCHES" },
                },
            );
            const args = customCommand.mock.calls[0][0] as string[];
            expect(args).toContain("EF_RUNTIME");
        });

        it("throws if batchSize is set without policy BATCHES", async () => {
            const { client } = mockClient();
            await expect(
                GlideFt.hybrid(
                    client,
                    "idx",
                    { query: "hello" },
                    {
                        field: "vec",
                        vector: "$v",
                        filter: { expression: "@title:hello", batchSize: 10 },
                    },
                ),
            ).rejects.toThrow(/batchSize/);
        });

        it("throws if batchSize is set with policy ADHOC (not BATCHES)", async () => {
            const { client } = mockClient();
            await expect(
                GlideFt.hybrid(
                    client,
                    "idx",
                    { query: "hello" },
                    {
                        field: "vec",
                        vector: "$v",
                        filter: {
                            expression: "@title:hello",
                            policy: "ADHOC",
                            batchSize: 10,
                        },
                    },
                ),
            ).rejects.toThrow(/batchSize/);
        });

        it("throws if combine RRF constant is negative", async () => {
            const { client } = mockClient();
            await expect(
                GlideFt.hybrid(
                    client,
                    "idx",
                    { query: "hello" },
                    { field: "vec", vector: "$v" },
                    { combine: { method: "RRF", constant: -1 } },
                ),
            ).rejects.toThrow(/constant/);
        });

        it("does not throw when combine RRF constant is 0", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.hybrid(
                client,
                "idx",
                { query: "hello" },
                { field: "vec", vector: "$v" },
                { combine: { method: "RRF", constant: 0 } },
            );
            const args = customCommand.mock.calls[0][0] as string[];
            expect(args).toContain("CONSTANT");
        });

        it("throws if combine LINEAR sets alpha without beta", async () => {
            const { client } = mockClient();
            await expect(
                GlideFt.hybrid(
                    client,
                    "idx",
                    { query: "hello" },
                    { field: "vec", vector: "$v" },
                    { combine: { method: "LINEAR", alpha: 0.5 } },
                ),
            ).rejects.toThrow(/alpha and beta/);
        });

        it("throws if combine LINEAR sets beta without alpha", async () => {
            const { client } = mockClient();
            await expect(
                GlideFt.hybrid(
                    client,
                    "idx",
                    { query: "hello" },
                    { field: "vec", vector: "$v" },
                    { combine: { method: "LINEAR", beta: 0.7 } },
                ),
            ).rejects.toThrow(/alpha and beta/);
        });

        it("does not throw when combine LINEAR sets neither alpha nor beta", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.hybrid(
                client,
                "idx",
                { query: "hello" },
                { field: "vec", vector: "$v" },
                { combine: { method: "LINEAR", window: 20 } },
            );
            const args = customCommand.mock.calls[0][0] as string[];
            expect(args).toContain("LINEAR");
        });

        it("emits a bare COMBINE RRF with no sub-args", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.hybrid(
                client,
                "idx",
                { query: "hello" },
                { field: "vec", vector: "$v" },
                { combine: { method: "RRF" } },
            );
            const args = customCommand.mock.calls[0][0] as string[];
            const i = args.indexOf("COMBINE");
            expect(args.slice(i, i + 3)).toEqual(["COMBINE", "RRF", "0"]);
        });

        it("emits COMBINE RRF with CONSTANT, WINDOW, YIELD_SCORE_AS", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.hybrid(
                client,
                "idx",
                { query: "hello" },
                { field: "vec", vector: "$v" },
                {
                    combine: {
                        method: "RRF",
                        constant: 60,
                        window: 50,
                        yieldScoreAs: "fused",
                    },
                },
            );
            const args = customCommand.mock.calls[0][0] as string[];
            const i = args.indexOf("COMBINE");
            expect(args.slice(i, i + 9)).toEqual([
                "COMBINE",
                "RRF",
                "6",
                "CONSTANT",
                "60",
                "WINDOW",
                "50",
                "YIELD_SCORE_AS",
                "fused",
            ]);
        });

        it("emits COMBINE LINEAR with ALPHA and BETA", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.hybrid(
                client,
                "idx",
                { query: "hello" },
                { field: "vec", vector: "$v" },
                { combine: { method: "LINEAR", alpha: 0.5, beta: 0.5 } },
            );
            const args = customCommand.mock.calls[0][0] as string[];
            const i = args.indexOf("COMBINE");
            expect(args.slice(i, i + 7)).toEqual([
                "COMBINE",
                "LINEAR",
                "4",
                "ALPHA",
                "0.5",
                "BETA",
                "0.5",
            ]);
        });

        it("emits COMBINE FUNCTION with EXPR", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.hybrid(
                client,
                "idx",
                { query: "hello" },
                { field: "vec", vector: "$v" },
                { combine: { method: "FUNCTION", expr: "@ss + @vs" } },
            );
            const args = customCommand.mock.calls[0][0] as string[];
            const i = args.indexOf("COMBINE");
            expect(args.slice(i, i + 4)).toEqual([
                "COMBINE",
                "FUNCTION",
                "2",
                "EXPR",
            ]);
            expect(args[i + 4]).toBe("@ss + @vs");
        });

        it("emits LOAD * for loadAll", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.hybrid(
                client,
                "idx",
                { query: "hello" },
                { field: "vec", vector: "$v" },
                { loadAll: true },
            );
            const args = customCommand.mock.calls[0][0] as string[];
            const i = args.indexOf("LOAD");
            expect(args.slice(i, i + 2)).toEqual(["LOAD", "*"]);
        });

        it("emits LOAD n fields for loadFields", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.hybrid(
                client,
                "idx",
                { query: "hello" },
                { field: "vec", vector: "$v" },
                { loadFields: ["title", "price"] },
            );
            const args = customCommand.mock.calls[0][0] as string[];
            const i = args.indexOf("LOAD");
            expect(args.slice(i, i + 4)).toEqual([
                "LOAD",
                "2",
                "title",
                "price",
            ]);
        });

        it("emits LOAD with a LoadField alias as a 3-token budget entry (field AS alias)", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.hybrid(
                client,
                "idx",
                { query: "hello" },
                { field: "vec", vector: "$v" },
                {
                    loadFields: [
                        "title",
                        { fieldIdentifier: "__score", alias: "score" },
                    ],
                },
            );
            const args = customCommand.mock.calls[0][0] as string[];
            const i = args.indexOf("LOAD");
            expect(args.slice(i, i + 7)).toEqual([
                "LOAD",
                "4",
                "title",
                "__score",
                "AS",
                "score",
            ]);
        });

        it("omits LOAD entirely for an empty loadFields array (never LOAD 0)", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.hybrid(
                client,
                "idx",
                { query: "hello" },
                { field: "vec", vector: "$v" },
                { loadFields: [] },
            );
            const args = customCommand.mock.calls[0][0] as string[];
            expect(args).not.toContain("LOAD");
        });

        it("emits LOCALONLY before LOAD, DIALECT, and PARAMS regardless of field order", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.hybrid(
                client,
                "idx",
                { query: "hello" },
                { field: "vec", vector: "$v" },
                {
                    dialect: 2,
                    loadFields: ["title"],
                    localOnly: true,
                    params: [{ key: "q", value: "value" }],
                },
            );
            const args = customCommand.mock.calls[0][0] as string[];
            // Everything after the VSIM arm's own tokens.
            const tail = args.slice(args.indexOf("VSIM") + 3);
            expect(tail).toEqual([
                "LOCALONLY",
                "LOAD",
                "1",
                "title",
                "PARAMS",
                "2",
                "q",
                "value",
                "DIALECT",
                "2",
            ]);
        });

        it("emits PARAMS before clauses even with nothing else in the tail (ordering-bug proof)", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.hybrid(
                client,
                "idx",
                { query: "hello" },
                { field: "vec", vector: "$v" },
                {
                    params: [{ key: "q", value: "value" }],
                    clauses: [{ type: "FILTER", expression: "@price>0" }],
                },
            );
            const args = customCommand.mock.calls[0][0] as string[];
            const tail = args.slice(args.indexOf("VSIM") + 3);
            expect(tail).toEqual([
                "PARAMS",
                "2",
                "q",
                "value",
                "FILTER",
                "@price>0",
            ]);
        });

        it("pins the full tail order: LOCALONLY, LOAD, PARAMS, clauses", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.hybrid(
                client,
                "idx",
                { query: "hello" },
                { field: "vec", vector: "$v" },
                {
                    localOnly: true,
                    loadFields: ["title"],
                    params: [{ key: "q", value: "value" }],
                    clauses: [{ type: "LIMIT", offset: 0, count: 10 }],
                },
            );
            const args = customCommand.mock.calls[0][0] as string[];
            const tail = args.slice(args.indexOf("VSIM") + 3);
            expect(tail).toEqual([
                "LOCALONLY",
                "LOAD",
                "1",
                "title",
                "PARAMS",
                "2",
                "q",
                "value",
                "LIMIT",
                "0",
                "10",
            ]);
        });

        it("emits a FILTER clause directly after the VSIM arm's own argv, not misread as a VSIM pre-filter, when there is no LOAD", async () => {
            // Regression proof: the server's VSIM parser claims FILTER (and
            // POLICY/BATCH_SIZE) as its own immediately after the VSIM
            // arm's K/EF_RUNTIME/SHARD_K_RATIO/YIELD_SCORE_AS tokens. A
            // FtAggregateFilter clause placed in the aggregate tail must
            // never land there. PARAMS must always separate the VSIM arm
            // from any aggregate-tail clause, which this test's exact
            // scenario (no LOAD, no combine, no localOnly, no timeout)
            // isolates: with a LOAD clause in between, the bug would be
            // masked even if PARAMS were missing.
            const { client, customCommand } = mockClient();
            await GlideFt.hybrid(
                client,
                "idx",
                { query: "hello", yieldScoreAs: "ss" },
                { field: "vec", vector: "$v", k: 5 },
                {
                    params: [{ key: "q", value: "value" }],
                    clauses: [{ type: "FILTER", expression: "@ss > 0" }],
                },
            );
            const args = customCommand.mock.calls[0][0] as string[];
            const vsimIdx = args.indexOf("VSIM");
            // VSIM, @vec, $v, KNN, 2, K, 5 -- exactly 7 tokens, then PARAMS
            // must appear before FILTER, proving FILTER is in the
            // aggregate tail and not swallowed by the VSIM arm's own
            // sub-argument scan.
            expect(args.slice(vsimIdx, vsimIdx + 7)).toEqual([
                "VSIM",
                "@vec",
                "$v",
                "KNN",
                "2",
                "K",
                "5",
            ]);
            const paramsIdx = args.indexOf("PARAMS");
            const filterIdx = args.indexOf("FILTER");
            expect(paramsIdx).toBe(vsimIdx + 7);
            expect(filterIdx).toBeGreaterThan(paramsIdx);
            expect(args.slice(filterIdx, filterIdx + 2)).toEqual([
                "FILTER",
                "@ss > 0",
            ]);
        });

        it("emits WITHCURSOR after clauses, before DIALECT", async () => {
            const { client, customCommand } = mockClient();
            await GlideFt.hybrid(
                client,
                "idx",
                { query: "hello" },
                { field: "vec", vector: "$v" },
                {
                    withCursor: { count: 5 },
                    dialect: 2,
                },
            );
            const args = customCommand.mock.calls[0][0] as string[];
            const cursorIdx = args.indexOf("WITHCURSOR");
            const dialectIdx = args.indexOf("DIALECT");
            expect(args.slice(cursorIdx, cursorIdx + 3)).toEqual([
                "WITHCURSOR",
                "COUNT",
                "5",
            ]);
            expect(dialectIdx).toBeGreaterThan(cursorIdx);
        });
    });

    describe("FT.CREATE", () => {
        it("emits SORTABLE then UNF directly after it", async () => {
            const { client, customCommand } = mockClient();
            const field: TextField = {
                type: "TEXT",
                name: "title",
                sortable: true,
                unf: true,
            };
            await GlideFt.create(client, "idx", [field]);
            const args = customCommand.mock.calls[0][0] as string[];
            const i = args.indexOf("SORTABLE");
            expect(i).toBeGreaterThanOrEqual(0);
            expect(args[i + 1]).toBe("UNF");
        });

        it("throws if unf is set without sortable", async () => {
            const { client } = mockClient();
            const field: TextField = {
                type: "TEXT",
                name: "title",
                unf: true,
            };
            await expect(
                GlideFt.create(client, "idx", [field]),
            ).rejects.toThrow(/unf requires sortable/);
        });

        it("accepts FLOAT16 and BFLOAT16 vector types", async () => {
            const { client, customCommand } = mockClient();
            const field: VectorField = {
                type: "VECTOR",
                name: "vec",
                attributes: {
                    algorithm: "FLAT",
                    dimensions: 4,
                    distanceMetric: "L2",
                    type: "FLOAT16",
                },
            };
            await GlideFt.create(client, "idx", [field]);
            const args = customCommand.mock.calls[0][0] as string[];
            expect(args).toContain("FLOAT16");
            expect(args).not.toContain("FLOAT32");
        });

        it("emits FILTER, SCORE_FIELD and NOHL", async () => {
            const { client, customCommand } = mockClient();
            const options: FtCreateOptions = {
                dataType: "HASH",
                filter: "@price > 0",
                scoreField: "boost",
                noHighlight: true,
            };
            await GlideFt.create(client, "idx", [], options);
            const args = customCommand.mock.calls[0][0] as string[];
            expect(args).toEqual(
                expect.arrayContaining([
                    "FILTER",
                    "@price > 0",
                    "SCORE_FIELD",
                    "boost",
                    "NOHL",
                ]),
            );
        });

        it("rejects an empty filter", async () => {
            const { client } = mockClient();
            const options: FtCreateOptions = {
                dataType: "HASH",
                filter: "",
            };
            await expect(
                GlideFt.create(client, "idx", [], options),
            ).rejects.toThrow(/filter cannot be empty/);
        });
    });

    describe("FtAggregateReducer typed factories", () => {
        it("toList serializes to a generic TOLIST reducer shape", () => {
            const reducer = FtAggregateReducer.toList("@color", "colors");
            expect(reducer).toEqual({
                function: "TOLIST",
                args: ["@color"],
                name: "colors",
            });
        });

        it("firstValue without by omits BY/order", () => {
            const reducer = FtAggregateReducer.firstValue("@title");
            expect(reducer).toEqual({
                function: "FIRST_VALUE",
                args: ["@title"],
                name: undefined,
            });
        });

        it("firstValue with by and order emits BY <field> <order>", () => {
            const reducer = FtAggregateReducer.firstValue(
                "@title",
                { by: "@price", order: "DESC" },
                "most_expensive_title",
            );
            expect(reducer).toEqual({
                function: "FIRST_VALUE",
                args: ["@title", "BY", "@price", "DESC"],
                name: "most_expensive_title",
            });
        });

        it("firstValue throws if order is set without by", () => {
            expect(() =>
                FtAggregateReducer.firstValue("@title", { order: "DESC" }),
            ).toThrow(/order requires by/);
        });

        it("randomSample serializes field and size", () => {
            const reducer = FtAggregateReducer.randomSample(
                "@score",
                5,
                "score_sample",
            );
            expect(reducer).toEqual({
                function: "RANDOM_SAMPLE",
                args: ["@score", "5"],
                name: "score_sample",
            });
        });
    });
});
