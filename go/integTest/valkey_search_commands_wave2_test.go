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
// READ/DEL, primary-pinned routing regardless of ReadFrom), and FT.HYBRID. Gated behind
// probeSearch13CapabilityCluster, the same capability probe wave 1 already established in this
// file -- no wave-2 test here calls getEmulateReleaseLevel, since this wave reports the same
// pass/skip counts at both the default and the 1.3.0 emulate-release level. Mirrors
// python/tests/async_tests/tests_server_modules/test_ft.py's test_ft_search_with_scores*,
// test_ft_aggregate_add_scores*, test_ft_cursor_affinity*, test_ft_aggregate_typed_cursor*, and
// test_ft_hybrid_* live tests.

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

// float32ToFloat16Bits converts an IEEE754 float32 to IEEE754 binary16 (half-precision) bits.
// Only exercised against exact binary powers of two (0.0, 1.0, 2.0) in this file's tests, so
// this is not a fully general rounding implementation -- it is correct for those exact values
// and for normal values generally, but does not handle subnormal-range rounding or NaN payloads.
func float32ToFloat16Bits(f float32) uint16 {
	bits := math.Float32bits(f)
	sign := uint16((bits >> 16) & 0x8000)
	exp := int32((bits>>23)&0xFF) - 127 + 15
	mant := bits & 0x7FFFFF
	if exp <= 0 {
		return sign
	}
	if exp >= 0x1F {
		return sign | 0x7C00
	}
	return sign | uint16(exp)<<10 | uint16(mant>>13)
}

// packFloat16Vector packs a sequence of float32 values into a little-endian binary16 byte
// string, the wire representation FT.CREATE's FLOAT16 vector fields expect.
func packFloat16Vector(values ...float32) string {
	buf := make([]byte, 2*len(values))
	for i, v := range values {
		binary.LittleEndian.PutUint16(buf[i*2:], float32ToFloat16Bits(v))
	}
	return string(buf)
}

// packBfloat16Vector packs a sequence of float32 values into a little-endian bfloat16 byte
// string (the high-order 16 bits of each float32, i.e. truncation rather than
// round-to-nearest-ties-to-even), the wire representation FT.CREATE's BFLOAT16 vector fields
// expect. Test values are restricted to exact binary powers of two so this simple truncation
// agrees with the server's own round-to-nearest-ties-to-even (src/indexes/bfloat16.h).
func packBfloat16Vector(values ...float32) string {
	buf := make([]byte, 2*len(values))
	for i, v := range values {
		binary.LittleEndian.PutUint16(buf[i*2:], uint16(math.Float32bits(v)>>16))
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
	// Assert the actual SORTBY price ASC order and the SortKey values, not just that
	// Score/SortKey/Fields are present.
	assert.Equal(suite.T(), []string{prefix + "1", prefix + "2"}, []string{
		result.Documents[0].Key, result.Documents[1].Key,
	}, "must be sorted by price ascending (10 before 20)")
	assert.Equal(suite.T(), "#10", result.Documents[0].SortKey)
	assert.Equal(suite.T(), "#20", result.Documents[1].SortKey)

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

// unwrapClusterCustomCommand unwraps a models.ClusterValue[any] for an unrouted command like
// CLUSTER KEYSLOT/CLUSTER SHARDS: the single answering node's own reply lands under
// MultiValue() keyed by the reply's own content rather than a node address (confirmed
// empirically against a live cluster). Unlike getEmulateReleaseLevel's inline unwrap (which
// always rebuilds a map, since CONFIG GET's single-entry map has a meaningful key -- the config
// name -- worth preserving for readMapField), this returns the lone value directly when exactly
// one entry is present, since CLUSTER KEYSLOT/CLUSTER SHARDS have no analogous per-entry key to
// keep; only a genuine multi-node fan-out falls through to the rebuilt-map case below.
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
// map[string]any. Shares its flat-pair-walk logic with readMapField
// (valkey_search_commands_test.go), which looks up a single field rather than building the
// whole map; the two are kept as separate functions since one returns a map and the other a
// single (value, ok) pair, but the underlying "odd index is key, even index is value" walk is
// identical.
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

// TestModuleFtHybridVsimRangeRejected ports Python's test_ft_hybrid_vsim_range_rejected
// tripwire: FT.HYBRID's VSIM ... RANGE form parses for shape but is rejected at execution
// (ParseFtHybridCommand returns an UnimplementedError after parse completes). RANGE stays
// excluded from the typed FtHybridVsimArm API entirely (EfRuntime's own docstring notes "RANGE
// is not currently supported by this typed API"), so this goes through CustomCommand directly
// rather than the typed glideft.FtHybrid wrapper -- this test exists so that if this
// limitation is ever lifted upstream, it fails loudly instead of the exclusion silently going
// stale.
func (suite *GlideTestSuite) TestModuleFtHybridVsimRangeRejected() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	if !suite.probeSearch13CapabilityCluster(ctx, client) {
		suite.T().Skip("search module present but pre-1.3 (INKEYS probe rejected it)")
	}

	prefix := "{ft-hybrid-range-" + uuid.New().String() + "}:"
	index := prefix + "index"

	_, err := glideft.ClusterFtCreate(
		ctx, client, index,
		[]options.Field{
			options.NewTextField("title"),
			options.NewVectorFieldFlat("vec", constants.DistanceMetricL2, 2),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}},
	)
	require.NoError(suite.T(), err)

	queryBlob := packFloat32Vector(1.0, 2.0)
	_, err = client.CustomCommand(ctx, []string{
		"FT.HYBRID", index,
		"SEARCH", "hello",
		"VSIM", "@vec", "$blob",
		"RANGE", "2", "RADIUS", "5.0",
		"PARAMS", "2", "blob", queryBlob,
	})
	assert.ErrorContains(suite.T(), err, "RANGE")

	glideft.ClusterFtDropIndex(ctx, client, index)
}

