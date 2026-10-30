/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.FT;

import static glide.api.models.GlideString.gs;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.models.GlideString;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Argv-serialization tests for FT.HYBRID's typed options: {@link FTHybridSearchArm}, {@link
 * FTHybridVsimArm}, {@link FTHybridVsimFilter}, {@link FTHybridCombine} and {@link
 * FTHybridOptions}. Mirrors Python's {@code test_options.py} (TestFtHybridSearchArm,
 * TestFtHybridVsimArm, TestFtHybridVsimFilter, TestFtHybridCombine, TestFtWithCursor,
 * TestFtHybridOptions).
 */
public class FTHybridOptions13Test {

    // ---- FTHybridSearchArm ----

    @Test
    public void testSearchArm_bareQuery() {
        assertArrayEquals(
                new GlideString[] {gs("SEARCH"), gs("hello")}, new FTHybridSearchArm("hello").toArgs());
    }

    @Test
    public void testSearchArm_scorer() {
        assertArrayEquals(
                new GlideString[] {gs("SEARCH"), gs("hello"), gs("SCORER"), gs("BM25STD")},
                FTHybridSearchArm.withScorer("hello", "BM25STD").toArgs());
    }

    @Test
    public void testSearchArm_yieldScoreAs() {
        assertArrayEquals(
                new GlideString[] {gs("SEARCH"), gs("hello"), gs("YIELD_SCORE_AS"), gs("ss")},
                FTHybridSearchArm.withYieldScoreAs("hello", "ss").toArgs());
    }

    @Test
    public void testSearchArm_scorerAndYieldScoreAsCombined() {
        GlideString[] args = new FTHybridSearchArm(gs("hello"), gs("BM25STD"), gs("ss")).toArgs();
        assertArrayEquals(
                new GlideString[] {
                    gs("SEARCH"), gs("hello"), gs("SCORER"), gs("BM25STD"), gs("YIELD_SCORE_AS"), gs("ss")
                },
                args);
    }

    // ---- FTHybridVsimArm ----

    @Test
    public void testVsimArm_paramReferenceVectorUsedDirectly() {
        assertArrayEquals(
                new GlideString[] {gs("VSIM"), gs("@vec"), gs("$myvec")},
                new FTHybridVsimArm("vec", "$myvec").toArgs());
    }

    @Test
    public void testVsimArm_rawBytesVectorRequiresGeneratedParamName() {
        GlideString[] args = new FTHybridVsimArm("vec", "rawbytes".getBytes()).toArgs(gs("$generated"));
        assertArrayEquals(new GlideString[] {gs("VSIM"), gs("@vec"), gs("$generated")}, args);
    }

    @Test
    public void testVsimArm_isParamReferenceDetectsDollarPrefixedString() {
        assertTrue(new FTHybridVsimArm("vec", "$myvec").isParamReference());
        assertThrows(IllegalArgumentException.class, () -> new FTHybridVsimArm("vec", "notaparam"));
    }

    @Test
    public void testVsimArm_byteArrayAlwaysRawVectorDataEvenStartingWithDollarByte() {
        // A raw vector's packed binary representation is arbitrary and can legitimately start
        // with the byte 0x24 ('$') by coincidence. byte[] must never be treated as a $name
        // parameter reference regardless of its first byte -- this is decided purely by which
        // constructor overload (String vs byte[]) was called, never by inspecting content.
        byte[] rawVectorStartingWithDollarByte = {0x24, 0x01, 0x02, 0x03};
        assertEquals((byte) '$', rawVectorStartingWithDollarByte[0]);
        FTHybridVsimArm arm = new FTHybridVsimArm("vec", rawVectorStartingWithDollarByte);
        assertFalse(arm.isParamReference());
        assertArrayEquals(rawVectorStartingWithDollarByte, arm.getRawVector());

        FTHybridVsimArm dollarBytesArm = new FTHybridVsimArm("vec", "$myvec".getBytes());
        assertFalse(dollarBytesArm.isParamReference());
    }

    @Test
    public void testVsimArm_toArgsRequiresGeneratedParamNameForRawBytes() {
        FTHybridVsimArm arm = new FTHybridVsimArm("vec", "rawbytes".getBytes());
        assertThrows(IllegalStateException.class, arm::toArgs);
        GlideString[] args = arm.toArgs(gs("$generated"));
        assertArrayEquals(new GlideString[] {gs("VSIM"), gs("@vec"), gs("$generated")}, args);
    }

