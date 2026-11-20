// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"fmt"
	"strconv"
	"time"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
	"github.com/valkey-io/valkey-glide/go/v2/servermodules/glideft"
)

// Live-test gaps closed against Python's feature-to-test table (valkey-search 1.3): bare `*`
// match-all, Vector Range with $yield_distance_as on both FT.SEARCH and FT.AGGREGATE, and
// SORTBY widening by an adjacent LIMIT. Mirrors
// python/tests/async_tests/tests_server_modules/test_ft.py's test_ft_search_wildcard,
// test_ft_search_vector_range, test_ft_aggregate_vector_range, and
// test_ft_aggregate_sortby_adjacent_limit_widens_retained_rows.

// --- ft_search bare wildcard ---

// TestModuleFtSearchWildcard covers FT.SEARCH with a bare `*` query, matching every document in
// the index (valkey-search 1.3, PR #960). Before 1.3 a bare `*` did not run as match-all; this
// directly exercises the fix instead of the `@field:[-inf +inf]` workaround used elsewhere in
// this port. Mirrors Python's test_ft_search_wildcard.
func (suite *GlideTestSuite) TestModuleFtSearchWildcard() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	if !suite.probeSearch13CapabilityCluster(ctx, client) {
		suite.T().Skip("search module present but pre-1.3 (INKEYS probe rejected it)")
	}

	prefix := "{ft-wildcard-" + uuid.New().String() + "}:"
	index := prefix + "index"

	_, err := glideft.ClusterFtCreate(ctx, client, index,
		[]options.Field{options.NewTextField("title"), options.NewNumericField("price")},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	require.NoError(suite.T(), err)

	docs := map[string]map[string]string{
		"1": {"title": "alpha", "price": "10"},
		"2": {"title": "beta", "price": "20"},
		"3": {"title": "gamma", "price": "30"},
	}
	expectedKeys := make(map[string]bool, len(docs))
	for suffix, fields := range docs {
		_, err = client.HSet(ctx, prefix+suffix, fields)
		require.NoError(suite.T(), err)
		expectedKeys[prefix+suffix] = true
	}
	time.Sleep(time.Second)

	result, err := glideft.ClusterFtSearch(ctx, client, index, "*", nil)
	require.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(len(docs)), result.TotalResults)
	require.Len(suite.T(), result.Documents, len(docs))

	gotKeys := make(map[string]bool, len(result.Documents))
	for _, doc := range result.Documents {
		gotKeys[doc.Key] = true
		fields, ok := docs[doc.Key[len(prefix):]]
		require.True(suite.T(), ok, "unexpected key %s in wildcard results", doc.Key)
		assert.Equal(suite.T(), fields["title"], fmt.Sprintf("%v", doc.Fields["title"]))
		assert.Equal(suite.T(), fields["price"], fmt.Sprintf("%v", doc.Fields["price"]))
	}
	assert.Equal(suite.T(), expectedKeys, gotKeys)

	glideft.ClusterFtDropIndex(ctx, client, index)
}

// --- ft_search Vector Range with $yield_distance_as ---

