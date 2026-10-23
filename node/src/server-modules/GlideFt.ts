/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { randomUUID } from "crypto";

import {
    convertGlideRecordToRecord,
    Decoder,
    DecoderOption,
    GlideRecord,
    GlideReturnType,
    GlideString,
    GlideClient,
    GlideClusterClient,
    Field,
    FtAggregateOptions,
    FtCreateOptions,
    FtHybridOptions,
    FtHybridSearchArm,
    FtHybridVsimArm,
    FtInfoOptions,
    FtSearchOptions,
    FtWithCursor,
} from "..";

/** Response type of {@link GlideFt.info | ft.info} command. */
export type FtInfoReturnType = Record<
    string,
    | GlideString
    | number
    | GlideString[]
    | Record<string, GlideString | Record<string, GlideString | number>[]>
>;

/**
 * Response type for the {@link GlideFt.search | ft.search} command.
 */
export type FtSearchReturnType = [
    number,
    GlideRecord<GlideRecord<GlideString>>,
];

/**
 * Response type for the {@link GlideFt.aggregate | ft.aggregate} command.
 */
export type FtAggregateReturnType = GlideRecord<GlideReturnType>[];

/** Module for Vector Search commands. */
export class GlideFt {
    /**
     * Creates an index and initiates a backfill of that index.
     *
     * @param client - The client to execute the command.
     * @param indexName - The index name for the index to be created.
     * @param schema - The fields of the index schema, specifying the fields and their types.
     * @param options - (Optional) Options for the `FT.CREATE` command. See {@link FtCreateOptions}.
     * @returns If the index is successfully created, returns "OK".
     *
     * @example
     * ```typescript
     * // Example usage of FT.CREATE to create a 6-dimensional JSON index using the HNSW algorithm
     * await GlideFt.create(client, "json_idx1", [{
     *      type: "VECTOR",
     *      name: "$.vec",
     *      alias: "VEC",
     *      attributes: {
     *          algorithm: "HNSW",
     *          type: "FLOAT32",
     *          dimension: 6,
     *          distanceMetric: "L2",
     *          numberOfEdges: 32,
     *      },
     *  }], {
     *      dataType: "JSON",
     *      prefixes: ["json:"]
     *  });
     *
     * // Create a text search index with all options:
     * // Note: noStopWords/stopWords are mutually exclusive;
     * // withOffsets/noOffsets are mutually exclusive.
     * await GlideFt.create(client, "text_idx", [
     *     { type: "TEXT", name: "title", sortable: true, nostem: true, weight: 2.0,
     *       withsuffixtrie: true },
     *     { type: "NUMERIC", name: "price", sortable: true },
     *     { type: "TAG", name: "category", sortable: true },
     * ], {
     *     dataType: "HASH",
     *     prefixes: ["product:"],
     *     score: 1.0,
     *     language: "english",
     *     skipInitialScan: true,
     *     minStemSize: 4,
     *     withOffsets: true,
     *     stopWords: ["the", "a", "is"],
     *     punctuation: ".,;!?",
     * });
     * ```
     */
    static async create(
        client: GlideClient | GlideClusterClient,
        indexName: GlideString,
        schema: Field[],
        options?: FtCreateOptions,
    ): Promise<"OK"> {
        const args: GlideString[] = ["FT.CREATE", indexName];

        if (options) {
            if ("dataType" in options) {
                args.push("ON", options.dataType);
            }

            if ("prefixes" in options && options.prefixes) {
                args.push(
                    "PREFIX",
                    options.prefixes.length.toString(),
                    ...options.prefixes,
                );
            }

            if (options.score !== undefined) {
                args.push("SCORE", options.score.toString());
            }

            if (options.language) {
                args.push("LANGUAGE", options.language);
            }

            if (options.skipInitialScan) {
                args.push("SKIPINITIALSCAN");
            }

            if (options.minStemSize !== undefined) {
                args.push("MINSTEMSIZE", options.minStemSize.toString());
            }

            if (options.withOffsets && options.noOffsets) {
                throw new Error(
                    "withOffsets and noOffsets are mutually exclusive.",
                );
            }

            if (options.noStopWords && options.stopWords) {
                throw new Error(
                    "noStopWords and stopWords are mutually exclusive.",
                );
            }

            if (options.withOffsets) {
                args.push("WITHOFFSETS");
            } else if (options.noOffsets) {
                args.push("NOOFFSETS");
            }

            if (options.noStopWords) {
                args.push("NOSTOPWORDS");
            } else if (options.stopWords) {
                args.push(
                    "STOPWORDS",
                    options.stopWords.length.toString(),
                    ...options.stopWords,
                );
            }

            if (options.punctuation) {
                args.push("PUNCTUATION", options.punctuation);
            }

            if (options.filter !== undefined) {
                if (options.filter === "") {
                    throw new Error("filter cannot be empty.");
                }

                args.push("FILTER", options.filter);
            }

            if (options.scoreField !== undefined) {
                args.push("SCORE_FIELD", options.scoreField);
            }

            if (options.noHighlight) {
                args.push("NOHL");
            }
        }

        args.push("SCHEMA");

        schema.forEach((f) => {
            args.push(f.name);

            if (f.alias) {
                args.push("AS", f.alias);
            }

            args.push(f.type);

            switch (f.type) {
                case "TEXT": {
                    if (f.nostem) {
                        args.push("NOSTEM");
                    }

                    if (f.weight !== undefined) {
                        args.push("WEIGHT", f.weight.toString());
                    }

                    if (f.withsuffixtrie) {
                        args.push("WITHSUFFIXTRIE");
                    } else if (f.nosuffixtrie) {
                        args.push("NOSUFFIXTRIE");
                    }

                    if (f.sortable) {
                        args.push("SORTABLE");

                        if (f.unf) {
                            args.push("UNF");
                        }
                    } else if (f.unf) {
                        throw new Error("unf requires sortable to be set.");
                    }

                    break;
                }

                case "TAG": {
                    if (f.separator) {
                        args.push("SEPARATOR", f.separator);
                    }

                    if (f.caseSensitive) {
                        args.push("CASESENSITIVE");
                    }

                    if (f.sortable) {
                        args.push("SORTABLE");

                        if (f.unf) {
                            args.push("UNF");
                        }
                    } else if (f.unf) {
                        throw new Error("unf requires sortable to be set.");
                    }

                    break;
                }

                case "NUMERIC": {
                    if (f.sortable) {
                        args.push("SORTABLE");

                        if (f.unf) {
                            args.push("UNF");
                        }
                    } else if (f.unf) {
                        throw new Error("unf requires sortable to be set.");
                    }

                    break;
                }

                case "VECTOR": {
                    if (f.attributes) {
                        args.push(f.attributes.algorithm);

                        const attributes: GlideString[] = [];

                        // all VectorFieldAttributes attributes
                        if (f.attributes.dimensions) {
                            attributes.push(
                                "DIM",
                                f.attributes.dimensions.toString(),
                            );
                        }

                        if (f.attributes.distanceMetric) {
                            attributes.push(
                                "DISTANCE_METRIC",
                                f.attributes.distanceMetric.toString(),
                            );
                        }

                        if (f.attributes.type) {
                            attributes.push(
                                "TYPE",
                                f.attributes.type.toString(),
                            );
                        } else {
                            attributes.push("TYPE", "FLOAT32");
                        }

                        if (f.attributes.initialCap) {
                            attributes.push(
                                "INITIAL_CAP",
                                f.attributes.initialCap.toString(),
                            );
                        }

                        // VectorFieldAttributesHnsw attributes
                        if (
                            "numberOfEdges" in f.attributes &&
                            f.attributes.numberOfEdges
                        ) {
                            attributes.push(
                                "M",
                                f.attributes.numberOfEdges.toString(),
                            );
                        }

                        if (
                            "vectorsExaminedOnConstruction" in f.attributes &&
                            f.attributes.vectorsExaminedOnConstruction
                        ) {
                            attributes.push(
                                "EF_CONSTRUCTION",
                                f.attributes.vectorsExaminedOnConstruction.toString(),
                            );
                        }

                        if (
                            "vectorsExaminedOnRuntime" in f.attributes &&
                            f.attributes.vectorsExaminedOnRuntime
                        ) {
                            attributes.push(
                                "EF_RUNTIME",
                                f.attributes.vectorsExaminedOnRuntime.toString(),
                            );
                        }

                        args.push(attributes.length.toString(), ...attributes);
                    }

                    break;
                }

                default:
                // no-op
            }
        });

        return _handleCustomCommand(client, args, {
            decoder: Decoder.String,
        }) as Promise<"OK">;
    }

