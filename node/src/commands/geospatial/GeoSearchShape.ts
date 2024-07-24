/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { GeoUnit } from "./GeoUnit";

/**
 * Basic interface. Please use one of the following implementations:
 *
 * - {@link GeoCircleShape}
 *
 * - {@link GeoBoxShape}
 */
export type GeoSearchShape = {
    /** Convert to the command arguments according to the Valkey API. */
    toArgs(): string[];
};

/** Circle search shape defined by the radius value and measurement unit. */
export class GeoCircleShape implements GeoSearchShape {
    private readonly radius: number;
    private readonly unit: GeoUnit;

    /**
     * Circle search shape defined by the radius value and measurement unit.
     *
     * @param radius - The radius to search by.
     * @param unit - The measurement unit of the radius.
     */
    public constructor(radius: number, unit: GeoUnit) {
        this.radius = radius;
        this.unit = unit;
    }

    toArgs(): string[] {
        return ["BYRADIUS", this.radius.toString(), this.unit];
    }
}

/** Rectangle search shape defined by the width and height and measurement unit. */
export class GeoBoxShape implements GeoSearchShape {
    private readonly width: number;
    private readonly height: number;
    private readonly unit: GeoUnit;

    /**
     * Rectangle search shape defined by the width and height and measurement unit.
     *
     * @param width - The width to search by.
     * @param height - The height to search by.
     * @param unit - The measurement unit of the width and height.
     */
    public constructor(width: number, height: number, unit: GeoUnit) {
        this.width = width;
        this.height = height;
        this.unit = unit;
    }

    toArgs(): string[] {
        return [
            "BYBOX",
            this.width.toString(),
            this.height.toString(),
            this.unit,
        ];
    }
}
