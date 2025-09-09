/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.geospatial;

import glide.api.commands.GeospatialIndicesBaseCommands;

/**
 * The query's shape for {@link GeospatialIndicesBaseCommands} command.
 *
 * @see <a href="https://valkey.io/commands/geosearch/">valkey.io</a>
 */
public final class GeoSearchShape {

    /** The geosearch query's shape. */
    enum SearchShape {
        /** Circular shaped query. */
        BYRADIUS,
        /** Rectangular shaped query. */
        BYBOX
    }

    private final SearchShape shape;
    private final double radius;
    private final double width;
    private final double height;
    private final GeoUnit unit;

    /**
     * Defines a circular search area.
     *
     * @param radius The radius to search by.
     * @param unit The measurement unit of the radius.
     */
    public GeoSearchShape(double radius, GeoUnit unit) {
        this.shape = SearchShape.BYRADIUS;
        this.radius = radius;
        this.unit = unit;

        // unused variables
        this.width = -1;
        this.height = -1;
    }

    /**
     * Defines a rectangular search area.
     *
     * @param width The width to search by.
     * @param height The height to search by.
     * @param unit The measurement unit of the width and height.
     */
    public GeoSearchShape(double width, double height, GeoUnit unit) {
        this.shape = SearchShape.BYBOX;
        this.width = width;
        this.height = height;
        this.unit = unit;

        // unused variable
        this.radius = -1;
    }

    /**
     * Converts GeoSearchShape into a String[].
     *
     * @return String[] An array containing arguments corresponding to the shape to search by.
     */
    public String[] toArgs() {
        switch (shape) {
            case BYRADIUS:
                return new String[] {shape.toString(), Double.toString(radius), unit.getValkeyAPI()};
            case BYBOX:
                return new String[] {
                    shape.toString(), Double.toString(width), Double.toString(height), unit.getValkeyAPI()
                };
            default: // unreachable
                return new String[] {};
        }
    }
}
