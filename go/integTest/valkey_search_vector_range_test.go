// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

// Integration tests for valkey-search 1.3 VECTOR_RANGE queries, the bare * wildcard and SORTBY with LIMIT.

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

func (suite *GlideTestSuite) TestModuleFtSearchWildcard() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	if !suite.probeSearchInKeysSupportCluster(ctx, client) {
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

func (suite *GlideTestSuite) TestModuleFtSearchVectorRangeYieldDistanceAs() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	if !suite.probeSearchInKeysSupportCluster(ctx, client) {
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

	result, err := glideft.ClusterFtSearch(ctx, client, index,
		fmt.Sprintf("@%s:[VECTOR_RANGE 2 $blob]", vecField),
		&options.FtSearchOptions{
			Params:    []options.FtSearchParam{{Key: "blob", Value: queryBlob}},
			NoContent: true,
		})
	require.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(2), result.TotalResults)
	assert.Equal(suite.T(), map[string]bool{prefix + "0": true, prefix + "1": true}, keySetFromDocs(result.Documents))

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

	resultAnd, err := glideft.ClusterFtSearch(ctx, client, index,
		fmt.Sprintf("@%s:[VECTOR_RANGE 2 $blob] @category:{a}", vecField),
		&options.FtSearchOptions{
			Params:    []options.FtSearchParam{{Key: "blob", Value: queryBlob}},
			NoContent: true,
		})
	require.NoError(suite.T(), err)
	assert.Equal(suite.T(), int64(1), resultAnd.TotalResults)
	assert.Equal(suite.T(), map[string]bool{prefix + "0": true}, keySetFromDocs(resultAnd.Documents))

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

func keySetFromDocs(docs []models.FtSearchDocument) map[string]bool {
	set := make(map[string]bool, len(docs))
	for _, d := range docs {
		set[d.Key] = true
	}
	return set
}

func (suite *GlideTestSuite) TestModuleFtAggregateVectorRangeYieldDistanceAs() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	if !suite.probeSearchInKeysSupportCluster(ctx, client) {
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

func (suite *GlideTestSuite) TestModuleFtAggregateSortByAdjacentLimitWidensRetainedRows() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	if !suite.probeSearchInKeysSupportCluster(ctx, client) {
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

	rows, err := glideft.ClusterFtAggregate(ctx, client, index, "*",
		&options.FtAggregateOptions{Clauses: []options.FtAggregateClause{sortAsc}})
	require.NoError(suite.T(), err)
	assert.Len(suite.T(), rows, 10)

	rows, err = glideft.ClusterFtAggregate(ctx, client, index, "*",
		&options.FtAggregateOptions{Clauses: []options.FtAggregateClause{sortMax5}})
	require.NoError(suite.T(), err)
	assert.Len(suite.T(), rows, 5)

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