// TestModuleFtSearchVectorRangeYieldDistanceAs covers FT.SEARCH's `[VECTOR_RANGE <radius>
// $blob]` predicate (valkey-search 1.3, PR #985) combined with `=>{$yield_distance_as: dist}`,
// plus AND/OR/negation composition with a tag filter. Points sit at known L2 distances from the
// origin (0, 1, 4, 9) so a radius of 2 unambiguously includes distances 0 and 1 and excludes 4
// and 9. Mirrors Python's test_ft_search_vector_range.
func (suite *GlideTestSuite) TestModuleFtSearchVectorRangeYieldDistanceAs() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	if !suite.probeSearch13CapabilityCluster(ctx, client) {
		suite.T().Skip("search module present but pre-1.3 (INKEYS probe rejected it)")
	}

	prefix := "{ft-vecrange-" + uuid.New().String() + "}:"
	index := prefix + "index"
	vecField := "vec"

	_, err := glideft.ClusterFtCreate(ctx, client, index,
		[]options.Field{
			options.NewVectorFieldFlat(vecField, constants.DistanceMetricL2, 2),
			options.NewTagField("category"),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	require.NoError(suite.T(), err)

	// Points at known L2 distances from the origin: 0, 1, 4, 9.
	points := map[string]struct {
		coords   [2]float32
		category string
	}{
		"0": {[2]float32{0.0, 0.0}, "a"},
		"1": {[2]float32{1.0, 0.0}, "b"},
		"2": {[2]float32{2.0, 0.0}, "a"},
		"3": {[2]float32{3.0, 0.0}, "b"},
	}
	for suffix, p := range points {
		_, err = client.HSet(ctx, prefix+suffix, map[string]string{
			vecField:   packFloat32Vector(p.coords[0], p.coords[1]),
			"category": p.category,
		})
		require.NoError(suite.T(), err)
	}
	time.Sleep(time.Second)

	queryBlob := packFloat32Vector(0.0, 0.0)

	// Standalone VECTOR_RANGE: radius 2 covers distances 0 and 1 (keys 0, 1); key 2 (distance 4)
	// and key 3 (distance 9) fall outside.
	result, err := glideft.ClusterFtSearch(ctx, client, index,
		fmt.Sprintf("@%s:[VECTOR_RANGE 2 $blob]", vecField),
		&options.FtSearchOptions{
			Params:    []options.FtSearchParam{{Key: "blob", Value: queryBlob}},
			NoContent: true,
		})
	require.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(2), result.TotalResults)
	assert.Equal(suite.T(), map[string]bool{prefix + "0": true, prefix + "1": true}, keySetFromDocs(result.Documents))

	// YIELD_DISTANCE_AS: the computed distance is returned under the given property name,
	// resolved via PARAMS for both the radius and the blob.
	resultDist, err := glideft.ClusterFtSearch(ctx, client, index,
		fmt.Sprintf("@%s:[VECTOR_RANGE $r $blob]=>{$yield_distance_as: dist}", vecField),
		&options.FtSearchOptions{
			Params:       []options.FtSearchParam{{Key: "blob", Value: queryBlob}, {Key: "r", Value: "0"}},
			ReturnFields: []options.FtSearchReturnField{{FieldIdentifier: "dist"}},
		})
	require.NoError(suite.T(), err)
	require.Equal(suite.T(), int64(1), resultDist.TotalResults)
	require.Len(suite.T(), resultDist.Documents, 1)
	assert.Equal(suite.T(), prefix+"0", resultDist.Documents[0].Key)
	distVal, err := strconv.ParseFloat(fmt.Sprintf("%v", resultDist.Documents[0].Fields["dist"]), 64)
	require.NoError(suite.T(), err)
	assert.Equal(suite.T(), 0.0, distVal)

	// AND with a tag filter: radius 2 (keys 0, 1) intersected with category "a" (keys 0, 2)
	// leaves only key 0.
	resultAnd, err := glideft.ClusterFtSearch(ctx, client, index,
		fmt.Sprintf("@%s:[VECTOR_RANGE 2 $blob] @category:{a}", vecField),
		&options.FtSearchOptions{
			Params:    []options.FtSearchParam{{Key: "blob", Value: queryBlob}},
			NoContent: true,
		})
	require.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(1), resultAnd.TotalResults)
	assert.Equal(suite.T(), map[string]bool{prefix + "0": true}, keySetFromDocs(resultAnd.Documents))

	// OR with a tag filter: radius 0 (key 0 only) union category "b" (keys 1, 3) gives keys
	// 0, 1, 3.
	resultOr, err := glideft.ClusterFtSearch(ctx, client, index,
		fmt.Sprintf("@%s:[VECTOR_RANGE 0 $blob] | @category:{b}", vecField),
		&options.FtSearchOptions{
			Params:    []options.FtSearchParam{{Key: "blob", Value: queryBlob}},
			NoContent: true,
		})
	require.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(3), resultOr.TotalResults)
	assert.Equal(
		suite.T(),
		map[string]bool{prefix + "0": true, prefix + "1": true, prefix + "3": true},
		keySetFromDocs(resultOr.Documents),
	)

	// Negation: everything NOT within radius 1 of the origin (excludes keys 0 and 1, leaves
	// keys 2 and 3).
	resultNegate, err := glideft.ClusterFtSearch(ctx, client, index,
		fmt.Sprintf("-@%s:[VECTOR_RANGE 1 $blob]", vecField),
		&options.FtSearchOptions{
			Params:    []options.FtSearchParam{{Key: "blob", Value: queryBlob}},
			NoContent: true,
		})
	require.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(2), resultNegate.TotalResults)
	assert.Equal(suite.T(), map[string]bool{prefix + "2": true, prefix + "3": true}, keySetFromDocs(resultNegate.Documents))

	glideft.ClusterFtDropIndex(ctx, client, index)
}

// keySetFromDocs reduces a slice of FtSearchDocument to the set of keys it carries, for
// order-independent set comparisons.
func keySetFromDocs(docs []models.FtSearchDocument) map[string]bool {
	set := make(map[string]bool, len(docs))
	for _, d := range docs {
		set[d.Key] = true
	}
	return set
}

// --- ft_aggregate Vector Range with $yield_distance_as ---

