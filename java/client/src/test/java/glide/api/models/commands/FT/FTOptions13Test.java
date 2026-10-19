/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.FT;

import static glide.api.models.GlideString.gs;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.models.GlideString;
import glide.api.models.commands.FT.FTCreateOptions.DataType;
import glide.api.models.commands.FT.FTCreateOptions.DistanceMetric;
import glide.api.models.commands.FT.FTCreateOptions.NumericField;
import glide.api.models.commands.FT.FTCreateOptions.TagField;
import glide.api.models.commands.FT.FTCreateOptions.TextField;
import glide.api.models.commands.FT.FTCreateOptions.VectorFieldFlat;
import glide.api.models.commands.FT.FTCreateOptions.VectorType;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Argv-serialization tests for the valkey-search 1.3 stable surface added to the FT.* option
 * classes: INKEYS, FLOAT16/BFLOAT16 vector types, FT.CREATE FILTER/SCORE_FIELD/NOHL, per-field UNF,
 * and {@code count}'s LIMIT 0 0 serialization. Mirrors Node's {@code GlideFtOptions.test.ts} and
 * Python's {@code test_options.py}.
 */
public class FTOptions13Test {

    // ---- FTCreateOptions ----

    @Test
    public void testFTCreateOptions_filterScoreFieldNoHighlight() {
        GlideString[] args =
                FTCreateOptions.builder()
                        .dataType(DataType.HASH)
                        .filter("@price > 0")
                        .scoreField("boost")
                        .noHighlight(true)
                        .build()
                        .toArgs();
        List<GlideString> argList = Arrays.asList(args);
        assertTrue(argList.contains(gs("FILTER")));
        assertEquals(gs("@price > 0"), args[argList.indexOf(gs("FILTER")) + 1]);
        assertTrue(argList.contains(gs("SCORE_FIELD")));
        assertEquals(gs("boost"), args[argList.indexOf(gs("SCORE_FIELD")) + 1]);
        assertTrue(argList.contains(gs("NOHL")));
    }

