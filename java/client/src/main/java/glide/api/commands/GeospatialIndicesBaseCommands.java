/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.commands.geospatial.GeoAddOptions;
import glide.api.models.commands.geospatial.GeoUnit;
import glide.api.models.commands.geospatial.GeospatialData;
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
     * @param membersToGeospatialData A mapping of member names to their corresponding positions - see
     *     {@link GeospatialData}. The command will report an error when the user attempts to index
     *     coordinates outside the specified ranges.
     * @param options The GeoAdd options - see {@link GeoAddOptions}
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
     * @param membersToGeospatialData A mapping of member names to their corresponding positions - see
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

    /**
     * Returns the positions (longitude,latitude) of all the specified <code>members</code> of the
     * geospatial index represented by the sorted set at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/geopos">valkey.io</a> for more details.
     * @param key The key of the sorted set.
     * @param members The members for which to get the positions.
     * @return A 2D <code>array</code> which represent positions (longitude and latitude)
     *     corresponding to the given members. If a member does not exist, its position will be
     *     </code>null</code>.
     * @example
     *     <pre>{@code
     * // When added via GEOADD, the geospatial coordinates are converted into a 52 bit geohash, so the coordinates
     * // returned might not be exactly the same as the input values
     * client.geoadd("mySortedSet", Map.of("Palermo", new GeospatialData(13.361389, 38.115556), "Catania", new GeospatialData(15.087269, 37.502669))).get();
     * Double[][] result = client.geopos("mySortedSet", new String[]{"Palermo", "Catania", "NonExisting"}).get();
     * System.out.println(Arrays.deepToString(result));
     * }</pre>
     */
    CompletableFuture<Double[][]> geopos(String key, String[] members);

    /**
     * Returns the distance between <code>member1</code> and <code>member2</code> saved in the
     * geospatial index stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/geodist">valkey.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member1 The name of the first member.
     * @param member2 The name of the second member.
     * @param geoUnit The unit of distance measurement {@link GeoUnit}.
     * @return The distance between <code>member1</code> and <code>member2</code>. If one or both
     *     members do not exist, or if the key does not exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Double result = client.geodist("mySortedSet", "Palermo", "Catania", GeoUnit.KILOMETERS).get();
     * System.out.println(result);
     * }</pre>
     */
    CompletableFuture<Double> geodist(String key, String member1, String member2, GeoUnit geoUnit);

    /**
     * Returns the distance between <code>member1</code> and <code>member2</code> saved in the
     * geospatial index stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/geodist">valkey.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member1 The name of the first member.
     * @param member2 The name of the second member.
     * @return The distance between <code>member1</code> and <code>member2</code>. If one or both
     *     members do not exist, or if the key does not exist, returns <code>null</code>. The default
     *     unit is <code>METERS</code>.
     * @example
     *     <pre>{@code
     * Double result = client.geodist("mySortedSet", "Palermo", "Catania").get();
     * System.out.println(result);
     * }</pre>
     */
    CompletableFuture<Double> geodist(String key, String member1, String member2);
}
