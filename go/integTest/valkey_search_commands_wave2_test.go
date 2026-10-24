// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"encoding/binary"
	"fmt"
	"math"
	"strconv"
	"time"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
	"github.com/valkey-io/valkey-glide/go/v2/pipeline"
	"github.com/valkey-io/valkey-glide/go/v2/servermodules/glideft"
)

// Wave 2 of the valkey-search 1.3 stable surface: scoring (WITHSCORES/SCORER on FT.SEARCH,
// ADDSCORES/SCORER on FT.AGGREGATE), cursors (FT.AGGREGATE/FT.HYBRID WITHCURSOR, FT.CURSOR
// READ/DEL, primary-pinned routing regardless of ReadFrom), and FT.HYBRID. Gated behind the
// same probeSearch13CapabilityCluster/getEmulateReleaseLevel helpers wave 1 already established
// in this file. Mirrors python/tests/async_tests/tests_server_modules/test_ft.py's
// test_ft_search_with_scores*, test_ft_aggregate_add_scores*, test_ft_cursor_affinity*,
// test_ft_aggregate_typed_cursor*, and test_ft_hybrid_* live tests.

// packFloat32Vector packs a sequence of float32 values into a little-endian byte string, the
// same wire representation FT.CREATE's FLOAT32 vector fields expect. Go's HSet takes
// map[string]string, and a Go string is a byte sequence (not UTF-8 validated), so this is the
// same "string holds raw bytes" convention this file's other tests already rely on.
func packFloat32Vector(values ...float32) string {
	buf := make([]byte, 4*len(values))
	for i, v := range values {
		binary.LittleEndian.PutUint32(buf[i*4:], math.Float32bits(v))
	}
	return string(buf)
}

const (
	hybridDocTextWinner   = "textwinner"
	hybridDocVectorWinner = "vectorwinner"
	hybridSearchQuery     = "hello"
	hybridSearchAlias     = "ss"
	hybridVsimAlias       = "vs"
)

var hybridQueryVector = [2]float32{1.0, 1.0}

// createHybridIndex creates an index with a TEXT field ("title") and a 2-dimensional FLAT
// FLOAT32 VECTOR field ("vec"), seeds two documents -- one the SEARCH arm strongly favours
// (hybridDocTextWinner, matching "hello" and far from the query vector) and one the VSIM arm
// strongly favours (hybridDocVectorWinner, not matching "hello" and near the query vector) --
// and waits briefly for indexing. Mirrors Python's _create_hybrid_index/HYBRID_DOCS fixture.
func createHybridIndex(ctx context.Context, client *glide.ClusterClient, prefix string) string {
	index := prefix + "index"
	_, err := glideft.ClusterFtCreate(
		ctx, client, index,
		[]options.Field{
			options.NewTextField("title"),
			options.NewVectorFieldFlat("vec", constants.DistanceMetricL2, 2),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}},
	)
	if err != nil {
		panic(err)
	}
	client.HSet(ctx, prefix+hybridDocTextWinner, map[string]string{
		"title": "hello hello hello world", "vec": packFloat32Vector(100.0, 100.0),
	})
	client.HSet(ctx, prefix+hybridDocVectorWinner, map[string]string{
		"title": "goodbye", "vec": packFloat32Vector(1.0, 1.0),
	})
	time.Sleep(time.Second)
	return index
}

// --- ft_search scoring ---

func (suite *GlideTestSuite) TestModuleFtSearchWithScoresAndScorer() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	if !suite.probeSearch13CapabilityCluster(ctx, client) {
		suite.T().Skip("search module present but pre-1.3 (INKEYS probe rejected it)")
	}

	prefix := "{ft-with-scores-" + uuid.New().String() + "}:"
	index := prefix + "index"

	_, err := glideft.ClusterFtCreate(ctx, client, index,
		[]options.Field{options.NewTextField("title")},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	require.NoError(suite.T(), err)

	// Repeat the search term in one document so BM25STD scores it strictly higher -- two
	// documents with identical term counts would score identically even if the scorer silently
	// fell back to a constant/no-op.
	_, err = client.HSet(ctx, prefix+"1", map[string]string{"title": "hello hello hello world"})
	require.NoError(suite.T(), err)
	_, err = client.HSet(ctx, prefix+"2", map[string]string{"title": "hello there"})
	require.NoError(suite.T(), err)
	time.Sleep(time.Second)

	result, err := glideft.ClusterFtSearch(ctx, client, index, "hello",
		&options.FtSearchOptions{WithScores: true, Scorer: "BM25STD"})
	require.NoError(suite.T(), err)
	assert.EqualValues(suite.T(), 2, result.TotalResults)
	require.Len(suite.T(), result.Documents, 2)

	scoresByKey := map[string]float64{}
	for _, doc := range result.Documents {
		require.NotNil(suite.T(), doc.Score)
		assert.Greater(suite.T(), *doc.Score, 0.0)
		scoresByKey[doc.Key] = *doc.Score
		assert.Contains(suite.T(), doc.Fields, "title")
	}
	assert.Greater(suite.T(), scoresByKey[prefix+"1"], scoresByKey[prefix+"2"],
		"the document repeating the search term should score strictly higher under BM25STD")

	glideft.ClusterFtDropIndex(ctx, client, index)
}