    /**
     * Deletes an index and associated content. Indexed document keys are unaffected.
     *
     * @param client - The client to execute the command.
     * @param indexName - The index name.
     * @returns "OK"
     *
     * @example
     * ```typescript
     * // Example usage of FT.DROPINDEX to drop an index
     * await GlideFt.dropindex(client, "json_idx1"); // "OK"
     * ```
     */
    static async dropindex(
        client: GlideClient | GlideClusterClient,
        indexName: GlideString,
    ): Promise<"OK"> {
        const args: GlideString[] = ["FT.DROPINDEX", indexName];

        return _handleCustomCommand(client, args, {
            decoder: Decoder.String,
        }) as Promise<"OK">;
    }

    /**
     * Lists all indexes.
     *
     * @param client - The client to execute the command.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns An array of index names.
     *
     * @example
     * ```typescript
     * console.log(await GlideFt.list(client)); // Output: ["index1", "index2"]
     * ```
     */
    static async list(
        client: GlideClient | GlideClusterClient,
        options?: DecoderOption,
    ): Promise<GlideString[]> {
        return _handleCustomCommand(client, ["FT._LIST"], options) as Promise<
            GlideString[]
        >;
    }

    /**
     * Runs a search query on an index, and perform aggregate transformations on the results.
     *
     * @param client - The client to execute the command.
     * @param indexName - The index name.
     * @param query - The text query to search.
     * @param options - Additional parameters for the command - see {@link FtAggregateOptions} and {@link DecoderOption}.
     * @returns Results of the last stage of the pipeline.
     *
     * @example
     * ```typescript
     * const options: FtAggregateOptions = {
     *      loadFields: ["__key"],
     *      clauses: [
     *          {
     *              type: "GROUPBY",
     *              properties: ["@condition"],
     *              reducers: [
     *                  {
     *                      function: "TOLIST",
     *                      args: ["__key"],
     *                      name: "bicycles",
     *                  },
     *              ],
     *          },
     *      ],
     *  };
     * const result = await GlideFt.aggregate(client, "myIndex", "*", options);
     * console.log(result); // Output:
     * // [
     * //     [
     * //         {
     * //             key: "condition",
     * //             value: "refurbished"
     * //         },
     * //         {
     * //             key: "bicycles",
     * //             value: [ "bicycle:9" ]
     * //         }
     * //     ],
     * //     [
     * //         {
     * //             key: "condition",
     * //             value: "used"
     * //         },
     * //         {
     * //             key: "bicycles",
     * //             value: [ "bicycle:1", "bicycle:2", "bicycle:3" ]
     * //         }
     * //     ],
     * //     [
     * //         {
     * //             key: "condition",
     * //             value: "new"
     * //         },
     * //         {
     * //             key: "bicycles",
     * //             value: [ "bicycle:0", "bicycle:5" ]
     * //         }
     * //     ]
     * // ]
     *
     * // Aggregate with all query flags:
     * const result12 = await GlideFt.aggregate(client, "myIndex", "@score:[20 +inf]",
     *     { loadAll: true, verbatim: true, inorder: true, slop: 1, dialect: 2 });
     * ```
     */
    static async aggregate(
        client: GlideClient | GlideClusterClient,
        indexName: GlideString,
        query: GlideString,
        options?: DecoderOption & FtAggregateOptions,
    ): Promise<FtAggregateReturnType> {
        const args: GlideString[] = [
            "FT.AGGREGATE",
            indexName,
            query,
            ..._addFtAggregateOptions(options),
        ];

        return _handleCustomCommand(
            client,
            args,
            options,
        ) as Promise<FtAggregateReturnType>;
    }

