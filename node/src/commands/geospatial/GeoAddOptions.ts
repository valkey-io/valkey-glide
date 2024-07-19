/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { ConditionalChange } from "../ConditionalChange";

/**
 * Optional arguments for the GeoAdd command.
 *
 * See https://valkey.io/commands/geoadd/ for more details.
 */
export class GeoAddOptions {
    /** Valkey API keyword use to modify the return value from the number of new elements added, to the total number of elements changed. */
    public static CHANGED_VALKEY_API = "CH";

    private updateMode?: ConditionalChange;

    private changed?: boolean;

    /**
     * Default constructor for GeoAddOptions.
     *
     * @param updateMode - Options for handling existing members. See {@link ConditionalChange}.
     * @param latitude - If `true`, returns the count of changed elements instead of new elements added.
     */
    constructor(options: {
        updateMode?: ConditionalChange;
        changed?: boolean;
    }) {
        this.updateMode = options.updateMode;
        this.changed = options.changed;
    }

    /**
     * Converts GeoAddOptions into a string[].
     *
     * @returns string[]
     */
    public toArgs(): string[] {
        const args: string[] = [];

        if (this.updateMode) {
            args.push(this.updateMode);
        }

        if (this.changed) {
            args.push(GeoAddOptions.CHANGED_VALKEY_API);
        }

        return args;
    }
}
