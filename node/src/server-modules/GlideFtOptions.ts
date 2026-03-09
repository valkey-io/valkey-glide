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
    /** Vector type. The only supported type is FLOAT32. */
    type?: "FLOAT32";
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
} & (
    | {
          /** List of fields to load from the index. */
          loadFields?: GlideString[];
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
 * Represents the input options to be used in the FT.SEARCH command.
 * All fields in this class are optional inputs for FT.SEARCH.
 */
export type FtSearchOptions = {
    /** Query timeout in milliseconds. */
    timeout?: number;

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
           * Once set, the query will return only the number of documents in the result set without actually
           * returning them.
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
