/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.FT;

import static glide.api.models.GlideString.gs;
import static glide.utils.ArrayTransformUtils.concatenateArrays;

import glide.api.commands.servermodules.FT;
import glide.api.models.GlideString;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Mandatory parameters for {@link FT#profile} command. */
public class FTProfileOptions {
    private final QueryType queryType;
    private final boolean limited;
    private final GlideString[] query;

    /** Query type being profiled. */
    public enum QueryType {
        SEARCH,
        AGGREGATE
    }

    /**
     * Profile a query given as an array of module command line arguments.
     *
     * @param queryType The query type.
     * @param commandLine Command arguments (not including index name).
     */
    public FTProfileOptions(QueryType queryType, GlideString[] commandLine) {
        this(queryType, commandLine, false);
    }

    /**
     * Profile a query given as an array of module command line arguments.
     *
     * @param queryType The query type.
     * @param commandLine Command arguments (not including index name).
     */
    public FTProfileOptions(QueryType queryType, String[] commandLine) {
        this(queryType, commandLine, false);
    }

    /**
     * Profile a query given as an array of module command line arguments.
     *
     * @param queryType The query type.
     * @param commandLine Command arguments (not including index name).
     * @param limited Either provide a full verbose output or some brief version (limited).
     */
    public FTProfileOptions(QueryType queryType, GlideString[] commandLine, boolean limited) {
        this.queryType = queryType;
        this.query = commandLine;
        this.limited = limited;
    }

    /**
     * Profile a query given as an array of module command line arguments.
     *
     * @param queryType The query type.
     * @param commandLine Command arguments (not including index name).
     * @param limited Either provide a full verbose output or some brief version (limited).
     */
    public FTProfileOptions(QueryType queryType, String[] commandLine, boolean limited) {
        this(
                queryType,
                Stream.of(commandLine).map(GlideString::gs).toArray(GlideString[]::new),
                limited);
    }

    /**
     * Profile an aggregation query with given parameters.
     *
     * @param query The query itself.
     * @param options {@link FT#aggregate} options.
     */
    public FTProfileOptions(String query, FTAggregateOptions options) {
        this(gs(query), options);
    }

    /**
     * Profile an aggregation query with given parameters.
     *
     * @param query The query itself.
     * @param options {@link FT#aggregate} options.
     */
    public FTProfileOptions(GlideString query, FTAggregateOptions options) {
        this(QueryType.AGGREGATE, concatenateArrays(new GlideString[] {query}, options.toArgs()));
    }

    /**
     * Profile a search query with given parameters.
     *
     * @param query The query itself.
     * @param options {@link FT#search} options.
     */
    public FTProfileOptions(String query, FTSearchOptions options) {
        this(gs(query), options);
    }

    /**
     * Profile a search query with given parameters.
     *
     * @param query The query itself.
     * @param options {@link FT#search} options.
     */
    public FTProfileOptions(GlideString query, FTSearchOptions options) {
        this(QueryType.SEARCH, concatenateArrays(new GlideString[] {query}, options.toArgs()));
    }

    /**
     * Profile an aggregation query with given parameters.
     *
     * @param query The query itself.
     * @param options {@link FT#aggregate} options.
     * @param limited Either provide a full verbose output or some brief version (limited).
     */
    public FTProfileOptions(String query, FTAggregateOptions options, boolean limited) {
        this(gs(query), options, limited);
    }

    /**
     * Profile a search query with given parameters.
     *
     * @param query The query itself.
     * @param options {@link FT#search} options.
     * @param limited Either provide a full verbose output or some brief version (limited).
     */
    public FTProfileOptions(GlideString query, FTAggregateOptions options, boolean limited) {
        this(
                QueryType.AGGREGATE,
                concatenateArrays(new GlideString[] {query}, options.toArgs()),
                limited);
    }

    /**
     * Profile an aggregation query with given parameters.
     *
     * @param query The query itself.
     * @param options {@link FT#aggregate} options.
     * @param limited Either provide a full verbose output or some brief version (limited).
     */
    public FTProfileOptions(String query, FTSearchOptions options, boolean limited) {
        this(gs(query), options, limited);
    }

    /**
     * Profile a search query with given parameters.
     *
     * @param query The query itself.
     * @param options {@link FT#search} options.
     * @param limited Either provide a full verbose output or some brief version (limited).
     */
    public FTProfileOptions(GlideString query, FTSearchOptions options, boolean limited) {
        this(QueryType.SEARCH, concatenateArrays(new GlideString[] {query}, options.toArgs()), limited);
    }

    /** Convert to module API. */
    public GlideString[] toArgs() {
        var args = new ArrayList<GlideString>();
        args.add(gs(queryType.toString()));
        if (limited) args.add(gs("LIMITED"));
        args.add(gs("QUERY"));
        args.addAll(List.of(query));
        return args.toArray(GlideString[]::new);
    }
}
