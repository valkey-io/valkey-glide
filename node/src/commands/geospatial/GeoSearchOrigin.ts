/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { GeospatialData } from "./GeospatialData";

/**
 * Basic interface. Please use one of the following implementations:
 *
 * - {@link CoordOrigin}
 *
 * - {@link MemberOrigin}
 */
export type SearchOrigin = {
    /** Convert to the command arguments according to the Valkey API. */
    toArgs(): string[];
};

/** The search origin represented by a {@link GeospatialData} position. */
export class CoordOrigin implements SearchOrigin {
    private readonly position: GeospatialData;

    /**
     * The search origin represented by a {@link GeospatialData} position.
     *
     * @param position - The pivot location to search from.
     */
    public constructor(position: GeospatialData) {
        this.position = position;
    }

    toArgs(): string[] {
        return ["FROMLONLAT"].concat(this.position.toArgs());
    }
}

/** The search origin represented by an existing member. */
export class MemberOrigin implements SearchOrigin {
    private readonly member: string;

    /**
     * The search origin represented by an existing member.
     *
     * @param member - Member (location) name stored in the sorted set to use as a search pivot.
     */
    public constructor(member: string) {
        this.member = member;
    }

    toArgs(): string[] {
        return ["FROMMEMBER", this.member];
    }
}
