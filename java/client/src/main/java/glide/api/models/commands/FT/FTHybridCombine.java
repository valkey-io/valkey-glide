/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.FT;

import static glide.api.models.GlideString.gs;

import glide.api.models.GlideString;
import java.util.ArrayList;
import lombok.NonNull;

/**
 * Abstract base class for the COMBINE clause of FT.HYBRID, controlling how the SEARCH and VSIM
 * arms' per-document scores are fused into one ranking. See {@link FTHybridCombine.Rrf}, {@link
 * FTHybridCombine.Linear} and {@link FTHybridCombine.Function}.
 */
public abstract class FTHybridCombine {
    /** Get the arguments for the COMBINE clause. */
    public abstract GlideString[] toArgs();

    /**
     * Fuses the two arms' results using Reciprocal Rank Fusion: each arm's rank (not its raw
     * score) contributes {@code 1 / (constant + rank)} to the fused score.
     */
    public static class Rrf extends FTHybridCombine {
        private final Double constant;
        private final Integer window;
        private final GlideString yieldScoreAs;

        /** Initialize a new instance with no options; the server applies its own defaults. */
        public Rrf() {
            this(null, null, null);
        }

        /**
         * Initialize a new instance.
         *
         * @param constant The RRF constant. Must be non-negative -- the server rejects a negative
         *     value (it places a pole at {@code rank == -constant}).
         * @param window How many of each arm's results take part in fusion (and, in cluster mode,
         *     how many each shard is asked to fetch). Bounded by the server's {@code
         *     max-combine-window} config.
         * @param yieldScoreAs Exposes the fused score under the given column name instead of the
         *     default {@code __score}.
         * @throws IllegalArgumentException If {@code constant} is negative.
         */
        public Rrf(Double constant, Integer window, GlideString yieldScoreAs) {
            if (constant != null && constant < 0) {
                throw new IllegalArgumentException("constant must not be negative.");
            }
            this.constant = constant;
            this.window = window;
            this.yieldScoreAs = yieldScoreAs;
        }

        @Override
        public GlideString[] toArgs() {
            ArrayList<GlideString> subArgs = new ArrayList<>();
            if (constant != null) {
                subArgs.add(gs("CONSTANT"));
                subArgs.add(gs(constant.toString()));
            }
            if (window != null) {
                subArgs.add(gs("WINDOW"));
                subArgs.add(gs(window.toString()));
            }
            if (yieldScoreAs != null) {
                subArgs.add(gs("YIELD_SCORE_AS"));
                subArgs.add(yieldScoreAs);
            }
            ArrayList<GlideString> args = new ArrayList<>();
            args.add(gs("COMBINE"));
            args.add(gs("RRF"));
            args.add(gs(Integer.toString(subArgs.size())));
            args.addAll(subArgs);
            return args.toArray(new GlideString[0]);
        }
    }

    /**
     * Fuses the two arms' results linearly: {@code alpha * search_score + beta * vsim_score}.
     * Writing neither {@code alpha} nor {@code beta} defaults both to 0.3/0.7 respectively;
     * writing exactly one without the other is rejected by the server as more likely a typo than
     * an intentional default.
     */
    public static class Linear extends FTHybridCombine {
        private final Double alpha;
        private final Double beta;
        private final Integer window;
        private final GlideString yieldScoreAs;

        /** Initialize a new instance with no options; the server defaults alpha/beta to 0.3/0.7. */
        public Linear() {
            this(null, null, null, null);
        }

        /**
         * Initialize a new instance.
         *
         * @param alpha The SEARCH arm's weight. Defaults (server-side) to {@code 0.3} when neither
         *     {@code alpha} nor {@code beta} is set. Setting one without the other is rejected by
         *     the server.
         * @param beta The VSIM arm's weight. Defaults (server-side) to {@code 0.7} when neither is
         *     set.
         * @param window How many of each arm's results take part in fusion.
         * @param yieldScoreAs Exposes the fused score under the given column name instead of the
         *     default {@code __score}.
         * @throws IllegalArgumentException If exactly one of {@code alpha}/{@code beta} is set
         *     without the other.
         */
        public Linear(Double alpha, Double beta, Integer window, GlideString yieldScoreAs) {
            if ((alpha == null) != (beta == null)) {
                throw new IllegalArgumentException(
                        "alpha and beta must both be set, or both left unset -- the server "
                                + "rejects writing exactly one of them.");
            }
            this.alpha = alpha;
            this.beta = beta;
            this.window = window;
            this.yieldScoreAs = yieldScoreAs;
        }

