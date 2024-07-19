/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

/**
 * Optional arguments to LPOS command.
 *
 * See https://valkey.io/commands/lpos/ for more details.
 */
export class LPosOptions {
    /** Redis API keyword use to determine the rank of the match to return. */
    public static RANK_REDIS_API = "RANK";
    /** Redis API keyword used to extract specific number of matching indices from a list. */
    public static COUNT_REDIS_API = "COUNT";
    /** Redis API keyword used to determine the maximum number of list items to compare. */
    public static MAXLEN_REDIS_API = "MAXLEN";
    /** The rank of the match to return. */
    private rank?: number;
    /** The specific number of matching indices from a list. */
    private count?: number;
    /** The maximum number of comparisons to make between the element and the items in the list. */
    private maxLength?: number;

    constructor({
        rank,
        count,
        maxLength,
    }: {
        rank?: number;
        count?: number;
        maxLength?: number;
    }) {
        this.rank = rank;
        this.count = count;
        this.maxLength = maxLength;
    }

    /**
     *
     * Converts LPosOptions into a string[].
     *
     * @returns string[]
     */
    public toArgs(): string[] {
        const args: string[] = [];

        if (this.rank !== undefined) {
            args.push(LPosOptions.RANK_REDIS_API);
            args.push(this.rank.toString());
        }

        if (this.count !== undefined) {
            args.push(LPosOptions.COUNT_REDIS_API);
            args.push(this.count.toString());
        }

        if (this.maxLength !== undefined) {
            args.push(LPosOptions.MAXLEN_REDIS_API);
            args.push(this.maxLength.toString());
        }

        return args;
    }
}
