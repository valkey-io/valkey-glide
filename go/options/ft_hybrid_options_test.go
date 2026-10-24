// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"testing"

	"github.com/stretchr/testify/assert"

	"github.com/valkey-io/valkey-glide/go/v2/constants"
)

// Argv-serialization tests for wave 2 of the valkey-search 1.3 stable surface: scoring
// (WITHSCORES/SCORER on FT.SEARCH, ADDSCORES/SCORER on FT.AGGREGATE), cursors (FT.AGGREGATE
// WITHCURSOR), and FT.HYBRID (SEARCH arm, VSIM arm, COMBINE RRF/LINEAR/FUNCTION, VSIM
// FILTER/POLICY/BATCH_SIZE, LOCALONLY, LOAD, WITHCURSOR, PARAMS, DIALECT). Mirrors Python's
// test_options.py's TestFtSearchOptionsScoring / TestFtAggregateOptionsScoring /
// TestFtAggregateOptionsWithCursor / TestFtHybrid* / TestFtWithCursor classes.

// --- FT.SEARCH scoring ---

func TestFtSearchOptionsToArgs_WithScoresAbsentByDefault(t *testing.T) {
	args, err := (&FtSearchOptions{}).ToArgs()
	assert.NoError(t, err)
	assert.NotContains(t, args, "WITHSCORES")
}

func TestFtSearchOptionsToArgs_WithScoresEmitsFlag(t *testing.T) {
	args, err := (&FtSearchOptions{WithScores: true}).ToArgs()
	assert.NoError(t, err)
	assert.Contains(t, args, "WITHSCORES")
}

func TestFtSearchOptionsToArgs_ScorerEmitsKeywordAndValue(t *testing.T) {
	args, err := (&FtSearchOptions{Scorer: "BM25STD"}).ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"SCORER", "BM25STD"}, args)
}

func TestFtSearchOptionsToArgs_WithScoresAndScorerCombined(t *testing.T) {
	args, err := (&FtSearchOptions{WithScores: true, Scorer: "BM25STD"}).ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"WITHSCORES", "SCORER", "BM25STD"}, args)
}

// --- FT.AGGREGATE scoring ---

func TestFtAggregateOptionsToArgs_AddScoresAbsentByDefault(t *testing.T) {
	args, err := (&FtAggregateOptions{}).ToArgs()
	assert.NoError(t, err)
	assert.NotContains(t, args, "ADDSCORES")
}

func TestFtAggregateOptionsToArgs_AddScoresEmitsFlag(t *testing.T) {
	args, err := (&FtAggregateOptions{AddScores: true}).ToArgs()
	assert.NoError(t, err)
	assert.Contains(t, args, "ADDSCORES")
}

func TestFtAggregateOptionsToArgs_ScorerEmitsKeywordAndValue(t *testing.T) {
	args, err := (&FtAggregateOptions{Scorer: "BM25STD"}).ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"SCORER", "BM25STD"}, args)
}

func TestFtAggregateOptionsToArgs_AddScoresAndScorerCombinedAfterOtherClauses(t *testing.T) {
	args, err := (&FtAggregateOptions{
		AddScores: true, Scorer: "BM25STD", LoadFields: []string{"price"},
	}).ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"LOAD", "1", "price", "ADDSCORES", "SCORER", "BM25STD"}, args)
}

// --- FT.AGGREGATE typed cursor ---

func TestFtAggregateOptionsToArgs_WithCursorAbsentByDefault(t *testing.T) {
	args, err := (&FtAggregateOptions{}).ToArgs()
	assert.NoError(t, err)
	assert.NotContains(t, args, "WITHCURSOR")
}

func TestFtAggregateOptionsToArgs_WithCursorBare(t *testing.T) {
	args, err := (&FtAggregateOptions{WithCursor: &FtWithCursor{}}).ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"WITHCURSOR"}, args)
}

func TestFtAggregateOptionsToArgs_WithCursorCountAndMaxIdle(t *testing.T) {
	count, maxIdle := 10, 5000
	args, err := (&FtAggregateOptions{WithCursor: &FtWithCursor{Count: &count, MaxIdle: &maxIdle}}).ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"WITHCURSOR", "COUNT", "10", "MAXIDLE", "5000"}, args)
}