// --- ft_aggregate LOAD ... AS (LoadField) ---

// TestModuleFtAggregateLoadFieldAlias covers FT.AGGREGATE's LOAD ... AS form (valkey-search
// 1.3.0, src/commands/ft_aggregate_parser.cc, compat flag ft_aggregate_load_as). Confirmed via
// the live emulate-release level that the alias takes effect as the output column name at
// search.emulate-release >= 1.3.0. Mixes a plain string LoadFields entry with a LoadFieldEntries
// entry in the same aggregate call to confirm the two forms compose (non-breaking addition:
// existing []string LoadFields callers keep working unchanged).
//
// Below search.emulate-release 1.3.0, the legacy branch never consumes AS as a rename keyword --
// it falls through and gets parsed as its own LOAD field, which doesn't exist on the schema,
// failing the clause outright (ConstructLoadParser()). Asserts that legacy failure directly,
// rather than skipping, since the two branches intentionally produce different outcomes and both
// are worth pinning down. Mirrors Python's test_ft_aggregate_load_as.
func (suite *GlideTestSuite) TestModuleFtAggregateLoadFieldAlias() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	if !suite.probeSearch13CapabilityCluster(ctx, client) {
		suite.T().Skip("search module present but pre-1.3 (INKEYS probe rejected it)")
	}

	prefix := "{ft-load-as-" + uuid.New().String() + "}:"
	index := prefix + "index"

	_, err := glideft.ClusterFtCreate(ctx, client, index,
		[]options.Field{options.NewTextField("title"), options.NewNumericField("price")},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	require.NoError(suite.T(), err)
	_, err = client.HSet(ctx, prefix+"1", map[string]string{"title": "widget", "price": "9"})
	require.NoError(suite.T(), err)
	time.Sleep(time.Second)

	aggOpts := &options.FtAggregateOptions{
		LoadFields:       []string{"title"},
		LoadFieldEntries: []options.LoadField{{FieldIdentifier: "price", Alias: "cost"}},
	}

	level := suite.getEmulateReleaseLevel(ctx, client)
	if checkVersionLt(level, "1.3.0") {
		_, err = glideft.ClusterFtAggregate(ctx, client, index, "*", aggOpts)
		assert.ErrorContains(suite.T(), err, "Index: field `AS` does not exist")
	} else {
		rows, err := glideft.ClusterFtAggregate(ctx, client, index, "*", aggOpts)
		require.NoError(suite.T(), err)
		require.Len(suite.T(), rows, 1)
		assert.Equal(suite.T(), "widget", fmt.Sprintf("%v", rows[0]["title"]))
		assert.Equal(suite.T(), "9", fmt.Sprintf("%v", rows[0]["cost"]))
		_, pricePresent := rows[0]["price"]
		assert.False(suite.T(), pricePresent, "price should be loaded only under its alias, cost")
	}

	glideft.ClusterFtDropIndex(ctx, client, index)
}

