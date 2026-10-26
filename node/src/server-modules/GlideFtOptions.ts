/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

// eslint-disable-next-line @typescript-eslint/no-unused-vars
import { GlideFt, GlideRecord, GlideString, SortOrder } from "..";

interface BaseField {
    /** The name of the field. */
    name: GlideString;
    /** An alias for field. */
    alias?: GlideString;
    /** If set, the field value can be used for sorting. Applies to TEXT, TAG, and NUMERIC fields. */
    sortable?: boolean;
    /**
     * If set, keeps the sorted value in its original (un-normalized) form, e.g. preserving case
     * for TEXT/TAG fields that would otherwise be lowercased for sorting (valkey-search 1.3.0).
     * Requires `sortable` to also be set; emitted directly after `SORTABLE`, the only position
     * valkey-search recognises `UNF` in. Needs `search.emulate-release` >= 1.3.0.
     */
    unf?: boolean;
}

/**
 * Field contains any blob of data.
 */
export type TextField = BaseField & {
    /** Field identifier */
    type: "TEXT";
    /** If set, disables stemming when indexing the field. */
    nostem?: boolean;
    /** Declares the importance of this field when calculating result accuracy. Default is 1. */
    weight?: number;
    /**
     * If set, keeps a suffix trie for the field to optimize contains and suffix queries.
     * Mutually exclusive with `nosuffixtrie`.
     */
    withsuffixtrie?: boolean;
    /**
     * If set, disables the suffix trie for the field.
     * Mutually exclusive with `withsuffixtrie`.
     */
    nosuffixtrie?: boolean;
};

/**
 * Tag fields are similar to full-text fields, but they interpret the text as a simple list of
 * tags delimited by a separator character.
 *
 * For HASH fields, separator default is a comma (`,`). For JSON fields, there is no default
 * separator; you must declare one explicitly if needed.
 */
export type TagField = BaseField & {
    /** Field identifier */
    type: "TAG";
    /** Specify how text in the attribute is split into individual tags. Must be a single character. */
    separator?: GlideString;
    /** Preserve the original letter cases of tags. If set to `false`, characters are converted to lowercase by default. */
    caseSensitive?: boolean;
};

/**
 * Field contains a number.
 */
export type NumericField = BaseField & {
    /** Field identifier */
    type: "NUMERIC";
};

/**
 * Superclass for vector field implementations, contains common logic.
 */
export type VectorField = BaseField & {
    /** Field identifier */
    type: "VECTOR";
    /** Additional attributes to be passed with the vector field after the algorithm name. */
    attributes: VectorFieldAttributesFlat | VectorFieldAttributesHnsw;
};

/**
 * Base class for defining vector field attributes to be used after the vector algorithm name.
 */
interface VectorFieldAttributes {
    /** Number of dimensions in the vector. Equivalent to `DIM` in the module API. */
    dimensions: number;
    /**
     * The distance metric used in vector type field. Can be one of `[L2 | IP | COSINE]`.
     * Equivalent to `DISTANCE_METRIC` in the module API.
     */
    distanceMetric: "L2" | "IP" | "COSINE";
    /**
     * Vector type. `FLOAT32` is the default if unset. `FLOAT16` and `BFLOAT16` were added in
     * valkey-search 1.3.0 (2 bytes per element each). Callers must pass already-encoded bytes
     * for every type; GLIDE does not encode vectors on the caller's behalf.
     */
    type?: "FLOAT32" | "FLOAT16" | "BFLOAT16";
    /**
     * Initial vector capacity in the index affecting memory allocation size of the index.
     * Defaults to `1024`. Equivalent to `INITIAL_CAP` in the module API.
     */
    initialCap?: number;
}

/**
 * Vector field that supports vector search by FLAT (brute force) algorithm.
 *
 * The algorithm is a brute force linear processing of each vector in the index, yielding exact
 * answers within the bounds of the precision of the distance computations.
 */
export type VectorFieldAttributesFlat = VectorFieldAttributes & {
    algorithm: "FLAT";
};

/**
 * Vector field that supports vector search by HNSM (Hierarchical Navigable Small World) algorithm.
 *
 * The algorithm provides an approximation of the correct answer in exchange for substantially
 * lower execution times.
 */
export type VectorFieldAttributesHnsw = VectorFieldAttributes & {
    algorithm: "HNSW";
    /**
     * Number of maximum allowed outgoing edges for each node in the graph in each layer.
     * Default is `16`, maximum is `512`. Equivalent to `M` in the module API.
     */
    numberOfEdges?: number;
    /**
     * Controls the number of vectors examined during index construction.
     * Default value is `200`, Maximum value is `4096`. Equivalent to `EF_CONSTRUCTION` in the module API.
     */
    vectorsExaminedOnConstruction?: number;
    /**
     * Controls the number of vectors examined during query operations.
     * Default value is `10`, Maximum value is `4096`. Equivalent to `EF_RUNTIME` in the module API.
     */
    vectorsExaminedOnRuntime?: number;
};

