/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

// Import below added to fix up the TSdoc link, but eslint complains about unused import.
/* eslint-disable-next-line @typescript-eslint/no-unused-vars */
import { BaseClient } from "src/BaseClient";

/**
 * Enumeration defining the bitwise operation to use in the {@link BaseClient.bitop|bitop} command. Specifies the
 * bitwise operation to perform between the passed in keys.
 */
export enum BitwiseOperation {
    AND = "AND",
    OR = "OR",
    XOR = "XOR",
    NOT = "NOT",
}
