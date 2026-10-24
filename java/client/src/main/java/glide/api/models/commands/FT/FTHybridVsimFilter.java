/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.FT;

import static glide.api.models.GlideString.gs;

import glide.api.models.GlideString;
import java.util.ArrayList;
import lombok.NonNull;

/**
 * A pre-filter on the VSIM arm's vector search, written in the same query language the SEARCH
 * arm uses (e.g. {@code @title:hello}). With a filter present, the VSIM arm becomes an ordinary
 * FT.SEARCH-style vector query under the hood ({@code <filter>=>[KNN ...]}), so the filter
 * decides membership and the arm's score stays the vector distance -- it is never replaced by a
 * text-relevance score the way a plain hybrid text query's score would be.
 */
public class FTHybridVsimFilter {
    private final GlideString expression;
    private final HybridFilterPolicy policy;
    private final Integer batchSize;

    /**
     * Initialize a new instance with no policy or batch size (the uncounted extension form).
     *
     * @param expression The filter expression, in the same query language FT.SEARCH uses.
     */
    public FTHybridVsimFilter(@NonNull String expression) {
        this(gs(expression), null, null);
    }

    /**
     * Initialize a new instance with no policy or batch size (the uncounted extension form).
     *
     * @param expression The filter expression, in the same query language FT.SEARCH uses.
     */
    public FTHybridVsimFilter(@NonNull GlideString expression) {
        this(expression, null, null);
    }

    /**
     * Initialize a new instance with a policy and no batch size.
     *
     * @param expression The filter expression.
     * @param policy Controls how the filter is applied. Setting this forces the wire-level counted
     *     VSIM FILTER form (required for the server to recognise POLICY at all).
     */
    public FTHybridVsimFilter(@NonNull String expression, @NonNull HybridFilterPolicy policy) {
        this(gs(expression), policy, null);
    }

    /**
     * Initialize a new instance with a policy and no batch size.
     *
     * @param expression The filter expression.
     * @param policy Controls how the filter is applied. Setting this forces the wire-level counted
     *     VSIM FILTER form (required for the server to recognise POLICY at all).
     */
    public FTHybridVsimFilter(@NonNull GlideString expression, @NonNull HybridFilterPolicy policy) {
        this(expression, policy, null);
    }

    /**
     * Initialize a new instance.
     *
     * @param expression The filter expression.
     * @param policy Controls how the filter is applied. See {@link HybridFilterPolicy}.
     * @param batchSize Tunes how many candidates {@code POLICY BATCHES} draws per batch. Requires
     *     {@code policy == HybridFilterPolicy.BATCHES}. The value is accepted but currently
     *     discarded by the server (valkey-search issue #1457), so this currently has no observable
     *     effect; included for forward compatibility and because the server still validates its
     *     presence/ordering against {@code policy}.
     * @throws IllegalArgumentException If {@code batchSize} is set without {@code policy ==
     *     HybridFilterPolicy.BATCHES}.
     */
    public FTHybridVsimFilter(
            @NonNull String expression, HybridFilterPolicy policy, Integer batchSize) {
        this(gs(expression), policy, batchSize);
    }

    /**
     * Initialize a new instance.
     *
     * @param expression The filter expression.
     * @param policy Controls how the filter is applied. See {@link HybridFilterPolicy}.
     * @param batchSize Tunes how many candidates {@code POLICY BATCHES} draws per batch. Requires
     *     {@code policy == HybridFilterPolicy.BATCHES}. The value is accepted but currently
     *     discarded by the server (valkey-search issue #1457), so this currently has no observable
     *     effect; included for forward compatibility and because the server still validates its
     *     presence/ordering against {@code policy}.
     * @throws IllegalArgumentException If {@code batchSize} is set without {@code policy ==
     *     HybridFilterPolicy.BATCHES}.
     */
    public FTHybridVsimFilter(
            @NonNull GlideString expression, HybridFilterPolicy policy, Integer batchSize) {
        if (batchSize != null && policy != HybridFilterPolicy.BATCHES) {
            throw new IllegalArgumentException("batchSize requires policy=HybridFilterPolicy.BATCHES.");
        }
        this.expression = expression;
        this.policy = policy;
        this.batchSize = batchSize;
    }

    public HybridFilterPolicy getPolicy() {
        return policy;
    }

    /** Get the arguments for the VSIM FILTER clause. */
    public GlideString[] toArgs() {
        ArrayList<GlideString> subArgs = new ArrayList<>();
        if (policy != null) {
            subArgs.add(gs("POLICY"));
            subArgs.add(gs(policy.toString()));
        }
        if (batchSize != null) {
            subArgs.add(gs("BATCH_SIZE"));
            subArgs.add(gs(batchSize.toString()));
        }
        if (subArgs.isEmpty()) {
            // Uncounted extension: a bare expression, no POLICY/BATCH_SIZE possible in this form.
            return new GlideString[] {gs("FILTER"), expression};
        }
        int tokenCount = 1 + subArgs.size();
        ArrayList<GlideString> args = new ArrayList<>();
        args.add(gs("FILTER"));
        args.add(gs(Integer.toString(tokenCount)));
        args.add(expression);
        args.addAll(subArgs);
        return args.toArray(new GlideString[0]);
    }
}
