/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

// Import below added to fix up the TSdoc link, but eslint blames for unused import.
/* eslint-disable-next-line @typescript-eslint/no-unused-vars */
import { BaseClient } from "src/BaseClient";
import { BitmapIndexType } from "./BitmapIndexType";

/**
 * Represents offsets specifying a string interval to analyze in the {@link BaseClient.bitcount|bitcount} command. The offsets are
 * zero-based indexes, with `0` being the first index of the string, `1` being the next index and so on.
 * The offsets can also be negative numbers indicating offsets starting at the end of the string, with `-1` being
 * the last index of the string, `-2` being the penultimate, and so on.
 *
 * See https://valkey.io/commands/bitcount/ for more details.
 */
export class BitOffsetOptions {
    private start: number;
    private end: number;
    private indexType?: BitmapIndexType;

    /**
     * @param start - The starting offset index.
     * @param end - The ending offset index.
     * @param indexType - The index offset type. This option can only be specified if you are using server version 7.0.0 or above.
     *     Could be either {@link BitmapIndexType.BYTE} or {@link BitmapIndexType.BIT}.
     *     If no index type is provided, the indexes will be assumed to be byte indexes.
     */
    constructor(start: number, end: number, indexType?: BitmapIndexType) {
        this.start = start;
        this.end = end;
        this.indexType = indexType;
    }

    /**
     * Converts BitOffsetOptions into a string[].
     *
     * @returns string[]
     */
    public toArgs(): string[] {
        const args = [this.start.toString(), this.end.toString()];

        if (this.indexType) args.push(this.indexType);

        return args;
    }
}