    /**
     * Reads the next page of results from a cursor opened by {@link GlideFt.aggregate | FT.AGGREGATE}
     * (with `withCursor` set) or {@link GlideFt.hybrid | FT.HYBRID} (with `withCursor` set).
     *
     * @param client - The client to execute the command.
     * @param indexName - The index name the cursor was opened against.
     * @param cursorId - The cursor id, from the opening command's reply or a previous
     *     `cursorRead` call. `0` means the cursor is already exhausted; calling this with `0`
     *     is a caller error, not something this function special-cases.
     * @param options - (Optional) `count`: overrides how many rows this page returns, instead of
     *     the count the cursor was opened with. Plus {@link DecoderOption}.
     * @returns A two-element array of `[rows, cursorId]`, where `rows` is the same per-document
     *     array shape {@link GlideFt.aggregate}/{@link GlideFt.hybrid} return, and `cursorId` is
     *     `0` once the cursor is exhausted, or the id to pass to the next `cursorRead` call
     *     otherwise.
     *
     * Always reads from the PRIMARY of the index's slot, regardless of the client's `ReadFrom`
     * configuration -- see the cursor-affinity note on {@link GlideFt.aggregate}/{@link GlideFt.hybrid}.
     * A cursor opened by FT.HYBRID is read the same way as one opened by FT.AGGREGATE; the
     * server does not distinguish which command created it.
     *
     * @example
     * ```typescript
     * let [rows, cursorId] = await GlideFt.aggregate(client, "myIndex", "*",
     *     { loadFields: ["__key"], withCursor: { count: 10 } });
     * while (cursorId !== 0) {
     *     [rows, cursorId] = await GlideFt.cursorRead(client, "myIndex", cursorId);
     * }
     * ```
     */
    static async cursorRead(
        client: GlideClient | GlideClusterClient,
        indexName: GlideString,
        cursorId: number,
        options?: DecoderOption & { count?: number },
    ): Promise<FtAggregateReturnType> {
        const args: GlideString[] = [
            "FT.CURSOR",
            "READ",
            indexName,
            cursorId.toString(),
        ];

        if (options?.count !== undefined) {
            args.push("COUNT", options.count.toString());
        }

        return _handleCustomCommand(
            client,
            args,
            options,
        ) as Promise<FtAggregateReturnType>;
    }

    /**
     * Deletes an open cursor, discarding it before it would otherwise be exhausted or time out.
     *
     * @param client - The client to execute the command.
     * @param indexName - The index name the cursor was opened against.
     * @param cursorId - The cursor id to delete.
     * @returns `"OK"`
     *
     * Always reads from the PRIMARY of the index's slot, regardless of the client's `ReadFrom`
     * configuration -- see the cursor-affinity note on {@link GlideFt.aggregate}/{@link GlideFt.hybrid}.
     *
     * @example
     * ```typescript
     * const [rows, cursorId] = await GlideFt.aggregate(client, "myIndex", "*",
     *     { loadFields: ["__key"], withCursor: { count: 1 } });
     * await GlideFt.cursorDel(client, "myIndex", cursorId); // "OK"
     * ```
     */
    static async cursorDel(
        client: GlideClient | GlideClusterClient,
        indexName: GlideString,
        cursorId: number,
    ): Promise<"OK"> {
        const args: GlideString[] = [
            "FT.CURSOR",
            "DEL",
            indexName,
            cursorId.toString(),
        ];

        return _handleCustomCommand(client, args, {
            decoder: Decoder.String,
        }) as Promise<"OK">;
    }

    /**
     * Combines a text/tag/numeric query (the SEARCH arm) with a vector similarity search (the
     * VSIM arm) in a single query, fusing both arms' results into one ranked reply.
     *
     * @param client - The client to execute the command.
     * @param indexName - The index name to query.
     * @param searchArm - The text/tag/numeric query arm. See {@link FtHybridSearchArm}.
     * @param vsimArm - The vector similarity search arm. See {@link FtHybridVsimArm}.
     * @param options - (Optional) Additional parameters for the command, covering COMBINE,
     *     LOAD, FILTER/LIMIT/GROUPBY/SORTBY/APPLY clauses, WITHCURSOR, LOCALONLY, TIMEOUT,
     *     PARAMS and DIALECT. See {@link FtHybridOptions} and {@link DecoderOption}.
     * @returns An array containing a mapping of field name and associated value for each
     *     matching document, after both arms' results have been fused and any clauses in
     *     `options` applied. With no LOAD clause in `options` (the default), each mapping
     *     contains just the document key and the fused score. When `options.withCursor` is set,
     *     the reply shape instead matches FT.AGGREGATE's own WITHCURSOR shape: a two-element
     *     array of `[rows, cursorId]`, where `rows` is this same response shape and `cursorId`
     *     is passed to a subsequent {@link GlideFt.cursorRead} call to fetch the next page (`0`
     *     once exhausted).
     *
     * A paged (`withCursor`) FT.HYBRID query, and every subsequent FT.CURSOR READ/DEL against
     * the cursor it opens, always reads from the PRIMARY of the index's slot, regardless of the
     * client's `ReadFrom` configuration. This is because a cursor is a per-process, node-local
     * resource on the server that opened it -- it is not shared or forwarded between nodes, so a
     * read that lands on a different node (a replica a `PREFER_REPLICA`/AZ-affinity client would
     * otherwise have used) fails with "Cursor not found". A failover of the primary that was
     * holding an open cursor loses that cursor, the same as any other cursor-discarding event on
     * that node.
     *
     * @example
     * ```typescript
     * // For the following example to work, an index named "idx" must already exist, with a
     * // TEXT field "title" and a VECTOR field "vec".
     * const result = await GlideFt.hybrid(
     *     client,
     *     "idx",
     *     { query: "hello world" },
     *     { field: "vec", vector: queryVectorBytes, k: 10 },
     * );
     * // Output: [{ key: "__key", value: "d1" }, { key: "__score", value: "0.014925" }], ...
     *
     * // With RRF fusion, a LOAD clause and WITHCURSOR paging:
     * const result2 = await GlideFt.hybrid(
     *     client,
     *     "idx",
     *     { query: "hello world", yieldScoreAs: "ss" },
     *     { field: "vec", vector: queryVectorBytes, k: 10, yieldScoreAs: "vs" },
     *     {
     *         combine: { method: "RRF", constant: 60 },
     *         loadFields: ["title"],
     *         withCursor: { count: 10 },
     *     },
     * );
     * ```
     */
    static async hybrid(
        client: GlideClient | GlideClusterClient,
        indexName: GlideString,
        searchArm: FtHybridSearchArm,
        vsimArm: FtHybridVsimArm,
        options?: DecoderOption & FtHybridOptions,
    ): Promise<FtAggregateReturnType> {
        const args: GlideString[] = ["FT.HYBRID", indexName];
        args.push(..._addFtHybridSearchArm(searchArm));

        let generatedVsimParam:
            { name: GlideString; value: GlideString } | undefined;

        if (_isVsimParamReference(vsimArm.vector)) {
            args.push(..._addFtHybridVsimArm(vsimArm, vsimArm.vector));
        } else {
            const generatedName = `$${randomUUID().replace(/-/g, "")}`;
            generatedVsimParam = {
                name: generatedName.slice(1),
                value: vsimArm.vector,
            };
            args.push(..._addFtHybridVsimArm(vsimArm, generatedName));
        }

        if (options?.combine) {
            args.push(..._addFtHybridCombine(options.combine));
        }

        args.push(..._addFtHybridOptions(options, generatedVsimParam));

        return _handleCustomCommand(
            client,
            args,
            options,
        ) as Promise<FtAggregateReturnType>;
    }

