/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

/**
 * Pure argv-serialization tests for the valkey-search 1.3 stable-surface port
 * (INKEYS, typed reducer factories, FLOAT16/BFLOAT16, FILTER/UNF/NOHL/SCORE_FIELD,
 * count -> LIMIT 0 0). No live server is needed: a mock client captures the
 * argv array `GlideFt` builds and passes to `customCommand`, mirroring the
 * Python reference's `test_options.py`, which calls `to_args()` directly on
 * each option object. Node's option-serialization helpers are internal module
 * functions rather than public methods, so a mock `customCommand` is the
 * equivalent seam here.
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