func TestFtAggregateOptionsToArgs_WithCursorCombinedWithLoadAndScoring(t *testing.T) {
	count := 5
	args, err := (&FtAggregateOptions{
		LoadFields: []string{"price"},
		AddScores:  true,
		WithCursor: &FtWithCursor{Count: &count},
	}).ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"LOAD", "1", "price", "ADDSCORES", "WITHCURSOR", "COUNT", "5"}, args)
}

func TestFtWithCursorToArgs_Bare(t *testing.T) {
	args, err := (&FtAggregateOptions{WithCursor: &FtWithCursor{}}).ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"WITHCURSOR"}, args)
}

func TestFtWithCursorToArgs_CountAndMaxIdle(t *testing.T) {
	count, maxIdle := 10, 5000
	c := &FtWithCursor{Count: &count, MaxIdle: &maxIdle}
	assert.Equal(t, []string{"WITHCURSOR", "COUNT", "10", "MAXIDLE", "5000"}, c.toArgs())
}

// --- FtHybridSearchArm ---

func TestFtHybridSearchArmToArgs_BareQuery(t *testing.T) {
	a := &FtHybridSearchArm{Query: "hello"}
	assert.Equal(t, []string{"SEARCH", "hello"}, a.ToArgs())
}

func TestFtHybridSearchArmToArgs_Scorer(t *testing.T) {
	a := &FtHybridSearchArm{Query: "hello", Scorer: "BM25STD"}
	assert.Equal(t, []string{"SEARCH", "hello", "SCORER", "BM25STD"}, a.ToArgs())
}

func TestFtHybridSearchArmToArgs_YieldScoreAs(t *testing.T) {
	a := &FtHybridSearchArm{Query: "hello", YieldScoreAs: "ss"}
	assert.Equal(t, []string{"SEARCH", "hello", "YIELD_SCORE_AS", "ss"}, a.ToArgs())
}

func TestFtHybridSearchArmToArgs_ScorerAndYieldScoreAsCombined(t *testing.T) {
	a := &FtHybridSearchArm{Query: "hello", Scorer: "BM25STD", YieldScoreAs: "ss"}
	assert.Equal(t, []string{"SEARCH", "hello", "SCORER", "BM25STD", "YIELD_SCORE_AS", "ss"}, a.ToArgs())
}

// --- FtHybridVsimArm ---

func TestFtHybridVsimArmToArgs_ParamReferenceVectorUsedDirectly(t *testing.T) {
	a := &FtHybridVsimArm{Field: "vec", VectorParam: "$myvec"}
	args, err := a.ToArgs("")
	assert.NoError(t, err)
	assert.Equal(t, []string{"VSIM", "@vec", "$myvec"}, args)
}

func TestFtHybridVsimArmToArgs_RawBytesVectorRequiresGeneratedParamName(t *testing.T) {
	a := &FtHybridVsimArm{Field: "vec", Vector: []byte("rawbytes")}
	args, err := a.ToArgs("$generated")
	assert.NoError(t, err)
	assert.Equal(t, []string{"VSIM", "@vec", "$generated"}, args)
}

func TestFtHybridVsimArmToArgs_RawBytesWithoutGeneratedParamNameErrors(t *testing.T) {
	a := &FtHybridVsimArm{Field: "vec", Vector: []byte("rawbytes")}
	_, err := a.ToArgs("")
	assert.ErrorContains(t, err, "generatedParamName is required")
}

func TestFtHybridVsimArmToArgs_NeitherVectorNorVectorParamErrors(t *testing.T) {
	a := &FtHybridVsimArm{Field: "vec"}
	_, err := a.ToArgs("$generated")
	assert.ErrorContains(t, err, "exactly one of Vector or VectorParam")
}

func TestFtHybridVsimArmToArgs_BothVectorAndVectorParamErrors(t *testing.T) {
	a := &FtHybridVsimArm{Field: "vec", Vector: []byte("rawbytes"), VectorParam: "$myvec"}
	_, err := a.ToArgs("$generated")
	assert.ErrorContains(t, err, "exactly one of Vector or VectorParam")
}