export type Field = TextField | TagField | NumericField | VectorField;

/**
 * Represents the input options to be used in the {@link GlideFt.create | FT.CREATE} command.
 * All fields in this class are optional inputs for FT.CREATE.
 */
export interface FtCreateOptions {
    /** The type of data to be indexed using FT.CREATE. */
    dataType: "JSON" | "HASH";
    /** The prefix of the key to be indexed. */
    prefixes?: GlideString[];
    /** Default score for documents in the index. Default is 1.0. */
    score?: number;
    /** Default language for documents in the index. */
    language?: string;
    /** If set, does not scan and index existing documents on index creation. */
    skipInitialScan?: boolean;
    /** Minimum word length to stem. Words shorter than this are not stemmed. */
    minStemSize?: number;
    /**
     * If set, stores term offsets for document fields.
     * Mutually exclusive with `noOffsets`.
     */
    withOffsets?: boolean;
    /**
     * If set, does not store term offsets.
     * Mutually exclusive with `withOffsets`.
     */
    noOffsets?: boolean;
    /**
     * If set, disables stop-word filtering.
     * Mutually exclusive with `stopWords`.
     */
    noStopWords?: boolean;
    /**
     * Custom list of stop words.
     * Mutually exclusive with `noStopWords`.
     */
    stopWords?: GlideString[];
    /** Custom punctuation characters to use during tokenization. */
    punctuation?: GlideString;
    /**
     * A filter expression limiting which documents the index applies to (valkey-search 1.3.0).
     * A document that doesn't match this expression is excluded from the index even if its key
     * matches a configured prefix. Cannot be an empty string.
     */
    filter?: GlideString;
    /**
     * The name of a document field whose numeric value is used as the document's custom score,
     * multiplying into the final relevance score (valkey-search 1.3.0). The field must already
     * exist in the document; it is not itself a schema definition. FT.INFO only reports this
     * option back under `index_definition`'s `score_field` key when `search.emulate-release` is
     * at least 1.3.0; below that level the pair is omitted and `default_score` reports the
     * literal string "1" regardless of this option. The scoring behaviour itself is unaffected.
     */
    scoreField?: GlideString;
    /**
     * Emits `NOHL`. valkey-search does not support highlighting at all, so this is a genuine
     * no-op on this engine; the token is accepted but not even stored for FT.INFO to report.
     * Implemented for wire compatibility with RediSearch clients.
     */
    noHighlight?: boolean;
}

/** Additional parameters for {@link GlideFt.aggregate | FT.AGGREGATE} command. */
export type FtAggregateOptions = {
    /** Query timeout in milliseconds. */
    timeout?: number;
    /**
     * {@link FtAggregateFilter | FILTER}, {@link FtAggregateLimit | LIMIT}, {@link FtAggregateGroupBy | GROUPBY},
     * {@link FtAggregateSortBy | SORTBY} and {@link FtAggregateApply | APPLY} clauses, that can be repeated
     * multiple times in any order and be freely intermixed. They are applied in the order specified,
     * with the output of one clause feeding the input of the next clause.
     */
    clauses?: (
        | FtAggregateLimit
        | FtAggregateFilter
        | FtAggregateGroupBy
        | FtAggregateSortBy
        | FtAggregateApply
    )[];
    /**
     * Query parameters, which could be referenced in the query by `$` sign, followed by
     * the parameter name.
     */
    params?: GlideRecord<GlideString>;
    /** If set, stemming is not applied to term searches. */
    verbatim?: boolean;
    /** If set, proximity matching of terms must be in order. */
    inorder?: boolean;
    /** Specifies a slop value for proximity matching of terms. */
    slop?: number;
    /** The query dialect version to use. */
    dialect?: number;
    /**
     * If set, exposes the relevance score as a pipeline field named `__score` (valkey-search
     * 1.3.0). Confirmed live: `__score` can then be referenced like any other pipeline field in
     * a `loadFields` entry, an {@link FtAggregateApply | APPLY} expression, or an
     * {@link FtAggregateSortProperty | SORTBY} property (e.g. `{ property: "@__score", order:
     * "DESC" }` placed in `clauses`).
     */
    addScores?: boolean;
    /**
     * The scoring function used to compute `__score` and rank results (valkey-search 1.3.0).
     * `"BM25STD"` is the only value currently registered and selectable; any other value is
     * rejected by the server. Has no visible effect unless combined with `addScores: true` (or
     * inspected indirectly via a SORTBY on `__score`).
     */
    scorer?: GlideString;
    /**
     * Requests a paged reply instead of the whole result set at once. See {@link FtWithCursor}.
     * The reply shape becomes a two-element array of `[rows, cursorId]`, where `rows` is this
     * same aggregate response shape and `cursorId` is passed to a subsequent
     * {@link GlideFt.cursorRead | GlideFt.cursorRead} call to fetch the next page (`0` once
     * exhausted).
     *
     * A paged query, and every subsequent `cursorRead`/`cursorDel` against the cursor it opens,
     * is pinned to the PRIMARY of the index's slot regardless of `ReadFrom` -- a cursor is a
     * per-process, node-local resource, and this is a deliberate, documented exception to
     * GLIDE's normal read routing. A plain aggregate without `withCursor` is unaffected and
     * still prefers replicas as normal.
     */
    withCursor?: FtWithCursor;
} & (
    | {
          /** List of fields to load from the index, either a plain identifier or a {@link LoadField} for an aliased entry. */
          loadFields?: (GlideString | LoadField)[];
          /** `loadAll` and `loadFields` are mutually exclusive. */
          loadAll?: never;
      }
    | {
          /** Option to load all fields declared in the index */
          loadAll?: boolean;
          /** `loadAll` and `loadFields` are mutually exclusive. */
          loadFields?: never;
      }
);

