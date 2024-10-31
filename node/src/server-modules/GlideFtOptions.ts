/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { GlideRecord, GlideString } from "../BaseClient";

interface BaseField {
    /** The name of the field. */
    name: GlideString;
    /** An alias for field. */
    alias?: GlideString;
}

/**
 * Field contains any blob of data.
 */
export type TextField = BaseField & {
    /** Field identifier */
    type: "TEXT";
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
export interface VectorFieldAttributes {
    /** Number of dimensions in the vector. Equivalent to `DIM` in the module API. */
    dimensions: number;
    /**
     * The distance metric used in vector type field. Can be one of `[L2 | IP | COSINE]`. Equivalent to `DISTANCE_METRIC` in the module API.
     */
    distanceMetric: "L2" | "IP" | "COSINE";
    /** Vector type. The only supported type is FLOAT32. */
    type?: "FLOAT32";
    /**
     * Initial vector capacity in the index affecting memory allocation size of the index. Defaults to `1024`. Equivalent to `INITIAL_CAP` in the module API.
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
     * Number of maximum allowed outgoing edges for each node in the graph in each layer. Default is `16`, maximum is `512`.
     * Equivalent to `M` in the module API.
     */
    numberOfEdges?: number;
    /**
     * Controls the number of vectors examined during index construction. Default value is `200`, Maximum value is `4096`.
     * Equivalent to `EF_CONSTRUCTION` in the module API.
     */
    vectorsExaminedOnConstruction?: number;
    /**
     * Controls the number of vectors examined during query operations. Default value is `10`, Maximum value is `4096`.
     * Equivalent to `EF_RUNTIME` in the module API.
     */
    vectorsExaminedOnRuntime?: number;
};

export type Field = TextField | TagField | NumericField | VectorField;

/**
 * Represents the input options to be used in the FT.CREATE command.
 * All fields in this class are optional inputs for FT.CREATE.
 */
export interface FtCreateOptions {
    /** The type of data to be indexed using FT.CREATE. */
    dataType: "JSON" | "HASH";
    /** The prefix of the key to be indexed. */
    prefixes?: GlideString[];
}

/**
 * Represents the input options to be used in the FT.SEARCH command.
 * All fields in this class are optional inputs for FT.SEARCH.
 */
export interface FtSearchOptions {
    /**
     * Add a field to be returned.
     * @param fieldIdentifier field name to return.
     * @param alias optional alias for the field name to return.
     */
    returnFields?: { fieldIdentifier: GlideString; alias?: GlideString }[];

    /** Query timeout in milliseconds. */
    timeout?: number;

    /**
     * Query parameters, which could be referenced in the query by `$` sign, followed by
     * the parameter name.
     */
    params?: GlideRecord<GlideString>;

    /**
     * Configure query pagination. By default only first 10 documents are returned.
     *
     * @param offset Zero-based offset.
     * @param count Number of elements to return.
     */
    limit?: { offset: number; count: number };

    /**
     * Once set, the query will return only the number of documents in the result set without actually
     * returning them.
     */
    count?: boolean;
}
