/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

/**
 * Represents a geographic position defined by longitude and latitude.
 * The exact limits, as specified by `EPSG:900913 / EPSG:3785 / OSGEO:41001` are the
 * following:
 *
 *   Valid longitudes are from `-180` to `180` degrees.
 *   Valid latitudes are from `-85.05112878` to `85.05112878` degrees.
 */
export class GeospatialData {
    private longitude: number;

    private latitude: number;

    /**
     * Default constructor for GeospatialData.
     *
     * @param longitude - The longitude coordinate.
     * @param latitude - The latitude coordinate.
     */
    constructor(longitude: number, latitude: number) {
        this.longitude = longitude;
        this.latitude = latitude;
    }

    /**
     * Converts GeospatialData into a string[].
     *
     * @returns string[]
     */
    public toArgs(): string[] {
        return [this.longitude.toString(), this.latitude.toString()];
    }
}