func (suite *GlideTestSuite) TestModuleFtSearchWithScoresAndWithSortKeysCombinedShape() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	if !suite.probeSearch13CapabilityCluster(ctx, client) {
		suite.T().Skip("search module present but pre-1.3 (INKEYS probe rejected it)")
	}

	prefix := "{ft-scores-sortkeys-" + uuid.New().String() + "}:"
	index := prefix + "index"

	_, err := glideft.ClusterFtCreate(ctx, client, index,
		[]options.Field{
			options.NewTextField("title").SetSortable(true),
			options.NewNumericField("price").SetSortable(true),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	require.NoError(suite.T(), err)

	_, err = client.HSet(ctx, prefix+"1", map[string]string{"title": "hello", "price": "10"})
	require.NoError(suite.T(), err)
	_, err = client.HSet(ctx, prefix+"2", map[string]string{"title": "world", "price": "20"})
	require.NoError(suite.T(), err)
	time.Sleep(time.Second)

	result, err := glideft.ClusterFtSearch(ctx, client, index, "*", &options.FtSearchOptions{
		WithScores: true, SortBy: "price", SortByOrder: constants.FtSearchSortOrderAsc, WithSortKeys: true,
	})
	require.NoError(suite.T(), err)
	assert.EqualValues(suite.T(), 2, result.TotalResults)
	require.Len(suite.T(), result.Documents, 2)
	for _, doc := range result.Documents {
		require.NotNil(suite.T(), doc.Score)
		assert.GreaterOrEqual(suite.T(), *doc.Score, 0.0)
		assert.NotEmpty(suite.T(), doc.SortKey)
		assert.Contains(suite.T(), doc.Fields, "title")
	}

	glideft.ClusterFtDropIndex(ctx, client, index)
}

func (suite *GlideTestSuite) TestModuleFtSearchScorerTfidfRejected() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	if !suite.probeSearch13CapabilityCluster(ctx, client) {
		suite.T().Skip("search module present but pre-1.3 (INKEYS probe rejected it)")
	}

	prefix := "{ft-scorer-tfidf-" + uuid.New().String() + "}:"
	index := prefix + "index"

	_, err := glideft.ClusterFtCreate(ctx, client, index,
		[]options.Field{options.NewTextField("title")},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	require.NoError(suite.T(), err)
	_, err = client.HSet(ctx, prefix+"1", map[string]string{"title": "hello world"})
	require.NoError(suite.T(), err)
	time.Sleep(time.Second)

	_, err = glideft.ClusterFtSearch(ctx, client, index, "hello",
		&options.FtSearchOptions{WithScores: true, Scorer: "TFIDF"})
	assert.ErrorContains(suite.T(), err, "SCORER")

	glideft.ClusterFtDropIndex(ctx, client, index)
}

// --- ft_aggregate scoring ---

func (suite *GlideTestSuite) TestModuleFtAggregateAddScoresSortableByScore() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	if !suite.probeSearch13CapabilityCluster(ctx, client) {
		suite.T().Skip("search module present but pre-1.3 (INKEYS probe rejected it)")
	}

	prefix := "{ft-add-scores-" + uuid.New().String() + "}:"
	index := prefix + "index"

	_, err := glideft.ClusterFtCreate(ctx, client, index,
		[]options.Field{options.NewTextField("title")},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	require.NoError(suite.T(), err)
	_, err = client.HSet(ctx, prefix+"1", map[string]string{"title": "hello world"})
	require.NoError(suite.T(), err)
	_, err = client.HSet(ctx, prefix+"2", map[string]string{"title": "hello there friend"})
	require.NoError(suite.T(), err)
	time.Sleep(time.Second)

	rows, err := glideft.ClusterFtAggregate(ctx, client, index, "hello", &options.FtAggregateOptions{
		AddScores:  true,
		LoadFields: []string{"title"},
		Clauses: []options.FtAggregateClause{
			&options.FtAggregateSortBy{
				Properties: []options.FtAggregateSortProperty{
					{Property: "@__score", Order: constants.FtAggregateOrderByDesc},
				},
			},
		},
	})
	require.NoError(suite.T(), err)
	require.Len(suite.T(), rows, 2)

	scores := make([]float64, len(rows))
	for i, row := range rows {
		scoreStr, ok := row["__score"].(string)
		require.True(suite.T(), ok, "__score should be present")
		score, err := strconv.ParseFloat(scoreStr, 64)
		require.NoError(suite.T(), err)
		assert.Greater(suite.T(), score, 0.0)
		scores[i] = score
	}
	// Sorted descending by __score -- the fused ordering must be stable and monotonic.
	for i := 1; i < len(scores); i++ {
		assert.GreaterOrEqual(suite.T(), scores[i-1], scores[i])
	}

	glideft.ClusterFtDropIndex(ctx, client, index)
}

// --- ft_aggregate typed cursor ---

func (suite *GlideTestSuite) TestModuleFtAggregateTypedCursorPagedToExhaustion() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	if !suite.probeSearch13CapabilityCluster(ctx, client) {
		suite.T().Skip("search module present but pre-1.3 (INKEYS probe rejected it)")
	}

	prefix := "{ft-typed-cursor-" + uuid.New().String() + "}:"
	index := prefix + "index"

	_, err := glideft.ClusterFtCreate(ctx, client, index,
		[]options.Field{options.NewNumericField("n").SetSortable(true)},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	require.NoError(suite.T(), err)
	for i := 1; i <= 20; i++ {
		_, err = client.HSet(ctx, prefix+"d"+strconv.Itoa(i), map[string]string{"n": strconv.Itoa(i)})
		require.NoError(suite.T(), err)
	}
	time.Sleep(time.Second)

	count := 5
	rows, cursorID, err := glideft.ClusterFtAggregateWithCursor(ctx, client, index, "*", &options.FtAggregateOptions{
		LoadFields: []string{"n"},
		WithCursor: &options.FtWithCursor{Count: &count},
	})
	require.NoError(suite.T(), err)

	totalRows := len(rows)
	seenN := map[string]bool{}
	for _, row := range rows {
		seenN[fmt.Sprintf("%v", row["n"])] = true
	}

	pagesRead := 1
	for cursorID != 0 {
		var page []map[string]any
		page, cursorID, err = glideft.ClusterFtCursorRead(ctx, client, index, cursorID, nil)
		require.NoError(suite.T(), err)
		totalRows += len(page)
		for _, row := range page {
			seenN[fmt.Sprintf("%v", row["n"])] = true
		}
		pagesRead++
		require.LessOrEqual(suite.T(), pagesRead, 10, "paging did not terminate as expected")
	}
	assert.Equal(suite.T(), 20, totalRows)

	expectedN := map[string]bool{}
	for i := 1; i <= 20; i++ {
		expectedN[strconv.Itoa(i)] = true
	}
	assert.Equal(suite.T(), expectedN, seenN,
		"paging returned the right row COUNT but the wrong SET of rows")

	glideft.ClusterFtDropIndex(ctx, client, index)
}

func (suite *GlideTestSuite) TestModuleFtAggregateTypedCursorDelAndErrors() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	if !suite.probeSearch13CapabilityCluster(ctx, client) {
		suite.T().Skip("search module present but pre-1.3 (INKEYS probe rejected it)")
	}

	prefix := "{ft-typed-cursor-del-" + uuid.New().String() + "}:"
	index := prefix + "index"

	_, err := glideft.ClusterFtCreate(ctx, client, index,
		[]options.Field{options.NewNumericField("n").SetSortable(true)},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	require.NoError(suite.T(), err)
	for i := 1; i <= 5; i++ {
		_, err = client.HSet(ctx, prefix+"d"+strconv.Itoa(i), map[string]string{"n": strconv.Itoa(i)})
		require.NoError(suite.T(), err)
	}
	time.Sleep(time.Second)

	count := 1
	_, openCursorID, err := glideft.ClusterFtAggregateWithCursor(ctx, client, index, "*", &options.FtAggregateOptions{
		LoadFields: []string{"n"},
		WithCursor: &options.FtWithCursor{Count: &count},
	})
	require.NoError(suite.T(), err)
	require.NotEqual(suite.T(), int64(0), openCursorID)

	deleted, err := glideft.ClusterFtCursorDel(ctx, client, index, openCursorID)
	require.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", deleted)

	_, _, err = glideft.ClusterFtCursorRead(ctx, client, index, openCursorID, nil)
	assert.ErrorContains(suite.T(), err, "Cursor")

	_, _, err = glideft.ClusterFtCursorRead(ctx, client, index, 999999999, nil)
	assert.ErrorContains(suite.T(), err, "Cursor")

	glideft.ClusterFtDropIndex(ctx, client, index)
}

// --- cursor affinity: FT.AGGREGATE WITHCURSOR and FT.CURSOR always pin to the PRIMARY ---

func (suite *GlideTestSuite) TestModuleFtCursorAffinityPreferReplicaClient() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	if !suite.probeSearch13CapabilityCluster(ctx, client) {
		suite.T().Skip("search module present but pre-1.3 (INKEYS probe rejected it)")
	}
	if len(suite.clusterHosts) < 2 {
		suite.T().Skip("test requires a real multi-shard, replica-bearing cluster")
	}

	prefix := "{ft-cursor-affinity-" + uuid.New().String() + "}:"
	index := prefix + "index"

	_, err := glideft.ClusterFtCreate(ctx, client, index,
		[]options.Field{options.NewNumericField("n").SetSortable(true)},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	require.NoError(suite.T(), err)
	for i := 1; i <= 20; i++ {
		_, err = client.HSet(ctx, prefix+"d"+strconv.Itoa(i), map[string]string{"n": strconv.Itoa(i)})
		require.NoError(suite.T(), err)
	}
	time.Sleep(time.Second)

	preferReplicaCfg := suite.defaultClusterClientConfig().WithReadFrom(config.PreferReplica)
	preferReplicaClient, err := suite.clusterClient(preferReplicaCfg)
	require.NoError(suite.T(), err)

	count := 5
	rows, cursorID, err := glideft.ClusterFtAggregateWithCursor(ctx, preferReplicaClient, index, "*",
		&options.FtAggregateOptions{LoadFields: []string{"n"}, WithCursor: &options.FtWithCursor{Count: &count}})
	require.NoError(suite.T(), err)

	totalRows := len(rows)
	seenN := map[string]bool{}
	for _, row := range rows {
		seenN[fmt.Sprintf("%v", row["n"])] = true
	}

	pagesRead := 1
	for cursorID != 0 {
		var page []map[string]any
		page, cursorID, err = glideft.ClusterFtCursorRead(ctx, preferReplicaClient, index, cursorID, nil)
		require.NoError(suite.T(), err, "a PREFER_REPLICA client's cursor paging must not hit \"Cursor not found\"")
		totalRows += len(page)
		for _, row := range page {
			seenN[fmt.Sprintf("%v", row["n"])] = true
		}
		pagesRead++
		require.LessOrEqual(suite.T(), pagesRead, 10, "paging did not terminate as expected")
	}
	assert.Equal(suite.T(), 20, totalRows)
	expectedN := map[string]bool{}
	for i := 1; i <= 20; i++ {
		expectedN[strconv.Itoa(i)] = true
	}
	assert.Equal(suite.T(), expectedN, seenN,
		"paging returned the right row COUNT but the wrong SET of rows")

	// FT.CURSOR DEL on an open cursor.
	_, openCursorID, err := glideft.ClusterFtAggregateWithCursor(ctx, preferReplicaClient, index, "*",
		&options.FtAggregateOptions{LoadFields: []string{"n"}, WithCursor: &options.FtWithCursor{Count: &count}})
	require.NoError(suite.T(), err)
	deleted, err := glideft.ClusterFtCursorDel(ctx, preferReplicaClient, index, openCursorID)
	require.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", deleted)

	_, _, err = glideft.ClusterFtCursorRead(ctx, preferReplicaClient, index, 999999999, nil)
	assert.ErrorContains(suite.T(), err, "Cursor")
	_, _, err = glideft.ClusterFtCursorRead(ctx, preferReplicaClient, index, openCursorID, nil)
	assert.ErrorContains(suite.T(), err, "Cursor")

	// Negative control, mirroring Python's test: a read explicitly routed to a REPLICA of the
	// index's OWN slot fails, confirming the cursor really is node-local.
	_, anotherCursorID, err := glideft.ClusterFtAggregateWithCursor(ctx, preferReplicaClient, index, "*",
		&options.FtAggregateOptions{LoadFields: []string{"n"}, WithCursor: &options.FtWithCursor{Count: &count}})
	require.NoError(suite.T(), err)

	replicaAddrs := suite.findReplicaAddrsForKey(ctx, preferReplicaClient, index)
	require.NotEmpty(suite.T(), replicaAddrs,
		"test fixture bug, not an engine finding: no replica found for the index's own shard")
	for _, addr := range replicaAddrs {
		route := config.NewByAddressRoute(addr.Host, int32(addr.Port))
		_, err = preferReplicaClient.CustomCommandWithRoute(
			ctx, []string{"FT.CURSOR", "READ", index, strconv.FormatInt(anotherCursorID, 10)}, route,
		)
		assert.ErrorContains(suite.T(), err, "Cursor")
	}

	glideft.ClusterFtDropIndex(ctx, client, index)
}

type hostPort struct {
	Host string
	Port int
}

// unwrapClusterCustomCommand unwraps a models.ClusterValue[any] the same way
// getEmulateReleaseLevel does for an unrouted command like CLUSTER KEYSLOT/CLUSTER SHARDS: the
// single answering node's own reply lands under MultiValue() keyed by the reply's own content
// rather than a node address (confirmed empirically against a live cluster), so the multi-value
// map IS the reply's own contents here, not a per-node breakdown.
func unwrapClusterCustomCommand(result models.ClusterValue[any]) any {
	if !result.IsMultiValue() {
		return result.SingleValue()
	}
	multi := result.MultiValue()
	if len(multi) == 1 {
		for _, v := range multi {
			return v
		}
	}
	unwrapped := make(map[string]any, len(multi))
	for k, v := range multi {
		unwrapped[k] = v
	}
	return unwrapped
}

// findReplicaAddrsForKey returns the addresses of every REPLICA node serving the shard that
// owns indexKey's slot, by walking CLUSTER SHARDS' raw reply generically (any-typed, since the
// shape returned through CustomCommand is a nested []any/map[string]any tree, not a typed
// struct). Mirrors Python's test's inline CLUSTER SHARDS walk.
func (suite *GlideTestSuite) findReplicaAddrsForKey(
	ctx context.Context, client *glide.ClusterClient, indexKey string,
) []hostPort {
	t := suite.T()
	slotResult, err := client.CustomCommand(ctx, []string{"CLUSTER", "KEYSLOT", indexKey})
	require.NoError(t, err)
	slot, ok := toInt64(unwrapClusterCustomCommand(slotResult))
	require.True(t, ok, "unexpected CLUSTER KEYSLOT reply type: %T", unwrapClusterCustomCommand(slotResult))

	shardsResult, err := client.CustomCommand(ctx, []string{"CLUSTER", "SHARDS"})
	require.NoError(t, err)
	shards := unwrapClusterCustomCommand(shardsResult)

	var replicas []hostPort
	shardsArr, ok := shards.([]any)
	require.True(t, ok, "unexpected CLUSTER SHARDS reply type: %T", shards)
	for _, rawShard := range shardsArr {
		shardMap := flatToMap(rawShard)
		slots, _ := shardMap["slots"].([]any)
		owns := false
		for i := 0; i+1 < len(slots); i += 2 {
			lo, loOk := toInt64(slots[i])
			hi, hiOk := toInt64(slots[i+1])
			if loOk && hiOk && lo <= slot && slot <= hi {
				owns = true
				break
			}
		}
		if !owns {
			continue
		}
		nodesAny, _ := shardMap["nodes"].([]any)
		for _, rawNode := range nodesAny {
			nodeMap := flatToMap(rawNode)
			role, _ := nodeMap["role"].(string)
			if role != "replica" {
				continue
			}
			host, _ := nodeMap["ip"].(string)
			port, _ := toInt64(nodeMap["port"])
			replicas = append(replicas, hostPort{Host: host, Port: int(port)})
		}
	}
	return replicas
}

// flatToMap normalizes either a map[string]any or a flat []any [k1, v1, k2, v2, ...] (the shape
// a raw CustomCommand reply may come back as, depending on RESP2 vs RESP3) into a
// map[string]any.
func flatToMap(v any) map[string]any {
	if m, ok := v.(map[string]any); ok {
		return m
	}
	arr, ok := v.([]any)
	if !ok {
		return map[string]any{}
	}
	m := make(map[string]any, len(arr)/2)
	for i := 0; i+1 < len(arr); i += 2 {
		if key, ok := arr[i].(string); ok {
			m[key] = arr[i+1]
		}
	}
	return m
}

func toInt64(v any) (int64, bool) {
	switch n := v.(type) {
	case int64:
		return n, true
	case int:
		return int64(n), true
	case float64:
		return int64(n), true
	default:
		return 0, false
	}
}

// --- FT.HYBRID ---

func (suite *GlideTestSuite) TestModuleFtHybridBasicQuery() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	if !suite.probeSearch13CapabilityCluster(ctx, client) {
		suite.T().Skip("search module present but pre-1.3 (INKEYS probe rejected it)")
	}

	prefix := "{ft-hybrid-basic-" + uuid.New().String() + "}:"
	index := createHybridIndex(ctx, client, prefix)

	rows, err := glideft.ClusterFtHybrid(ctx, client, index,
		options.FtHybridSearchArm{Query: "hello"},
		options.FtHybridVsimArm{Field: "vec", Vector: []byte(packFloat32Vector(1.0, 1.0)), K: intPtr(5)},
		nil,
	)
	require.NoError(suite.T(), err)
	assert.Len(suite.T(), rows, 2)

	keys := map[string]bool{}
	for _, row := range rows {
		keys[fmt.Sprintf("%v", row["__key"])] = true
		assert.Contains(suite.T(), row, "__score")
	}
	assert.Equal(suite.T(), map[string]bool{
		prefix + hybridDocTextWinner:   true,
		prefix + hybridDocVectorWinner: true,
	}, keys)

	glideft.ClusterFtDropIndex(ctx, client, index)
}

