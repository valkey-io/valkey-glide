/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.FT;

import static glide.api.models.GlideString.gs;

import glide.api.BaseClient;
import glide.api.commands.servermodules.FT;
import glide.api.models.GlideString;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;

/**
 * Additional parameters for {@link FT#create(BaseClient, String, FieldInfo[], FTCreateOptions)}
 * command.
 */
@Builder
public class FTCreateOptions {
    /** The index type. If not given a {@link IndexType#HASH} index is created. */
    private final IndexType indexType;

    /** A list of prefixes of index definitions. */
    private final GlideString[] prefixes;

    FTCreateOptions(IndexType indexType, GlideString[] prefixes) {
        this.indexType = indexType;
        this.prefixes = prefixes;
    }

    public static FTCreateOptionsBuilder builder() {
        return new FTCreateOptionsBuilder();
    }

    public GlideString[] toArgs() {
        var args = new ArrayList<GlideString>();
        if (indexType != null) {
            args.add(gs("ON"));
            args.add(gs(indexType.toString()));
        }
        if (prefixes != null && prefixes.length > 0) {
            args.add(gs("PREFIX"));
            args.add(gs(Integer.toString(prefixes.length)));
            args.addAll(List.of(prefixes));
        }
        return args.toArray(GlideString[]::new);
    }

    public static class FTCreateOptionsBuilder {
        public FTCreateOptionsBuilder prefixes(String[] prefixes) {
            this.prefixes = Stream.of(prefixes).map(GlideString::gs).toArray(GlideString[]::new);
            return this;
        }
    }

    /** Type of the index dataset. */
    public enum IndexType {
        /** Data stored in hashes, so field identifiers are field names within the hashes. */
        HASH,
        /** Data stored in JSONs, so field identifiers are JSON Path expressions. */
        JSON
    }

    /**
     * A vector search field. Could be one of the following:
     *
     * <ul>
     *   <li>{@link NumericField}
     *   <li>{@link TextField}
     *   <li>{@link TagField}
     *   <li>{@link VectorFieldHnsw}
     *   <li>{@link VectorFieldFlat}
     * </ul>
     */
    public interface Field {
        /** Convert to module API. */
        String[] toArgs();
    }

    private enum FieldType {
        NUMERIC,
        TEXT,
        TAG,
        VECTOR
    }

    /** Field contains a number. */
    public static class NumericField implements Field {
        @Override
        public String[] toArgs() {
            return new String[] {FieldType.NUMERIC.toString()};
        }
    }

    /** Field contains any blob of data. */
    public static class TextField implements Field {
        @Override
        public String[] toArgs() {
            return new String[] {FieldType.TEXT.toString()};
        }
    }

    /**
     * Tag fields are similar to full-text fields, but they interpret the text as a simple list of
     * tags delimited by a separator character.<br>
     * For {@link IndexType#HASH} fields, separator default is a comma (<code>,</code>). For {@link
     * IndexType#JSON} fields, there is no default separator; you must declare one explicitly if
     * needed.
     */
    public static class TagField implements Field {
        private Optional<Character> separator;
        private final boolean caseSensitive;

        /** Create a <code>TAG</code> field. */
        public TagField() {
            this.separator = Optional.empty();
            this.caseSensitive = false;
        }

        /**
         * Create a <code>TAG</code> field.
         *
         * @param separator The tag separator.
         */
        public TagField(char separator) {
            this.separator = Optional.of(separator);
            this.caseSensitive = false;
        }

        /**
         * Create a <code>TAG</code> field.
         *
         * @param separator The tag separator.
         * @param caseSensitive Whether to keep the original case.
         */
        public TagField(char separator, boolean caseSensitive) {
            this.separator = Optional.of(separator);
            this.caseSensitive = caseSensitive;
        }

        /**
         * Create a <code>TAG</code> field.
         *
         * @param caseSensitive Whether to keep the original case.
         */
        public TagField(boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
        }

        @Override
        public String[] toArgs() {
            var args = new ArrayList<String>();
            args.add(FieldType.TAG.toString());
            if (separator.isPresent()) {
                args.add("SEPARATOR");
                args.add(separator.get().toString());
            }
            if (caseSensitive) {
                args.add("CASESENSITIVE");
            }
            return args.toArray(String[]::new);
        }
    }