        @Override
        public GlideString[] toArgs() {
            ArrayList<GlideString> subArgs = new ArrayList<>();
            if (alpha != null) {
                subArgs.add(gs("ALPHA"));
                subArgs.add(gs(alpha.toString()));
            }
            if (beta != null) {
                subArgs.add(gs("BETA"));
                subArgs.add(gs(beta.toString()));
            }
            if (window != null) {
                subArgs.add(gs("WINDOW"));
                subArgs.add(gs(window.toString()));
            }
            if (yieldScoreAs != null) {
                subArgs.add(gs("YIELD_SCORE_AS"));
                subArgs.add(yieldScoreAs);
            }
            ArrayList<GlideString> args = new ArrayList<>();
            args.add(gs("COMBINE"));
            args.add(gs("LINEAR"));
            args.add(gs(Integer.toString(subArgs.size())));
            args.addAll(subArgs);
            return args.toArray(new GlideString[0]);
        }
    }

    /**
     * Fuses the two arms' results using a caller-supplied expression. {@code expr} is a raw
     * string written in the same expression language an aggregate APPLY clause's expression
     * uses -- the server compiles it against a context where each arm's score is reachable via
     * its own {@code YIELD_SCORE_AS} alias (if any), a positional default {@code
     * @__arm<i>_score}, and (for this SEARCH+VSIM two-arm shape) {@code @__search_score} / {@code
     * @__vector_score} specifically.
     *
     * <p>This is a valkey-search extension not present in RediSearch's FT.HYBRID (included here
     * anyway rather than dropped).
     */
    public static class Function extends FTHybridCombine {
        private final GlideString expr;
        private final Integer window;
        private final GlideString yieldScoreAs;

        /**
         * Initialize a new instance.
         *
         * @param expr The fusion expression, as a raw string (e.g. {@code "@__search_score +
         *     @__vector_score"} or, with explicit arm aliases, {@code "@ss + @vs"}).
         */
        public Function(@NonNull String expr) {
            this(gs(expr), null, null);
        }

        /**
         * Initialize a new instance.
         *
         * @param expr The fusion expression, as a raw string.
         * @param window How many of each arm's results take part in fusion. If left unset, the
         *     server defaults this to the widest window allowed rather than the usual RRF/LINEAR
         *     default, since a caller-written expression typically expects to see every matching
         *     candidate, not a top-window slice.
         * @param yieldScoreAs Exposes the fused score under the given column name instead of the
         *     default {@code __score}.
         */
        public Function(@NonNull String expr, Integer window, GlideString yieldScoreAs) {
            this(gs(expr), window, yieldScoreAs);
        }

        /**
         * Initialize a new instance.
         *
         * @param expr The fusion expression, as a raw string.
         * @param window How many of each arm's results take part in fusion.
         * @param yieldScoreAs Exposes the fused score under the given column name instead of the
         *     default {@code __score}.
         */
        public Function(@NonNull GlideString expr, Integer window, GlideString yieldScoreAs) {
            this.expr = expr;
            this.window = window;
            this.yieldScoreAs = yieldScoreAs;
        }

        @Override
        public GlideString[] toArgs() {
            ArrayList<GlideString> subArgs = new ArrayList<>();
            subArgs.add(gs("EXPR"));
            subArgs.add(expr);
            if (window != null) {
                subArgs.add(gs("WINDOW"));
                subArgs.add(gs(window.toString()));
            }
            if (yieldScoreAs != null) {
                subArgs.add(gs("YIELD_SCORE_AS"));
                subArgs.add(yieldScoreAs);
            }
            ArrayList<GlideString> args = new ArrayList<>();
            args.add(gs("COMBINE"));
            args.add(gs("FUNCTION"));
            args.add(gs(Integer.toString(subArgs.size())));
            args.addAll(subArgs);
            return args.toArray(new GlideString[0]);
        }
    }
}