// TestModuleFtHybridAggregateClauseFiltersPostFusionRegression is the dedicated regression
// test for the FT.HYBRID tail-ordering bug: a post-fusion FILTER clause with NO LOAD clause in
// between, placed directly after the VSIM arm's own argv (a LOAD clause in between would mask
// the bug even if PARAMS were missing). With the ordering bug present, the server's VSIM parser
// misparses this as its own pre-filter and the query fails outright; with the fix, the FILTER
// clause only keeps rows where the SEARCH arm's "ss" alias scored positively.
func (suite *GlideTestSuite) TestModuleFtHybridAggregateClauseFiltersPostFusionRegression() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	if !suite.probeSearch13CapabilityCluster(ctx, client) {
		suite.T().Skip("search module present but pre-1.3 (INKEYS probe rejected it)")
	}

	prefix := "{ft-hybrid-postfusion-filter-" + uuid.New().String() + "}:"
	index := createHybridIndex(ctx, client, prefix)

	rows, err := glideft.ClusterFtHybrid(ctx, client, index,
		options.FtHybridSearchArm{Query: hybridSearchQuery, YieldScoreAs: hybridSearchAlias},
		options.FtHybridVsimArm{Field: "vec", Vector: []byte(packFloat32Vector(1.0, 1.0)), K: intPtr(5)},
		&options.FtHybridOptions{
			Clauses: []options.FtAggregateClause{&options.FtAggregateFilter{Expression: "@ss > 0"}},
		},
	)
	require.NoError(suite.T(), err)
	require.Len(suite.T(), rows, 1)
	assert.Equal(suite.T(), prefix+hybridDocTextWinner, fmt.Sprintf("%v", rows[0]["__key"]))

	glideft.ClusterFtDropIndex(ctx, client, index)
}

