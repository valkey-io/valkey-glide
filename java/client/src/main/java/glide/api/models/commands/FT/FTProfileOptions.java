/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.FT;

import static glide.api.models.GlideString.gs;
import static glide.utils.ArrayTransformUtils.concatenateArrays;

import glide.api.commands.servermodules.FT;
import glide.api.models.GlideString;
import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;

/** Mandatory parameters for {@link FT#profile} command. */
public class FTProfileOptions {
    private final QueryType queryType;
    private final boolean limited;
    private final GlideString[] commandLine;

    /** Query type being profiled. */
    enum QueryType {
        SEARCH,
        AGGREGATE
    }

    /**
     * Profile an aggregation query with given parameters.
     *
     * @param query The query itself.
     * @param options {@link FT#aggregate} options.
     */
    public FTProfileOptions(@NonNull String query, @NonNull FTAggregateOptions options) {
        this(gs(query), options);
    }

    /**
     * Profile an aggregation query with given parameters.
     *
     * @param query The query itself.
     * @param options {@link FT#aggregate} options.
     */
    public FTProfileOptions(@NonNull GlideString query, @NonNull FTAggregateOptions options) {
        this(query, options, false);
    }

    /**
     * Profile a search query with given parameters.
     *
     * @param query The query itself.
     * @param options {@link FT#search} options.
     */
    public FTProfileOptions(@NonNull String query, @NonNull FTSearchOptions options) {
        this(gs(query), options);
    }

    /**
     * Profile a search query with given parameters.
     *
     * @param query The query itself.
     * @param options {@link FT#search} options.
     */
    public FTProfileOptions(@NonNull GlideString query, @NonNull FTSearchOptions options) {
        this(query, options, false);
    }

    /**
     * Profile an aggregation query with given parameters.
     *
     * @param query The query itself.
     * @param options {@link FT#aggregate} options.
     * @param limited Either provide a full verbose output or some brief version (limited).
     */
    public FTProfileOptions(
            @NonNull String query, @NonNull FTAggregateOptions options, boolean limited) {
        this(gs(query), options, limited);
    }

    /**
     * Profile a search query with given parameters.
     *
     * @param query The query itself.
     * @param options {@link FT#search} options.
     * @param limited Either provide a full verbose output or some brief version (limited).
     */
    public FTProfileOptions(
            @NonNull GlideString query, @NonNull FTAggregateOptions options, boolean limited) {
        queryType = QueryType.AGGREGATE;
        commandLine = concatenateArrays(new GlideString[] {query}, options.toArgs());
        this.limited = limited;
    }

    /**
     * Profile an aggregation query with given parameters.
     *
     * @param query The query itself.
     * @param options {@link FT#aggregate} options.
     * @param limited Either provide a full verbose output or some brief version (limited).
     */
    public FTProfileOptions(
            @NonNull String query, @NonNull FTSearchOptions options, boolean limited) {
        this(gs(query), options, limited);
    }

    /**
     * Profile a search query with given parameters.
     *
     * @param query The query itself.
     * @param options {@link FT#search} options.
     * @param limited Either provide a full verbose output or some brief version (limited).
     */
    public FTProfileOptions(
            @NonNull GlideString query, @NonNull FTSearchOptions options, boolean limited) {
        queryType = QueryType.SEARCH;
        commandLine = concatenateArrays(new GlideString[] {query}, options.toArgs());
        this.limited = limited;
    }

    /** Convert to module API. */
    public GlideString[] toArgs() {
        var args = new ArrayList<GlideString>();
        args.add(gs(queryType.toString()));
        if (limited) args.add(gs("LIMITED"));
        args.add(gs("QUERY"));
        args.addAll(List.of(commandLine));
        return args.toArray(GlideString[]::new);
    }
}
