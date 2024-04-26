/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents a geographic position defined by longitude and latitude.<br>
 * The exact limits, as specified by <code>EPSG:900913 / EPSG:3785 / OSGEO:41001</code> are the
 * following:
 *
 * <ul>
 *   <li>Valid longitudes are from <code>-180</code> to <code>180</code> degrees.
 *   <li>Valid latitudes are from <code>-85.05112878</code> to <code>85.05112878</code> degrees.
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public class GeospatialData {
    /** The longitude coordinate. */
    private final double longitude;

    /** The latitude coordinate. */
    private final double latitude;
}