    @Test
    public void testVsimArm_vectorParamRefMustStartWithDollar() {
        assertThrows(IllegalArgumentException.class, () -> new FTHybridVsimArm("vec", "notaparam"));
    }

    @Test
    public void testVsimArm_knnBlockOmittedWhenNoKnnSubArgsGiven() {
        // Omitting the whole KNN block defaults K to 10 server-side -- the same default
        // RediSearch documents.
        GlideString[] args = new FTHybridVsimArm("vec", "$q").toArgs();
        assertFalse(Arrays.asList(args).contains(gs("KNN")));
    }

    @Test
    public void testVsimArm_kEmitsKnnBlock() {
        GlideString[] args = new FTHybridVsimArm("vec", "$q", 5, null, null).toArgs();
        assertArrayEquals(
                new GlideString[] {gs("VSIM"), gs("@vec"), gs("$q"), gs("KNN"), gs("2"), gs("K"), gs("5")},
                args);
    }

    @Test
    public void testVsimArm_efRuntimeAndShardKRatio() {
        GlideString[] args = new FTHybridVsimArm("vec", "$q", 5, 100, 0.5).toArgs();
        assertArrayEquals(
                new GlideString[] {
                    gs("VSIM"),
                    gs("@vec"),
                    gs("$q"),
                    gs("KNN"),
                    gs("6"),
                    gs("K"),
                    gs("5"),
                    gs("EF_RUNTIME"),
                    gs("100"),
                    gs("SHARD_K_RATIO"),
                    gs("0.5")
                },
                args);
    }

