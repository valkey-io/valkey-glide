/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.FT;

import static glide.api.models.GlideString.gs;

import glide.api.models.GlideString;
import java.util.ArrayList;
import lombok.NonNull;

/**
 * The SEARCH arm of an FT.HYBRID query: an ordinary (non-vector) FT.SEARCH-style text/tag/
 * numeric query, run alongside the VSIM arm and fused with it. A vector query (e.g. a {@code
 * =>[KNN ...]} clause) is rejected here -- use {@link FTHybridVsimArm} for the vector half
 * instead.
 */
public class FTHybridSearchArm {
    private final GlideString query;
    private final GlideString scorer;
    private final GlideString yieldScoreAs;

    /**
     * Initialize a new instance with no scoring or alias options.
     *
     * @param query The text/tag/numeric query, in the same language FT.SEARCH uses. Must not be a
     *     vector query (e.g. must not contain a {@code =>[KNN ...]} clause); the server rejects
     *     that combination.
     */
    public FTHybridSearchArm(@NonNull String query) {
        this(gs(query), null, null);
    }

    /**
     * Initialize a new instance with no scoring or alias options.
     *
     * @param query The text/tag/numeric query, in the same language FT.SEARCH uses. Must not be a
     *     vector query (e.g. must not contain a {@code =>[KNN ...]} clause); the server rejects
     *     that combination.
     */
    public FTHybridSearchArm(@NonNull GlideString query) {
        this(query, null, null);
    }

    /**
     * Initialize a new instance.
     *
     * @param query The text/tag/numeric query, in the same language FT.SEARCH uses.
     * @param scorer The scoring function used to rank this arm's results (valkey-search 1.3.0).
     *     {@code "BM25STD"} is the only value currently registered and selectable; any other value
     *     is rejected by the server.
     * @param yieldScoreAs Exposes this arm's score under the given column name, reachable from
     *     aggregate-tail clauses. This alias cannot itself be named inside a {@link
     *     FTAggregateOptions.LoadField}/{@link FTHybridOptions} load-field entry -- it is already
     *     present in the default reply with no LOAD needed.
     */
    public FTHybridSearchArm(
            @NonNull String query, GlideString scorer, GlideString yieldScoreAs) {
        this(gs(query), scorer, yieldScoreAs);
    }

    /**
     * Initialize a new instance.
     *
     * @param query The text/tag/numeric query, in the same language FT.SEARCH uses.
     * @param scorer The scoring function used to rank this arm's results (valkey-search 1.3.0).
     *     {@code "BM25STD"} is the only value currently registered and selectable; any other value
     *     is rejected by the server.
     * @param yieldScoreAs Exposes this arm's score under the given column name, reachable from
     *     aggregate-tail clauses. This alias cannot itself be named inside a {@link
     *     FTAggregateOptions.LoadField}/{@link FTHybridOptions} load-field entry -- it is already
     *     present in the default reply with no LOAD needed.
     */
    public FTHybridSearchArm(
            @NonNull GlideString query, GlideString scorer, GlideString yieldScoreAs) {
        this.query = query;
        this.scorer = scorer;
        this.yieldScoreAs = yieldScoreAs;
    }

    /**
     * Initialize a new instance with a scorer and no yield-score alias.
     *
     * @param query The text/tag/numeric query.
     * @param scorer The scoring function name.
     */
    public static FTHybridSearchArm withScorer(@NonNull String query, @NonNull String scorer) {
        return new FTHybridSearchArm(gs(query), gs(scorer), null);
    }

    /**
     * Initialize a new instance with a yield-score alias and no scorer.
     *
     * @param query The text/tag/numeric query.
     * @param yieldScoreAs The column name to expose this arm's score under.
     */
    public static FTHybridSearchArm withYieldScoreAs(
            @NonNull String query, @NonNull String yieldScoreAs) {
        return new FTHybridSearchArm(gs(query), null, gs(yieldScoreAs));
    }

    /** Get the arguments for the SEARCH clause. */
    public GlideString[] toArgs() {
        ArrayList<GlideString> args = new ArrayList<>();
        args.add(gs("SEARCH"));
        args.add(query);
        if (scorer != null) {
            args.add(gs("SCORER"));
            args.add(scorer);
        }
        if (yieldScoreAs != null) {
            args.add(gs("YIELD_SCORE_AS"));
            args.add(yieldScoreAs);
        }
        return args.toArray(new GlideString[0]);
    }
}