    /**
     * Returns information about a given index.
     *
     * @param client - The client to execute the command.
     * @param indexName - The index name.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns Nested maps with info about the index. See example for more details.
     *
     * @example
     * ```typescript
     * const info = await GlideFt.info(client, "myIndex");
     * console.log(info); // Output:
     * // {
     * //     index_name: 'myIndex',
     * //     index_status: 'AVAILABLE',
     * //     key_type: 'JSON',
     * //     creation_timestamp: 1728348101728771,
     * //     key_prefixes: [ 'json:' ],
     * //     num_indexed_vectors: 0,
     * //     space_usage: 653471,
     * //     num_docs: 0,
     * //     vector_space_usage: 653471,
     * //     index_degradation_percentage: 0,
     * //     fulltext_space_usage: 0,
     * //     current_lag: 0,
     * //     fields: [
     * //         {
     * //             identifier: '$.vec',
     * //             type: 'VECTOR',
     * //             field_name: 'VEC',
     * //             option: '',
     * //             vector_params: {
     * //                 data_type: 'FLOAT32',
     * //                 initial_capacity: 1000,
     * //                 current_capacity: 1000,
     * //                 distance_metric: 'L2',
     * //                 dimension: 6,
     * //                 block_size: 1024,
     * //                 algorithm: 'FLAT'
     * //             }
     * //         },
     * //         {
     * //             identifier: 'name',
     * //             type: 'TEXT',
     * //             field_name: 'name',
     * //             option: ''
     * //         },
     * //     ]
     * // }
     *
     * // Get info with scope options:
     * const localInfo = await GlideFt.info(client, "myIndex", { scope: "LOCAL" });
     *
     * // Get cluster-wide info (requires coordinator):
     * const clusterInfo = await GlideFt.info(client, "myIndex", {
     *     scope: "PRIMARY",
     *     shardScope: "ALLSHARDS",
     *     consistency: "CONSISTENT",
     * });
     * ```
     */
    static async info(
        client: GlideClient | GlideClusterClient,
        indexName: GlideString,
        options?: DecoderOption & FtInfoOptions,
    ): Promise<FtInfoReturnType> {
        const args: GlideString[] = ["FT.INFO", indexName];

        if (options?.scope) {
            args.push(options.scope);
        }

        if (options?.shardScope) {
            args.push(options.shardScope);
        }

        if (options?.consistency) {
            args.push(options.consistency);
        }

        return (
            _handleCustomCommand(client, args, options) as Promise<
                GlideRecord<GlideString>
            >
        ).then(convertGlideRecordToRecord);
    }

    /**
     * Parse a query and return information about how that query was parsed.
     *
     * @param client - The client to execute the command.
     * @param indexName - The index name.
     * @param query - The text query to search. It is the same as the query passed as
     * an argument to {@link search | FT.SEARCH} or {@link aggregate | FT.AGGREGATE}.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A query execution plan.
     *
     * @example
     * ```typescript
     * const result = GlideFt.explain(client, "myIndex", "@price:[0 10]");
     * console.log(result); // Output: "Field {\n\tprice\n\t0\n\t10\n}"
     * ```
     */
    static explain(
        client: GlideClient | GlideClusterClient,
        indexName: GlideString,
        query: GlideString,
        options?: DecoderOption,
    ): Promise<GlideString> {
        const args = ["FT.EXPLAIN", indexName, query];

        return _handleCustomCommand(client, args, options);
    }

    /**
     * Parse a query and return information about how that query was parsed.
     * Same as {@link explain | FT.EXPLAIN}, except that the results are
     * displayed in a different format.
     *
     * @param client - The client to execute the command.
     * @param indexName - The index name.
     * @param query - The text query to search. It is the same as the query passed as
     * an argument to {@link search | FT.SEARCH} or {@link aggregate | FT.AGGREGATE}.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A query execution plan.
     *
     * @example
     * ```typescript
     * const result = GlideFt.explaincli(client, "myIndex", "@price:[0 10]");
     * console.log(result); // Output: ["Field {", "price", "0", "10", "}"]
     * ```
     */
    static explaincli(
        client: GlideClient | GlideClusterClient,
        indexName: GlideString,
        query: GlideString,
        options?: DecoderOption,
    ): Promise<GlideString[]> {
        const args = ["FT.EXPLAINCLI", indexName, query];

        return _handleCustomCommand(client, args, options);
    }

