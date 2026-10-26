/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.FT;

import static glide.api.models.GlideString.gs;

import glide.api.models.GlideString;
import java.util.ArrayList;
import java.util.Arrays;
import lombok.NonNull;

/**
 * The VSIM arm of an FT.HYBRID query: a vector similarity search against a vector field, run
 * alongside the SEARCH arm and fused with it.
 *
 * <p>The query vector can be given two ways:
 *
 * <ul>
 *   <li>As raw bytes (via the {@code byte[]} constructors): the client generates an internal {@code
 *       $}-prefixed PARAMS entry under the hood and references it on the wire. FT.HYBRID's VSIM
 *       clause has no wire-level inline vector literal at all -- the parameter token must start
 *       with {@code $} or the server rejects it outright, so an auto-generated PARAMS entry is the
 *       only way to offer a "just pass the bytes" call shape. A {@code byte[]} is ALWAYS treated as
 *       raw vector data, even if its first byte happens to equal the ASCII value of {@code $}
 *       (0x24) -- never as a parameter reference, regardless of content.
 *   <li>As an explicit {@code $name} string (via the {@code String} constructors): the caller
 *       manages their own entry in {@link FTHybridOptions}' params, exactly like {@code
 *       FTSearchOptions}/{@code FTAggregateOptions} params already work for every other
 *       PARAMS-driven query. Only a {@code String} starting with {@code $} is ever treated as a
 *       parameter reference -- this is decided by which constructor was called, not by inspecting
 *       the value's bytes.
 * </ul>
 */
public class FTHybridVsimArm {
    private final GlideString field;
    private final GlideString vectorParamRef;
    private final byte[] rawVector;
    private final Integer k;
    private final Integer efRuntime;
    private final Double shardKRatio;
    private final GlideString yieldScoreAs;
    private final FTHybridVsimFilter filter;

    private FTHybridVsimArm(
            GlideString field,
            GlideString vectorParamRef,
            byte[] rawVector,
            Integer k,
            Integer efRuntime,
            Double shardKRatio,
            GlideString yieldScoreAs,
            FTHybridVsimFilter filter) {
        if (efRuntime != null && filter != null && filter.getPolicy() == HybridFilterPolicy.ADHOC) {
            throw new IllegalArgumentException(
                    "efRuntime is not supported with filter.policy=HybridFilterPolicy.ADHOC "
                            + "(the server rejects this combination: "
                            + "\"EF_RUNTIME is irrelevant for POLICY ADHOC\").");
        }
        this.field = field;
        this.vectorParamRef = vectorParamRef;
        this.rawVector = rawVector;
        this.k = k;
        this.efRuntime = efRuntime;
        this.shardKRatio = shardKRatio;
        this.yieldScoreAs = yieldScoreAs;
        this.filter = filter;
    }

    /**
     * Initialize a new instance referencing a {@code $name} parameter the caller manages in {@link
     * FTHybridOptions}' params.
     *
     * @param field The vector field's identifier, without the leading {@code @} (added
     *     automatically).
     * @param vectorParamRef The {@code $name} parameter reference. Must start with {@code $}.
     */
    public FTHybridVsimArm(@NonNull String field, @NonNull String vectorParamRef) {
        this(gs(field), requireParamReference(vectorParamRef), null, null, null, null, null, null);
    }

    /**
     * Initialize a new instance with raw vector bytes. An internal PARAMS entry is generated
     * automatically.
     *
     * @param field The vector field's identifier, without the leading {@code @} (added
     *     automatically).
     * @param vector The raw query vector bytes. Always treated as raw vector data, never as a
     *     parameter reference, regardless of content.
     */
    public FTHybridVsimArm(@NonNull String field, @NonNull byte[] vector) {
        this(gs(field), null, vector.clone(), null, null, null, null, null);
    }