    @Test
    public void testVsimArm_efRuntimeRejectedWithAdhocFilterPolicy() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FTHybridVsimArm(
                                "vec",
                                "$q",
                                null,
                                100,
                                null,
                                null,
                                new FTHybridVsimFilter("@title:hello", HybridFilterPolicy.ADHOC)));
    }

    @Test
    public void testVsimArm_efRuntimeAllowedWithBatchesFilterPolicy() {
        GlideString[] args =
                new FTHybridVsimArm(
                                "vec",
                                "$q",
                                null,
                                100,
                                null,
                                null,
                                new FTHybridVsimFilter("@title:hello", HybridFilterPolicy.BATCHES))
                        .toArgs();
        assertTrue(Arrays.asList(args).contains(gs("EF_RUNTIME")));
    }

    @Test
    public void testVsimArm_efRuntimeAllowedWithUnfilteredVsim() {
        GlideString[] args = new FTHybridVsimArm("vec", "$q", null, 100, null).toArgs();
        assertTrue(Arrays.asList(args).contains(gs("EF_RUNTIME")));
    }

    @Test
    public void testVsimArm_yieldScoreAsAfterKnnBlock() {
        GlideString[] args = new FTHybridVsimArm("vec", "$q", 5, null, null, gs("vs")).toArgs();
        assertArrayEquals(
                new GlideString[] {
                    gs("VSIM"),
                    gs("@vec"),
                    gs("$q"),
                    gs("KNN"),
                    gs("2"),
                    gs("K"),
                    gs("5"),
                    gs("YIELD_SCORE_AS"),
                    gs("vs")
                },
                args);
    }

    @Test
    public void testVsimArm_filterAppendedAfterYieldScoreAs() {
        GlideString[] args =
                new FTHybridVsimArm(
                                "vec", "$q", null, null, null, gs("vs"), new FTHybridVsimFilter("@title:hello"))
                        .toArgs();
        assertArrayEquals(
                new GlideString[] {
                    gs("VSIM"),
                    gs("@vec"),
                    gs("$q"),
                    gs("YIELD_SCORE_AS"),
                    gs("vs"),
                    gs("FILTER"),
                    gs("@title:hello")
                },
                args);
    }

    // ---- FTHybridVsimArm.Builder (consistent with FTHybridOptions' existing @Builder usage) ----

    @Test
    public void testVsimArmBuilder_paramReferenceEquivalentToConstructor() {
        GlideString[] args = FTHybridVsimArm.builder("vec", "$myvec").build().toArgs();
        assertArrayEquals(new GlideString[] {gs("VSIM"), gs("@vec"), gs("$myvec")}, args);
    }

    @Test
    public void testVsimArmBuilder_rawBytesEquivalentToConstructor() {
        GlideString[] args =
                FTHybridVsimArm.builder("vec", "rawbytes".getBytes()).build().toArgs(gs("$generated"));
        assertArrayEquals(new GlideString[] {gs("VSIM"), gs("@vec"), gs("$generated")}, args);
    }

    @Test
    public void testVsimArmBuilder_byteArrayAlwaysRawVectorDataEvenStartingWithDollarByte() {
        byte[] rawVectorStartingWithDollarByte = {0x24, 0x01, 0x02, 0x03};
        FTHybridVsimArm arm = FTHybridVsimArm.builder("vec", rawVectorStartingWithDollarByte).build();
        assertFalse(arm.isParamReference());
        assertArrayEquals(rawVectorStartingWithDollarByte, arm.getRawVector());
    }

    @Test
    public void testVsimArmBuilder_everyOptionChained() {
        GlideString[] args =
                FTHybridVsimArm.builder("vec", "$q")
                        .k(5)
                        .efRuntime(100)
                        .shardKRatio(0.5)
                        .yieldScoreAs("vs")
                        .build()
                        .toArgs();
        assertArrayEquals(
                new GlideString[] {
                    gs("VSIM"),
                    gs("@vec"),
                    gs("$q"),
                    gs("KNN"),
                    gs("6"),
                    gs("K"),
                    gs("5"),
                    gs("EF_RUNTIME"),
                    gs("100"),
                    gs("SHARD_K_RATIO"),
                    gs("0.5"),
                    gs("YIELD_SCORE_AS"),
                    gs("vs")
                },
                args);
    }

    @Test
    public void testVsimArmBuilder_efRuntimeRejectedWithAdhocFilterPolicy() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FTHybridVsimArm.builder("vec", "$q")
                                .efRuntime(100)
                                .filter(new FTHybridVsimFilter("@title:hello", HybridFilterPolicy.ADHOC))
                                .build());
    }

    // ---- FTHybridVsimFilter ----

    @Test
    public void testVsimFilter_bareExpressionUsesUncountedExtensionForm() {
        assertArrayEquals(
                new GlideString[] {gs("FILTER"), gs("@title:hello")},
                new FTHybridVsimFilter("@title:hello").toArgs());
    }

    @Test
    public void testVsimFilter_policyForcesCountedForm() {
        GlideString[] args = new FTHybridVsimFilter("@title:hello", HybridFilterPolicy.ADHOC).toArgs();
        assertArrayEquals(
                new GlideString[] {gs("FILTER"), gs("3"), gs("@title:hello"), gs("POLICY"), gs("ADHOC")},
                args);
    }

    @Test
    public void testVsimFilter_batchSizeRequiresPolicyBatches() {
        assertThrows(
                IllegalArgumentException.class, () -> new FTHybridVsimFilter(gs("@title:hello"), null, 10));
    }

    @Test
    public void testVsimFilter_policyBatchesWithBatchSize() {
        GlideString[] args =
                new FTHybridVsimFilter("@title:hello", HybridFilterPolicy.BATCHES, 10).toArgs();
        assertArrayEquals(
                new GlideString[] {
                    gs("FILTER"),
                    gs("5"),
                    gs("@title:hello"),
                    gs("POLICY"),
                    gs("BATCHES"),
                    gs("BATCH_SIZE"),
                    gs("10")
                },
                args);
    }

    // ---- FTHybridCombine ----

    @Test
    public void testCombine_rrfBare() {
        assertArrayEquals(
                new GlideString[] {gs("COMBINE"), gs("RRF"), gs("0")}, new FTHybridCombine.Rrf().toArgs());
    }

    @Test
    public void testCombine_rrfWithConstant() {
        GlideString[] args = new FTHybridCombine.Rrf(60.0, null, null).toArgs();
        assertArrayEquals(
                new GlideString[] {gs("COMBINE"), gs("RRF"), gs("2"), gs("CONSTANT"), gs("60.0")}, args);
    }

    @Test
    public void testCombine_rrfNegativeConstantRejected() {
        assertThrows(IllegalArgumentException.class, () -> new FTHybridCombine.Rrf(-1.0, null, null));
    }

    @Test
    public void testCombine_rrfWithWindowAndYieldScoreAs() {
        GlideString[] args = new FTHybridCombine.Rrf(null, 50, gs("fused")).toArgs();
        assertArrayEquals(
                new GlideString[] {
                    gs("COMBINE"),
                    gs("RRF"),
                    gs("4"),
                    gs("WINDOW"),
                    gs("50"),
                    gs("YIELD_SCORE_AS"),
                    gs("fused")
                },
                args);
    }

    @Test
    public void testCombine_linearBareDefaultsBothServerSide() {
        assertArrayEquals(
                new GlideString[] {gs("COMBINE"), gs("LINEAR"), gs("0")},
                new FTHybridCombine.Linear().toArgs());
    }

    @Test
    public void testCombine_linearWithBothWeights() {
        GlideString[] args = new FTHybridCombine.Linear(0.5, 0.5, null, null).toArgs();
        assertArrayEquals(
                new GlideString[] {
                    gs("COMBINE"), gs("LINEAR"), gs("4"), gs("ALPHA"), gs("0.5"), gs("BETA"), gs("0.5")
                },
                args);
    }

    @Test
    public void testCombine_linearOneWeightWithoutTheOtherRejected() {
        assertThrows(
                IllegalArgumentException.class, () -> new FTHybridCombine.Linear(0.5, null, null, null));
        assertThrows(
                IllegalArgumentException.class, () -> new FTHybridCombine.Linear(null, 0.5, null, null));
    }

    @Test
    public void testCombine_functionRequiresExpr() {
        GlideString[] args = new FTHybridCombine.Function("@ss + @vs").toArgs();
        assertArrayEquals(
                new GlideString[] {gs("COMBINE"), gs("FUNCTION"), gs("2"), gs("EXPR"), gs("@ss + @vs")},
                args);
    }

    @Test
    public void testCombine_functionWithWindow() {
        GlideString[] args = new FTHybridCombine.Function("@ss + @vs", 1000, null).toArgs();
        assertArrayEquals(
                new GlideString[] {
                    gs("COMBINE"),
                    gs("FUNCTION"),
                    gs("4"),
                    gs("EXPR"),
                    gs("@ss + @vs"),
                    gs("WINDOW"),
                    gs("1000")
                },
                args);
    }

    // ---- FTHybridCombine builders (consistent with FTHybridOptions' existing @Builder usage) ----

    @Test
    public void testCombineBuilder_rrfWithConstantAndWindowAndYieldScoreAs() {
        GlideString[] args =
                FTHybridCombine.Rrf.builder()
                        .constant(60.0)
                        .window(50)
                        .yieldScoreAs("fused")
                        .build()
                        .toArgs();
        assertArrayEquals(
                new GlideString[] {
                    gs("COMBINE"),
                    gs("RRF"),
                    gs("6"),
                    gs("CONSTANT"),
                    gs("60.0"),
                    gs("WINDOW"),
                    gs("50"),
                    gs("YIELD_SCORE_AS"),
                    gs("fused")
                },
                args);
    }

    @Test
    public void testCombineBuilder_rrfNegativeConstantRejected() {
        assertThrows(
                IllegalArgumentException.class, () -> FTHybridCombine.Rrf.builder().constant(-1.0).build());
    }

    @Test
    public void testCombineBuilder_linearWithBothWeights() {
        GlideString[] args = FTHybridCombine.Linear.builder().alpha(0.5).beta(0.5).build().toArgs();
        assertArrayEquals(
                new GlideString[] {
                    gs("COMBINE"), gs("LINEAR"), gs("4"), gs("ALPHA"), gs("0.5"), gs("BETA"), gs("0.5")
                },
                args);
    }

    @Test
    public void testCombineBuilder_linearOneWeightWithoutTheOtherRejected() {
        assertThrows(
                IllegalArgumentException.class, () -> FTHybridCombine.Linear.builder().alpha(0.5).build());
        assertThrows(
                IllegalArgumentException.class, () -> FTHybridCombine.Linear.builder().beta(0.5).build());
    }

    @Test
    public void testCombineBuilder_functionWithWindowAndYieldScoreAs() {
        GlideString[] args =
                FTHybridCombine.Function.builder("@ss + @vs")
                        .window(1000)
                        .yieldScoreAs("fused")
                        .build()
                        .toArgs();
        assertArrayEquals(
                new GlideString[] {
                    gs("COMBINE"),
                    gs("FUNCTION"),
                    gs("6"),
                    gs("EXPR"),
                    gs("@ss + @vs"),
                    gs("WINDOW"),
                    gs("1000"),
                    gs("YIELD_SCORE_AS"),
                    gs("fused")
                },
                args);
    }

    // ---- FTWithCursor ----

    @Test
    public void testWithCursor_bare() {
        assertArrayEquals(new GlideString[] {gs("WITHCURSOR")}, new FTWithCursor().toArgs());
    }

    @Test
    public void testWithCursor_countAndMaxIdle() {
        GlideString[] args = new FTWithCursor(10, 5000).toArgs();
        assertArrayEquals(
                new GlideString[] {gs("WITHCURSOR"), gs("COUNT"), gs("10"), gs("MAXIDLE"), gs("5000")},
                args);
    }

    // ---- FTHybridOptions ----

    @Test
    public void testHybridOptions_emptyOptionsEmitsNothing() {
        assertArrayEquals(new GlideString[0], FTHybridOptions.builder().build().toArgs());
    }

    @Test
    public void testHybridOptions_loadAll() {
        assertArrayEquals(
                new GlideString[] {gs("LOAD"), gs("*")},
                FTHybridOptions.builder().loadAll().build().toArgs());
    }

    @Test
    public void testHybridOptions_loadFields() {
        GlideString[] args =
                FTHybridOptions.builder().loadFields(new String[] {"title", "price"}).build().toArgs();
        assertArrayEquals(new GlideString[] {gs("LOAD"), gs("2"), gs("title"), gs("price")}, args);
    }

    @Test
    public void testHybridOptions_localOnlyEmittedFirstRegardlessOfFieldOrder() {
        GlideString[] args =
                FTHybridOptions.builder()
                        .localOnly()
                        .loadFields(new String[] {"title"})
                        .dialect(2)
                        .build()
                        .toArgs();
        assertArrayEquals(
                new GlideString[] {
                    gs("LOCALONLY"), gs("LOAD"), gs("1"), gs("title"), gs("DIALECT"), gs("2")
                },
                args);
    }

    @Test
    public void testHybridOptions_withCursor() {
        GlideString[] args = FTHybridOptions.builder().withCursor(new FTWithCursor(5)).build().toArgs();
        assertArrayEquals(new GlideString[] {gs("WITHCURSOR"), gs("COUNT"), gs("5")}, args);
    }

    @Test
    public void testHybridOptions_params() {
        Map<String, GlideString> params = new HashMap<>();
        params.put("q", gs("value"));
        GlideString[] args = FTHybridOptions.builder().params(params).build().toArgs();
        assertArrayEquals(new GlideString[] {gs("PARAMS"), gs("2"), gs("q"), gs("value")}, args);
    }

    @Test
    public void testHybridOptions_generatedVsimParamMergedWithCallerParams() {
        Map<String, GlideString> params = new HashMap<>();
        params.put("other", gs("x"));
        Map<String, GlideString> generated = new HashMap<>();
        generated.put("generated123", gs("rawbytes"));
        GlideString[] args = FTHybridOptions.builder().params(params).build().toArgs(generated);
        assertEquals(gs("PARAMS"), args[0]);
        assertEquals(gs("4"), args[1]);
        Map<GlideString, GlideString> pairs = new HashMap<>();
        pairs.put(args[2], args[3]);
        pairs.put(args[4], args[5]);
        assertEquals(gs("x"), pairs.get(gs("other")));
        assertEquals(gs("rawbytes"), pairs.get(gs("generated123")));
    }

    @Test
    public void testHybridOptions_generatedVsimParamNameCollisionRaises() {
        Map<String, GlideString> params = new HashMap<>();
        params.put("q", gs("caller value"));
        Map<String, GlideString> generated = new HashMap<>();
        generated.put("q", gs("generated value"));
        assertThrows(
                IllegalArgumentException.class,
                () -> FTHybridOptions.builder().params(params).build().toArgs(generated));
    }

    @Test
    public void testHybridOptions_timeout() {
        GlideString[] args = FTHybridOptions.builder().timeout(100).build().toArgs();
        assertArrayEquals(new GlideString[] {gs("TIMEOUT"), gs("100")}, args);
    }

    @Test
    public void testHybridOptions_clausesAppliedInOrder() {
        GlideString[] args =
                FTHybridOptions.builder().addClause(new FTAggregateOptions.Limit(0, 10)).build().toArgs();
        assertArrayEquals(new GlideString[] {gs("LIMIT"), gs("0"), gs("10")}, args);
    }

    @Test
    public void testHybridOptions_paramsEmittedBeforeClauses() {
        // Regression test: with no combine/localOnly/load/timeout, a leading clause used to land
        // right after the VSIM arm's own tokens, where the server's VSIM parser claims a leading
        // FILTER as its own pre-filter instead. PARAMS is always present for FT.HYBRID, so
        // toArgs() must always put it ahead of clauses regardless of field-construction order.
        Map<String, GlideString> params = new HashMap<>();
        params.put("q", gs("value"));
        GlideString[] args =
                FTHybridOptions.builder()
                        .addClause(new FTAggregateOptions.Filter("@price>0"))
                        .params(params)
                        .build()
                        .toArgs();
        assertArrayEquals(
                new GlideString[] {
                    gs("PARAMS"), gs("2"), gs("q"), gs("value"), gs("FILTER"), gs("@price>0")
                },
                args);
    }

    @Test
    public void testHybridOptions_localOnlyParamsAndClausesOrder() {
        // Pins the full tail order end to end: LOCALONLY, then LOAD, then PARAMS, then clauses.
        Map<String, GlideString> params = new HashMap<>();
        params.put("q", gs("value"));
        GlideString[] args =
                FTHybridOptions.builder()
                        .localOnly()
                        .loadFields(new String[] {"title"})
                        .params(params)
                        .addClause(new FTAggregateOptions.Limit(0, 10))
                        .build()
                        .toArgs();
        assertArrayEquals(
                new GlideString[] {
                    gs("LOCALONLY"),
                    gs("LOAD"),
                    gs("1"),
                    gs("title"),
                    gs("PARAMS"),
                    gs("2"),
                    gs("q"),
                    gs("value"),
                    gs("LIMIT"),
                    gs("0"),
                    gs("10")
                },
                args);
    }

    // ---- FT.HYBRID full-command regression test: post-fusion FILTER, no LOAD, no PARAMS set
    // by caller -- the server's VSIM parser must not claim this FILTER as its own pre-filter. ----

    @Test
    public void testHybridFullArgv_postFusionFilterWithNoLoadBetweenRegressionCase() {
        // Builds the exact same argv glide.api.commands.servermodules.FT#hybrid would produce,
        // without going through a live client: SEARCH arm, VSIM arm (param reference, so no
        // generated PARAMS entry), then FTHybridOptions.toArgs() -- which must emit the caller's
        // own PARAMS before the FILTER clause even though no LOAD clause separates the VSIM arm's
        // own argv from the FILTER. A LOAD clause in between would mask the bug even if PARAMS
        // were missing, so this test deliberately omits one.
        FTHybridSearchArm searchArm = FTHybridSearchArm.withYieldScoreAs("goodbye", "ss");
        FTHybridVsimArm vsimArm = new FTHybridVsimArm("vec", "$q", 5, null, null);
        Map<String, GlideString> params = new HashMap<>();
        params.put("q", gs("rawbytesforvec"));
        FTHybridOptions options =
                FTHybridOptions.builder()
                        .params(params)
                        .addClause(new FTAggregateOptions.Filter("@ss > 0"))
                        .build();

        List<GlideString> args = new java.util.ArrayList<>();
        args.add(gs("FT.HYBRID"));
        args.add(gs("myindex"));
        args.addAll(Arrays.asList(searchArm.toArgs()));
        args.addAll(Arrays.asList(vsimArm.toArgs()));
        args.addAll(Arrays.asList(options.toArgs()));

        assertArrayEquals(
                new GlideString[] {
                    gs("FT.HYBRID"),
                    gs("myindex"),
                    gs("SEARCH"),
                    gs("goodbye"),
                    gs("YIELD_SCORE_AS"),
                    gs("ss"),
                    gs("VSIM"),
                    gs("@vec"),
                    gs("$q"),
                    gs("KNN"),
                    gs("2"),
                    gs("K"),
                    gs("5"),
                    // PARAMS must appear here, directly after the VSIM arm's own argv, BEFORE
                    // FILTER -- otherwise the server's VSIM parser would claim "@ss > 0" as its
                    // own VSIM pre-filter instead of an aggregate-pipeline FILTER clause.
                    gs("PARAMS"),
                    gs("2"),
                    gs("q"),
                    gs("rawbytesforvec"),
                    gs("FILTER"),
                    gs("@ss > 0")
                },
                args.toArray(new GlideString[0]));
    }
}
