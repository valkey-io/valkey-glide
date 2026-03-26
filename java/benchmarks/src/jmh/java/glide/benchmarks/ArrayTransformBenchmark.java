/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks;

import static glide.api.models.GlideString.gs;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import glide.api.models.GlideString;
import glide.utils.ArrayTransformUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * JMH microbenchmarks for ArrayTransformUtils methods.
 *
 * <p>Measures the for-loop + pre-sized array implementations. GC profiler captures allocations/op
 * alongside latency.
 *
 * <p>Run: ./gradlew :benchmarks:jmh
 *
 * <p>Results: build/reports/jmh/results.json
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)
public class ArrayTransformBenchmark {

    /** Representative map sizes: small (typical command), medium, large (bulk ops). */
    @Param({"5", "50", "500"})
    public int mapSize;

    private Map<GlideString, GlideString> glideStringMap;
    private Map<String, String> stringMap;
    private Map<String, Double> scoreMap;
    private Map<GlideString, Double> scoreBinaryMap;

    @Setup
    public void setup() {
        glideStringMap = new LinkedHashMap<>();
        stringMap = new LinkedHashMap<>();
        scoreMap = new LinkedHashMap<>();
        scoreBinaryMap = new LinkedHashMap<>();
        for (int i = 0; i < mapSize; i++) {
            String key = "key" + i;
            String val = "value" + i;
            glideStringMap.put(gs(key), gs(val));
            stringMap.put(key, val);
            scoreMap.put(key, (double) i);
            scoreBinaryMap.put(gs(key), (double) i);
        }
    }

    // --- Map-to-interleaved-array methods (key, value, key, value, ...) ---

    /** Used by: mset, msetnx (binary variants). */
    @Benchmark
    public GlideString[] convertMapToKeyValueGlideStringArray() {
        return ArrayTransformUtils.convertMapToKeyValueGlideStringArray(glideStringMap);
    }

    /** Used by: mset, hset (String variants). */
    @Benchmark
    public String[] convertMapToKeyValueStringArray() {
        return ArrayTransformUtils.convertMapToKeyValueStringArray(stringMap);
    }

    /** Used by: zadd with scores. */
    @Benchmark
    public String[] convertMapToValueKeyStringArray() {
        return ArrayTransformUtils.convertMapToValueKeyStringArray(scoreMap);
    }

    /** Used by: zadd binary variant. */
    @Benchmark
    public GlideString[] convertMapToValueKeyStringArrayBinary() {
        return ArrayTransformUtils.convertMapToValueKeyStringArrayBinary(scoreBinaryMap);
    }

    /** Used by: geoadd and other commands accepting generic maps. */
    @Benchmark
    public GlideString[] flattenMapToGlideStringArray() {
        return ArrayTransformUtils.flattenMapToGlideStringArray(glideStringMap);
    }

    @Benchmark
    public GlideString[] flattenMapToGlideStringArrayValueFirst() {
        return ArrayTransformUtils.flattenMapToGlideStringArrayValueFirst(glideStringMap);
    }

    /**
     * Worst case: 2 ArrayLists + 2 intermediate arrays + concatenateArrays stream internally. Used by
     * commands that need keys-first, values-second layout.
     */
    @Benchmark
    public GlideString[] flattenAllKeysFollowedByAllValues() {
        return ArrayTransformUtils.flattenAllKeysFollowedByAllValues(glideStringMap);
    }
}