// --- ft_create float16/bfloat16: real KNN query, not acceptance-only ---

// TestModuleFtCreateFloat16VectorKnnReturnsRealNeighbors covers FLOAT16 vector fields
// (valkey-search 1.3, src/indexes/vector_base.h -- 2-byte-per-element format) with an actual KNN
// query, confirming the server returns real neighbors rather than merely accepting the schema.
// Test values (0.0, 1.0, 2.0) are exact binary powers, so FLOAT16 round-trips them exactly.
// Mirrors Python's test_ft_create_float16_bfloat16_vector (FLOAT16 branch).
func (suite *GlideTestSuite) TestModuleFtCreateFloat16VectorKnnReturnsRealNeighbors() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	if !suite.probeSearch13CapabilityCluster(ctx, client) {
		suite.T().Skip("search module present but pre-1.3 (INKEYS probe rejected it)")
	}

	prefix := "{ft-vectype-f16-" + uuid.New().String() + "}:"
	index := prefix + "index"
	vecField := "vec"

	_, err := glideft.ClusterFtCreate(ctx, client, index,
		[]options.Field{
			options.NewVectorFieldFlat(vecField, constants.DistanceMetricL2, 2).
				SetType(constants.VectorDataTypeFloat16),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	require.NoError(suite.T(), err)

	points := map[string][2]float32{"0": {0.0, 0.0}, "1": {1.0, 0.0}, "2": {2.0, 0.0}}
	for keySuffix, coords := range points {
		_, err = client.HSet(ctx, prefix+keySuffix, map[string]string{vecField: packFloat16Vector(coords[0], coords[1])})
		require.NoError(suite.T(), err)
	}
	time.Sleep(time.Second)

	queryBlob := packFloat16Vector(1.0, 0.0)
	knnQuery := fmt.Sprintf("*=>[KNN 1 @%s $blob]", vecField)
	result, err := glideft.ClusterFtSearch(ctx, client, index, knnQuery, &options.FtSearchOptions{
		Params:       []options.FtSearchParam{{Key: "blob", Value: queryBlob}},
		ReturnFields: []options.FtSearchReturnField{{FieldIdentifier: "__" + vecField + "_score"}},
	})
	require.NoError(suite.T(), err)
	require.Equal(suite.T(), int64(1), result.TotalResults)
	require.Len(suite.T(), result.Documents, 1)
	matchedKey := prefix + "1"
	assert.Equal(suite.T(), matchedKey, result.Documents[0].Key)
	scoreStr := fmt.Sprintf("%v", result.Documents[0].Fields["__"+vecField+"_score"])
	score, err := strconv.ParseFloat(scoreStr, 64)
	require.NoError(suite.T(), err)
	assert.Equal(suite.T(), 0.0, score)

	glideft.ClusterFtDropIndex(ctx, client, index)
}

// TestModuleFtCreateBfloat16VectorKnnReturnsRealNeighbors is
// TestModuleFtCreateFloat16VectorKnnReturnsRealNeighbors for BFLOAT16. Mirrors Python's
// test_ft_create_float16_bfloat16_vector (BFLOAT16 branch).
func (suite *GlideTestSuite) TestModuleFtCreateBfloat16VectorKnnReturnsRealNeighbors() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	if !suite.probeSearch13CapabilityCluster(ctx, client) {
		suite.T().Skip("search module present but pre-1.3 (INKEYS probe rejected it)")
	}

	prefix := "{ft-vectype-bf16-" + uuid.New().String() + "}:"
	index := prefix + "index"
	vecField := "vec"

	_, err := glideft.ClusterFtCreate(ctx, client, index,
		[]options.Field{
			options.NewVectorFieldFlat(vecField, constants.DistanceMetricL2, 2).
				SetType(constants.VectorDataTypeBfloat16),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}})
	require.NoError(suite.T(), err)

	points := map[string][2]float32{"0": {0.0, 0.0}, "1": {1.0, 0.0}, "2": {2.0, 0.0}}
	for keySuffix, coords := range points {
		_, err = client.HSet(ctx, prefix+keySuffix, map[string]string{vecField: packBfloat16Vector(coords[0], coords[1])})
		require.NoError(suite.T(), err)
	}
	time.Sleep(time.Second)

	queryBlob := packBfloat16Vector(1.0, 0.0)
	knnQuery := fmt.Sprintf("*=>[KNN 1 @%s $blob]", vecField)
	result, err := glideft.ClusterFtSearch(ctx, client, index, knnQuery, &options.FtSearchOptions{
		Params:       []options.FtSearchParam{{Key: "blob", Value: queryBlob}},
		ReturnFields: []options.FtSearchReturnField{{FieldIdentifier: "__" + vecField + "_score"}},
	})
	require.NoError(suite.T(), err)
	require.Equal(suite.T(), int64(1), result.TotalResults)
	require.Len(suite.T(), result.Documents, 1)
	matchedKey := prefix + "1"
	assert.Equal(suite.T(), matchedKey, result.Documents[0].Key)
	scoreStr := fmt.Sprintf("%v", result.Documents[0].Fields["__"+vecField+"_score"])
	score, err := strconv.ParseFloat(scoreStr, 64)
	require.NoError(suite.T(), err)
	assert.Equal(suite.T(), 0.0, score)

	glideft.ClusterFtDropIndex(ctx, client, index)
}

