/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

// Import below added to fix up the TSdoc link, but eslint blames for unused import.
/* eslint-disable-next-line @typescript-eslint/no-unused-vars */
import { BaseClient } from "src/BaseClient";

/** Defines the sort order for nested results. */
export enum SortOrder {
    /** Sort by ascending order. */
    ASC = "ASC",
    /** Sort by descending order. */
    DESC = "DESC",
}

// This class unites `GeoSearchResultOptions` and `GeoSearchOptions` of the java client
/**
 * Optional parameters for {@link BaseClient.geosearch|geosearch} command which defines what should be included in the
 * search results and how results should be ordered and limited.
 */
export class GeoSearchResultOptions {
    private readonly withCoord: boolean;
    private readonly withDist: boolean;
    private readonly withHash: boolean;
    private readonly sortOrder?: SortOrder;
    private readonly count?: number;
    private readonly isAny: boolean;

    /**
     * Optional parameters for {@link BaseClient.geosearch|geosearch} command which defines what should be included in the
     * search results and how results should be ordered and limited.
     *
     * @param withCoord - Include the coordinate of the returned items.
     * @param withDist - Include the distance of the returned items from the specified center point.
     *     The distance is returned in the same unit as specified for the `searchBy` argument.
     * @param withHash - Include the geohash of the returned items.
     * @param sortOrder - Indicates the order the result should be sorted in.
     * @param count - Indicates the number of matches the result should be limited to.
     * @param isAny - Whether to allow returning as enough matches are found. This requires `count` parameter to be set.
     */
    public constructor(options: {
        withCoord?: boolean;
        withDist?: boolean;
        withHash?: boolean;
        sortOrder?: SortOrder;
        count?: number;
        isAny?: boolean;
    }) {
        this.withCoord = options.withCoord ?? false;
        this.withDist = options.withDist ?? false;
        this.withHash = options.withHash ?? false;
        this.sortOrder = options.sortOrder;
        this.count = options.count;
        this.isAny = options.isAny ?? false;
    }

    /** Convert to the command arguments according to the Valkey API. */
    public toArgs(): string[] {
        const args: string[] = [];

        if (this.withCoord) args.push("WITHCOORD");
        if (this.withDist) args.push("WITHDIST");
        if (this.withHash) args.push("WITHHASH");

        if (this.count) {
            args.push("COUNT", this.count?.toString());

            if (this.isAny) args.push("ANY");
        }

        if (this.sortOrder) args.push(this.sortOrder);

        return args;
    }
}