func TestFtHybridVsimArmIsParamReference_RawBytesStartingWithDollarByteIsAlwaysRaw(t *testing.T) {
	// A raw vector's packed binary representation is arbitrary and can legitimately start with
	// the byte 0x24 ("$") by coincidence. Go's []byte Vector field must never be treated as a
	// $name parameter reference regardless of its first byte -- unlike some other languages'
	// single any-typed vector field, Go's VectorParam is a dedicated string field, so there is no
	// runtime type-sniffing to get wrong here: a []byte in Vector is unconditionally raw data,
	// full stop, by construction.
	a := &FtHybridVsimArm{Field: "vec", Vector: []byte("$myvec")}
	assert.False(t, a.IsParamReference())
	_, err := a.ToArgs("") // no generatedParamName supplied -- must error, not silently pass through
	assert.ErrorContains(t, err, "generatedParamName is required")
	args, err := a.ToArgs("$generated")
	assert.NoError(t, err)
	assert.Equal(t, []string{"VSIM", "@vec", "$generated"}, args)
}

func TestFtHybridVsimArmToArgs_KnnBlockOmittedWhenNoKnnSubArgsGiven(t *testing.T) {
	a := &FtHybridVsimArm{Field: "vec", VectorParam: "$q"}
	args, err := a.ToArgs("")
	assert.NoError(t, err)
	assert.NotContains(t, args, "KNN")
}

func TestFtHybridVsimArmToArgs_KEmitsKnnBlock(t *testing.T) {
	k := 5
	a := &FtHybridVsimArm{Field: "vec", VectorParam: "$q", K: &k}
	args, err := a.ToArgs("")
	assert.NoError(t, err)
	assert.Equal(t, []string{"VSIM", "@vec", "$q", "KNN", "2", "K", "5"}, args)
}

func TestFtHybridVsimArmToArgs_EfRuntimeAndShardKRatio(t *testing.T) {
	k, ef := 5, 100
	ratio := 0.5
	a := &FtHybridVsimArm{Field: "vec", VectorParam: "$q", K: &k, EfRuntime: &ef, ShardKRatio: &ratio}
	args, err := a.ToArgs("")
	assert.NoError(t, err)
	assert.Equal(t, []string{
		"VSIM", "@vec", "$q", "KNN", "6", "K", "5", "EF_RUNTIME", "100", "SHARD_K_RATIO", "0.5",
	}, args)
}

func TestFtHybridVsimArmToArgs_EfRuntimeRejectedWithAdhocFilterPolicy(t *testing.T) {
	ef := 100
	a := &FtHybridVsimArm{
		Field: "vec", VectorParam: "$q", EfRuntime: &ef,
		Filter: &FtHybridVsimFilter{Expression: "@title:hello", Policy: constants.HybridFilterPolicyAdhoc},
	}
	_, err := a.ToArgs("")
	assert.ErrorContains(t, err, "EfRuntime")
}

func TestFtHybridVsimArmToArgs_EfRuntimeAllowedWithBatchesFilterPolicy(t *testing.T) {
	ef := 100
	a := &FtHybridVsimArm{
		Field: "vec", VectorParam: "$q", EfRuntime: &ef,
		Filter: &FtHybridVsimFilter{Expression: "@title:hello", Policy: constants.HybridFilterPolicyBatches},
	}
	args, err := a.ToArgs("")
	assert.NoError(t, err)
	assert.Contains(t, args, "EF_RUNTIME")
}

func TestFtHybridVsimArmToArgs_EfRuntimeAllowedWithUnfilteredVsim(t *testing.T) {
	ef := 100
	a := &FtHybridVsimArm{Field: "vec", VectorParam: "$q", EfRuntime: &ef}
	args, err := a.ToArgs("")
	assert.NoError(t, err)
	assert.Contains(t, args, "EF_RUNTIME")
}

func TestFtHybridVsimArmToArgs_YieldScoreAsAfterKnnBlock(t *testing.T) {
	k := 5
	a := &FtHybridVsimArm{Field: "vec", VectorParam: "$q", K: &k, YieldScoreAs: "vs"}
	args, err := a.ToArgs("")
	assert.NoError(t, err)
	assert.Equal(t, []string{"VSIM", "@vec", "$q", "KNN", "2", "K", "5", "YIELD_SCORE_AS", "vs"}, args)
}