/**
 * The WITHCURSOR option, requesting a paged reply instead of the whole result set at once.
 * Shared verbatim between {@link GlideFt.aggregate | FT.AGGREGATE} and
 * {@link GlideFt.hybrid | FT.HYBRID} -- both commands drive the exact same aggregate-suffix
 * parser, so the option's own grammar (`WITHCURSOR [COUNT n] [MAXIDLE ms]`) and reply shape are
 * identical regardless of which command opened the cursor. Every subsequent
 * {@link GlideFt.cursorRead | cursorRead}/{@link GlideFt.cursorDel | cursorDel} against the
 * cursor it opens is pinned to the PRIMARY of the index's slot regardless of `ReadFrom` -- a
 * cursor is a per-process, node-local resource, and this is a deliberate, documented exception
 * to GLIDE's normal read routing.
 */
export interface FtWithCursor {
    /** The number of rows to return per page. */
    count?: number;
    /**
     * How long (in milliseconds) the cursor may sit idle between reads before the server
     * discards it.
     */
    maxidle?: number;
}

/** A clause for limiting the number of retained records. */
export interface FtAggregateLimit {
    type: "LIMIT";
    /** Starting point from which the records have to be retained. */
    offset: number;
    /** The total number of records to be retained. */
    count: number;
}

/**
 * A clause for filtering the results using predicate expression relating to values in each result.
 * It is applied post query and relate to the current state of the pipeline.
 */
export interface FtAggregateFilter {
    type: "FILTER";
    /** The expression to filter the results. */
    expression: GlideString;
}

/** A clause for grouping the results in the pipeline based on one or more properties. */
export interface FtAggregateGroupBy {
    type: "GROUPBY";
    /** The list of properties to be used for grouping the results in the pipeline. */
    properties: GlideString[];
    /** The list of functions that handles the group entries by performing multiple aggregate operations. */
    reducers: FtAggregateReducer[];
}

/**
 * A clause for reducing the matching results in each group using a reduction function.
 * The matching results are reduced into a single record.
 */
export interface FtAggregateReducer {
    /** The reduction function name for the respective group. */
    function: string;
    /** The list of arguments for the reducer. */
    args: GlideString[];
    /** User defined property name for the reducer. */
    name?: GlideString;
}

/**
 * Typed factories for the reducer functions valkey-search 1.3.0 added, built on top of the
 * generic {@link FtAggregateReducer} shape. Equivalent to Python's
 * `FtAggregateReducer.to_list`/`first_value`/`random_sample` classmethods.
 */