func (suite *GlideTestSuite) TestModuleFtHybridCombineMethodsFuseResults() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	if !suite.probeSearch13CapabilityCluster(ctx, client) {
		suite.T().Skip("search module present but pre-1.3 (INKEYS probe rejected it)")
	}

	cases := []struct {
		name          string
		combine       options.FtHybridCombine
		expectedFirst string
	}{
		{"LINEAR-text-only", &options.FtHybridLinear{Alpha: f64Ptr(1.0), Beta: f64Ptr(0.0)}, hybridDocTextWinner},
		{"LINEAR-vector-only", &options.FtHybridLinear{Alpha: f64Ptr(0.0), Beta: f64Ptr(1.0)}, hybridDocVectorWinner},
		{"FUNCTION-text", &options.FtHybridFunction{Expr: "@" + hybridSearchAlias}, hybridDocTextWinner},
		{"FUNCTION-vec", &options.FtHybridFunction{Expr: "@" + hybridVsimAlias}, hybridDocVectorWinner},
	}

	for _, tc := range cases {
		suite.Run(tc.name, func() {
			prefix := "{ft-hybrid-combine-" + uuid.New().String() + "}:"
			index := createHybridIndex(ctx, client, prefix)

			rows, err := glideft.ClusterFtHybrid(ctx, client, index,
				options.FtHybridSearchArm{Query: hybridSearchQuery, YieldScoreAs: hybridSearchAlias},
				options.FtHybridVsimArm{
					Field: "vec", Vector: []byte(packFloat32Vector(hybridQueryVector[0], hybridQueryVector[1])),
					K: intPtr(5), YieldScoreAs: hybridVsimAlias,
				},
				&options.FtHybridOptions{Combine: tc.combine},
			)
			require.NoError(suite.T(), err)
			require.Len(suite.T(), rows, 2)

			rowsByKey := map[string]map[string]any{}
			for _, row := range rows {
				rowsByKey[fmt.Sprintf("%v", row["__key"])] = row
				assert.Contains(suite.T(), row, "__score")
				assert.Contains(suite.T(), row, hybridVsimAlias)
			}
			assert.Equal(suite.T(), prefix+tc.expectedFirst, fmt.Sprintf("%v", rows[0]["__key"]))
			assert.Contains(suite.T(), rowsByKey[prefix+hybridDocTextWinner], hybridSearchAlias)
			assert.NotContains(suite.T(), rowsByKey[prefix+hybridDocVectorWinner], hybridSearchAlias)

			glideft.ClusterFtDropIndex(ctx, client, index)
		})
	}
}

