/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { Decoder, DecoderOption, GlideString } from "../BaseClient";
import { GlideClient } from "../GlideClient";
import { GlideClusterClient } from "../GlideClusterClient";
import { Field, FtCreateOptions } from "./GlideFtOptions";

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
export async function create(
    client: GlideClient | GlideClusterClient,
    indexName: GlideString,
    schema: Field[],
    options?: FtCreateOptions | DecoderOption,
): Promise<"OK" | null> {
    const args: GlideString[] = ["FT.CREATE", indexName];

    schema.forEach((f) => {
        args.push(f.toString());
    });

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

    return _handleCustomCommand(client, args, {
        decoder: Decoder.String,
    }) as Promise<"OK" | null>;
}

function _handleCustomCommand(
    client: GlideClient | GlideClusterClient,
    args: GlideString[],
    decoderOption: DecoderOption,
) {
    return client instanceof GlideClient
        ? (client as GlideClient).customCommand(args, decoderOption)
        : (client as GlideClusterClient).customCommand(args, decoderOption);
}
