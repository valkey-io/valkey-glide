/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { GlideString } from "../BaseClient";

/**
 * Options for the type of data for which the index is being created.
 */
export enum DataType {
    /** If the created index will index HASH data. */
    Hash = "HASH",
    /** If the created index will index JSON document data. */
    Json = "JSON",
}

/**
 * Algorithm for vector type fields used for vector similarity search.
 */
export enum VectorAlgorithm {
    /**
     * Hierarchical Navigable Small World algorithm.
     */
    HNSW = "HNSW",
    /**
     * Flat algorithm or the brute force algorithm.
     */
    FLAT = "FLAT",
}

/**
 * The metric options for the distance in vector type field.
 */
export enum DistanceMetricType {
    /**
     * Euclidean distance.
     */
    L2 = "L2",
    /**
     * Inner product
     */
    IP = "IP",
    /**
     * Cosine distance
     */
    COSINE = "COSINE",
}

/**
 * Type type for the vector field type.
 */
export enum VectorType {
    /**
     * FLOAT32 type of vector. The only supported type.
     */
    FLOAT32 = "FLOAT32",
}

/**
 * If the field contains any blob of data.
 */
export interface TextField {
    /** Field identifier */
    type: "TEXT";
    /** The name of the text field. */
    name: GlideString;
    /** An alias for the field. */
    alias?: GlideString;
}

/**
 * If the field contains a tag field.
 */
export interface TagField {
    /** Field identifier */
    type: "TAG";
    /** The name of the text field. */
    name: GlideString;
    /** An alias for the field. */
    alias?: GlideString;
    /** Specify how text in the attribute is split into individual tags. Must be a single character. */
    separator?: GlideString;
    /** Preserve the original letter cases of tags. If set to False, characters are converted to lowercase by default. */
    caseSensitive?: boolean;
}

/**
 * If the field contains a number.
 */
export interface NumericField {
    /** Field identifier */
    type: "NUMERIC";
    /** The name of the text field. */
    name: GlideString;
    /** An alias for the field. */
    alias?: GlideString;
}

/**
 * If the field is a vector field that supports vector search.
 */
export interface VectorField {
    /** Field identifier */
    type: "VECTOR";
    /** The name of the text field. */
    name: GlideString;
    /** An alias for the field. */
    alias?: GlideString;
    /** The vector indexing algorithm. */
    algorithm: VectorAlgorithm;
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
    distanceMetric: DistanceMetricType;
    /** Vector type. The only supported type is FLOAT32. */
    type: VectorType;
    /**
     * Initial vector capacity in the index affecting memory allocation size of the index. Defaults to 1024.
     */
    initialCap?: number;
}
export type VectorFieldAttributesFlat = VectorFieldAttributes;
export type VectorFieldAttributesHnsw = {
    /**
     * Number of maximum allowed outgoing edges for each node in the graph in each layer. Default is 16, maximum is 512.
     */
    m?: number;
    /**
     * Controls the number of vectors examined during index construction. Default value is 200, Maximum value is 4096.
     */
    efContruction?: number;
    /**
     * Controls the number of vectors examined during query operations. Default value is 10, Maximum value is 4096.
     */
    efRuntime?: number;
} & VectorFieldAttributes;

export type Field = TextField | TagField | NumericField | VectorField;

/**
 * Represents the input options to be used in the FT.CREATE command.
 * All fields in this class are optional inputs for FT.CREATE.
 */
export interface FtCreateOptions {
    /** The type of data to be indexed using FT.CREATE. */
    dataType: DataType;
    /** The prefix of the key to be indexed. */
    prefixes?: GlideString[];
}
