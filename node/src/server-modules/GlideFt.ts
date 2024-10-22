/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { Decoder, DecoderOption, GlideString } from "../BaseClient";
import { GlideClient } from "../GlideClient";
import { GlideClusterClient } from "../GlideClusterClient";
import { Field, FtCreateOptions } from "./GlideFtOptions";

/** Module for Vector Search commands */
export class GlideFt {
    /**
     * Creates an index and initiates a backfill of that index.
     *
     * @param client The client to execute the command.
     * @param indexName The index name for the index to be created.
     * @param schema The fields of the index schema, specifying the fields and their types.
     * @param options Optional arguments for the `FT.CREATE` command.
     *
     * @returns If the index is successfully created, returns "OK".
     */
    static async create(
        client: GlideClient | GlideClusterClient,
        indexName: GlideString,
        schema: Field[],
        options?: FtCreateOptions | DecoderOption,
    ): Promise<"OK" | null> {
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

            // TagField attributes
            if (f.type === "TAG") {
                if (f.separator) {
                    args.push("SEPARATOR", f.separator);
                }

                if (f.caseSensitive) {
                    args.push("CASESENSITIVE");
                }
            }

            if (f.type === "VECTOR") {
                // VectorField attributes
                if (f.algorithm) {
                    args.push(f.algorithm);
                }

                if (f.attributes) {
                    const attributes: GlideString[] = [];

                    // all VectorFieldAttributes attributes
                    if (f.attributes.dim) {
                        attributes.push("DIM", f.attributes.dim.toString());
                    }

                    if (f.attributes.distanceMetric) {
                        attributes.push("DISTANCE_METRIC", f.attributes.distanceMetric.toString());
                    }
                    
                    if (f.attributes.type) {
                        attributes.push("TYPE", f.attributes.type.toString());
                    }

                    if (f.attributes.initialCap) {
                        attributes.push("INITIAL_CAP", f.attributes.initialCap.toString());
                    }

                    // VectorFieldAttributesHnsw attributes
                    if ("m" in f.attributes && f.attributes.m) {
                        attributes.push("M", f.attributes.m.toString());
                    }

                    if ("efContruction" in f.attributes && f.attributes.efContruction) {
                        attributes.push("EF_CONSTRUCTION", f.attributes.efContruction.toString());
                    }

                    if ("efRuntime" in f.attributes && f.attributes.efRuntime) {
                        attributes.push("EF_RUNTIME", f.attributes.efRuntime.toString());
                    }

                    args.push(attributes.length.toString(), ...attributes);
                }
            }
        });

        console.log("Args:", args);

        return _handleCustomCommand(client, args, {
            decoder: Decoder.String,
        }) as Promise<"OK" | null>;
    };
};

/**
 * @internal
 */
function _handleCustomCommand(
    client: GlideClient | GlideClusterClient,
    args: GlideString[],
    decoderOption: DecoderOption,
) {
    return client instanceof GlideClient
        ? (client as GlideClient).customCommand(args, decoderOption)
        : (client as GlideClusterClient).customCommand(args, decoderOption);
}