func (suite *GlideTestSuite) TestModuleFtHybridVectorInlineBytesAndParamReference() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	if !suite.probeSearch13CapabilityCluster(ctx, client) {
		suite.T().Skip("search module present but pre-1.3 (INKEYS probe rejected it)")
	}

	prefix := "{ft-hybrid-vecparam-" + uuid.New().String() + "}:"
	index := createHybridIndex(ctx, client, prefix)
	vec := packFloat32Vector(hybridQueryVector[0], hybridQueryVector[1])

	inlineRows, err := glideft.ClusterFtHybrid(ctx, client, index,
		options.FtHybridSearchArm{Query: hybridSearchQuery},
		options.FtHybridVsimArm{Field: "vec", Vector: []byte(vec), K: intPtr(1), YieldScoreAs: hybridVsimAlias},
		nil,
	)
	require.NoError(suite.T(), err)

	paramRows, err := glideft.ClusterFtHybrid(ctx, client, index,
		options.FtHybridSearchArm{Query: hybridSearchQuery},
		options.FtHybridVsimArm{Field: "vec", VectorParam: "$myvec", K: intPtr(1), YieldScoreAs: hybridVsimAlias},
		&options.FtHybridOptions{Params: []options.FtAggregateParam{{Key: "myvec", Value: vec}}},
	)
	require.NoError(suite.T(), err)

	require.Len(suite.T(), inlineRows, 2)
	require.Len(suite.T(), paramRows, 2)

	inlineKeys := map[string]bool{}
	for _, row := range inlineRows {
		inlineKeys[fmt.Sprintf("%v", row["__key"])] = true
	}
	paramKeys := map[string]bool{}
	for _, row := range paramRows {
		paramKeys[fmt.Sprintf("%v", row["__key"])] = true
	}
	assert.Equal(suite.T(), inlineKeys, paramKeys)

	for _, rows := range [][]map[string]any{inlineRows, paramRows} {
		rowsWithVs := 0
		var vsKey string
		for _, row := range rows {
			if _, ok := row[hybridVsimAlias]; ok {
				rowsWithVs++
				vsKey = fmt.Sprintf("%v", row["__key"])
			}
		}
		assert.Equal(suite.T(), 1, rowsWithVs, "k=1 should select exactly one document as the VSIM arm's own KNN match")
		assert.Equal(suite.T(), prefix+hybridDocVectorWinner, vsKey)
	}

	glideft.ClusterFtDropIndex(ctx, client, index)
}