    /**
     * Initialize a new instance referencing a {@code $name} parameter, with KNN options.
     *
     * @param field The vector field's identifier, without the leading {@code @}.
     * @param vectorParamRef The {@code $name} parameter reference. Must start with {@code $}.
     * @param k The number of nearest neighbours to return. Omitting the whole KNN block (when {@code
     *     k}, {@code efRuntime} and {@code shardKRatio} are all {@code null}) defaults to 10
     *     server-side.
     * @param efRuntime HNSW search-time effort parameter. Only valid when the KNN block is written.
     * @param shardKRatio Tunes how much of {@code k} each shard returns during a cluster fanout.
     */
    public FTHybridVsimArm(
            @NonNull String field,
            @NonNull String vectorParamRef,
            Integer k,
            Integer efRuntime,
            Double shardKRatio) {
        this(
                gs(field),
                requireParamReference(vectorParamRef),
                null,
                k,
                efRuntime,
                shardKRatio,
                null,
                null);
    }

    /**
     * Initialize a new instance with raw vector bytes and KNN options. An internal PARAMS entry is
     * generated automatically.
     *
     * @param field The vector field's identifier, without the leading {@code @}.
     * @param vector The raw query vector bytes. Always treated as raw vector data.
     * @param k The number of nearest neighbours to return.
     * @param efRuntime HNSW search-time effort parameter.
     * @param shardKRatio Tunes how much of {@code k} each shard returns during a cluster fanout.
     */
    public FTHybridVsimArm(
            @NonNull String field,
            @NonNull byte[] vector,
            Integer k,
            Integer efRuntime,
            Double shardKRatio) {
        this(gs(field), null, vector.clone(), k, efRuntime, shardKRatio, null, null);
    }

    /**
     * Initialize a new instance referencing a {@code $name} parameter, with every option.
     *
     * @param field The vector field's identifier, without the leading {@code @}.
     * @param vectorParamRef The {@code $name} parameter reference. Must start with {@code $}.
     * @param k The number of nearest neighbours to return.
     * @param efRuntime HNSW search-time effort parameter.
     * @param shardKRatio Tunes how much of {@code k} each shard returns during a cluster fanout.
     * @param yieldScoreAs Exposes this arm's score (the vector distance) under the given column name.
     */
    public FTHybridVsimArm(
            @NonNull String field,
            @NonNull String vectorParamRef,
            Integer k,
            Integer efRuntime,
            Double shardKRatio,
            GlideString yieldScoreAs) {
        this(
                gs(field),
                requireParamReference(vectorParamRef),
                null,
                k,
                efRuntime,
                shardKRatio,
                yieldScoreAs,
                null);
    }

    /**
     * Initialize a new instance with raw vector bytes and every option. An internal PARAMS entry is
     * generated automatically.
     *
     * @param field The vector field's identifier, without the leading {@code @}.
     * @param vector The raw query vector bytes. Always treated as raw vector data.
     * @param k The number of nearest neighbours to return.
     * @param efRuntime HNSW search-time effort parameter.
     * @param shardKRatio Tunes how much of {@code k} each shard returns during a cluster fanout.
     * @param yieldScoreAs Exposes this arm's score (the vector distance) under the given column name.
     */
    public FTHybridVsimArm(
            @NonNull String field,
            @NonNull byte[] vector,
            Integer k,
            Integer efRuntime,
            Double shardKRatio,
            GlideString yieldScoreAs) {
        this(gs(field), null, vector.clone(), k, efRuntime, shardKRatio, yieldScoreAs, null);
    }

    /**
     * Initialize a new instance referencing a {@code $name} parameter, with every option including a
     * VSIM pre-filter.
     *
     * @param field The vector field's identifier, without the leading {@code @}.
     * @param vectorParamRef The {@code $name} parameter reference. Must start with {@code $}.
     * @param k The number of nearest neighbours to return.
     * @param efRuntime HNSW search-time effort parameter.
     * @param shardKRatio Tunes how much of {@code k} each shard returns during a cluster fanout.
     * @param yieldScoreAs Exposes this arm's score (the vector distance) under the given column name.
     * @param filter A pre-filter on the vector search. See {@link FTHybridVsimFilter}.
     * @throws IllegalArgumentException If {@code efRuntime} is set while {@code filter.policy} is
     *     {@code HybridFilterPolicy.ADHOC} -- the server rejects that combination outright.
     */
    public FTHybridVsimArm(
            @NonNull String field,
            @NonNull String vectorParamRef,
            Integer k,
            Integer efRuntime,
            Double shardKRatio,
            GlideString yieldScoreAs,
            FTHybridVsimFilter filter) {
        this(
                gs(field),
                requireParamReference(vectorParamRef),
                null,
                k,
                efRuntime,
                shardKRatio,
                yieldScoreAs,
                filter);
    }

