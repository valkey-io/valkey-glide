/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.geospatial;

import glide.api.commands.GeospatialIndicesBaseCommands;
import glide.api.models.commands.SortOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional arguments for {@link GeospatialIndicesBaseCommands#geosearch(String,
 * GeoSearchOrigin.SearchOrigin, GeoSearchShape, GeoSearchOptions)} command that contains up to 3
 * optional input, including:
 *
 * <ul>
 *   <li>{@link SortOrder} to order the search results by the distance to the center point of the
 *       search area.
 *   <li><code>COUNT</code> to limits the number of search results.
 *   <li><code>ANY</code> option, which could be used with <code>COUNT</code> only, makes command to
 *       return as soon as enough matches are found. This means that the results might may not be
 *       the ones closest to the origin.
 * </ul>
 *
 * @see <a href="https://valkey.io/commands/geosearch/">valkey.io</a>
 */
public class GeoSearchResultOptions {
    /** Valkey API keyword used to perform geosearch with count. */
    public static final String COUNT_VALKEY_API = "COUNT";

    /**
     * Valkey API keyword used to change search behavior to return as soon as enough matches are
     * found.
     */
    public static final String ANY_VALKEY_API = "ANY";

    /** Indicates the order the result should be sorted in. See {@link SortOrder} for detail. */
    private final SortOrder sortOrder;

    /** Indicates the number of matches the result should be limited to. */
    private final long count;

    /** Whether to allow returning as soon as enough matches are found. */
    private final boolean isAny;

    /** Define number of search results. */
    public GeoSearchResultOptions(long count) {
        this.sortOrder = null;
        this.count = count;
        this.isAny = false;
    }

    /** Define number of search results <code>ANY</code> option. */
    public GeoSearchResultOptions(long count, boolean isAny) {
        this.sortOrder = null;
        this.count = count;
        this.isAny = isAny;
    }

    /** Define the sort order only. */
    public GeoSearchResultOptions(SortOrder order) {
        this.sortOrder = order;
        this.count = -1;
        this.isAny = false;
    }

    /** Define the sort order and count. */
    public GeoSearchResultOptions(SortOrder order, long count) {
        this.sortOrder = order;
        this.count = count;
        this.isAny = false;
    }

    /** Configure all parameters. */
    public GeoSearchResultOptions(SortOrder order, long count, boolean isAny) {
        this.sortOrder = order;
        this.count = count;
        this.isAny = isAny;
    }

    /**
     * Converts GeoSearchResultOptions into a String[].
     *
     * @return String[]
     */
    public String[] toArgs() {
        List<String> arguments = new ArrayList<>();

        if (count > 0) {
            arguments.add(COUNT_VALKEY_API);
            arguments.add(Long.toString(count));

            if (isAny) {
                arguments.add(ANY_VALKEY_API);
            }
        }

        if (sortOrder != null) {
            arguments.add(sortOrder.toString());
        }

        return arguments.toArray(new String[0]);
    }
}