    /**
     * Distance metrics to measure the degree of similarity between two vectors.<br>
     * The above metrics calculate distance between two vectors, where the smaller the value is, the
     * closer the two vectors are in the vector space.
     */
    public enum DistanceMetric {
        /** Euclidean distance between two vectors. */
        L2,
        /** Inner product of two vectors. */
        IP,
        /** Cosine distance of two vectors. */
        COSINE
    }

    /** Superclass for vector field implementations, contains common logic. */
    @AllArgsConstructor(access = AccessLevel.PROTECTED)
    abstract static class VectorField implements Field {
        private final Map<VectorAlgorithmParam, String> params;
        private final VectorAlgorithm algorithm;

        @Override
        public String[] toArgs() {
            var args = new ArrayList<String>();
            args.add(FieldType.VECTOR.toString());
            args.add(algorithm.toString());
            args.add(Integer.toString(params.size() * 2));
            params.forEach(
                    (name, value) -> {
                        args.add(name.toString());
                        args.add(value);
                    });
            return args.toArray(String[]::new);
        }
    }

    private enum VectorAlgorithm {
        HNSW,
        FLAT
    }

    private enum VectorAlgorithmParam {
        M,
        EF_CONSTRUCTION,
        EF_RUNTIME,
        TYPE,
        DIM,
        DISTANCE_METRIC,
        INITIAL_CAP
    }

    /**
     * Vector field that supports vector search by <code>HNSM</code> (Hierarchical Navigable Small
     * World) algorithm.<br>
     * The algorithm provides an approximation of the correct answer in exchange for substantially
     * lower execution times.
     */
    public static class VectorFieldHnsw extends VectorField {
        private VectorFieldHnsw(Map<VectorAlgorithmParam, String> params) {
            super(params, VectorAlgorithm.HNSW);
        }

        /**
         * Init a builder.
         *
         * @param distanceMetric {@link DistanceMetric} to measure the degree of similarity between two
         *     vectors.
         * @param dimensions Vector dimension, specified as a positive integer. Maximum: 32768
         */
        public static VectorFieldHnswBuilder builder(
                @NonNull DistanceMetric distanceMetric, int dimensions) {
            return new VectorFieldHnswBuilder(distanceMetric, dimensions);
        }
    }

    public static class VectorFieldHnswBuilder extends VectorFieldBuilder<VectorFieldHnswBuilder> {
        VectorFieldHnswBuilder(DistanceMetric distanceMetric, int dimensions) {
            super(distanceMetric, dimensions);
        }

        @Override
        public VectorFieldHnsw build() {
            return new VectorFieldHnsw(params);
        }

        /**
         * Number of maximum allowed outgoing edges for each node in the graph in each layer. On layer
         * zero the maximal number of outgoing edges is doubled. Default is 16 Maximum is 512.
         */
        public VectorFieldHnswBuilder numberOfEdges(int numberOfEdges) {
            params.put(VectorAlgorithmParam.M, Integer.toString(numberOfEdges));
            return this;
        }

        /**
         * (Optional) The number of vectors examined during index construction. Higher values for this
         * parameter will improve recall ratio at the expense of longer index creation times. Default
         * value is 200. Maximum value is 4096.
         */
        public VectorFieldHnswBuilder vectorsExaminedOnConstruction(int vectorsExaminedOnConstruction) {
            params.put(
                    VectorAlgorithmParam.EF_CONSTRUCTION, Integer.toString(vectorsExaminedOnConstruction));
            return this;
        }

        /**
         * (Optional) The number of vectors examined during query operations. Higher values for this
         * parameter can yield improved recall at the expense of longer query times. The value of this
         * parameter can be overriden on a per-query basis. Default value is 10. Maximum value is 4096.
         */
        public VectorFieldHnswBuilder vectorsExaminedOnRuntime(int vectorsExaminedOnRuntime) {
            params.put(VectorAlgorithmParam.EF_RUNTIME, Integer.toString(vectorsExaminedOnRuntime));
            return this;
        }
    }