func (suite *GlideTestSuite) TestModuleFtHybridVsimFilterWithPolicy() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	if !suite.probeSearch13CapabilityCluster(ctx, client) {
		suite.T().Skip("search module present but pre-1.3 (INKEYS probe rejected it)")
	}

	for _, policy := range []constants.HybridFilterPolicy{
		constants.HybridFilterPolicyAdhoc, constants.HybridFilterPolicyBatches,
	} {
		suite.Run(string(policy), func() {
			prefix := "{ft-hybrid-filter-" + uuid.New().String() + "}:"
			index := createHybridIndex(ctx, client, prefix)

			rows, err := glideft.ClusterFtHybrid(ctx, client, index,
				options.FtHybridSearchArm{Query: "nonexistentterm12345"},
				options.FtHybridVsimArm{
					Field: "vec", Vector: []byte(packFloat32Vector(1.0, 1.0)), K: intPtr(5),
					Filter: &options.FtHybridVsimFilter{Expression: "@title:hello", Policy: policy},
				},
				nil,
			)
			require.NoError(suite.T(), err)
			require.Len(suite.T(), rows, 1)
			assert.Equal(suite.T(), prefix+hybridDocTextWinner, fmt.Sprintf("%v", rows[0]["__key"]))

			glideft.ClusterFtDropIndex(ctx, client, index)
		})
	}
}