export const FtAggregateReducer = {
    /**
     * Creates a TOLIST reducer, which collects all values for `field` across the records in a
     * group into a single array, removing duplicates and nil values. If a value is itself an
     * array, it is flattened one level before being added. Values are returned in the order they
     * were first encountered.
     *
     * @param field - The property whose values are collected into a list.
     * @param name - User defined property name for the reducer. If not provided, the server
     *     generates a default name.
     */
    toList(field: GlideString, name?: GlideString): FtAggregateReducer {
        return { function: "TOLIST", args: [field], name };
    },

    /**
     * Creates a FIRST_VALUE reducer, which returns the value of `field` from a single record in
     * the group. Without `by`, the first record encountered wins. With `by`, the record whose
     * `by` property is smallest (`SortOrder.ASC`, the default) or largest (`SortOrder.DESC`)
     * wins; ties keep the first-encountered record.
     *
     * @param field - The property whose value is returned.
     * @param options - (Optional) `by`: the property used to choose which record's value of
     *     `field` is returned, required if `order` is specified. `order`: the direction used to
     *     pick the winning record when `by` is set.
     * @param name - User defined property name for the reducer. If not provided, the server
     *     generates a default name.
     * @throws Error if `order` is specified without `by`.
     */
    firstValue(
        field: GlideString,
        options?: { by?: GlideString; order?: SortOrder | "ASC" | "DESC" },
        name?: GlideString,
    ): FtAggregateReducer {
        if (options?.order !== undefined && options?.by === undefined) {
            throw new Error("firstValue: order requires by to be set.");
        }

        const args: GlideString[] = [field];

        if (options?.by !== undefined) {
            args.push("BY", options.by);

            if (options?.order !== undefined) {
                args.push(options.order);
            }
        }

        return { function: "FIRST_VALUE", args, name };
    },

    /**
     * Creates a RANDOM_SAMPLE reducer, which returns a uniform random sample of up to `size`
     * values of `field` from the records in a group, using reservoir sampling.
     *
     * @param field - The property to sample values from.
     * @param size - The maximum number of values to sample. Must be a non-negative integer no
     *     greater than `1000`; the server rejects values outside this range.
     * @param name - User defined property name for the reducer. If not provided, the server
     *     generates a default name.
     */
    randomSample(
        field: GlideString,
        size: number,
        name?: GlideString,
    ): FtAggregateReducer {
        return {
            function: "RANDOM_SAMPLE",
            args: [field, size.toString()],
            name,
        };
    },
};

/** A clause for sorting the pipeline up until the point of SORTBY, using a list of properties. */
export interface FtAggregateSortBy {
    type: "SORTBY";
    /** A list of sorting parameters for the sort operation. */
    properties: FtAggregateSortProperty[];
    /** The MAX value for optimizing the sorting, by sorting only for the n-largest elements. */
    max?: number;
}

/** A single property for the {@link FtAggregateSortBy | SORTBY} clause. */
export interface FtAggregateSortProperty {
    /** The sorting parameter. */
    property: GlideString;
    /** The order for the sorting. */
    order: SortOrder;
}

/**
 * A clause for applying a 1-to-1 transformation on one or more properties and stores the result
 * as a new property down the pipeline or replaces any property using this transformation.
 */
export interface FtAggregateApply {
    type: "APPLY";
    /** The transformation expression. */
    expression: GlideString;
    /** The new property name to store the result of apply. This name can be referenced by further operations down the pipeline. */
    name: GlideString;
}

/**
 * A single entry in the LOAD clause of {@link GlideFt.aggregate | FT.AGGREGATE} or
 * {@link GlideFt.hybrid | FT.HYBRID}, optionally renaming the loaded field via `AS`. Modelled on
 * the `{ fieldIdentifier, alias }` shape {@link FtSearchOptions.returnFields} already uses for
 * FT.SEARCH's RETURN option.
 *
 * `AS <alias>` in the LOAD clause is a compatibility fix introduced in valkey-search 1.3.0
 * (gated by the `ft_aggregate_load_as` compatibility flag). Below `search.emulate-release
 * 1.3.0`, the legacy branch does not recognise `AS` as a rename keyword at all: it is consumed
 * as just another LOAD field token rather than a rename directive. GLIDE does not validate this
 * client-side; against a pre-1.3.0 emulate-release the server rejects the command outright with
 * a `RequestError` reading "Index: field `AS` does not exist" (confirmed live against
 * 1.3.0-rc1). Any code path that relies on the alias actually renaming the column needs
 * `search.emulate-release` >= 1.3.0.
 */
export interface LoadField {
    /** The identifier for the field to load. */
    fieldIdentifier: GlideString;
    /**
     * The alias to emit the field's column under. Requires `search.emulate-release` >= 1.3.0 to
     * take effect.
     */
    alias?: GlideString;
}

/**
 * Represents the input options to be used in the FT.SEARCH command.
 * All fields in this class are optional inputs for FT.SEARCH.
 */