    /**
     * Uses the provided query expression to locate keys within an index. Once located, the count
     * and/or content of indexed fields within those keys can be returned.
     *
     * @param client - The client to execute the command.
     * @param indexName - The index name to search into.
     * @param query - The text query to search.
     * @param options - (Optional) See {@link FtSearchOptions} and {@link DecoderOption}.
     * @returns A two-element array, where the first element is the number of documents in the result set, and the
     * second element has the format: `GlideRecord<GlideRecord<GlideString>>`:
     * a mapping between document names and a map of their attributes.
     * When `nocontent` is set, the attribute maps will be empty.
     *
     * If `count` or `limit` with values `{offset: 0, count: 0}` is
     * set, the command returns array with only one element: the number of documents.
     *
     * @example
     * ```typescript
     * //
     * const vector = Buffer.alloc(24);
     * const result = await GlideFt.search(client, "json_idx1", "*=>[KNN 2 @VEC $query_vec]", {params: [{key: "query_vec", value: vector}]});
     * console.log(result); // Output:
     * // [
     * //   2,
     * //   [
     * //     {
     * //       key: "json:2",
     * //       value: [
     * //         {
     * //           key: "$",
     * //           value: '{"vec":[1.1,1.2,1.3,1.4,1.5,1.6]}',
     * //         },
     * //         {
     * //           key: "__VEC_score",
     * //           value: "11.1100006104",
     * //         },
     * //       ],
     * //     },
     * //     {
     * //       key: "json:0",
     * //       value: [
     * //         {
     * //           key: "$",
     * //           value: '{"vec":[1,2,3,4,5,6]}',
     * //         },
     * //         {
     * //           key: "__VEC_score",
     * //           value: "91",
     * //         },
     * //       ],
     * //     },
     * //   ],
     * // ]
     *
     * // Text search with all options:
     * // Note: withSortKeys requires sortby; shardScope/consistency are cluster-mode options.
     * const textResult = await GlideFt.search(client, "myIndex", "hello world", {
     *     verbatim: true,
     *     inorder: true,
     *     slop: 1,
     *     sortby: "price",
     *     sortbyOrder: SortOrder.ASC,
     *     withsortkeys: true,
     *     shardScope: "ALLSHARDS",
     *     consistency: "CONSISTENT",
     *     dialect: 2,
     * });
     * ```
     */
    static async search(
        client: GlideClient | GlideClusterClient,
        indexName: GlideString,
        query: GlideString,
        options?: FtSearchOptions & DecoderOption,
    ): Promise<FtSearchReturnType> {
        const args: GlideString[] = [
            "FT.SEARCH",
            indexName,
            query,
            ..._addFtSearchOptions(options),
        ];

        return _handleCustomCommand(client, args, options) as Promise<
            [number, GlideRecord<GlideRecord<GlideString>>]
        >;
    }

    /**
     * Runs a search query and collects performance profiling information.
     *
     * @param client - The client to execute the command.
     * @param indexName - The index name.
     * @param query - The text query to search.
     * @param options - (Optional) See {@link FtSearchOptions} and {@link DecoderOption}. Additionally:
     * - `limited` (Optional) - Either provide a full verbose output or some brief version.
     *
     * @returns A two-element array. The first element contains results of the search query being profiled, the
     *     second element stores profiling information.
     *
     * @example
     * ```typescript
     * // Example of running profile on a search query
     * const vector = Buffer.alloc(24);
     * const result = await GlideFt.profileSearch(client, "json_idx1", "*=>[KNN 2 @VEC $query_vec]", {params: [{key: "query_vec", value: vector}]});
     * console.log(result); // Output:
     * // result[0] contains `FT.SEARCH` response with the given query
     * // result[1] contains profiling data as a `Record<string, number>`
     * ```
     */
    static async profileSearch(
        client: GlideClient | GlideClusterClient,
        indexName: GlideString,
        query: GlideString,
        options?: DecoderOption &
            FtSearchOptions & {
                limited?: boolean;
            },
    ): Promise<[FtSearchReturnType, Record<string, number>]> {
        const args: GlideString[] = ["FT.PROFILE", indexName, "SEARCH"];

        if (options?.limited) {
            args.push("LIMITED");
        }

        args.push("QUERY", query);

        if (options) {
            args.push(..._addFtSearchOptions(options));
        }

        return (
            _handleCustomCommand(
                client,
                args,
                options as DecoderOption,
            ) as Promise<[FtSearchReturnType, GlideRecord<number>]>
        ).then((v) => [v[0], convertGlideRecordToRecord(v[1])]);
    }

    /**
     * Runs an aggregate query and collects performance profiling information.
     *
     * @param client - The client to execute the command.
     * @param indexName - The index name.
     * @param query - The text query to search.
     * @param options - (Optional) See {@link FtAggregateOptions} and {@link DecoderOption}. Additionally:
     * - `limited` (Optional) - Either provide a full verbose output or some brief version.
     *
     * @returns A two-element array. The first element contains results of the aggregate query being profiled, the
     *     second element stores profiling information.
     *
     * @example
     * ```typescript
     * // Example of running profile on an aggregate query
     * const options: FtAggregateOptions = {
     *      loadFields: ["__key"],
     *      clauses: [
     *          {
     *              type: "GROUPBY",
     *              properties: ["@condition"],
     *              reducers: [
     *                  {
     *                      function: "TOLIST",
     *                      args: ["__key"],
     *                      name: "bicycles",
     *                  },
     *              ],
     *          },
     *      ],
     *  };
     * const result = await GlideFt.profileAggregate(client, "myIndex", "*", options);
     * console.log(result); // Output:
     * // result[0] contains `FT.AGGREGATE` response with the given query
     * // result[1] contains profiling data as a `Record<string, number>`
     * ```
     */
    static async profileAggregate(
        client: GlideClient | GlideClusterClient,
        indexName: GlideString,
        query: GlideString,
        options?: DecoderOption &
            FtAggregateOptions & {
                limited?: boolean;
            },
    ): Promise<[FtAggregateReturnType, Record<string, number>]> {
        const args: GlideString[] = ["FT.PROFILE", indexName, "AGGREGATE"];

        if (options?.limited) {
            args.push("LIMITED");
        }

        args.push("QUERY", query);

        if (options) {
            args.push(..._addFtAggregateOptions(options));
        }

        return (
            _handleCustomCommand(
                client,
                args,
                options as DecoderOption,
            ) as Promise<[FtAggregateReturnType, GlideRecord<number>]>
        ).then((v) => [v[0], convertGlideRecordToRecord(v[1])]);
    }

