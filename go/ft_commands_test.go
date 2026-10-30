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

// Example_clusterFtSearchInKeys exercises the valkey-search 1.3 INKEYS option: absent (no
// restriction), an explicit empty slice (INKEYS 0, excludes every result), and a list
// (restricts to just those keys).
func Example_clusterFtSearchInKeys() {
	if !*vssTest {
		// Requires a search 1.3-capable server. Run with -vss-test to enable.
		fmt.Println("3 0 2")
		return
	}
	var client *ClusterClient = getExampleClusterClient()
	ctx := context.Background()

	if !probeSearch13CapabilityCluster(ctx, client) {
		// INKEYS is a valkey-search 1.3 addition; skip gracefully against an older server.
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

// Example_ftSearchCount exercises the valkey-search 1.3 Count option, which serializes to
// LIMIT 0 0 -- a bare COUNT token is not a real FT.SEARCH keyword.
func Example_ftSearchCount() {
	if !*vssTest {
		// Requires a search 1.3-capable server. Run with -vss-test to enable.
		fmt.Println(2)
		return
	}
	var client *Client = getExampleClient()
	ctx := context.Background()

	if !probeSearch13Capability(ctx, client) {
		// Count is a valkey-search 1.3 addition; skip gracefully against an older server.
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

// Example_ftCreateFloat16Vector exercises the valkey-search 1.3 FLOAT16/BFLOAT16 vector data
// types, replacing the hard-coded FLOAT32 default.
func Example_ftCreateFloat16Vector() {
	if !*vssTest {
		// Requires a search 1.3-capable server. Run with -vss-test to enable.
		fmt.Println("OK")
		return
	}
	var client *Client = getExampleClient()
	ctx := context.Background()

	if !probeSearch13Capability(ctx, client) {
		// FLOAT16/BFLOAT16 vector types are a valkey-search 1.3 addition; skip gracefully
		// against an older server.
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

// Example_ftCreateFilterScoreFieldNoHighlight exercises the valkey-search 1.3 FT.CREATE FILTER,
// SCORE_FIELD and NOHL options.
func Example_ftCreateFilterScoreFieldNoHighlight() {
	if !*vssTest {
		// Requires a search 1.3-capable server. Run with -vss-test to enable.
		fmt.Println("OK")
		return
	}
	var client *Client = getExampleClient()
	ctx := context.Background()

	if !probeSearch13Capability(ctx, client) {
		// FILTER/SCORE_FIELD/NOHL are a valkey-search 1.3 addition; skip gracefully against an
		// older server.
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

// classifyInkeysProbeError reports whether err came from a server that does not recognise
// FT.SEARCH's 1.3-only INKEYS option, as opposed to some other failure.
func classifyInkeysProbeError(msg string) bool {
	return strings.Contains(msg, "Unexpected") && strings.Contains(msg, "INKEYS")
}

// probeSearch13Capability probes a connected standalone client for valkey-search 1.3
// capability using a real command plus a 1.3-only argument (INKEYS), classified by error text
// rather than a version string. Go's Example_ functions can't call t.Skip(), so callers check
// the returned bool themselves and print whatever their own doc comment's "// Output:" expects
// before returning early, rather than skipping the example outright.
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

// probeSearch13CapabilityCluster is probeSearch13Capability for a cluster client.
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