export type FtSearchOptions = {
    /** Query timeout in milliseconds. */
    timeout?: number;

    /**
     * Restricts the search to only this set of keys, filtering out any matching result whose key
     * is not in the list. The result count is adjusted to reflect the filtered set. `[]` (an
     * explicit empty array) is distinct from the default `undefined`: `undefined` omits `INKEYS`
     * entirely (no restriction), while `[]` sends `INKEYS 0` and excludes every result.
     */
    inKeys?: GlideString[];

    /**
     * Add a field to be returned.
     * @param fieldIdentifier field name to return.
     * @param alias optional alias for the field name to return.
     */
    returnFields?: { fieldIdentifier: GlideString; alias?: GlideString }[];

    /**
     * Query parameters, which could be referenced in the query by `$` sign, followed by
     * the parameter name.
     */
    params?: GlideRecord<GlideString>;

    /** If true, returns only document IDs without field content.
     * The document entries in the result will have empty value arrays. */
    nocontent?: boolean;

    /** Query dialect version. Only dialect 2 is currently supported in valkey-search. */
    dialect?: number;

    /** If set, stemming is not applied to text terms in the query. */
    verbatim?: boolean;

    /** If set, proximity matching of text terms must be in order. */
    inorder?: boolean;

    /** Specifies a slop value for proximity matching of text terms. */
    slop?: number;

    /** Field name to sort results by. Sorting is applied before the LIMIT clause. */
    sortby?: GlideString;

    /** Sort direction for `sortby`. Only used when `sortby` is set. */
    sortbyOrder?: SortOrder | "ASC" | "DESC";

    /** If set and `sortby` is specified, augments the output with the sort key value.
     * When enabled, each document value in the result map becomes a two-element array
     * `[sortKey, fieldMap]` instead of just `fieldMap`. The sort key is the value of the
     * field used for sorting, or `null` if the field is missing from the document.
     */
    withsortkeys?: boolean;

    /**
     * Controls shard participation in cluster mode.
     * `ALLSHARDS` terminates with timeout error if not all shards respond (default).
     * `SOMESHARDS` generates a best-effort reply if not all shards respond within the timeout.
     */
    shardScope?: "ALLSHARDS" | "SOMESHARDS";

    /**
     * Controls consistency requirements in cluster mode.
     * `CONSISTENT` terminates with an error if the cluster is in an inconsistent state (default).
     * `INCONSISTENT` generates a best-effort reply if the cluster remains inconsistent within the timeout.
     */
    consistency?: "CONSISTENT" | "INCONSISTENT";

    /**
     * If set, augments the output with each document's relevance score (valkey-search 1.3.0).
     * This changes the result shape: each document value in the result map becomes a
     * two-element array `[score, fieldMap]` instead of just `fieldMap` (a string-encoded decimal,
     * e.g. `"0.287682086229"` -- confirmed live, not a number, on both RESP2 and RESP3). With
     * `nocontent: true`, the shape becomes `[score, {}]`, with an empty field map. Combining
     * `withsortkeys: true` with `nocontent: true` gives the same `[score, {}]`, because the
     * server drops the sort key in that combination (confirmed live). It is not
     * `[score, null, {}]`. With both `withScores: true` and `withsortkeys: true` and content
     * present, each row becomes a three-element array `[score, sortKey, fieldMap]`, a genuinely
     * different shape from either flag alone.
     */
    withScores?: boolean;

    /**
     * The scoring function used to rank results (valkey-search 1.3.0). `"BM25STD"` is the only
     * value currently registered and selectable; any other value is rejected by the server. Has
     * no visible effect unless combined with `withScores: true` (or inspected indirectly via
     * `sortby`).
     */
    scorer?: GlideString;
} & (
    | {
          /**
           * Configure query pagination. By default only first 10 documents are returned.
           *
           * @param offset Zero-based offset.
           * @param count Number of elements to return.
           */
          limit?: { offset: number; count: number };
          /** `limit` and `count` are mutually exclusive. */
          count?: never;
      }
    | {
          /**
           * Once set, the query returns only the number of documents in the result set without
           * actually returning them. Serialized as `LIMIT 0 0`: a bare `COUNT` token is not a
           * real FT.SEARCH keyword -- valkey-search's parser rejects it outright.
           */
          count?: boolean;
          /** `limit` and `count` are mutually exclusive. */
          limit?: never;
      }
);

/** Additional parameters for {@link GlideFt.info | FT.INFO} command. */
export interface FtInfoOptions {
    /** Controls which nodes provide index information in cluster mode. */
    scope?: "LOCAL" | "PRIMARY" | "CLUSTER";
    /** Controls shard participation in cluster mode. */
    shardScope?: "ALLSHARDS" | "SOMESHARDS";
    /** Controls consistency requirements in cluster mode. */
    consistency?: "CONSISTENT" | "INCONSISTENT";
}

/**
 * Controls how a VSIM pre-filter (see {@link FtHybridVsimFilter}) is applied against the vector
 * search. Only meaningful inside a counted VSIM FILTER block -- valkey-search's existing
 * uncounted `FILTER <expr>` extension on VSIM (no leading token count) cannot carry a policy at
 * all; the server requires the counted form to accept POLICY. This is a genuinely different
 * grammar axis from {@link FtHybridCombine}'s RRF/LINEAR/FUNCTION method -- one controls how the
 * vector search itself is pre-filtered, the other controls how the SEARCH and VSIM arms' scores
 * are fused after both arms have already run.
 *
 * - `"ADHOC"`: Evaluates the filter against every candidate as it is produced (brute-force),
 *   rather than pre-restricting the vector index's own search space. {@link FtHybridVsimArm.efRuntime}
 *   on the same VSIM arm is rejected when this policy is set (confirmed live and guarded
 *   client-side: "EF_RUNTIME is irrelevant for POLICY ADHOC").
 * - `"BATCHES"`: Evaluates the filter in batches drawn from the vector index. Required for
 *   {@link FtHybridVsimFilter.batchSize} to be set (confirmed live and guarded client-side:
 *   "BATCH_SIZE requires POLICY BATCHES").
 */