func TestFtHybridVsimArmToArgs_FilterAppendedAfterYieldScoreAs(t *testing.T) {
	a := &FtHybridVsimArm{
		Field: "vec", VectorParam: "$q", YieldScoreAs: "vs",
		Filter: &FtHybridVsimFilter{Expression: "@title:hello"},
	}
	args, err := a.ToArgs("")
	assert.NoError(t, err)
	assert.Equal(t, []string{"VSIM", "@vec", "$q", "YIELD_SCORE_AS", "vs", "FILTER", "@title:hello"}, args)
}

// --- FtHybridVsimFilter ---

func TestFtHybridVsimFilterToArgs_BareExpressionUsesUncountedExtensionForm(t *testing.T) {
	a := &FtHybridVsimArm{Field: "vec", VectorParam: "$q", Filter: &FtHybridVsimFilter{Expression: "@title:hello"}}
	args, err := a.ToArgs("")
	assert.NoError(t, err)
	assert.Equal(t, []string{"VSIM", "@vec", "$q", "FILTER", "@title:hello"}, args)
}

func TestFtHybridVsimFilterToArgs_PolicyForcesCountedForm(t *testing.T) {
	a := &FtHybridVsimArm{
		Field: "vec", VectorParam: "$q",
		Filter: &FtHybridVsimFilter{Expression: "@title:hello", Policy: constants.HybridFilterPolicyAdhoc},
	}
	args, err := a.ToArgs("")
	assert.NoError(t, err)
	assert.Equal(t, []string{"VSIM", "@vec", "$q", "FILTER", "3", "@title:hello", "POLICY", "ADHOC"}, args)
}

func TestFtHybridVsimFilterToArgs_BatchSizeRequiresPolicyBatches(t *testing.T) {
	batchSize := 10
	a := &FtHybridVsimArm{
		Field: "vec", VectorParam: "$q",
		Filter: &FtHybridVsimFilter{Expression: "@title:hello", BatchSize: &batchSize},
	}
	_, err := a.ToArgs("")
	assert.ErrorContains(t, err, "BatchSize requires Policy")
}

func TestFtHybridVsimFilterToArgs_PolicyBatchesWithBatchSize(t *testing.T) {
	batchSize := 10
	a := &FtHybridVsimArm{
		Field: "vec", VectorParam: "$q",
		Filter: &FtHybridVsimFilter{
			Expression: "@title:hello", Policy: constants.HybridFilterPolicyBatches, BatchSize: &batchSize,
		},
	}
	args, err := a.ToArgs("")
	assert.NoError(t, err)
	assert.Equal(t, []string{
		"VSIM", "@vec", "$q", "FILTER", "5", "@title:hello", "POLICY", "BATCHES", "BATCH_SIZE", "10",
	}, args)
}

// --- FtHybridCombine: RRF / Linear / Function ---

func TestFtHybridRrfToArgs_Bare(t *testing.T) {
	c := &FtHybridRrf{}
	args, err := c.ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"COMBINE", "RRF", "0"}, args)
}

func TestFtHybridRrfToArgs_WithConstant(t *testing.T) {
	constant := 60.0
	c := &FtHybridRrf{Constant: &constant}
	args, err := c.ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"COMBINE", "RRF", "2", "CONSTANT", "60"}, args)
}

func TestFtHybridRrfToArgs_NegativeConstantRejected(t *testing.T) {
	constant := -1.0
	c := &FtHybridRrf{Constant: &constant}
	_, err := c.ToArgs()
	assert.ErrorContains(t, err, "must not be negative")
}

func TestFtHybridRrfToArgs_WithWindowAndYieldScoreAs(t *testing.T) {
	window := 50
	c := &FtHybridRrf{Window: &window, YieldScoreAs: "fused"}
	args, err := c.ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"COMBINE", "RRF", "4", "WINDOW", "50", "YIELD_SCORE_AS", "fused"}, args)
}

func TestFtHybridLinearToArgs_BareDefaultsBothServerSide(t *testing.T) {
	c := &FtHybridLinear{}
	args, err := c.ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"COMBINE", "LINEAR", "0"}, args)
}

func TestFtHybridLinearToArgs_WithBothWeights(t *testing.T) {
	alpha, beta := 0.5, 0.5
	c := &FtHybridLinear{Alpha: &alpha, Beta: &beta}
	args, err := c.ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"COMBINE", "LINEAR", "4", "ALPHA", "0.5", "BETA", "0.5"}, args)
}