    /**
     * Initialize a new instance with raw vector bytes and every option, including a VSIM pre-filter.
     * An internal PARAMS entry is generated automatically.
     *
     * @param field The vector field's identifier, without the leading {@code @}.
     * @param vector The raw query vector bytes. Always treated as raw vector data, even if its first
     *     byte happens to equal the ASCII value of {@code $} (0x24).
     * @param k The number of nearest neighbours to return.
     * @param efRuntime HNSW search-time effort parameter.
     * @param shardKRatio Tunes how much of {@code k} each shard returns during a cluster fanout.
     * @param yieldScoreAs Exposes this arm's score (the vector distance) under the given column name.
     * @param filter A pre-filter on the vector search. See {@link FTHybridVsimFilter}.
     * @throws IllegalArgumentException If {@code efRuntime} is set while {@code filter.policy} is
     *     {@code HybridFilterPolicy.ADHOC} -- the server rejects that combination outright.
     */
    public FTHybridVsimArm(
            @NonNull String field,
            @NonNull byte[] vector,
            Integer k,
            Integer efRuntime,
            Double shardKRatio,
            GlideString yieldScoreAs,
            FTHybridVsimFilter filter) {
        this(gs(field), null, vector.clone(), k, efRuntime, shardKRatio, yieldScoreAs, filter);
    }

    /**
     * Returns a builder referencing a {@code $name} parameter the caller manages in {@link
     * FTHybridOptions}' params. Keeps the type-split entry point (this overload vs {@link
     * #builder(String, byte[])}) so the param-reference-by-type rule still holds: only this {@code
     * String} overload can ever produce a {@code $name} reference.
     *
     * @param field The vector field's identifier, without the leading {@code @} (added
     *     automatically).
     * @param vectorParamRef The {@code $name} parameter reference. Must start with {@code $}.
     */
    public static Builder builder(@NonNull String field, @NonNull String vectorParamRef) {
        return new Builder(gs(field), requireParamReference(vectorParamRef), null);
    }

    /**
     * Returns a builder with raw vector bytes. An internal PARAMS entry is generated automatically. A
     * {@code byte[]} is always treated as raw vector data, never as a parameter reference, regardless
     * of content -- see {@link #builder(String, String)} for the {@code $name} form.
     *
     * @param field The vector field's identifier, without the leading {@code @} (added
     *     automatically).
     * @param vector The raw query vector bytes.
     */
    public static Builder builder(@NonNull String field, @NonNull byte[] vector) {
        return new Builder(gs(field), null, vector.clone());
    }

    /**
     * Builder for {@link FTHybridVsimArm}, returned by {@link #builder(String, String)}/{@link
     * #builder(String, byte[])} to keep the type-split vector entry points.
     */
    public static final class Builder {
        private final GlideString field;
        private final GlideString vectorParamRef;
        private final byte[] rawVector;
        private Integer k;
        private Integer efRuntime;
        private Double shardKRatio;
        private GlideString yieldScoreAs;
        private FTHybridVsimFilter filter;

        private Builder(GlideString field, GlideString vectorParamRef, byte[] rawVector) {
            this.field = field;
            this.vectorParamRef = vectorParamRef;
            this.rawVector = rawVector;
        }

        /**
         * The number of nearest neighbours to return. Omitting the whole KNN block (when {@code k},
         * {@code efRuntime} and {@code shardKRatio} are all unset) defaults to 10 server-side.
         */
        public Builder k(int k) {
            this.k = k;
            return this;
        }

        /** HNSW search-time effort parameter. Only valid when the KNN block is written. */
        public Builder efRuntime(int efRuntime) {
            this.efRuntime = efRuntime;
            return this;
        }

        /** Tunes how much of {@code k} each shard returns during a cluster fanout. */
        public Builder shardKRatio(double shardKRatio) {
            this.shardKRatio = shardKRatio;
            return this;
        }

