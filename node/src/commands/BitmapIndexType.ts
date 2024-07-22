/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

/**
 * Enumeration specifying if index arguments are BYTE indexes or BIT indexes.
 * Can be specified in {@link BitOffsetOptions}, which is an optional argument to the {@link BaseClient.bitcount|bitcount} command.
 * Can also be specified as an optional argument to the {@link BaseClient.bitposInverval|bitposInterval} command.
 *
 * since - Valkey version 7.0.0.
 */
export enum BitmapIndexType {
    /** Specifies that provided indexes are byte indexes. */
    BYTE = "BYTE",
    /** Specifies that provided indexes are bit indexes. */
    BIT = "BIT",
}