export type HybridFilterPolicy = "ADHOC" | "BATCHES";

/**
 * A pre-filter on the VSIM arm's vector search, written in the same query language the SEARCH
 * arm uses (e.g. `@title:hello`). Confirmed live: with a filter present, the VSIM arm becomes an
 * ordinary FT.SEARCH-style vector query under the hood (`<filter>=>[KNN ...]`), so the filter
 * decides membership and the arm's score stays the vector distance -- it is never replaced by a
 * text-relevance score the way a plain hybrid text query's score would be.
 */
export interface FtHybridVsimFilter {
    /** The filter expression, in the same query language FT.SEARCH uses (e.g. `@title:hello`). */
    expression: GlideString;
    /**
     * Controls how the filter is applied. See {@link HybridFilterPolicy}. Setting this forces
     * the wire-level counted VSIM FILTER form (required for the server to recognise POLICY at
     * all -- confirmed live).
     */
    policy?: HybridFilterPolicy;
    /**
     * Tunes how many candidates `POLICY BATCHES` draws per batch. Requires
     * `policy: "BATCHES"`. Confirmed live: the value is accepted but currently discarded by the
     * server, so this currently has no observable effect; included for forward compatibility
     * and because the server still validates its presence/ordering against `policy`. Validated
     * client-side too: {@link GlideFt.hybrid} throws an `Error` if `batchSize` is set without
     * `policy: "BATCHES"`, rather than serializing the invalid combination and relying on a
     * server round trip to reject it.
     */
    batchSize?: number;
}

/**
 * The SEARCH arm of an {@link GlideFt.hybrid | FT.HYBRID} query: an ordinary (non-vector)
 * FT.SEARCH-style text/tag/numeric query, run alongside the VSIM arm and fused with it. A vector
 * query (e.g. a `=>[KNN ...]` clause) is rejected here -- use {@link FtHybridVsimArm} for the
 * vector half instead.
 */
export interface FtHybridSearchArm {
    /**
     * The text/tag/numeric query, in the same language FT.SEARCH uses. Must not be a vector
     * query (e.g. must not contain a `=>[KNN ...]` clause); the server rejects that combination.
     */
    query: GlideString;
    /**
     * The scoring function used to rank this arm's results (valkey-search 1.3.0). `"BM25STD"`
     * is the only value currently registered and selectable; any other value is rejected by the
     * server.
     */
    scorer?: GlideString;
    /**
     * Exposes this arm's score under the given column name, reachable from an APPLY expression,
     * a SORTBY property, a GROUPBY/REDUCE expression, and {@link FtHybridCombine}'s FUNCTION
     * variant's `expr`. Confirmed live: this alias cannot itself be named inside a
     * `loadFields`/{@link FtHybridOptions.loadFields} entry (the server rejects it with "Index
     * field `<alias>` does not exist") -- it is already present in the default reply with no
     * LOAD needed, and remains reachable from every other clause type regardless of whether a
     * LOAD clause is present.
     */
    yieldScoreAs?: GlideString;
}

/**
 * The VSIM arm of an {@link GlideFt.hybrid | FT.HYBRID} query: a vector similarity search against
 * a vector field, run alongside the SEARCH arm and fused with it.
 *
 * The query vector can be given two ways:
 * - As raw bytes (a `Buffer`) or any other `GlideString`: the client generates an internal `$`-
 *   prefixed PARAMS entry under the hood and references it on the wire. Confirmed live:
 *   FT.HYBRID's VSIM clause has no wire-level inline vector literal at all -- the parameter
 *   token must start with `$` or the server rejects it outright ("VSIM: vector parameter must
 *   start with '$'"), so an auto-generated PARAMS entry is the only way to offer a "just pass
 *   the bytes" call shape.
 * - As an explicit `$name` string: the caller manages their own entry in
 *   {@link FtHybridOptions.params}, exactly like `FtSearchOptions.params`/`FtAggregateOptions.params`
 *   already work for every other PARAMS-driven query.
 *
 * Only a `string` starting with `$` is treated as a parameter reference. A `Buffer` is always
 * raw vector data, even if its first byte is `0x24` (`$`) -- a raw vector's packed binary
 * representation is arbitrary and can legitimately start with that byte by coincidence; treating
 * a `Buffer` the same way as a `string` here would misclassify that vector as a parameter
 * reference, skip generating the internal PARAMS entry, and send the raw vector bytes in the
 * parameter-reference position, which the server cannot resolve.
 */