    /**
     * Adds an alias for an index. The new alias name can be used anywhere that an index name is required.
     *
     * @param client - The client to execute the command.
     * @param indexName - The alias to be added to the index.
     * @param alias - The index name for which the alias has to be added.
     * @returns `"OK"`
     *
     * @example
     * ```typescript
     * // Example usage of FT.ALIASADD to add an alias for an index.
     * await GlideFt.aliasadd(client, "index", "alias"); // "OK"
     * ```
     */
    static async aliasadd(
        client: GlideClient | GlideClusterClient,
        indexName: GlideString,
        alias: GlideString,
    ): Promise<"OK"> {
        const args: GlideString[] = ["FT.ALIASADD", alias, indexName];
        return _handleCustomCommand(client, args, {
            decoder: Decoder.String,
        }) as Promise<"OK">;
    }

    /**
     * Deletes an existing alias for an index.
     *
     * @param client - The client to execute the command.
     * @param alias -  The existing alias to be deleted for an index.
     * @returns `"OK"`
     *
     * @example
     * ```typescript
     * // Example usage of FT.ALIASDEL to delete an existing alias.
     * await GlideFt.aliasdel(client, "alias"); // "OK"
     * ```
     */
    static async aliasdel(
        client: GlideClient | GlideClusterClient,
        alias: GlideString,
    ): Promise<"OK"> {
        const args: GlideString[] = ["FT.ALIASDEL", alias];
        return _handleCustomCommand(client, args, {
            decoder: Decoder.String,
        }) as Promise<"OK">;
    }

    /**
     * Updates an existing alias to point to a different physical index. This command only affects future references to the alias.
     *
     * @param client - The client to execute the command.
     * @param alias - The alias name. This alias will now be pointed to a different index.
     * @param indexName - The index name for which an existing alias has to updated.
     * @returns `"OK"`
     *
     * @example
     * ```typescript
     * // Example usage of FT.ALIASUPDATE to update an alias to point to a different index.
     * await GlideFt.aliasupdate(client, "newAlias", "index"); // "OK"
     * ```
     */
    static async aliasupdate(
        client: GlideClient | GlideClusterClient,
        alias: GlideString,
        indexName: GlideString,
    ): Promise<"OK"> {
        const args: GlideString[] = ["FT.ALIASUPDATE", alias, indexName];
        return _handleCustomCommand(client, args, {
            decoder: Decoder.String,
        }) as Promise<"OK">;
    }

    /**
     * List the index aliases.
     *
     * @param client - The client to execute the command.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A map of index aliases for indices being aliased.
     *
     * @example
     * ```typescript
     * // Example usage of FT._ALIASLIST to query index aliases
     * const result = await GlideFt.aliaslist(client);
     * console.log(result); // Output:
     * //[{"key": "alias1", "value": "index1"}, {"key": "alias2", "value": "index2"}]
     * ```
     */
    static async aliaslist(
        client: GlideClient | GlideClusterClient,
        options?: DecoderOption,
    ): Promise<GlideRecord<GlideString>> {
        const args: GlideString[] = ["FT._ALIASLIST"];
        return _handleCustomCommand(client, args, options);
    }
}

/**
 * @internal
 *
 * Only a `string` starting with `$` is treated as a parameter reference. A `Buffer`'s packed
 * binary representation is arbitrary and can legitimately start with the byte `0x24` (`$`) by
 * coincidence; treating it the same way as a `string` here would misclassify that vector as a
 * parameter reference, skip generating the internal PARAMS entry, and send the raw vector bytes
 * in the parameter-reference position, which the server cannot resolve. A `Buffer` is therefore
 * always raw vector data regardless of its first byte.
 */
function _isVsimParamReference(vector: GlideString): boolean {
    return typeof vector === "string" && vector.startsWith("$");
}

/**
 * @internal
 */
function _addFtHybridSearchArm(arm: FtHybridSearchArm): GlideString[] {
    const args: GlideString[] = ["SEARCH", arm.query];
    if (arm.scorer !== undefined) args.push("SCORER", arm.scorer);
    if (arm.yieldScoreAs !== undefined)
        args.push("YIELD_SCORE_AS", arm.yieldScoreAs);
    return args;
}

/**
 * @internal
 *
 * `paramRef` is the `$`-prefixed parameter name to reference on the wire -- either `vector`
 * itself (already a reference) or a generated name, supplied by the caller.
 */
function _addFtHybridVsimArm(
    arm: FtHybridVsimArm,
    paramRef: GlideString,
): GlideString[] {
    const fieldRef = Buffer.isBuffer(arm.field)
        ? Buffer.concat([Buffer.from("@"), arm.field])
        : `@${arm.field}`;

    const args: GlideString[] = ["VSIM", fieldRef, paramRef];

    if (
        arm.k !== undefined ||
        arm.efRuntime !== undefined ||
        arm.shardKRatio !== undefined
    ) {
        const subArgs: GlideString[] = [];
        if (arm.k !== undefined) subArgs.push("K", arm.k.toString());
        if (arm.efRuntime !== undefined)
            subArgs.push("EF_RUNTIME", arm.efRuntime.toString());
        if (arm.shardKRatio !== undefined)
            subArgs.push("SHARD_K_RATIO", arm.shardKRatio.toString());
        args.push("KNN", subArgs.length.toString(), ...subArgs);
    }

    if (arm.yieldScoreAs !== undefined)
        args.push("YIELD_SCORE_AS", arm.yieldScoreAs);

    if (arm.filter !== undefined) {
        const filter = arm.filter;
        const subArgs: GlideString[] = [];
        if (filter.policy !== undefined) subArgs.push("POLICY", filter.policy);
        if (filter.batchSize !== undefined)
            subArgs.push("BATCH_SIZE", filter.batchSize.toString());

        if (subArgs.length === 0) {
            // Uncounted extension: a bare expression, no POLICY/BATCH_SIZE
            // possible in this form (confirmed live).
            args.push("FILTER", filter.expression);
        } else {
            const tokenCount = 1 + subArgs.length;
            args.push(
                "FILTER",
                tokenCount.toString(),
                filter.expression,
                ...subArgs,
            );
        }
    }

    return args;
}