    /**
     * Vector field that supports vector search by <code>FLAT</code> (brute force) algorithm.<br>
     * The algorithm is a brute force linear processing of each vector in the index, yielding exact
     * answers within the bounds of the precision of the distance computations.
     */
    public static class VectorFieldFlat extends VectorField {

        private VectorFieldFlat(Map<VectorAlgorithmParam, String> params) {
            super(params, VectorAlgorithm.FLAT);
        }

        /**
         * Init a builder.
         *
         * @param distanceMetric {@link DistanceMetric} to measure the degree of similarity between two
         *     vectors.
         * @param dimensions Vector dimension, specified as a positive integer. Maximum: 32768
         */
        public static VectorFieldFlatBuilder builder(
                @NonNull DistanceMetric distanceMetric, int dimensions) {
            return new VectorFieldFlatBuilder(distanceMetric, dimensions);
        }
    }

    public static class VectorFieldFlatBuilder extends VectorFieldBuilder<VectorFieldFlatBuilder> {
        VectorFieldFlatBuilder(DistanceMetric distanceMetric, int dimensions) {
            super(distanceMetric, dimensions);
        }

        @Override
        public VectorFieldFlat build() {
            return new VectorFieldFlat(params);
        }
    }

    abstract static class VectorFieldBuilder<T extends VectorFieldBuilder<T>> {
        final Map<VectorAlgorithmParam, String> params = new HashMap<>();

        VectorFieldBuilder(DistanceMetric distanceMetric, int dimensions) {
            params.put(VectorAlgorithmParam.TYPE, "FLOAT32");
            params.put(VectorAlgorithmParam.DIM, Integer.toString(dimensions));
            params.put(VectorAlgorithmParam.DISTANCE_METRIC, distanceMetric.toString());
        }

        /**
         * Initial vector capacity in the index affecting memory allocation size of the index. Defaults
         * to 1024.
         */
        @SuppressWarnings("unchecked")
        public T initialCapacity(int initialCapacity) {
            params.put(VectorAlgorithmParam.INITIAL_CAP, Integer.toString(initialCapacity));
            return (T) this;
        }

        public abstract VectorField build();
    }

    /** Field definition to be added into index schema. */
    public static class FieldInfo {
        private final GlideString identifier;
        private final GlideString alias;
        private final Field field;

        /**
         * Field definition to be added into index schema.
         *
         * @param identifier Field identifier (name).
         * @param field The {@link Field} itself.
         */
        public FieldInfo(@NonNull String identifier, @NonNull Field field) {
            this.identifier = gs(identifier);
            this.field = field;
            this.alias = null;
        }

        /**
         * Field definition to be added into index schema.
         *
         * @param identifier Field identifier (name).
         * @param alias Field alias.
         * @param field The {@link Field} itself.
         */
        public FieldInfo(@NonNull String identifier, @NonNull String alias, @NonNull Field field) {
            this.identifier = gs(identifier);
            this.alias = gs(alias);
            this.field = field;
        }

        /**
         * Field definition to be added into index schema.
         *
         * @param identifier Field identifier (name).
         * @param field The {@link Field} itself.
         */
        public FieldInfo(@NonNull GlideString identifier, @NonNull Field field) {
            this.identifier = identifier;
            this.field = field;
            this.alias = null;
        }

        /**
         * Field definition to be added into index schema.
         *
         * @param identifier Field identifier (name).
         * @param alias Field alias.
         * @param field The {@link Field} itself.
         */
        public FieldInfo(
                @NonNull GlideString identifier, @NonNull GlideString alias, @NonNull Field field) {
            this.identifier = identifier;
            this.alias = alias;
            this.field = field;
        }

        /** Convert to module API. */
        public GlideString[] toArgs() {
            var args = new ArrayList<GlideString>();
            args.add(identifier);
            if (alias != null) {
                args.add(gs("AS"));
                args.add(alias);
            }
            args.addAll(Stream.of(field.toArgs()).map(GlideString::gs).collect(Collectors.toList()));
            return args.toArray(GlideString[]::new);
        }
    }
}
