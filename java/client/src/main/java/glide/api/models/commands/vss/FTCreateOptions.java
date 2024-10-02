/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.vss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

// TODO examples
public class FTCreateOptions {
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

    /** Field contains a number. */
    public static class NumericField implements Field {
        @Override
        public String[] toArgs() {
            return new String[] {"NUMERIC"};
        }
    }

    /** Field contains any blob of data. */
    public static class TextField implements Field {
        @Override
        public String[] toArgs() {
            return new String[] {"TEXT"};
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
            args.add("TAG");
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

    /** Vector index algorithm. */
    public enum Algorithm {
        /**
         * Hierarchical Navigable Small World provides an approximation of nearest neighbors algorithm
         * that uses a multi-layered graph.
         */
        HNSW,
        /**
         * The Flat algorithm is a brute force linear processing of each vector in the index, yielding
         * exact answers within the bounds of the precision of the distance computations.
         */
        FLAT
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
        private final Map<String, String> params;
        private final String Algorithm;

        @Override
        public String[] toArgs() {
            var args = new ArrayList<String>();
            args.add("VECTOR");
            args.add(Algorithm);
            args.add(Integer.toString(params.size() * 2));
            params.forEach(
                    (name, value) -> {
                        args.add(name);
                        args.add(value);
                    });
            return args.toArray(String[]::new);
        }
    }

    /**
     * Vector field that supports vector search by <code>HNSM</code> (Hierarchical Navigable Small
     * World) algorithm.<br>
     * The algorithm provides an approximation of the correct answer in exchange for substantially
     * lower execution times.
     */
    public static class VectorFieldHnsw extends VectorField {
        protected VectorFieldHnsw(Map<String, String> params) {
            super(params, "HNSW");
        }

        /**
         * Init a {@link VectorFieldHnsw}'s builder.
         *
         * @param distanceMetric {@link DistanceMetric} to measure the degree of similarity between two
         *     vectors.
         * @param dimensions Vector dimension, specified as a positive integer. Maximum: 32768
         */
        public static VectorFieldHnswBuilder builder(
                DistanceMetric distanceMetric, Integer dimensions) {
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
            params.put("M", Integer.toString(numberOfEdges));
            return this;
        }

        /**
         * (Optional) The number of vectors examined during index construction. Higher values for this
         * parameter will improve recall ratio at the expense of longer index creation times. Default
         * value is 200. Maximum value is 4096.
         */
        public VectorFieldHnswBuilder vectorsExaminedOnConstruction(int vectorsExaminedOnConstruction) {
            params.put("EF_CONSTRUCTION", Integer.toString(vectorsExaminedOnConstruction));
            return this;
        }

        /**
         * (Optional) The number of vectors examined during query operations. Higher values for this
         * parameter can yield improved recall at the expense of longer query times. The value of this
         * parameter can be overriden on a per-query basis. Default value is 10. Maximum value is 4096.
         */
        public VectorFieldHnswBuilder vectorsExaminedOnRuntime(int vectorsExaminedOnRuntime) {
            params.put("EF_RUNTIME", Integer.toString(vectorsExaminedOnRuntime));
            return this;
        }
    }

    /**
     * Vector field that supports vector search by <code>FLAT</code> (brute force) algorithm.<br>
     * The algorithm is a brute force linear processing of each vector in the index, yielding exact
     * answers within the bounds of the precision of the distance computations.
     */
    public static class VectorFieldFlat extends VectorField {

        protected VectorFieldFlat(Map<String, String> params) {
            super(params, "FLAT");
        }

        /**
         * Init a {@link VectorFieldFlat}'s builder.
         *
         * @param distanceMetric {@link DistanceMetric} to measure the degree of similarity between two
         *     vectors.
         * @param dimensions Vector dimension, specified as a positive integer. Maximum: 32768
         */
        public static VectorFieldFlatBuilder builder(
                DistanceMetric distanceMetric, Integer dimensions) {
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
        protected final Map<String, String> params = new HashMap<>();

        VectorFieldBuilder(DistanceMetric distanceMetric, int dimensions) {
            params.put("TYPE", "FLOAT32");
            params.put("DIM", Integer.toString(dimensions));
            params.put("DISTANCE_METRIC", distanceMetric.toString());
        }

        /**
         * Initial vector capacity in the index affecting memory allocation size of the index. Defaults
         * to 1024.
         */
        @SuppressWarnings("unchecked")
        public T initialCapacity(int initialCapacity) {
            params.put("INITIAL_CAP", Integer.toString(initialCapacity));
            return (T) this;
        }

        public abstract VectorField build();
    }

    /** Field definition to be added into index schema. */
    public static class FieldInfo {
        private final String identifier;
        private final String alias;
        private final Field field;

        /**
         * Field definition to be added into index schema.
         *
         * @param identifier Field identifier (name).
         * @param field The {@link Field} itself.
         */
        public FieldInfo(String identifier, Field field) {
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
        public FieldInfo(String identifier, String alias, Field field) {
            this.identifier = identifier;
            this.alias = alias;
            this.field = field;
        }

        /** Convert to module API. */
        public String[] toArgs() {
            var args = new ArrayList<String>();
            args.add(identifier);
            if (alias != null) {
                args.add("AS");
                args.add(alias);
            }
            args.addAll(List.of(field.toArgs()));
            return args.toArray(String[]::new);
        }
    }
}