// --- ft_create score_field: real ranking effect, not acceptance-only ---

// TestModuleFtCreateScoreFieldAffectsRanking covers FtCreateOptions.ScoreField actually
// affecting ranking, as opposed to merely being accepted. Two otherwise-identical documents, one
// with a much higher score_field value, must rank first and score higher under WithScores.
// Mirrors Python's test_ft_create_score_field_affects_ranking.
func (suite *GlideTestSuite) TestModuleFtCreateScoreFieldAffectsRanking() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	if !suite.probeSearch13CapabilityCluster(ctx, client) {
		suite.T().Skip("search module present but pre-1.3 (INKEYS probe rejected it)")
	}

	prefix := "{ft-score-field-" + uuid.New().String() + "}:"
	index := prefix + "index"

	_, err := glideft.ClusterFtCreate(ctx, client, index,
		[]options.Field{options.NewTextField("title")},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}, ScoreField: "boost"})
	require.NoError(suite.T(), err)

	lowKey := prefix + "low"
	highKey := prefix + "high"
	_, err = client.HSet(ctx, lowKey, map[string]string{"title": "hello world", "boost": "1.0"})
	require.NoError(suite.T(), err)
	_, err = client.HSet(ctx, highKey, map[string]string{"title": "hello world", "boost": "100.0"})
	require.NoError(suite.T(), err)
	time.Sleep(time.Second)

	result, err := glideft.ClusterFtSearch(ctx, client, index, "hello", &options.FtSearchOptions{WithScores: true})
	require.NoError(suite.T(), err)
	require.Equal(suite.T(), int64(2), result.TotalResults)
	require.Len(suite.T(), result.Documents, 2)

	// The high-boost document must rank first (FtSearchResult.Documents preserves the server's
	// ranking order) and score strictly higher than the low-boost one.
	assert.Equal(suite.T(), highKey, result.Documents[0].Key)
	byKey := make(map[string]float64, len(result.Documents))
	for _, doc := range result.Documents {
		require.NotNil(suite.T(), doc.Score, "WithScores should populate Score")
		byKey[doc.Key] = *doc.Score
	}
	assert.Greater(suite.T(), byKey[highKey], byKey[lowKey])

	glideft.ClusterFtDropIndex(ctx, client, index)
}
