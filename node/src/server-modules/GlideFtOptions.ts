/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { GlideString } from "../BaseClient";

interface BaseField {
    /** The name of the field. */
    name: GlideString;
    /** An alias for field. */
    alias?: GlideString;
}

/**
 * If the field contains any blob of data.
 */
export type TextField = BaseField & {
    /** Field identifier */
    type: "TEXT";
}

/**
 * If the field contains a tag field.
 */
export type TagField = BaseField & {
    /** Field identifier */
    type: "TAG";
    /** Specify how text in the attribute is split into individual tags. Must be a single character. */
    separator?: GlideString;
    /** Preserve the original letter cases of tags. If set to False, characters are converted to lowercase by default. */
    caseSensitive?: boolean;
}

/**
 * If the field contains a number.
 */
export type NumericField = BaseField & {
    /** Field identifier */
    type: "NUMERIC";
}

/**
 * If the field is a vector field that supports vector search.
 */
export type VectorField = BaseField & {
    /** Field identifier */
    type: "VECTOR";
    /** Additional attributes to be passed with the vector field after the algorithm name. */
    attributes: VectorFieldAttributesFlat | VectorFieldAttributesHnsw;
}

/**
 * Base class for defining vector field attributes to be used after the vector algorithm name.
 */
export interface VectorFieldAttributes {
    /** Number of dimensions in the vector. */
    dim: number;
    /**
     * The distance metric used in vector type field. Can be one of [L2 | IP | COSINE].
     */
    distanceMetric: "L2" | "IP" | "COSINE";
    /** Vector type. The only supported type is FLOAT32. */
    type: "FLOAT32";
    /**
     * Initial vector capacity in the index affecting memory allocation size of the index. Defaults to 1024.
     */
    initialCap?: number;
}
export type VectorFieldAttributesFlat = VectorFieldAttributes & {
    /**
     * Vector field that supports vector search by FLAT (brute force) algorithm.
     * The algorithm is a brute force linear processing of each vector in the index, yielding exact
     * answers within the bounds of the precision of the distance computations.
     */
    algorithm: "FLAT";
};
export type VectorFieldAttributesHnsw = VectorFieldAttributes & {
    /**
     * Vector field that supports vector search by HNSM (Hierarchical Navigable Small
     * World) algorithm.
     * The algorithm provides an approximation of the correct answer in exchange for substantially
     * lower execution times.
     */
    algorithm: "HNSW";
    /**
     * Number of maximum allowed outgoing edges for each node in the graph in each layer. Default is 16, maximum is 512.
     * Equivalent to the `m` attribute.
     */
    numberOfEdges?: number;
    /**
     * Controls the number of vectors examined during index construction. Default value is 200, Maximum value is 4096.
     * Equivalent to the `efContruction` attribute.
     */
    vectorsExaminedOnConstruction?: number;
    /**
     * Controls the number of vectors examined during query operations. Default value is 10, Maximum value is 4096.
     * Equivalent to the `efRuntime` attribute.
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
