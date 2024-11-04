/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import {
    convertGlideRecordToRecord,
    Decoder,
    DecoderOption,
    GlideRecord,
    GlideReturnType,
    GlideString,
} from "../BaseClient";
import { GlideClient } from "../GlideClient";
import { GlideClusterClient } from "../GlideClusterClient";
import { Field, FtCreateOptions, FtSearchOptions } from "./GlideFtOptions";

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

/** Module for Vector Search commands. */
export class GlideFt {
    /**
     * Creates an index and initiates a backfill of that index.
     *
     * @param client - The client to execute the command.
     * @param indexName - The index name for the index to be created.
     * @param schema - The fields of the index schema, specifying the fields and their types.
     * @param options - (Optional) Options for the `FT.CREATE` command. See {@link FtCreateOptions}.
     *
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
        }

        args.push("SCHEMA");

        schema.forEach((f) => {
            args.push(f.name);

            if (f.alias) {
                args.push("AS", f.alias);
            }

            args.push(f.type);

            switch (f.type) {
                case "TAG": {
                    if (f.separator) {
                        args.push("SEPARATOR", f.separator);
                    }

                    if (f.caseSensitive) {
                        args.push("CASESENSITIVE");
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
                        if ("m" in f.attributes && f.attributes.m) {
                            attributes.push("M", f.attributes.m.toString());
                        }

                        if (
                            "efContruction" in f.attributes &&
                            f.attributes.efContruction
                        ) {
                            attributes.push(
                                "EF_CONSTRUCTION",
                                f.attributes.efContruction.toString(),
                            );
                        }

                        if (
                            "efRuntime" in f.attributes &&
                            f.attributes.efRuntime
                        ) {
                            attributes.push(
                                "EF_RUNTIME",
                                f.attributes.efRuntime.toString(),
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
     *
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
     * Returns information about a given index.
     *
     * @param client - The client to execute the command.
     * @param indexName - The index name.
     * @param options - (Optional) See {@link DecoderOption}.
     *
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
     * ```
     */
    static async info(
        client: GlideClient | GlideClusterClient,
        indexName: GlideString,
        options?: DecoderOption,
    ): Promise<FtInfoReturnType> {
        const args: GlideString[] = ["FT.INFO", indexName];

        return (
            _handleCustomCommand(client, args, options) as Promise<
                GlideRecord<GlideString>
            >
        ).then(convertGlideRecordToRecord);
    }

    /**
     * Uses the provided query expression to locate keys within an index. Once located, the count
     * and/or content of indexed fields within those keys can be returned.
     *
     * @param client - The client to execute the command.
     * @param indexName - The index name to search into.
     * @param query - The text query to search.
     * @param options - (Optional) See {@link FtSearchOptions} and {@link DecoderOption}.
     *
     * @returns A two-element array, where the first element is the number of documents in the result set, and the
     * second element has the format: `GlideRecord<GlideRecord<GlideString>>`:
     * a mapping between document names and a map of their attributes.
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
     * ```
     */
    static async search(
        client: GlideClient | GlideClusterClient,
        indexName: GlideString,
        query: GlideString,
        options?: FtSearchOptions & DecoderOption,
    ): Promise<FtSearchReturnType> {
        const args: GlideString[] = ["FT.SEARCH", indexName, query];

        if (options) {
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
                args.push(
                    "RETURN",
                    returnFields.length.toString(),
                    ...returnFields,
                );
            }

            // TIMEOUT
            if (options.timeout) {
                args.push("TIMEOUT", options.timeout.toString());
            }

            // PARAMS
            if (options.params) {
                args.push("PARAMS", (options.params.length * 2).toString());
                options.params.forEach((param) =>
                    args.push(param.key, param.value),
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

            // COUNT
            if (options.count) {
                args.push("COUNT");
            }
        }

        return _handleCustomCommand(client, args, options) as Promise<
            [number, GlideRecord<GlideRecord<GlideString>>]
        >;
    }

    /**
     * Adds an alias for an index. The new alias name can be used anywhere that an index name is required.
     * 
     * @param client The client to execute the command.
     * @param indexName The alias to be added to the index.
     * @param alias The index name for which the alias has to be added.
     * @returns "OK"
     * 
     * @example
     */
    static async aliasadd(
        client: GlideClient | GlideClusterClient,
        indexName: GlideString,
        alias: GlideString
    ): Promise<"OK"> {
        const args: GlideString[] = ["FT.ALIASADD", alias, indexName];
        return _handleCustomCommand(client, args, {
            decoder: Decoder.String
        }) as Promise<"OK">;
    }

    /**
     * Deletes an existing alias for an index.
     * 
     * @param client The client to execute the command.
     * @param alias The existing alias to be deleted for an index.
     * 
     * @returns "OK"
     * 
     * @example
     */
    static async aliasdel(
        client: GlideClient | GlideClusterClient,
        alias: GlideString
    ): Promise<"OK"> {
        const args: GlideString[] = ["FT.ALIASDEL", alias];
        return _handleCustomCommand(client, args, {
            decoder: Decoder.String
        }) as Promise<"OK">;
    }

    /**
     * Updates an existing alias to point to a different physical index. This command only affects future references to the alias.
     * 
     * @param client The client to execute the command.
     * @param alias The alias name. This alias will now be pointed to a different index.
     * @param indexName The index name for which an existing alias has to updated.
     *  
     * @returns "OK"
     * 
     * @example
     */
    static async aliasupdate(
        client: GlideClient | GlideClusterClient,
        alias: GlideString,
        indexName: GlideString
    ): Promise<"OK"> {
        const args: GlideString[] = ["FT.ALIASUPDATE", alias, indexName];
        return _handleCustomCommand(client, args, {
            decoder: Decoder.String
        }) as Promise<"OK">;
    }
}

/**
 * @internal
 */
async function _handleCustomCommand(
    client: GlideClient | GlideClusterClient,
    args: GlideString[],
    decoderOption: DecoderOption = {},
): Promise<GlideReturnType> {
    return client instanceof GlideClient
        ? (client as GlideClient).customCommand(args, decoderOption)
        : (client as GlideClusterClient).customCommand(args, decoderOption);
}