/**
 * @internal
 */
function _addFtHybridCombine(
    combine: FtHybridOptions["combine"],
): GlideString[] {
    if (!combine) return [];

    switch (combine.method) {
        case "RRF": {
            const subArgs: GlideString[] = [];
            if (combine.constant !== undefined)
                subArgs.push("CONSTANT", combine.constant.toString());
            if (combine.window !== undefined)
                subArgs.push("WINDOW", combine.window.toString());
            if (combine.yieldScoreAs !== undefined)
                subArgs.push("YIELD_SCORE_AS", combine.yieldScoreAs);
            return ["COMBINE", "RRF", subArgs.length.toString(), ...subArgs];
        }

        case "LINEAR": {
            const subArgs: GlideString[] = [];
            if (combine.alpha !== undefined)
                subArgs.push("ALPHA", combine.alpha.toString());
            if (combine.beta !== undefined)
                subArgs.push("BETA", combine.beta.toString());
            if (combine.window !== undefined)
                subArgs.push("WINDOW", combine.window.toString());
            if (combine.yieldScoreAs !== undefined)
                subArgs.push("YIELD_SCORE_AS", combine.yieldScoreAs);
            return ["COMBINE", "LINEAR", subArgs.length.toString(), ...subArgs];
        }

        case "FUNCTION": {
            const subArgs: GlideString[] = ["EXPR", combine.expr];
            if (combine.window !== undefined)
                subArgs.push("WINDOW", combine.window.toString());
            if (combine.yieldScoreAs !== undefined)
                subArgs.push("YIELD_SCORE_AS", combine.yieldScoreAs);
            return [
                "COMBINE",
                "FUNCTION",
                subArgs.length.toString(),
                ...subArgs,
            ];
        }

        default:
            throw new Error("Unknown combine method in FtHybridOptions");
    }
}

/**
 * @internal
 *
 * Serializes FT.HYBRID's aggregate-suffix tail: LOCALONLY, then LOAD, then PARAMS (merged with
 * any internally generated VSIM param), then clauses, then WITHCURSOR, then DIALECT. LOCALONLY
 * must come before PARAMS (the server rejects it once PARAMS has been parsed), and PARAMS must
 * come before clauses (a leading FILTER clause emitted right after the VSIM arm would otherwise
 * be claimed by the server's VSIM parser as a VSIM pre-filter instead) -- this order is always
 * emitted regardless of the order `options`'s own fields were set in.
 */
function _addFtHybridOptions(
    options: FtHybridOptions | undefined,
    generatedVsimParam?: { name: GlideString; value: GlideString },
): GlideString[] {
    const args: GlideString[] = [];

    if (options?.localOnly) {
        args.push("LOCALONLY");
    }

    if (options?.loadAll) {
        args.push("LOAD", "*");
    } else if (options?.loadFields) {
        args.push(
            "LOAD",
            options.loadFields.length.toString(),
            ...options.loadFields,
        );
    }

    if (options?.timeout !== undefined) {
        args.push("TIMEOUT", options.timeout.toString());
    }

    const paramEntries: { key: GlideString; value: GlideString }[] = (
        options?.params ?? []
    ).map((p) => ({ key: p.key, value: p.value }));

    if (generatedVsimParam) {
        const keyAsString = (k: GlideString) =>
            Buffer.isBuffer(k) ? k.toString() : k;
        const collision = paramEntries.some(
            (p) => keyAsString(p.key) === generatedVsimParam!.name,
        );

        if (collision) {
            throw new Error(
                `Generated VSIM parameter name '${generatedVsimParam.name}' collides with ` +
                    "an entry already present in params; rename the caller-supplied params entry.",
            );
        }

        paramEntries.push({
            key: generatedVsimParam.name,
            value: generatedVsimParam.value,
        });
    }

    if (paramEntries.length > 0) {
        args.push(
            "PARAMS",
            (paramEntries.length * 2).toString(),
            ...paramEntries.flatMap((p) => [p.key, p.value]),
        );
    }

    args.push(..._addAggregateClauses(options?.clauses));
    args.push(..._addWithCursor(options?.withCursor));

    if (options?.dialect !== undefined) {
        args.push("DIALECT", options.dialect.toString());
    }

    return args;
}

/**
 * @internal
 */
function _addFtAggregateOptions(options?: FtAggregateOptions): GlideString[] {
    if (!options) return [];

    const args: GlideString[] = [];

    if (options.verbatim) args.push("VERBATIM");
    if (options.inorder) args.push("INORDER");
    if (options.slop !== undefined) args.push("SLOP", options.slop.toString());

    if (options.loadAll) args.push("LOAD", "*");
    else if (options.loadFields)
        args.push(
            "LOAD",
            options.loadFields.length.toString(),
            ...options.loadFields,
        );

    if (options.timeout) args.push("TIMEOUT", options.timeout.toString());

    if (options.params) {
        args.push(
            "PARAMS",
            (options.params.length * 2).toString(),
            ...options.params.flatMap((param) => [param.key, param.value]),
        );
    }

    args.push(..._addAggregateClauses(options.clauses));

    if (options.dialect !== undefined)
        args.push("DIALECT", options.dialect.toString());

    if (options.addScores) args.push("ADDSCORES");
    if (options.scorer !== undefined) args.push("SCORER", options.scorer);

    args.push(..._addWithCursor(options.withCursor));

    return args;
}

