/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.commands.GeoAddOptions;
import glide.api.models.commands.GeospatialData;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "Geospatial Indices Commands" group for standalone and
 * cluster clients.
 *
 * @see <a href="https://redis.io/commands/?group=geo">Geospatial Indices Commands</a>
 */
public interface GeospatialIndicesBaseCommands {

    /**
     * Adds geospatial members with their positions to the specified sorted set stored at <code>key
     * </code>.<br>
     * If a member is already a part of the sorted set, its position is updated.
     *
     * @see <a href="https://redis.io/commands/geoadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersToGeospatialData A mapping of member names to their corresponding positions. See
     *     {@link GeospatialData}. The command will report an error when the user attempts to index
     *     coordinates outside the specified ranges.
     * @param options The GeoAdd options. {@link GeoAddOptions}
     * @return The number of elements added to the sorted set. If <code>changed</code> is set to
     *     <code>true</code> in the options, returns the number of elements updated in the sorted set.
     * @example
     *     <pre>{@code
     * GeoAddOptions options = new GeoAddOptions(ConditionalChange.ONLY_IF_EXISTS, true);
     * Long num = client.geoadd("mySortedSet", Map.of("Palermo", new GeospatialData(13.361389, 38.115556)), options).get();
     * assert num == 1L; // Indicates that the position of an existing member in the sorted set "mySortedSet" has been updated.
     * }</pre>
     */
    CompletableFuture<Long> geoadd(
            String key, Map<String, GeospatialData> membersToGeospatialData, GeoAddOptions options);

    /**
     * Adds geospatial members with their positions to the specified sorted set stored at <code>key
     * </code>.<br>
     * If a member is already a part of the sorted set, its position is updated.<br>
     * To perform a <code>geoadd</code> operation while specifying optional parameters, use {@link
     * #geoadd(String, Map, GeoAddOptions)}.
     *
     * @see <a href="https://redis.io/commands/geoadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersToGeospatialData A mapping of member names to their corresponding positions. See
     *     {@link GeospatialData}. The command will report an error when the user attempts to index
     *     coordinates outside the specified ranges.
     * @return The number of elements added to the sorted set.
     * @example
     *     <pre>{@code
     * Long num = client.geoadd("mySortedSet", Map.of("Palermo", new GeospatialData(13.361389, 38.115556), "Catania", new GeospatialData(15.087269, 37.502669)).get();
     * assert num == 2L; // Indicates that two elements have been added to the sorted set "mySortedSet".
     * }</pre>
     */
    CompletableFuture<Long> geoadd(String key, Map<String, GeospatialData> membersToGeospatialData);
}