func (suite *GlideTestSuite) TestModuleFtHybridPerArmAndCombineYieldScoreAsColumns() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	if !suite.probeSearch13CapabilityCluster(ctx, client) {
		suite.T().Skip("search module present but pre-1.3 (INKEYS probe rejected it)")
	}

	prefix := "{ft-hybrid-yieldscore-" + uuid.New().String() + "}:"
	index := createHybridIndex(ctx, client, prefix)

	rows, err := glideft.ClusterFtHybrid(ctx, client, index,
		options.FtHybridSearchArm{Query: hybridSearchQuery, YieldScoreAs: hybridSearchAlias},
		options.FtHybridVsimArm{
			Field: "vec", Vector: []byte(packFloat32Vector(hybridQueryVector[0], hybridQueryVector[1])),
			K: intPtr(5), YieldScoreAs: hybridVsimAlias,
		},
		&options.FtHybridOptions{Combine: &options.FtHybridRrf{YieldScoreAs: "fused"}},
	)
	require.NoError(suite.T(), err)
	require.Len(suite.T(), rows, 2)

	rowsByKey := map[string]map[string]any{}
	for _, row := range rows {
		rowsByKey[fmt.Sprintf("%v", row["__key"])] = row
		assert.Contains(suite.T(), row, hybridVsimAlias)
		assert.Contains(suite.T(), row, "fused")
		assert.NotContains(suite.T(), row, "__score") // renamed via YieldScoreAs
	}
	assert.Contains(suite.T(), rowsByKey[prefix+hybridDocTextWinner], hybridSearchAlias)
	assert.NotContains(suite.T(), rowsByKey[prefix+hybridDocVectorWinner], hybridSearchAlias)

	glideft.ClusterFtDropIndex(ctx, client, index)
}

