// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"fmt"
	"time"

	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func ExampleClusterClient_FT_create() {
	if !*vssTest {
		// Requires a server with the search module loaded. Run with -vss-test to enable.
		fmt.Println("true") // pass the test
		return
	}
	var client *ClusterClient = getExampleClusterClient()

	ft := client.FT()
	ctx := context.Background()

	// Create a vector search index on hash keys with prefix "doc:"
	_, err := ft.Create(ctx, "my_index",
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

	result, err := ft.List(ctx)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(len(result) > 0)

	ft.DropIndex(ctx, "my_index")

	// Output:
	// true
}

func ExampleClusterClient_FT_search() {
	if !*vssTest {
		// Requires a server with the search module loaded. Run with -vss-test to enable.
		fmt.Println(1) // pass the test
		return
	}
	var client *ClusterClient = getExampleClusterClient()

	ft := client.FT()
	ctx := context.Background()

	prefix := "{searchdoc}:"
	index := "{searchdoc}:index"

	_, err := ft.Create(ctx, index,
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

	result, err := ft.Search(ctx, index, "@score:[10 10]", nil)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.TotalResults)

	ft.DropIndex(ctx, index)

	// Output:
	// 1
}

func ExampleClusterClient_FT_aggregate() {
	if !*vssTest {
		// Requires a server with the search module loaded. Run with -vss-test to enable.
		fmt.Println(2) // pass the test
		return
	}
	var client *ClusterClient = getExampleClusterClient()

	ft := client.FT()
	ctx := context.Background()

	prefix := "{aggdoc}:"
	index := "{aggdoc}:index"

	_, err := ft.Create(ctx, index,
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
	rows, err := ft.Aggregate(ctx, index, "@score:[20 +inf]",
		&options.FtAggregateOptions{
			LoadAll: true,
		},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(len(rows))

	ft.DropIndex(ctx, index)

	// Output:
	// 2
}

func ExampleClient_FT_create() {
	if !*vssTest {
		// Requires a server with the search module loaded. Run with -vss-test to enable.
		fmt.Println("true") // pass the test
		return
	}
	var client *Client = getExampleClient()

	ft := client.FT()
	ctx := context.Background()

	// Create a vector search index on hash keys with prefix "doc:"
	_, err := ft.Create(ctx, "my_standalone_index",
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

	result, err := ft.List(ctx)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(len(result) > 0)

	ft.DropIndex(ctx, "my_standalone_index")

	// Output:
	// true
}

func ExampleClient_FT_search() {
	if !*vssTest {
		// Requires a server with the search module loaded. Run with -vss-test to enable.
		fmt.Println(1) // pass the test
		return
	}
	var client *Client = getExampleClient()

	ft := client.FT()
	ctx := context.Background()

	prefix := "searchdoc:"
	index := "searchdoc:index"

	_, err := ft.Create(ctx, index,
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

	result, err := ft.Search(ctx, index, "@score:[10 10]", nil)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.TotalResults)

	ft.DropIndex(ctx, index)

	// Output:
	// 1
}

func ExampleClient_FT_aggregate() {
	if !*vssTest {
		// Requires a server with the search module loaded. Run with -vss-test to enable.
		fmt.Println(2) // pass the test
		return
	}
	var client *Client = getExampleClient()

	ft := client.FT()
	ctx := context.Background()

	prefix := "aggdoc:"
	index := "aggdoc:index"

	_, err := ft.Create(ctx, index,
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
	rows, err := ft.Aggregate(ctx, index, "@score:[20 +inf]",
		&options.FtAggregateOptions{
			LoadAll: true,
		},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(len(rows))

	ft.DropIndex(ctx, index)

	// Output:
	// 2
}