export interface FtHybridVsimArm {
    /** The vector field's identifier, without the leading `@` (added automatically). */
    field: GlideString;
    /**
     * The query vector, either as raw bytes/any `GlideString` (an internal PARAMS entry is
     * generated automatically) or as an explicit `$name` string referencing an entry the caller
     * has placed in {@link FtHybridOptions.params}. A `string` starting with `$` is always
     * treated as a parameter reference, never as literal vector bytes; a `Buffer` is always raw
     * vector data regardless of its first byte.
     */
    vector: GlideString;
    /**
     * The number of nearest neighbours to return. Confirmed live: optional on the wire --
     * omitting the whole KNN block (which happens when `k`, `efRuntime` and `shardKRatio` are
     * all unset) defaults to 10, the same default RediSearch documents for FT.HYBRID's VSIM
     * clause.
     */
    k?: number;
    /**
     * HNSW search-time effort parameter. Only valid when the KNN block is written (RANGE is not
     * currently supported by this typed API). Rejected by the server when `filter.policy` is
     * `"ADHOC"` ("EF_RUNTIME is irrelevant for POLICY ADHOC"). Validated client-side too:
     * {@link GlideFt.hybrid} throws an `Error` for that combination, rather than serializing the
     * invalid combination and relying on a server round trip to reject it.
     */
    efRuntime?: number;
    /**
     * Tunes how much of `k` each shard returns during a cluster fanout. Confirmed live: parsed
     * and accepted but does not change query results, only how much work each shard does to
     * produce them -- accepted here so a query written for RediSearch is not rejected by this
     * client either.
     */
    shardKRatio?: number;
    /**
     * Exposes this arm's score (the vector distance) under the given column name. See
     * {@link FtHybridSearchArm.yieldScoreAs}'s docstring for the LOAD-clause restriction that
     * applies identically here.
     */
    yieldScoreAs?: GlideString;
    /** A pre-filter on the vector search. See {@link FtHybridVsimFilter}. */
    filter?: FtHybridVsimFilter;
}

/**
 * Fuses the two arms' results using Reciprocal Rank Fusion: each arm's rank (not its raw score)
 * contributes `1 / (constant + rank)` to the fused score.
 */
export interface FtHybridRrf {
    method: "RRF";
    /**
     * The RRF constant. Must be non-negative (confirmed live: the server rejects a negative
     * value -- it places a pole at `rank == -constant`). Validated client-side too:
     * {@link GlideFt.hybrid} throws an `Error` for a negative `constant`, rather than
     * serializing the invalid value and relying on a server round trip to reject it.
     */
    constant?: number;
    /**
     * How many of each arm's results take part in fusion (and, in cluster mode, how many each
     * shard is asked to fetch). Bounded by the server's `max-combine-window` config.
     */
    window?: number;
    /** Exposes the fused score under the given column name instead of the default `__score`. */
    yieldScoreAs?: GlideString;
}

/**
 * Fuses the two arms' results linearly: `alpha * searchScore + beta * vsimScore`. Confirmed
 * live: writing neither `alpha` nor `beta` defaults both to 0.3/0.7 respectively; writing exactly
 * one without the other is rejected by the server as more likely a typo than an intentional
 * default.
 */
export interface FtHybridLinear {
    method: "LINEAR";
    /**
     * The SEARCH arm's weight. Defaults (server-side) to `0.3` when neither `alpha` nor `beta`
     * is set. Setting one without the other is rejected by the server. Validated client-side
     * too: {@link GlideFt.hybrid} throws an `Error` if exactly one of `alpha`/`beta` is set,
     * rather than serializing the invalid combination and relying on a server round trip to
     * reject it.
     */
    alpha?: number;
    /** The VSIM arm's weight. Defaults (server-side) to `0.7` when neither `alpha` nor `beta` is set. */
    beta?: number;
    /** How many of each arm's results take part in fusion. */
    window?: number;
    /** Exposes the fused score under the given column name instead of the default `__score`. */
    yieldScoreAs?: GlideString;
}

/**
 * Fuses the two arms' results using a caller-supplied expression. `expr` is a raw string written
 * in the same expression language {@link FtAggregateApply}'s `expression` uses -- the server
 * compiles it against a context where each arm's score is reachable via its own `yieldScoreAs`
 * alias (if any), a positional default `@__arm<i>_score`, and (for this SEARCH+VSIM two-arm
 * shape) `@__search_score` / `@__vector_score` specifically.
 *
 * This is a valkey-search extension not present in RediSearch's FT.HYBRID (included here anyway
 * rather than dropped).
 */
export interface FtHybridFunction {
    method: "FUNCTION";
    /**
     * The fusion expression, as a raw string (e.g. `"@__search_score + @__vector_score"` or,
     * with explicit arm aliases, `"@ss + @vs"`).
     */
    expr: GlideString;
    /**
     * How many of each arm's results take part in fusion. Confirmed live: if left unset, the
     * server defaults this to the widest window allowed rather than the usual RRF/LINEAR
     * default, since a caller-written expression typically expects to see every matching
     * candidate, not a top-window slice.
     */
    window?: number;
    /** Exposes the fused score under the given column name instead of the default `__score`. */
    yieldScoreAs?: GlideString;
}