func (suite *GlideTestSuite) TestModuleFtHybridWithCursorPreferReplicaClientPagedToExhaustion() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	if !suite.probeSearch13CapabilityCluster(ctx, client) {
		suite.T().Skip("search module present but pre-1.3 (INKEYS probe rejected it)")
	}
	if len(suite.clusterHosts) < 2 {
		suite.T().Skip("test requires a real multi-shard, replica-bearing cluster")
	}

	prefix := "{ft-hybrid-cursor-" + uuid.New().String() + "}:"
	index := createHybridIndex(ctx, client, prefix)

	preferReplicaCfg := suite.defaultClusterClientConfig().WithReadFrom(config.PreferReplica)
	preferReplicaClient, err := suite.clusterClient(preferReplicaCfg)
	require.NoError(suite.T(), err)

	count := 1
	rows, cursorID, err := glideft.ClusterFtHybridWithCursor(ctx, preferReplicaClient, index,
		options.FtHybridSearchArm{Query: "*"},
		options.FtHybridVsimArm{Field: "vec", Vector: []byte(packFloat32Vector(1.0, 1.0)), K: intPtr(5)},
		&options.FtHybridOptions{WithCursor: &options.FtWithCursor{Count: &count}},
	)
	require.NoError(suite.T(), err)

	totalRows := len(rows)
	seenKeys := map[string]bool{}
	for _, row := range rows {
		seenKeys[fmt.Sprintf("%v", row["__key"])] = true
	}
	pagesRead := 1
	for cursorID != 0 {
		var page []map[string]any
		page, cursorID, err = glideft.ClusterFtCursorRead(ctx, preferReplicaClient, index, cursorID, nil)
		require.NoError(suite.T(), err)
		totalRows += len(page)
		for _, row := range page {
			seenKeys[fmt.Sprintf("%v", row["__key"])] = true
		}
		pagesRead++
		require.LessOrEqual(suite.T(), pagesRead, 10, "paging did not terminate as expected")
	}
	assert.Equal(suite.T(), 2, totalRows)
	assert.Equal(suite.T(), map[string]bool{
		prefix + hybridDocTextWinner:   true,
		prefix + hybridDocVectorWinner: true,
	}, seenKeys, "paging returned the right row COUNT but the wrong SET of keys")

	glideft.ClusterFtDropIndex(ctx, client, index)
}

func (suite *GlideTestSuite) TestModuleFtHybridLocalOnlyAccepted() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	if !suite.probeSearch13CapabilityCluster(ctx, client) {
		suite.T().Skip("search module present but pre-1.3 (INKEYS probe rejected it)")
	}

	prefix := "{ft-hybrid-localonly-" + uuid.New().String() + "}:"
	index := createHybridIndex(ctx, client, prefix)

	rows, err := glideft.ClusterFtHybrid(ctx, client, index,
		options.FtHybridSearchArm{Query: "hello"},
		options.FtHybridVsimArm{Field: "vec", Vector: []byte(packFloat32Vector(1.0, 1.0)), K: intPtr(5)},
		&options.FtHybridOptions{LocalOnly: true},
	)
	require.NoError(suite.T(), err)
	assert.Len(suite.T(), rows, 2)

	glideft.ClusterFtDropIndex(ctx, client, index)
}

func (suite *GlideTestSuite) TestModuleFtHybridWithoutKUsesEngineDefault() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	if !suite.probeSearch13CapabilityCluster(ctx, client) {
		suite.T().Skip("search module present but pre-1.3 (INKEYS probe rejected it)")
	}

	prefix := "{ft-hybrid-default-k-" + uuid.New().String() + "}:"
	index := prefix + "index"
	const docCount = 15

	_, err := glideft.ClusterFtCreate(ctx, client, index,
		[]options.Field{
			options.NewTextField("title"),
			options.NewVectorFieldFlat("vec", constants.DistanceMetricL2, 2),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	require.NoError(suite.T(), err)
	for i := 1; i <= docCount; i++ {
		_, err = client.HSet(ctx, prefix+"d"+strconv.Itoa(i), map[string]string{
			"title": "goodbye", "vec": packFloat32Vector(float32(i), float32(i)),
		})
		require.NoError(suite.T(), err)
	}
	time.Sleep(time.Second)

	rows, err := glideft.ClusterFtHybrid(ctx, client, index,
		options.FtHybridSearchArm{Query: "nonexistentterm12345"},
		options.FtHybridVsimArm{Field: "vec", Vector: []byte(packFloat32Vector(1.0, 1.0))}, // no K
		nil,
	)
	require.NoError(suite.T(), err)
	assert.Len(suite.T(), rows, 10, "the engine's own VSIM default when K is omitted is 10")

	glideft.ClusterFtDropIndex(ctx, client, index)
}

func (suite *GlideTestSuite) TestModuleFtHybridInsideMultiExecRejected() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	if !suite.probeSearch13CapabilityCluster(ctx, client) {
		suite.T().Skip("search module present but pre-1.3 (INKEYS probe rejected it)")
	}

	prefix := "{ft-hybrid-multiexec-" + uuid.New().String() + "}:"
	index := createHybridIndex(ctx, client, prefix)

	vec := packFloat32Vector(hybridQueryVector[0], hybridQueryVector[1])
	batch := pipeline.NewClusterBatch(true).CustomCommand([]string{
		"FT.HYBRID", index,
		"SEARCH", hybridSearchQuery,
		"VSIM", "@vec", "$q",
		"PARAMS", "2", "q", vec,
	})
	_, err := client.Exec(ctx, *batch, true)
	assert.ErrorContains(suite.T(), err, "FT.HYBRID")

	glideft.ClusterFtDropIndex(ctx, client, index)
}
