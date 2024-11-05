/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import {
    convertGlideRecordToRecord,
    Decoder,
    DecoderOption,
    GlideRecord,
    GlideString,
} from "../BaseClient";
import { GlideClient } from "../GlideClient";
import { GlideClusterClient } from "../GlideClusterClient";
import { Field, FtCreateOptions } from "./GlideFtOptions";

/** Data type of {@link GlideFt.info | info} command response. */
type FtInfoReturnType = Record<
    string,
    | GlideString
    | number
    | GlideString[]
    | Record<string, GlideString | Record<string, GlideString | number>[]>
>;

/** Module for Vector Search commands. */
export class GlideFt {
    /**
     * Creates an index and initiates a backfill of that index.
     *
     * @param client The client to execute the command.
     * @param indexName The index name for the index to be created.
     * @param schema The fields of the index schema, specifying the fields and their types.
     * @param options Optional arguments for the `FT.CREATE` command. See {@link FtCreateOptions}.
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
     * @param client The client to execute the command.
     * @param indexName The index name.
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
}

/**
 * @internal
 */
async function _handleCustomCommand<T>(
    client: GlideClient | GlideClusterClient,
    args: GlideString[],
    decoderOption?: DecoderOption,
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