// TestModuleFtAggregateVectorRangeYieldDistanceAs covers FT.AGGREGATE's `[VECTOR_RANGE <radius>
// $blob]` predicate (valkey-search 1.3, PR #985) combined with `=>{$yield_distance_as: dist}`,
// loading the distance via LoadFields and sorting the pipeline by it. Distances 0, 1, 10 from
// the origin -- a radius of 5 must include the first two and exclude the third. Mirrors
// Python's test_ft_aggregate_vector_range.
func (suite *GlideTestSuite) TestModuleFtAggregateVectorRangeYieldDistanceAs() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	if !suite.probeSearch13CapabilityCluster(ctx, client) {
		suite.T().Skip("search module present but pre-1.3 (INKEYS probe rejected it)")
	}

	prefix := "{ft-agg-vecrange-" + uuid.New().String() + "}:"
	index := prefix + "index"
	vecField := "vec"

	_, err := glideft.ClusterFtCreate(ctx, client, index,
		[]options.Field{options.NewVectorFieldFlat(vecField, constants.DistanceMetricL2, 2)},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	require.NoError(suite.T(), err)

	points := [][2]float32{{0.0, 0.0}, {1.0, 0.0}, {10.0, 0.0}}
	for i, p := range points {
		_, err = client.HSet(ctx, fmt.Sprintf("%s%d", prefix, i), map[string]string{vecField: packFloat32Vector(p[0], p[1])})
		require.NoError(suite.T(), err)
	}
	time.Sleep(time.Second)

	queryBlob := packFloat32Vector(0.0, 0.0)

	rows, err := glideft.ClusterFtAggregate(ctx, client, index,
		fmt.Sprintf("@%s:[VECTOR_RANGE 5 $blob]=>{$yield_distance_as: dist}", vecField),
		&options.FtAggregateOptions{
			LoadFields: []string{"dist"},
			Params:     []options.FtAggregateParam{{Key: "blob", Value: queryBlob}},
			Clauses: []options.FtAggregateClause{
				&options.FtAggregateSortBy{
					Properties: []options.FtAggregateSortProperty{{Property: "@dist", Order: constants.FtAggregateOrderByAsc}},
				},
			},
		})
	require.NoError(suite.T(), err)
	require.Len(suite.T(), rows, 2)

	distances := make([]float64, len(rows))
	for i, row := range rows {
		d, parseErr := strconv.ParseFloat(fmt.Sprintf("%v", row["dist"]), 64)
		require.NoError(suite.T(), parseErr)
		distances[i] = d
	}
	assert.Equal(suite.T(), []float64{0.0, 1.0}, distances)

	glideft.ClusterFtDropIndex(ctx, client, index)
}

// --- ft_aggregate SORTBY widening by an adjacent LIMIT ---

