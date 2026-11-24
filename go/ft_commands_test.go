// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"fmt"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/options"
	"github.com/valkey-io/valkey-glide/go/v2/servermodules/glideft"
)

func Example_clusterFtCreate() {
	if !*vssTest {
		// Requires a server with the search module loaded. Run with -vss-test to enable.
		fmt.Println("true") // pass the test
		return
	}
	var client *ClusterClient = getExampleClusterClient()
	ctx := context.Background()

	// Create a vector search index on hash keys with prefix "doc:"
	_, err := glideft.ClusterFtCreate(
		ctx, client, "my_index",
		[]options.Field{
			options.NewTextField("title"),
			options.NewNumericField("score"),
			options.NewVectorFieldFlat("embedding", constants.DistanceMetricL2, 4),
		},
		&options.FtCreateOptions{
			DataType: constants.IndexDataTypeHash,
			Prefixes: []string{"doc:"},
		},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	result, err := glideft.ClusterFtList(ctx, client)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(len(result) > 0)

	glideft.ClusterFtDropIndex(ctx, client, "my_index")

	// Output:
	// true
}

func Example_clusterFtSearch() {
	if !*vssTest {
		// Requires a server with the search module loaded. Run with -vss-test to enable.
		fmt.Println(1) // pass the test
		return
	}
	var client *ClusterClient = getExampleClusterClient()
	ctx := context.Background()

	prefix := "{searchdoc}:"
	index := "{searchdoc}:index"

	_, err := glideft.ClusterFtCreate(
		ctx, client, index,
		[]options.Field{
			options.NewTextField("title"),
			options.NewNumericField("score"),
		},
		&options.FtCreateOptions{
			DataType: constants.IndexDataTypeHash,
			Prefixes: []string{prefix},
		},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	client.HSet(ctx, prefix+"1", map[string]string{"title": "hello world", "score": "10"})
	client.HSet(ctx, prefix+"2", map[string]string{"title": "hello there", "score": "20"})

	// Allow index to sync
	time.Sleep(time.Second)

	result, err := glideft.ClusterFtSearch(ctx, client, index, "@score:[10 10]", nil)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.TotalResults)

	glideft.ClusterFtDropIndex(ctx, client, index)

	// Output:
	// 1
}

func Example_clusterFtAggregate() {
	if !*vssTest {
		// Requires a server with the search module loaded. Run with -vss-test to enable.
		fmt.Println(2) // pass the test
		return
	}
	var client *ClusterClient = getExampleClusterClient()
	ctx := context.Background()

	prefix := "{aggdoc}:"
	index := "{aggdoc}:index"

	_, err := glideft.ClusterFtCreate(
		ctx, client, index,
		[]options.Field{
			options.NewNumericField("score"),
		},
		&options.FtCreateOptions{
			DataType: constants.IndexDataTypeHash,
			Prefixes: []string{prefix},
		},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	client.HSet(ctx, prefix+"1", map[string]string{"score": "10"})
	client.HSet(ctx, prefix+"2", map[string]string{"score": "20"})
	client.HSet(ctx, prefix+"3", map[string]string{"score": "30"})

	time.Sleep(time.Second)

	// Aggregate with LOAD to retrieve field values for matching documents
	rows, err := glideft.ClusterFtAggregate(
		ctx, client, index, "@score:[20 +inf]",
		&options.FtAggregateOptions{
			LoadAll: true,
		},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(len(rows))

	glideft.ClusterFtDropIndex(ctx, client, index)

	// Output:
	// 2
}

func Example_ftCreate() {
	if !*vssTest {
		// Requires a server with the search module loaded. Run with -vss-test to enable.
		fmt.Println("true") // pass the test
		return
	}
	var client *Client = getExampleClient()
	ctx := context.Background()

	// Create a vector search index on hash keys with prefix "doc:"
	_, err := glideft.FtCreate(
		ctx, client, "my_standalone_index",
		[]options.Field{
			options.NewTextField("title"),
			options.NewNumericField("score"),
			options.NewVectorFieldFlat("embedding", constants.DistanceMetricL2, 4),
		},
		&options.FtCreateOptions{
			DataType: constants.IndexDataTypeHash,
			Prefixes: []string{"doc:"},
		},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	result, err := glideft.FtList(ctx, client)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(len(result) > 0)

	glideft.FtDropIndex(ctx, client, "my_standalone_index")

	// Output:
	// true
}

func Example_ftSearch() {
	if !*vssTest {
		// Requires a server with the search module loaded. Run with -vss-test to enable.
		fmt.Println(1) // pass the test
		return
	}
	var client *Client = getExampleClient()
	ctx := context.Background()

	prefix := "searchdoc:"
	index := "searchdoc:index"

	_, err := glideft.FtCreate(
		ctx, client, index,
		[]options.Field{
			options.NewTextField("title"),
			options.NewNumericField("score"),
		},
		&options.FtCreateOptions{
			DataType: constants.IndexDataTypeHash,
			Prefixes: []string{prefix},
		},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	client.HSet(ctx, prefix+"1", map[string]string{"title": "hello world", "score": "10"})
	client.HSet(ctx, prefix+"2", map[string]string{"title": "hello there", "score": "20"})

	// Allow index to sync
	time.Sleep(time.Second)

	result, err := glideft.FtSearch(ctx, client, index, "@score:[10 10]", nil)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.TotalResults)

	glideft.FtDropIndex(ctx, client, index)

	// Output:
	// 1
}

func Example_ftAggregate() {
	if !*vssTest {
		// Requires a server with the search module loaded. Run with -vss-test to enable.
		fmt.Println(2) // pass the test
		return
	}
	var client *Client = getExampleClient()
	ctx := context.Background()

	prefix := "aggdoc:"
	index := "aggdoc:index"

	_, err := glideft.FtCreate(
		ctx, client, index,
		[]options.Field{
			options.NewNumericField("score"),
		},
		&options.FtCreateOptions{
			DataType: constants.IndexDataTypeHash,
			Prefixes: []string{prefix},
		},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	client.HSet(ctx, prefix+"1", map[string]string{"score": "10"})
	client.HSet(ctx, prefix+"2", map[string]string{"score": "20"})
	client.HSet(ctx, prefix+"3", map[string]string{"score": "30"})

	time.Sleep(time.Second)

	// Aggregate with LOAD to retrieve field values for matching documents
	rows, err := glideft.FtAggregate(
		ctx, client, index, "@score:[20 +inf]",
		&options.FtAggregateOptions{
			LoadAll: true,
		},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(len(rows))

	glideft.FtDropIndex(ctx, client, index)

	// Output:
	// 2
}

func Example_clusterFtSearchInKeys() {
	if !*vssTest {
		fmt.Println("3 0 2")
		return
	}
	var client *ClusterClient = getExampleClusterClient()
	ctx := context.Background()

	if !probeSearch13CapabilityCluster(ctx, client) {
		fmt.Println("3 0 2")
		return
	}

	prefix := "{inkeysdoc}:"
	index := "{inkeysdoc}:index"

	_, err := glideft.ClusterFtCreate(
		ctx, client, index,
		[]options.Field{options.NewTextField("title")},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	client.HSet(ctx, prefix+"1", map[string]string{"title": "hello world"})
	client.HSet(ctx, prefix+"2", map[string]string{"title": "hello world"})
	client.HSet(ctx, prefix+"3", map[string]string{"title": "hello world"})

	time.Sleep(time.Second)

	all, err := glideft.ClusterFtSearch(ctx, client, index, "*", nil)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	none, err := glideft.ClusterFtSearch(ctx, client, index, "*", &options.FtSearchOptions{InKeys: []string{}})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	some, err := glideft.ClusterFtSearch(
		ctx, client, index, "*",
		&options.FtSearchOptions{InKeys: []string{prefix + "1", prefix + "2"}},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(all.TotalResults, none.TotalResults, some.TotalResults)

	glideft.ClusterFtDropIndex(ctx, client, index)

	// Output:
	// 3 0 2
}

func Example_ftSearchCount() {
	if !*vssTest {
		fmt.Println(2)
		return
	}
	var client *Client = getExampleClient()
	ctx := context.Background()

	if !probeSearch13Capability(ctx, client) {
		fmt.Println(2)
		return
	}

	index := "count_index"

	_, err := glideft.FtCreate(
		ctx, client, index,
		[]options.Field{options.NewTextField("title")},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	client.HSet(ctx, "count:1", map[string]string{"title": "hello"})
	client.HSet(ctx, "count:2", map[string]string{"title": "hello"})

	time.Sleep(time.Second)

	result, err := glideft.FtSearch(ctx, client, index, "*", &options.FtSearchOptions{Count: true})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.TotalResults)

	glideft.FtDropIndex(ctx, client, index)

	// Output:
	// 2
}

func Example_ftCreateFloat16Vector() {
	if !*vssTest {
		fmt.Println("OK")
		return
	}
	var client *Client = getExampleClient()
	ctx := context.Background()

	if !probeSearch13Capability(ctx, client) {
		fmt.Println("OK")
		return
	}

	index := "float16_index"

	result, err := glideft.FtCreate(
		ctx, client, index,
		[]options.Field{
			options.NewVectorFieldFlat("vec", constants.DistanceMetricL2, 2).
				SetType(constants.VectorDataTypeFloat16),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	glideft.FtDropIndex(ctx, client, index)

	// Output:
	// OK
}

func Example_ftCreateFilterScoreFieldNoHighlight() {
	if !*vssTest {
		fmt.Println("OK")
		return
	}
	var client *Client = getExampleClient()
	ctx := context.Background()

	if !probeSearch13Capability(ctx, client) {
		fmt.Println("OK")
		return
	}

	index := "parity_index"

	result, err := glideft.FtCreate(
		ctx, client, index,
		[]options.Field{
			options.NewTextField("title").SetSortable(true).SetUnf(true),
			options.NewNumericField("boost"),
		},
		&options.FtCreateOptions{
			DataType:    constants.IndexDataTypeHash,
			Filter:      "@title != ''",
			ScoreField:  "boost",
			NoHighlight: true,
		},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	glideft.FtDropIndex(ctx, client, index)

	// Output:
	// OK
}

func Example_ftHybrid() {
	if !*vssTest {
		fmt.Println(2)
		return
	}
	var client *Client = getExampleClient()
	ctx := context.Background()

	if !probeSearch13Capability(ctx, client) {
		fmt.Println(2)
		return
	}

	prefix := "hybrid-" + uuid.New().String() + ":"
	index := prefix + "index"

	_, err := glideft.FtCreate(
		ctx, client, index,
		[]options.Field{
			options.NewTextField("title"),
			options.NewVectorFieldFlat("vec", constants.DistanceMetricL2, 2),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	client.HSet(ctx, prefix+"textwinner", map[string]string{
		"title": "hello hello hello world", "vec": "\x00\x00\xc8\x42\x00\x00\xc8\x42",
	})
	client.HSet(ctx, prefix+"vectorwinner", map[string]string{
		"title": "goodbye", "vec": "\x00\x00\x80\x3f\x00\x00\x80\x3f",
	})

	time.Sleep(time.Second)

	k := 5
	rows, err := glideft.FtHybrid(
		ctx, client, index,
		options.FtHybridSearchArm{Query: "hello"},
		options.FtHybridVsimArm{Field: "vec", Vector: []byte("\x00\x00\x80\x3f\x00\x00\x80\x3f"), K: &k},
		nil,
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(len(rows))

	glideft.FtDropIndex(ctx, client, index)

	// Output:
	// 2
}

func Example_clusterFtHybrid() {
	if !*vssTest {
		fmt.Println(2)
		return
	}
	var client *ClusterClient = getExampleClusterClient()
	ctx := context.Background()

	if !probeSearch13CapabilityCluster(ctx, client) {
		fmt.Println(2)
		return
	}

	prefix := "{hybrid-" + uuid.New().String() + "}:"
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
		fmt.Println("Glide example failed with an error: ", err)
	}

	client.HSet(ctx, prefix+"textwinner", map[string]string{
		"title": "hello hello hello world", "vec": "\x00\x00\xc8\x42\x00\x00\xc8\x42",
	})
	client.HSet(ctx, prefix+"vectorwinner", map[string]string{
		"title": "goodbye", "vec": "\x00\x00\x80\x3f\x00\x00\x80\x3f",
	})

	time.Sleep(time.Second)

	k := 5
	rows, err := glideft.ClusterFtHybrid(
		ctx, client, index,
		options.FtHybridSearchArm{Query: "hello"},
		options.FtHybridVsimArm{Field: "vec", Vector: []byte("\x00\x00\x80\x3f\x00\x00\x80\x3f"), K: &k},
		nil,
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(len(rows))

	glideft.ClusterFtDropIndex(ctx, client, index)

	// Output:
	// 2
}

func Example_clusterFtAggregateWithCursor() {
	if !*vssTest {
		fmt.Println(5)
		fmt.Println(5)
		fmt.Println("OK")
		return
	}
	var client *ClusterClient = getExampleClusterClient()
	ctx := context.Background()

	if !probeSearch13CapabilityCluster(ctx, client) {
		fmt.Println(5)
		fmt.Println(5)
		fmt.Println("OK")
		return
	}

	prefix := "{cursor-" + uuid.New().String() + "}:"
	index := prefix + "index"

	_, err := glideft.ClusterFtCreate(
		ctx, client, index,
		[]options.Field{
			options.NewNumericField("n").SetSortable(true),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	for i := 1; i <= 20; i++ {
		client.HSet(ctx, prefix+fmt.Sprint("d", i), map[string]string{"n": fmt.Sprint(i)})
	}

	time.Sleep(time.Second)

	count := 5
	rows, cursorID, err := glideft.ClusterFtAggregateWithCursor(
		ctx, client, index, "*",
		&options.FtAggregateOptions{
			LoadFields: []string{"n"},
			WithCursor: &options.FtWithCursor{Count: &count},
		},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(len(rows))

	page, _, err := glideft.ClusterFtCursorRead(ctx, client, index, cursorID, &count)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(len(page))

	deleted, err := glideft.ClusterFtCursorDel(ctx, client, index, cursorID)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(deleted)

	glideft.ClusterFtDropIndex(ctx, client, index)

	// Output:
	// 5
	// 5
	// OK
}

func Example_clusterFtSearchWithScores() {
	if !*vssTest {
		fmt.Println(2)
		fmt.Println(true)
		return
	}
	var client *ClusterClient = getExampleClusterClient()
	ctx := context.Background()

	if !probeSearch13CapabilityCluster(ctx, client) {
		fmt.Println(2)
		fmt.Println(true)
		return
	}

	prefix := "{scores-" + uuid.New().String() + "}:"
	index := prefix + "index"

	_, err := glideft.ClusterFtCreate(
		ctx, client, index,
		[]options.Field{options.NewTextField("title")},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	client.HSet(ctx, prefix+"1", map[string]string{"title": "hello hello hello world"})
	client.HSet(ctx, prefix+"2", map[string]string{"title": "hello there"})

	time.Sleep(time.Second)

	result, err := glideft.ClusterFtSearch(
		ctx, client, index, "hello",
		&options.FtSearchOptions{WithScores: true, Scorer: "BM25STD"},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.TotalResults)
	fmt.Println(len(result.Documents) > 0 && result.Documents[0].Key == prefix+"1")

	glideft.ClusterFtDropIndex(ctx, client, index)

	// Output:
	// 2
	// true
}

func Example_clusterFtAggregateLoadField() {
	if !*vssTest {
		fmt.Println("widget 9")
		return
	}
	var client *ClusterClient = getExampleClusterClient()
	ctx := context.Background()

	if !probeSearch13CapabilityCluster(ctx, client) {
		fmt.Println("widget 9")
		return
	}

	prefix := "{loadfield-" + uuid.New().String() + "}:"
	index := prefix + "index"

	_, err := glideft.ClusterFtCreate(
		ctx, client, index,
		[]options.Field{
			options.NewTextField("title"),
			options.NewNumericField("price"),
		},
		&options.FtCreateOptions{DataType: constants.IndexDataTypeHash, Prefixes: []string{prefix}},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	client.HSet(ctx, prefix+"1", map[string]string{"title": "widget", "price": "9"})

	time.Sleep(time.Second)

	rows, err := glideft.ClusterFtAggregate(
		ctx, client, index, "*",
		&options.FtAggregateOptions{
			LoadFields:       []string{"title"},
			LoadFieldEntries: []options.LoadField{{FieldIdentifier: "price", Alias: "cost"}},
		},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	for _, row := range rows {
		fmt.Println(row["title"], row["cost"])
	}

	glideft.ClusterFtDropIndex(ctx, client, index)

	// Output:
	// widget 9
}

func classifyInkeysProbeError(msg string) bool {
	return strings.Contains(msg, "Unexpected") && strings.Contains(msg, "INKEYS")
}

func probeSearch13Capability(ctx context.Context, client *Client) bool {
	probeIndex := "__glide_search_1_3_probe_" + uuid.New().String()
	if _, err := client.CustomCommand(ctx, []string{"FT.CREATE", probeIndex, "SCHEMA", "title", "TEXT"}); err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return false
	}
	defer glideft.FtDropIndex(ctx, client, probeIndex)

	_, err := client.CustomCommand(ctx, []string{"FT.SEARCH", probeIndex, "*", "INKEYS", "0"})
	if err == nil {
		return true
	}
	return !classifyInkeysProbeError(err.Error())
}

func probeSearch13CapabilityCluster(ctx context.Context, client *ClusterClient) bool {
	probeIndex := "__glide_search_1_3_probe_" + uuid.New().String()
	if _, err := client.CustomCommand(ctx, []string{"FT.CREATE", probeIndex, "SCHEMA", "title", "TEXT"}); err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return false
	}
	defer glideft.ClusterFtDropIndex(ctx, client, probeIndex)

	_, err := client.CustomCommand(ctx, []string{"FT.SEARCH", probeIndex, "*", "INKEYS", "0"})
	if err == nil {
		return true
	}
	return !classifyInkeysProbeError(err.Error())
}
