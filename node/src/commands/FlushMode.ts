/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

// Import below added to fix up the TSdoc link, but eslint blames for unused import.
/* eslint-disable-next-line @typescript-eslint/no-unused-vars */
import { GlideClient } from "src/GlideClient";

/**
 * Defines flushing mode for {@link GlideClient.flushall|flushall} and {@link GlideClient.flushdb|flushdb} commands.
 *
 * See https://valkey.io/commands/flushall/ and https://valkey.io/commands/flushdb/ for details.
 */
export enum FlushMode {
    /**
     * Flushes synchronously.
     *
     * since Valkey version 6.2.0.
     */
    SYNC = "SYNC",
    /** Flushes asynchronously. */
    ASYNC = "ASYNC",
}