func TestFtHybridLinearToArgs_OneWeightWithoutTheOtherRejected(t *testing.T) {
	alpha := 0.5
	_, err := (&FtHybridLinear{Alpha: &alpha}).ToArgs()
	assert.ErrorContains(t, err, "must both be set")

	beta := 0.5
	_, err = (&FtHybridLinear{Beta: &beta}).ToArgs()
	assert.ErrorContains(t, err, "must both be set")
}

func TestFtHybridFunctionToArgs_RequiresExpr(t *testing.T) {
	c := &FtHybridFunction{Expr: "@ss + @vs"}
	args, err := c.ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"COMBINE", "FUNCTION", "2", "EXPR", "@ss + @vs"}, args)
}

func TestFtHybridFunctionToArgs_WithWindow(t *testing.T) {
	window := 1000
	c := &FtHybridFunction{Expr: "@ss + @vs", Window: &window}
	args, err := c.ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"COMBINE", "FUNCTION", "4", "EXPR", "@ss + @vs", "WINDOW", "1000"}, args)
}

// --- FtHybridOptions ---

func TestFtHybridOptionsToArgs_EmptyOptionsEmitsNothing(t *testing.T) {
	args, err := (&FtHybridOptions{}).ToArgs("", "")
	assert.NoError(t, err)
	assert.Empty(t, args)
}

func TestFtHybridOptionsToArgs_LoadAll(t *testing.T) {
	args, err := (&FtHybridOptions{LoadAll: true}).ToArgs("", "")
	assert.NoError(t, err)
	assert.Equal(t, []string{"LOAD", "*"}, args)
}

func TestFtHybridOptionsToArgs_LoadFields(t *testing.T) {
	args, err := (&FtHybridOptions{LoadFields: []string{"title", "price"}}).ToArgs("", "")
	assert.NoError(t, err)
	assert.Equal(t, []string{"LOAD", "2", "title", "price"}, args)
}

func TestFtHybridOptionsToArgs_LoadAllAndLoadFieldsMutuallyExclusive(t *testing.T) {
	_, err := (&FtHybridOptions{LoadAll: true, LoadFields: []string{"title"}}).ToArgs("", "")
	assert.ErrorContains(t, err, "mutually exclusive")
}

func TestFtHybridOptionsToArgs_LocalOnlyEmittedFirstRegardlessOfFieldOrder(t *testing.T) {
	dialect := 2
	args, err := (&FtHybridOptions{
		LocalOnly: true, LoadFields: []string{"title"}, Dialect: &dialect,
	}).ToArgs("", "")
	assert.NoError(t, err)
	assert.Equal(t, []string{"LOCALONLY", "LOAD", "1", "title", "DIALECT", "2"}, args)
}

func TestFtHybridOptionsToArgs_WithCursor(t *testing.T) {
	count := 5
	args, err := (&FtHybridOptions{WithCursor: &FtWithCursor{Count: &count}}).ToArgs("", "")
	assert.NoError(t, err)
	assert.Equal(t, []string{"WITHCURSOR", "COUNT", "5"}, args)
}

func TestFtHybridOptionsToArgs_Params(t *testing.T) {
	args, err := (&FtHybridOptions{Params: []FtAggregateParam{{Key: "q", Value: "value"}}}).ToArgs("", "")
	assert.NoError(t, err)
	assert.Equal(t, []string{"PARAMS", "2", "q", "value"}, args)
}

func TestFtHybridOptionsToArgs_GeneratedVsimParamMergedWithCallerParams(t *testing.T) {
	opts := &FtHybridOptions{Params: []FtAggregateParam{{Key: "other", Value: "x"}}}
	args, err := opts.ToArgs("generated123", "rawbytes")
	assert.NoError(t, err)
	assert.Equal(t, "PARAMS", args[0])
	assert.Equal(t, "4", args[1])
	pairs := map[string]string{args[2]: args[3], args[4]: args[5]}
	assert.Equal(t, map[string]string{"other": "x", "generated123": "rawbytes"}, pairs)
}

func TestFtHybridOptionsToArgs_GeneratedVsimParamNameCollisionErrors(t *testing.T) {
	opts := &FtHybridOptions{Params: []FtAggregateParam{{Key: "q", Value: "caller value"}}}
	_, err := opts.ToArgs("q", "generated value")
	assert.ErrorContains(t, err, "collides")
}

