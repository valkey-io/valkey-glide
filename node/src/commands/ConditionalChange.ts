/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

/* eslint-disable-next-line @typescript-eslint/no-unused-vars */
import { BaseClient } from "src/BaseClient";

/**
 * An optional condition to the {@link BaseClient.geoadd} command.
 */
export enum ConditionalChange {
    /**
     * Only update elements that already exist. Don't add new elements. Equivalent to `XX` in the Valkey API.
     */
    ONLY_IF_EXISTS = "XX",

    /**
     * Only add new elements. Don't update already existing elements. Equivalent to `NX` in the Valkey API.
     * */
    ONLY_IF_DOES_NOT_EXIST = "NX",
}