        /** Exposes this arm's score (the vector distance) under the given column name. */
        public Builder yieldScoreAs(@NonNull String yieldScoreAs) {
            this.yieldScoreAs = gs(yieldScoreAs);
            return this;
        }

        /** Exposes this arm's score (the vector distance) under the given column name. */
        public Builder yieldScoreAs(@NonNull GlideString yieldScoreAs) {
            this.yieldScoreAs = yieldScoreAs;
            return this;
        }

        /** A pre-filter on the vector search. See {@link FTHybridVsimFilter}. */
        public Builder filter(@NonNull FTHybridVsimFilter filter) {
            this.filter = filter;
            return this;
        }

        /**
         * Builds the {@link FTHybridVsimArm}.
         *
         * @throws IllegalArgumentException If {@code efRuntime} is set while {@code filter.policy} is
         *     {@code HybridFilterPolicy.ADHOC} -- the server rejects that combination outright.
         */
        public FTHybridVsimArm build() {
            return new FTHybridVsimArm(
                    field, vectorParamRef, rawVector, k, efRuntime, shardKRatio, yieldScoreAs, filter);
        }
    }

    private static GlideString requireParamReference(String vectorParamRef) {
        if (!vectorParamRef.startsWith("$")) {
            throw new IllegalArgumentException(
                    "vectorParamRef must start with '$' -- the server rejects a VSIM vector "
                            + "parameter token that does not.");
        }
        return gs(vectorParamRef);
    }

    /**
     * {@code true} if this arm references an existing {@code $name} parameter rather than raw vector
     * bytes requiring a generated parameter name.
     */
    public boolean isParamReference() {
        return vectorParamRef != null;
    }

    /**
     * The raw vector bytes, if this arm was constructed with raw bytes rather than a {@code $name}
     * reference.
     */
    public byte[] getRawVector() {
        return rawVector == null ? null : rawVector.clone();
    }

    /** Get the arguments for the VSIM clause, assuming this arm is a {@code $name} reference. */
    public GlideString[] toArgs() {
        if (!isParamReference()) {
            throw new IllegalStateException(
                    "generatedParamName is required when vector is raw bytes rather than an "
                            + "explicit $name parameter reference; call toArgs(GlideString) instead.");
        }
        return toArgs(vectorParamRef);
    }

    /**
     * Get the arguments for the VSIM clause.
     *
     * @param generatedParamName The {@code $}-prefixed parameter name to reference on the wire when
     *     this arm was given raw bytes rather than an explicit {@code $name} string. Ignored if this
     *     arm is already a parameter reference.
     */
    public GlideString[] toArgs(GlideString generatedParamName) {
        GlideString paramRef = isParamReference() ? vectorParamRef : generatedParamName;
        if (paramRef == null) {
            throw new IllegalStateException(
                    "generatedParamName is required when vector is raw bytes rather than an "
                            + "explicit $name parameter reference");
        }
        ArrayList<GlideString> args = new ArrayList<>();
        args.add(gs("VSIM"));
        args.add(gs("@").concat(field));
        args.add(paramRef);
        if (k != null || efRuntime != null || shardKRatio != null) {
            ArrayList<GlideString> subArgs = new ArrayList<>();
            if (k != null) {
                subArgs.add(gs("K"));
                subArgs.add(gs(k.toString()));
            }
            if (efRuntime != null) {
                subArgs.add(gs("EF_RUNTIME"));
                subArgs.add(gs(efRuntime.toString()));
            }
            if (shardKRatio != null) {
                subArgs.add(gs("SHARD_K_RATIO"));
                subArgs.add(gs(shardKRatio.toString()));
            }
            args.add(gs("KNN"));
            args.add(gs(Integer.toString(subArgs.size())));
            args.addAll(subArgs);
        }
        if (yieldScoreAs != null) {
            args.add(gs("YIELD_SCORE_AS"));
            args.add(yieldScoreAs);
        }
        if (filter != null) {
            args.addAll(Arrays.asList(filter.toArgs()));
        }
        return args.toArray(new GlideString[0]);
    }
}
