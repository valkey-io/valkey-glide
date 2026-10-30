/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands.servermodules;

import static glide.api.models.GlideString.gs;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import glide.api.models.GlideString;
import glide.api.models.commands.FT.FTAggregateOptions;
import glide.api.models.commands.FT.FTHybridCombine;
import glide.api.models.commands.FT.FTHybridOptions;
import glide.api.models.commands.FT.FTHybridSearchArm;
import glide.api.models.commands.FT.FTHybridVsimArm;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FT#hybridArgs}, FT.HYBRID's full argv assembly (the UUID-generated {@code
 * $<name>}/PARAMS pair for a raw-bytes VSIM arm, the merge with caller-supplied params, and COMBINE
 * placement), exercised without a live client via an injectable deterministic name supplier.
 */
public class FTTest {

    private static String fixedGeneratedName() {
        return "$generated123";
    }

    @Test
    public void hybridArgs_rawBytesVectorGeneratesParamsEntry() {
        FTHybridSearchArm searchArm = new FTHybridSearchArm("hello");
        FTHybridVsimArm vsimArm = new FTHybridVsimArm("vec", "rawbytesforvec".getBytes());
        FTHybridOptions options = FTHybridOptions.builder().build();

        GlideString[] args =
                FT.hybridArgs(gs("myindex"), searchArm, vsimArm, options, FTTest::fixedGeneratedName);

        assertArrayEquals(
                new GlideString[] {
                    gs("FT.HYBRID"),
                    gs("myindex"),
                    gs("SEARCH"),
                    gs("hello"),
                    gs("VSIM"),
                    gs("@vec"),
                    gs("$generated123"),
                    gs("PARAMS"),
                    gs("2"),
                    gs("generated123"),
                    gs("rawbytesforvec")
                },
                args);
    }

    @Test
    public void hybridArgs_dollarLeadingRawBytesStillGeneratesParamsEntryNotTreatedAsReference() {
        // A raw vector's packed binary representation is arbitrary and can legitimately start
        // with the byte 0x24 ('$') by coincidence. hybridArgs must still generate a PARAMS entry
        // for it end to end -- it must never be mistaken for an existing $name reference just
        // because its first byte happens to match.
        byte[] rawVectorStartingWithDollarByte = {0x24, 0x01, 0x02, 0x03};
        FTHybridSearchArm searchArm = new FTHybridSearchArm("hello");
        FTHybridVsimArm vsimArm = new FTHybridVsimArm("vec", rawVectorStartingWithDollarByte);
        FTHybridOptions options = FTHybridOptions.builder().build();

        GlideString[] args =
                FT.hybridArgs(gs("myindex"), searchArm, vsimArm, options, FTTest::fixedGeneratedName);

        assertArrayEquals(
                new GlideString[] {
                    gs("FT.HYBRID"),
                    gs("myindex"),
                    gs("SEARCH"),
                    gs("hello"),
                    gs("VSIM"),
                    gs("@vec"),
                    gs("$generated123"),
                    gs("PARAMS"),
                    gs("2"),
                    gs("generated123"),
                    gs(rawVectorStartingWithDollarByte)
                },
                args);
    }

    @Test
    public void hybridArgs_paramReferenceVectorSkipsGeneratedParams() {
        FTHybridSearchArm searchArm = new FTHybridSearchArm("hello");
        FTHybridVsimArm vsimArm = new FTHybridVsimArm("vec", "$myvec");
        Map<String, GlideString> params = new HashMap<>();
        params.put("myvec", gs("rawbytes"));
        FTHybridOptions options = FTHybridOptions.builder().params(params).build();

        GlideString[] args =
                FT.hybridArgs(gs("myindex"), searchArm, vsimArm, options, FTTest::fixedGeneratedName);

        assertArrayEquals(
                new GlideString[] {
                    gs("FT.HYBRID"),
                    gs("myindex"),
                    gs("SEARCH"),
                    gs("hello"),
                    gs("VSIM"),
                    gs("@vec"),
                    gs("$myvec"),
                    gs("PARAMS"),
                    gs("2"),
                    gs("myvec"),
                    gs("rawbytes")
                },
                args);
    }

    @Test
    public void hybridArgs_combinePlacedRightAfterVsimArmBeforeClauses() {
        FTHybridSearchArm searchArm = FTHybridSearchArm.withYieldScoreAs("goodbye", "ss");
        FTHybridVsimArm vsimArm = new FTHybridVsimArm("vec", "$q", 5, null, null);
        Map<String, GlideString> params = new HashMap<>();
        params.put("q", gs("rawbytesforvec"));
        FTHybridOptions options =
                FTHybridOptions.builder()
                        .combine(new FTHybridCombine.Rrf(60.0, null, null))
                        .params(params)
                        .addClause(new FTAggregateOptions.Filter("@ss > 0"))
                        .build();

        GlideString[] args =
                FT.hybridArgs(gs("myindex"), searchArm, vsimArm, options, FTTest::fixedGeneratedName);

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
                    // COMBINE must be emitted right after the VSIM arm's own argv, before PARAMS
                    // and any aggregate-tail clause.
                    gs("COMBINE"),
                    gs("RRF"),
                    gs("2"),
                    gs("CONSTANT"),
                    gs("60.0"),
                    gs("PARAMS"),
                    gs("2"),
                    gs("q"),
                    gs("rawbytesforvec"),
                    gs("FILTER"),
                    gs("@ss > 0")
                },
                args);
    }
}