    @Test
    public void testFTCreateOptions_emptyFilterThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FTCreateOptions.builder().dataType(DataType.HASH).filter("").build().toArgs());
    }

    @Test
    public void testFTCreateOptions_noHighlightOmittedWhenUnset() {
        GlideString[] args = FTCreateOptions.builder().dataType(DataType.HASH).build().toArgs();
        assertFalse(Arrays.asList(args).contains(gs("NOHL")));
    }

    // ---- Field UNF ----

    @Test
    public void testTextField_sortableThenUnf() {
        String[] args = new TextField(false, null, false, false, true, true).toArgs();
        List<String> argList = Arrays.asList(args);
        int sortableIndex = argList.indexOf("SORTABLE");
        assertTrue(sortableIndex >= 0);
        assertEquals("UNF", args[sortableIndex + 1]);
    }

    @Test
    public void testTextField_unfWithoutSortableThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TextField(false, null, false, false, false, true));
    }

    @Test
    public void testTagField_sortableThenUnf() {
        String[] args = new TagField(',', false, true, true).toArgs();
        List<String> argList = Arrays.asList(args);
        int sortableIndex = argList.indexOf("SORTABLE");
        assertTrue(sortableIndex >= 0);
        assertEquals("UNF", args[sortableIndex + 1]);
    }

    @Test
    public void testTagField_unfWithoutSortableThrows() {
        assertThrows(IllegalArgumentException.class, () -> new TagField(',', false, false, true));
    }

    @Test
    public void testNumericField_sortableThenUnf() {
        String[] args = new NumericField(true, true).toArgs();
        List<String> argList = Arrays.asList(args);
        int sortableIndex = argList.indexOf("SORTABLE");
        assertTrue(sortableIndex >= 0);
        assertEquals("UNF", args[sortableIndex + 1]);
    }

    @Test
    public void testNumericField_unfWithoutSortableThrows() {
        assertThrows(IllegalArgumentException.class, () -> new NumericField(false, true));
    }

    // ---- Vector field types ----

    @Test
    public void testVectorFieldFlat_float16Type() {
        String[] args =
                VectorFieldFlat.builder(DistanceMetric.L2, 2).type(VectorType.FLOAT16).build().toArgs();
        assertTrue(Arrays.asList(args).contains("FLOAT16"));
        assertFalse(Arrays.asList(args).contains("FLOAT32"));
    }

    @Test
    public void testVectorFieldFlat_bfloat16Type() {
        String[] args =
                VectorFieldFlat.builder(DistanceMetric.L2, 2).type(VectorType.BFLOAT16).build().toArgs();
        assertTrue(Arrays.asList(args).contains("BFLOAT16"));
    }

    @Test
    public void testVectorFieldFlat_defaultsToFloat32() {
        String[] args = VectorFieldFlat.builder(DistanceMetric.L2, 2).build().toArgs();
        assertTrue(Arrays.asList(args).contains("FLOAT32"));
    }

    // ---- FTSearchOptions ----

    @Test
    public void testFTSearchOptions_inKeysAbsent() {
        GlideString[] args = FTSearchOptions.builder().build().toArgs();
        assertFalse(Arrays.asList(args).contains(gs("INKEYS")));
    }

    @Test
    public void testFTSearchOptions_inKeysEmptyArray() {
        GlideString[] args = FTSearchOptions.builder().inKeys(new GlideString[0]).build().toArgs();
        List<GlideString> argList = Arrays.asList(args);
        int i = argList.indexOf(gs("INKEYS"));
        assertTrue(i >= 0);
        assertEquals(gs("0"), args[i + 1]);
    }

    @Test
    public void testFTSearchOptions_inKeysWithList() {
        GlideString[] args =
                FTSearchOptions.builder()
                        .inKeys(new GlideString[] {gs("key1"), gs("key2")})
                        .build()
                        .toArgs();
        List<GlideString> argList = Arrays.asList(args);
        int i = argList.indexOf(gs("INKEYS"));
        assertArrayEquals(
                new GlideString[] {gs("INKEYS"), gs("2"), gs("key1"), gs("key2")},
                Arrays.copyOfRange(args, i, i + 4));
    }

    @Test
    public void testFTSearchOptions_countMapsToLimitZeroZero() {
        GlideString[] args = FTSearchOptions.builder().count().build().toArgs();
        List<GlideString> argList = Arrays.asList(args);
        assertFalse(argList.contains(gs("COUNT")));
        int i = argList.indexOf(gs("LIMIT"));
        assertArrayEquals(
                new GlideString[] {gs("LIMIT"), gs("0"), gs("0")}, Arrays.copyOfRange(args, i, i + 3));
    }

    @Test
    public void testFTSearchOptions_countAndLimitAreMutuallyExclusive() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FTSearchOptions.builder().count().limit(0, 10).build().toArgs());
    }

    // ---- FTAggregateOptions.GroupBy.Reducer typed factories ----

    @Test
    public void testReducer_toList() {
        FTAggregateOptions.GroupBy.Reducer reducer =
                FTAggregateOptions.GroupBy.Reducer.toList("@price", "prices");
        GlideString[] args =
                new FTAggregateOptions.GroupBy(
                                new String[] {"@category"}, new FTAggregateOptions.GroupBy.Reducer[] {reducer})
                        .toArgs();
        List<GlideString> argList = Arrays.asList(args);
        int i = argList.indexOf(gs("REDUCE"));
        assertArrayEquals(
                new GlideString[] {
                    gs("REDUCE"), gs("TOLIST"), gs("1"), gs("@price"), gs("AS"), gs("prices")
                },
                Arrays.copyOfRange(args, i, i + 6));
    }

    @Test
    public void testReducer_firstValueWithoutBy() {
        FTAggregateOptions.GroupBy.Reducer reducer =
                FTAggregateOptions.GroupBy.Reducer.firstValue("@price", null, null, "first_price");
        GlideString[] args =
                new FTAggregateOptions.GroupBy(
                                new String[] {"@category"}, new FTAggregateOptions.GroupBy.Reducer[] {reducer})
                        .toArgs();
        List<GlideString> argList = Arrays.asList(args);
        int i = argList.indexOf(gs("REDUCE"));
        assertArrayEquals(
                new GlideString[] {
                    gs("REDUCE"), gs("FIRST_VALUE"), gs("1"), gs("@price"), gs("AS"), gs("first_price")
                },
                Arrays.copyOfRange(args, i, i + 6));
    }

    @Test
    public void testReducer_firstValueWithByAndOrder() {
        FTAggregateOptions.GroupBy.Reducer reducer =
                FTAggregateOptions.GroupBy.Reducer.firstValue(
                        "@price", "@price", FTAggregateOptions.SortBy.SortOrder.DESC, "max_price");
        GlideString[] args =
                new FTAggregateOptions.GroupBy(
                                new String[] {"@category"}, new FTAggregateOptions.GroupBy.Reducer[] {reducer})
                        .toArgs();
        List<GlideString> argList = Arrays.asList(args);
        int i = argList.indexOf(gs("REDUCE"));
        assertArrayEquals(
                new GlideString[] {
                    gs("REDUCE"),
                    gs("FIRST_VALUE"),
                    gs("4"),
                    gs("@price"),
                    gs("BY"),
                    gs("@price"),
                    gs("DESC"),
                    gs("AS"),
                    gs("max_price")
                },
                Arrays.copyOfRange(args, i, i + 9));
    }

    @Test
    public void testReducer_firstValueOrderWithoutByThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FTAggregateOptions.GroupBy.Reducer.firstValue(
                                "@price", null, FTAggregateOptions.SortBy.SortOrder.ASC, "first_price"));
    }

    @Test
    public void testReducer_randomSample() {
        FTAggregateOptions.GroupBy.Reducer reducer =
                FTAggregateOptions.GroupBy.Reducer.randomSample("@price", 2, "sample");
        GlideString[] args =
                new FTAggregateOptions.GroupBy(
                                new String[] {"@category"}, new FTAggregateOptions.GroupBy.Reducer[] {reducer})
                        .toArgs();
        List<GlideString> argList = Arrays.asList(args);
        int i = argList.indexOf(gs("REDUCE"));
        assertArrayEquals(
                new GlideString[] {
                    gs("REDUCE"), gs("RANDOM_SAMPLE"), gs("2"), gs("@price"), gs("2"), gs("AS"), gs("sample")
                },
                Arrays.copyOfRange(args, i, i + 7));
    }
}