/**
 * The COMBINE clause of {@link GlideFt.hybrid | FT.HYBRID}, controlling how the SEARCH and VSIM
 * arms' per-document scores are fused into one ranking. See {@link FtHybridRrf},
 * {@link FtHybridLinear} and {@link FtHybridFunction}. If omitted entirely from
 * {@link FtHybridOptions.combine}, the server's own default fusion method applies.
 */
export type FtHybridCombine = FtHybridRrf | FtHybridLinear | FtHybridFunction;

/**
 * Additional parameters for {@link GlideFt.hybrid | FT.HYBRID}, applied to the aggregate-suffix
 * tail that follows the mandatory SEARCH, VSIM and optional COMBINE clauses (which are passed
 * directly to {@link GlideFt.hybrid}, not through this type).
 */
export type FtHybridOptions = {
    /**
     * Controls how the SEARCH and VSIM arms' scores are fused. See {@link FtHybridCombine}. If
     * omitted, the server's own default fusion method applies.
     */
    combine?: FtHybridCombine;
    /**
     * FILTER, LIMIT, GROUPBY, SORTBY and APPLY clauses, reusing the same clause shapes as
     * {@link FtAggregateOptions.clauses} verbatim (FT.HYBRID forwards this whole tail to the
     * exact same aggregate pipeline parser FT.AGGREGATE uses). Confirmed live: every arm alias
     * is freely reachable from these clauses (APPLY, SORTBY, GROUPBY/REDUCE) -- only the LOAD
     * clause has the restriction described on {@link FtHybridSearchArm.yieldScoreAs}. FT.HYBRID
     * also applies its own default LIMIT of 10 rows when no `FtAggregateLimit` clause is given
     * here (confirmed live and in source) -- FT.AGGREGATE has no such default and returns
     * everything. This tail is always emitted after `params`'s own serialized arguments (PARAMS
     * is always present for FT.HYBRID, so this tail is never empty) -- emitting a leading FILTER
     * clause right after the VSIM arm would otherwise let the server's VSIM parser claim it as a
     * VSIM pre-filter instead.
     */
    clauses?: (
        | FtAggregateLimit
        | FtAggregateFilter
        | FtAggregateGroupBy
        | FtAggregateSortBy
        | FtAggregateApply
    )[];
    /**
     * Requests a paged reply. See {@link FtWithCursor}.
     */
    withCursor?: FtWithCursor;
    /**
     * Restricts the query to the local shard rather than fanning out across the cluster. This is
     * a valkey-search extension not present in RediSearch's FT.HYBRID (included here anyway
     * rather than dropped). Confirmed live: LOCALONLY is accepted anywhere in this tail BEFORE
     * PARAMS (including after DIALECT or a LOAD clause) but is rejected ("Unexpected argument
     * `LOCALONLY`") once PARAMS has been parsed, regardless of what follows PARAMS. `GlideFt.hybrid`
     * always emits it before `params` and `clauses` (PARAMS always follows it immediately), so
     * this restriction is handled transparently regardless of the order its own arguments were
     * given in.
     */
    localOnly?: boolean;
    /** Overrides the timeout parameter of the module, in milliseconds. */
    timeout?: number;
    /**
     * Param key/value pairs referenced from within the SEARCH query, a VSIM arm's `$name` vector
     * reference, or any clause's expression text. If {@link FtHybridVsimArm.vector} in the same
     * call is given raw vector bytes rather than an explicit `$name` string, an internally
     * generated entry is merged into this mapping automatically -- a name collision with a
     * caller-supplied entry here raises an `Error` rather than silently overwriting either one.
     */
    params?: GlideRecord<GlideString>;
    /**
     * The query dialect version to use. FT.HYBRID is fixed at dialect 2 server-side (confirmed
     * in source); passing any other value is rejected by the server.
     */
    dialect?: number;
} & (
    | {
          /** List of fields to load from the index, either a plain identifier or a {@link LoadField} for an aliased entry. */
          loadFields?: (GlideString | LoadField)[];
          /** `loadAll` and `loadFields` are mutually exclusive. */
          loadAll?: never;
      }
    | {
          /**
           * Loads every field declared in the index, plus both arms' score aliases
           * automatically. Confirmed live: unlike a named `loadFields` list, `LOAD *` cannot
           * name an arm's score alias at all (there is nothing to name), so this form has no
           * equivalent of the LOAD-clause alias restriction described on
           * {@link FtHybridSearchArm.yieldScoreAs}.
           */
          loadAll?: boolean;
          /** `loadAll` and `loadFields` are mutually exclusive. */
          loadFields?: never;
      }
);