// TestModuleFtAggregateSortByAdjacentLimitWidensRetainedRows covers valkey-search 1.3.0's "LIMIT
// widens an adjacent SORTBY" compatibility fix (src/commands/ft_aggregate_parser.cc
// ResolveSortByBounds(), "sortby_max_follows_limit"). Before 1.3, a bare SORTBY always truncated
// to its own default (10 rows) or explicit MAX, so a LIMIT past that point returned fewer rows
// than asked for -- reproduced here, matching every row of the source's own measured table:
//
//	SORTBY, no MAX, no LIMIT          ->    10 rows (the default)
//	SORTBY MAX 5, no LIMIT            ->     5 rows
//	SORTBY + LIMIT 0 15 (adjacent)     ->    15 rows (LIMIT raises it)
//	SORTBY MAX 5 + LIMIT 0 12          ->    12 rows (LIMIT outranks MAX)
//	LIMIT 0 15 then SORTBY             ->    15 rows (either side counts)
//	SORTBY + GROUPBY + LIMIT 0 15      ->    10 rows (LIMIT is not adjacent --
//	                                          GROUPBY sits between them, so it
//	                                          does not widen SORTBY)
//
// Below search.emulate-release 1.3.0, the sortby_max_follows_limit gate stays on its legacy
// branch (sortby's own max_ keeps the explicit MAX, or the SORTBY default if none was given,
// regardless of any adjacent LIMIT). Only the two rows above where an adjacent LIMIT would
// otherwise widen the retained window change: both cap at the SORTBY's own max (10 and 5
// respectively) instead of the LIMIT's count. The no-adjacent-LIMIT rows and the
// GROUPBY-blocks-widening row are identical on both branches, since neither one involves the
// gate at all. Branches on this port's getEmulateReleaseLevel/checkVersionLt level fixture, the
// same helper the existing score_field and LoadField tests use. Mirrors Python's
// test_ft_aggregate_sortby_adjacent_limit_widens_retained_rows.
func (suite *GlideTestSuite) TestModuleFtAggregateSortByAdjacentLimitWidensRetainedRows() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	if !suite.probeSearch13CapabilityCluster(ctx, client) {
		suite.T().Skip("search module present but pre-1.3 (INKEYS probe rejected it)")
	}

	prefix := "{ft-sortby-limit-" + uuid.New().String() + "}:"
	index := prefix + "index"
	legacy := checkVersionLt(suite.getEmulateReleaseLevel(ctx, client), "1.3.0")

	_, err := glideft.ClusterFtCreate(ctx, client, index,
		[]options.Field{options.NewNumericField("n").SetSortable(true)},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	require.NoError(suite.T(), err)
	for i := 1; i <= 20; i++ {
		_, err = client.HSet(ctx, fmt.Sprintf("%sd%d", prefix, i), map[string]string{"n": strconv.Itoa(i)})
		require.NoError(suite.T(), err)
	}
	time.Sleep(time.Second)

	sortAsc := &options.FtAggregateSortBy{
		Properties: []options.FtAggregateSortProperty{{Property: "@n", Order: constants.FtAggregateOrderByAsc}},
	}
	sortMax5Val := 5
	sortMax5 := &options.FtAggregateSortBy{
		Properties: []options.FtAggregateSortProperty{{Property: "@n", Order: constants.FtAggregateOrderByAsc}},
		Max:        &sortMax5Val,
	}

	// SORTBY, no MAX, no LIMIT -> the default 10 rows. Unaffected by the gate: no adjacent
	// LIMIT, same on both branches.
	rows, err := glideft.ClusterFtAggregate(ctx, client, index, "*",
		&options.FtAggregateOptions{Clauses: []options.FtAggregateClause{sortAsc}})
	require.NoError(suite.T(), err)
	assert.Len(suite.T(), rows, 10)

	// SORTBY MAX 5, no LIMIT -> 5 rows. Unaffected by the gate: no adjacent LIMIT, same on both
	// branches.
	rows, err = glideft.ClusterFtAggregate(ctx, client, index, "*",
		&options.FtAggregateOptions{Clauses: []options.FtAggregateClause{sortMax5}})
	require.NoError(suite.T(), err)
	assert.Len(suite.T(), rows, 5)

	// SORTBY + LIMIT 0 15, adjacent -> the 1.3 fix raises the retained window to 15 rows; below
	// 1.3.0, the adjacent LIMIT does not widen SORTBY's own default-10 retention.
	rows, err = glideft.ClusterFtAggregate(
		ctx,
		client,
		index,
		"*",
		&options.FtAggregateOptions{
			Clauses: []options.FtAggregateClause{sortAsc, &options.FtAggregateLimit{Offset: 0, Count: 15}},
		},
	)
	require.NoError(suite.T(), err)
	assert.Len(suite.T(), rows, map[bool]int{true: 10, false: 15}[legacy])

	// SORTBY MAX 5 + LIMIT 0 12, adjacent -> the 1.3 fix lets LIMIT outrank MAX; below 1.3.0,
	// SORTBY's own MAX 5 still wins.
	rows, err = glideft.ClusterFtAggregate(
		ctx,
		client,
		index,
		"*",
		&options.FtAggregateOptions{
			Clauses: []options.FtAggregateClause{sortMax5, &options.FtAggregateLimit{Offset: 0, Count: 12}},
		},
	)
	require.NoError(suite.T(), err)
	assert.Len(suite.T(), rows, map[bool]int{true: 5, false: 12}[legacy])

	// LIMIT 0 15 THEN SORTBY -- either side of the pair counts as adjacent, not just
	// LIMIT-after-SORTBY. Same legacy-vs-fixed split as the SORTBY-then-LIMIT row above.
	rows, err = glideft.ClusterFtAggregate(
		ctx,
		client,
		index,
		"*",
		&options.FtAggregateOptions{
			Clauses: []options.FtAggregateClause{&options.FtAggregateLimit{Offset: 0, Count: 15}, sortAsc},
		},
	)
	require.NoError(suite.T(), err)
	assert.Len(suite.T(), rows, map[bool]int{true: 10, false: 15}[legacy])

	// SORTBY + GROUPBY + LIMIT 0 15 -- GROUPBY sits between SORTBY and LIMIT, so the LIMIT is
	// not adjacent and must NOT widen SORTBY's default-10 retention. Unaffected by the gate:
	// GROUPBY blocks widening on both branches.
	rows, err = glideft.ClusterFtAggregate(ctx, client, index, "*",
		&options.FtAggregateOptions{
			Clauses: []options.FtAggregateClause{
				sortAsc,
				&options.FtAggregateGroupBy{Properties: []string{"@n"}, Reducers: nil},
				&options.FtAggregateLimit{Offset: 0, Count: 15},
			},
		})
	require.NoError(suite.T(), err)
	assert.Len(suite.T(), rows, 10)

	glideft.ClusterFtDropIndex(ctx, client, index)
}