/**
 * @internal
 *
 * Serializes FILTER/LIMIT/GROUPBY/SORTBY/APPLY clauses, shared between FT.AGGREGATE and
 * FT.HYBRID (the two commands' aggregate-suffix clause grammar is byte-identical).
 */
function _addAggregateClauses(
    clauses: FtAggregateOptions["clauses"],
): GlideString[] {
    if (!clauses) return [];

    const args: GlideString[] = [];

    for (const clause of clauses) {
        switch (clause.type) {
            case "LIMIT":
                args.push(
                    clause.type,
                    clause.offset.toString(),
                    clause.count.toString(),
                );
                break;
            case "FILTER":
                args.push(clause.type, clause.expression);
                break;
            case "GROUPBY":
                args.push(
                    clause.type,
                    clause.properties.length.toString(),
                    ...clause.properties,
                );

                for (const reducer of clause.reducers) {
                    args.push(
                        "REDUCE",
                        reducer.function,
                        reducer.args.length.toString(),
                        ...reducer.args,
                    );
                    if (reducer.name) args.push("AS", reducer.name);
                }

                break;
            case "SORTBY":
                args.push(
                    clause.type,
                    (clause.properties.length * 2).toString(),
                );
                for (const property of clause.properties)
                    args.push(property.property, property.order);
                if (clause.max) args.push("MAX", clause.max.toString());
                break;
            case "APPLY":
                args.push(clause.type, clause.expression, "AS", clause.name);
                break;
            default:
                throw new Error("Unknown clause type in FtAggregateOptions");
        }
    }

    return args;
}

/**
 * @internal
 *
 * Serializes the WITHCURSOR option, shared between FT.AGGREGATE and FT.HYBRID (both commands
 * drive the exact same aggregate-suffix parser, so the option's own grammar is identical
 * regardless of which command opened the cursor).
 */
function _addWithCursor(withCursor?: FtWithCursor): GlideString[] {
    if (!withCursor) return [];

    const args: GlideString[] = ["WITHCURSOR"];
    if (withCursor.count !== undefined)
        args.push("COUNT", withCursor.count.toString());
    if (withCursor.maxidle !== undefined)
        args.push("MAXIDLE", withCursor.maxidle.toString());
    return args;
}

/**
 * @internal
 */
function _addFtSearchOptions(options?: FtSearchOptions): GlideString[] {
    if (!options) return [];

    if (!options.sortby && options.sortbyOrder) {
        throw new Error("sortbyOrder requires sortby to be set.");
    }

    if (!options.sortby && options.withsortkeys) {
        throw new Error("withsortkeys requires sortby to be set.");
    }

    const args: GlideString[] = [];

    // SHARD SCOPE
    if (options.shardScope) {
        args.push(options.shardScope);
    }

    // CONSISTENCY
    if (options.consistency) {
        args.push(options.consistency);
    }

    // NOCONTENT
    if (options.nocontent) {
        args.push("NOCONTENT");
    }

    // VERBATIM
    if (options.verbatim) {
        args.push("VERBATIM");
    }

    // INORDER
    if (options.inorder) {
        args.push("INORDER");
    }

    // SLOP
    if (options.slop !== undefined) {
        args.push("SLOP", options.slop.toString());
    }

    // INKEYS
    if (options.inKeys !== undefined) {
        args.push(
            "INKEYS",
            options.inKeys.length.toString(),
            ...options.inKeys,
        );
    }

    // RETURN
    if (options.returnFields) {
        const returnFields: GlideString[] = [];
        options.returnFields.forEach((returnField) =>
            returnField.alias
                ? returnFields.push(
                      returnField.fieldIdentifier,
                      "AS",
                      returnField.alias,
                  )
                : returnFields.push(returnField.fieldIdentifier),
        );
        args.push("RETURN", returnFields.length.toString(), ...returnFields);
    }

    // SORTBY
    if (options.sortby) {
        args.push("SORTBY", options.sortby);

        if (options.sortbyOrder) {
            args.push(options.sortbyOrder);
        }
    }

    // WITHSORTKEYS
    if (options.withsortkeys) {
        args.push("WITHSORTKEYS");
    }

    // TIMEOUT
    if (options.timeout) {
        args.push("TIMEOUT", options.timeout.toString());
    }

    // PARAMS
    if (options.params) {
        args.push(
            "PARAMS",
            (options.params.length * 2).toString(),
            ...options.params.flatMap((param) => [param.key, param.value]),
        );
    }

    // LIMIT
    if (options.limit) {
        args.push(
            "LIMIT",
            options.limit.offset.toString(),
            options.limit.count.toString(),
        );
    }

    // COUNT: a bare COUNT token is not a real FT.SEARCH keyword --
    // valkey-search's parser rejects it outright. The documented
    // count-only form is LIMIT 0 0, which also returns just the total
    // count with zero result rows. `count` and `limit` are mutually
    // exclusive at the type level, so this never collides with the
    // LIMIT emitted just above.
    if (options.count) {
        args.push("LIMIT", "0", "0");
    }

    // WITHSCORES
    if (options.withScores) {
        args.push("WITHSCORES");
    }

    // SCORER
    if (options.scorer !== undefined) {
        args.push("SCORER", options.scorer);
    }

    // DIALECT
    if (options.dialect !== undefined) {
        args.push("DIALECT", options.dialect.toString());
    }

    return args;
}

/**
 * @internal
 */
async function _handleCustomCommand<T>(
    client: GlideClient | GlideClusterClient,
    args: GlideString[],
    decoderOption: DecoderOption = {},
): Promise<T> {
    return client instanceof GlideClient
        ? ((client as GlideClient).customCommand(
              args,
              decoderOption,
          ) as Promise<T>)
        : ((client as GlideClusterClient).customCommand(
              args,
              decoderOption,
          ) as Promise<T>);
}
