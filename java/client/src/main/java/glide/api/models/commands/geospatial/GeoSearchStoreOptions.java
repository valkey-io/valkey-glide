/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.geospatial;

import glide.api.commands.GeospatialIndicesBaseCommands;
import lombok.Builder;

/**
 * Optional arguments for {@link GeospatialIndicesBaseCommands#geosearchstore(String, String,
 * GeoSearchOrigin.SearchOrigin, GeoSearchShape, GeoSearchStoreOptions)} command.
 *
 * @see <a href="https://redis.io/commands/geosearch/">redis.io</a>
 */
@Builder
public final class GeoSearchStoreOptions {
    /**
     * Redis API keyword used to perform geosearchstore and optionally sort the results with their
     * distance from the center.
     */
    public static final String GEOSEARCHSTORE_REDIS_API = "STOREDIST";

    /**
     * boolean value indicating if the STOREDIST option should be included. Can be included in builder
     * construction by using {@link GeoSearchStoreOptionsBuilder#storedist()}.
     */
    private boolean storeDist;

    /**
     * Converts GeoSearchStoreOptions into a String[].
     *
     * @return String[]
     */
    public String[] toArgs() {
        if (storeDist) {
            return new String[] {GEOSEARCHSTORE_REDIS_API};
        }

        return new String[] {};
    }

    public static class GeoSearchStoreOptionsBuilder {
        public GeoSearchStoreOptionsBuilder() {}

        public GeoSearchStoreOptionsBuilder storedist() {
            return storeDist(true);
        }
    }
}