func TestFtHybridOptionsToArgs_Timeout(t *testing.T) {
	timeout := 100
	args, err := (&FtHybridOptions{Timeout: &timeout}).ToArgs("", "")
	assert.NoError(t, err)
	assert.Equal(t, []string{"TIMEOUT", "100"}, args)
}

func TestFtHybridOptionsToArgs_ClausesAppliedInOrder(t *testing.T) {
	args, err := (&FtHybridOptions{
		Clauses: []FtAggregateClause{&FtAggregateLimit{Offset: 0, Count: 10}},
	}).ToArgs("", "")
	assert.NoError(t, err)
	assert.Equal(t, []string{"LIMIT", "0", "10"}, args)
}

// TestFtHybridOptionsToArgs_ParamsEmittedBeforeClauses is the regression test for the
// tail-ordering bug: with no combine/local_only/load_*/timeout, a leading clause used to land
// right after the VSIM arm's own tokens, where the server's VSIM parser claims a leading FILTER
// as its own pre-filter instead (ft_hybrid_parser.cc's vsim_scoped FILTER handling). PARAMS is
// always present for FT.HYBRID, so ToArgs must always put it ahead of Clauses regardless of
// field-set order. Deliberately no LoadFields/LoadAll here -- a LOAD clause in between the VSIM
// arm and this FILTER would mask the bug even if PARAMS were missing, since LOAD itself would
// already separate FILTER from the VSIM arm's own argv.
func TestFtHybridOptionsToArgs_ParamsEmittedBeforeClauses(t *testing.T) {
	args, err := (&FtHybridOptions{
		Clauses: []FtAggregateClause{&FtAggregateFilter{Expression: "@price>0"}},
		Params:  []FtAggregateParam{{Key: "q", Value: "value"}},
	}).ToArgs("", "")
	assert.NoError(t, err)
	assert.Equal(t, []string{"PARAMS", "2", "q", "value", "FILTER", "@price>0"}, args)
}

func TestFtHybridOptionsToArgs_LocalOnlyParamsAndClausesOrder(t *testing.T) {
	args, err := (&FtHybridOptions{
		LocalOnly:  true,
		LoadFields: []string{"title"},
		Params:     []FtAggregateParam{{Key: "q", Value: "value"}},
		Clauses:    []FtAggregateClause{&FtAggregateLimit{Offset: 0, Count: 10}},
	}).ToArgs("", "")
	assert.NoError(t, err)
	assert.Equal(t, []string{
		"LOCALONLY", "LOAD", "1", "title", "PARAMS", "2", "q", "value", "LIMIT", "0", "10",
	}, args)
}

// TestFtHybridRegressionPostFusionFilterNoLoad is the full end-to-end regression case (3 of the
// 3 byte-identical-with-Python cases cited in this port's report): a FILTER clause placed
// directly after the VSIM arm's own argv, with NO LOAD clause in between and NO combine, so a
// tail-ordering bug (PARAMS missing or emitted after Clauses) would let the server's VSIM
// parser claim this FILTER as its own VSIM pre-filter instead of an aggregate-pipeline FILTER.
func TestFtHybridRegressionPostFusionFilterNoLoad(t *testing.T) {
	searchArm := &FtHybridSearchArm{Query: "goodbye", YieldScoreAs: "ss"}
	k := 5
	vsimArm := &FtHybridVsimArm{Field: "vec", VectorParam: "$q", K: &k}
	opts := &FtHybridOptions{
		Clauses: []FtAggregateClause{&FtAggregateFilter{Expression: "@ss > 0"}},
		Params:  []FtAggregateParam{{Key: "q", Value: "rawbytesplaceholder"}},
	}

	args := []string{"FT.HYBRID", "idx"}
	args = append(args, searchArm.ToArgs()...)
	vsimArgs, err := vsimArm.ToArgs("")
	assert.NoError(t, err)
	args = append(args, vsimArgs...)
	tailArgs, err := opts.ToArgs("", "")
	assert.NoError(t, err)
	args = append(args, tailArgs...)

	assert.Equal(t, []string{
		"FT.HYBRID", "idx",
		"SEARCH", "goodbye", "YIELD_SCORE_AS", "ss",
		"VSIM", "@vec", "$q", "KNN", "2", "K", "5",
		"PARAMS", "2", "q", "rawbytesplaceholder",
		"FILTER", "@ss > 0",
	}, args)
}
