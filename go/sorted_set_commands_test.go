// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"encoding/json"
	"fmt"
	"sort"

	"github.com/valkey-io/valkey-glide/go/v2/constants"

	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func ExampleClient_ZAdd() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 3
}

func ExampleClusterClient_ZAdd() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 3
}

func ExampleClient_ZAddWithOptions() {
	var client *Client = getExampleClient() // example helper function

	opts, err := options.NewZAddOptions().SetChanged(true)
	result, err := client.ZAddWithOptions(context.Background(),
		"key1",
		map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0},
		*opts,
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 3
}

func ExampleClusterClient_ZAddWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	opts, err := options.NewZAddOptions().SetChanged(true)
	result, err := client.ZAddWithOptions(context.Background(),
		"key1",
		map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0},
		*opts,
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 3
}

func ExampleClient_ZAddIncr() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.ZAddIncr(context.Background(), "key1", "one", 1.0)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// {1 false}
}

func ExampleClusterClient_ZAddIncr() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	result, err := client.ZAddIncr(context.Background(), "key1", "one", 1.0)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// {1 false}
}

func ExampleClient_ZAddIncrWithOptions() {
	var client *Client = getExampleClient() // example helper function

	opts, err := options.NewZAddOptions().SetChanged(true) // should return an error
	result, err := client.ZAddIncrWithOptions(context.Background(), "key1", "one", 1.0, *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// Glide example failed with an error:  incr cannot be set when changed is true
	// {0 true}
}

func ExampleClusterClient_ZAddIncrWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	opts, err := options.NewZAddOptions().SetChanged(true) // should return an error
	result, err := client.ZAddIncrWithOptions(context.Background(), "key1", "one", 1.0, *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// Glide example failed with an error:  incr cannot be set when changed is true
	// {0 true}
}

func ExampleClient_ZIncrBy() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZIncrBy(context.Background(), "key1", 3.0, "two")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 3
	// 5
}

func ExampleClusterClient_ZIncrBy() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZIncrBy(context.Background(), "key1", 3.0, "two")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 3
	// 5
}

func ExampleClient_ZPopMin() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZPopMin(context.Background(), "key1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 3
	// map[one:1]
}

func ExampleClusterClient_ZPopMin() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZPopMin(context.Background(), "key1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 3
	// map[one:1]
}

func ExampleClient_ZPopMinWithOptions() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	opts := options.NewZPopOptions().SetCount(2)
	result1, err := client.ZPopMinWithOptions(context.Background(), "key1", *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 3
	// map[one:1 two:2]
}

func ExampleClusterClient_ZPopMinWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	opts := options.NewZPopOptions().SetCount(2)
	result1, err := client.ZPopMinWithOptions(context.Background(), "key1", *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 3
	// map[one:1 two:2]
}

func ExampleClient_ZPopMax() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZPopMax(context.Background(), "key1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 3
	// map[three:3]
}

func ExampleClusterClient_ZPopMax() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZPopMax(context.Background(), "key1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 3
	// map[three:3]
}

func ExampleClient_ZPopMaxWithOptions() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	opts := options.NewZPopOptions().SetCount(2)
	result1, err := client.ZPopMaxWithOptions(context.Background(), "key1", *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 3
	// map[three:3 two:2]
}

func ExampleClusterClient_ZPopMaxWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	opts := options.NewZPopOptions().SetCount(2)
	result1, err := client.ZPopMaxWithOptions(context.Background(), "key1", *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 3
	// map[three:3 two:2]
}

func ExampleClient_ZRem() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZRem(context.Background(), "key1", []string{"one", "two", "nonMember"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 3
	// 2
}

func ExampleClusterClient_ZRem() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZRem(context.Background(), "key1", []string{"one", "two", "nonMember"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 3
	// 2
}

func ExampleClient_ZCard() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZCard(context.Background(), "key1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 3
	// 3
}

func ExampleClusterClient_ZCard() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZCard(context.Background(), "key1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 3
	// 3
}

func ExampleClient_BZPopMin() {
	var client *Client = getExampleClient() // example helper function

	zaddResult1, err := client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 1.5})
	zaddResult2, err := client.ZAdd(context.Background(), "key2", map[string]float64{"c": 2.0})
	result1, err := client.BZPopMin(context.Background(), []string{"key1", "key2"}, 0.5)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(zaddResult1)
	fmt.Println(zaddResult2)
	fmt.Println(result1)

	// Output:
	// 2
	// 1
	// {{key1 a 1} false}
}

func ExampleClusterClient_BZPopMin() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	zaddResult1, err := client.ZAdd(context.Background(), "{key}1", map[string]float64{"a": 1.0, "b": 1.5})
	zaddResult2, err := client.ZAdd(context.Background(), "{key}2", map[string]float64{"c": 2.0})
	result1, err := client.BZPopMin(context.Background(), []string{"{key}1", "{key}2"}, 0.5)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(zaddResult1)
	fmt.Println(zaddResult2)
	fmt.Println(result1)

	// Output:
	// 2
	// 1
	// {{{key}1 a 1} false}
}

func ExampleClient_ZRange() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZRange(context.Background(), "key1", options.NewRangeByIndexQuery(0, -1)) // Ascending order

	// Retrieve members within a score range in descending order
	query := options.NewRangeByScoreQuery(
		options.NewScoreBoundary(3, false),
		options.NewInfiniteScoreBoundary(constants.NegativeInfinity)).SetReverse()
	result2, err := client.ZRange(context.Background(), "key1", query)
	// `result` contains members which have scores within the range of negative infinity to 3, in descending order
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// 3
	// [one two three]
	// [two one]
}

func ExampleClusterClient_ZRange() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZRange(context.Background(), "key1", options.NewRangeByIndexQuery(0, -1)) // Ascending order

	// Retrieve members within a score range in descending order
	query := options.NewRangeByScoreQuery(
		options.NewScoreBoundary(3, false),
		options.NewInfiniteScoreBoundary(constants.NegativeInfinity)).SetReverse()
	result2, err := client.ZRange(context.Background(), "key1", query)
	// `result` contains members which have scores within the range of negative infinity to 3, in descending order
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// 3
	// [one two three]
	// [two one]
}

func ExampleClient_ZRangeWithScores() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZRangeWithScores(context.Background(), "key1", options.NewRangeByIndexQuery(0, -1))

	query := options.NewRangeByScoreQuery(
		options.NewScoreBoundary(3, false),
		options.NewInfiniteScoreBoundary(constants.NegativeInfinity)).SetReverse()
	result2, err := client.ZRangeWithScores(context.Background(), "key1", query)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// 3
	// [{one 1} {two 2} {three 3}]
	// [{two 2} {one 1}]
}

func ExampleClusterClient_ZRangeWithScores() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZRangeWithScores(context.Background(), "key1", options.NewRangeByIndexQuery(0, -1))

	query := options.NewRangeByScoreQuery(
		options.NewScoreBoundary(3, false),
		options.NewInfiniteScoreBoundary(constants.NegativeInfinity)).SetReverse()
	result2, err := client.ZRangeWithScores(context.Background(), "key1", query)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// 3
	// [{one 1} {two 2} {three 3}]
	// [{two 2} {one 1}]
}

func ExampleClient_ZRangeStore() {
	var client *Client = getExampleClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	query := options.NewRangeByScoreQuery(
		options.NewScoreBoundary(3, false),
		options.NewInfiniteScoreBoundary(constants.NegativeInfinity)).SetReverse()
	result, err := client.ZRangeStore(context.Background(), "dest", "key1", query)
	// `result` contains members which have scores within the range of negative infinity to 3, in descending order
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleClusterClient_ZRangeStore() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.ZAdd(context.Background(), "{key}1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	query := options.NewRangeByScoreQuery(
		options.NewScoreBoundary(3, false),
		options.NewInfiniteScoreBoundary(constants.NegativeInfinity)).SetReverse()
	result, err := client.ZRangeStore(context.Background(), "{key}dest", "{key}1", query)
	// `result` contains members which have scores within the range of negative infinity to 3, in descending order
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleClient_ZRank() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZRank(context.Background(), "key1", "two")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 3
	// {1 false}
}

func ExampleClusterClient_ZRank() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZRank(context.Background(), "key1", "two")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 3
	// {1 false}
}

func ExampleClient_ZRankWithScore() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	resRank, resScore, err := client.ZRankWithScore(context.Background(), "key1", "two")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(resRank)
	fmt.Println(resScore)

	// Output:
	// 3
	// {1 false}
	// {2 false}
}

func ExampleClusterClient_ZRankWithScore() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	resRank, resScore, err := client.ZRankWithScore(context.Background(), "key1", "two")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(resRank)
	fmt.Println(resScore)

	// Output:
	// 3
	// {1 false}
	// {2 false}
}

func ExampleClient_ZRevRank() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.ZAdd(
		context.Background(),
		"key1",
		map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0},
	)
	result1, err := client.ZRevRank(context.Background(), "key1", "two")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 4
	// {2 false}
}

func ExampleClusterClient_ZRevRank() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	result, err := client.ZAdd(
		context.Background(),
		"key1",
		map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0},
	)
	result1, err := client.ZRevRank(context.Background(), "key1", "two")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 4
	// {2 false}
}

func ExampleClient_ZRevRankWithScore() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.ZAdd(
		context.Background(),
		"key1",
		map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0},
	)
	resRank, resScore, err := client.ZRevRankWithScore(context.Background(), "key1", "two")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(resRank)
	fmt.Println(resScore)

	// Output:
	// 4
	// {2 false}
	// {2 false}
}

func ExampleClusterClient_ZRevRankWithScore() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	result, err := client.ZAdd(
		context.Background(),
		"key1",
		map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0},
	)
	resRank, resScore, err := client.ZRevRankWithScore(context.Background(), "key1", "two")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(resRank)
	fmt.Println(resScore)

	// Output:
	// 4
	// {2 false}
	// {2 false}
}

func ExampleClient_ZScore() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.ZAdd(
		context.Background(),
		"key1",
		map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0},
	)
	result1, err := client.ZScore(context.Background(), "key1", "three")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 4
	// {3 false}
}

func ExampleClusterClient_ZScore() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	result, err := client.ZAdd(
		context.Background(),
		"key1",
		map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0},
	)
	result1, err := client.ZScore(context.Background(), "key1", "three")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 4
	// {3 false}
}

func ExampleClient_ZCount() {
	var client *Client = getExampleClient() // example helper function

	zCountRange := options.NewZCountRange(
		options.NewInclusiveScoreBoundary(2.0),
		options.NewInfiniteScoreBoundary(constants.PositiveInfinity),
	)
	result, err := client.ZAdd(
		context.Background(),
		"key1",
		map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0},
	)
	result1, err := client.ZCount(context.Background(), "key1", *zCountRange)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 4
	// 3
}

func ExampleClusterClient_ZCount() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	zCountRange := options.NewZCountRange(
		options.NewInclusiveScoreBoundary(2.0),
		options.NewInfiniteScoreBoundary(constants.PositiveInfinity),
	)
	result, err := client.ZAdd(
		context.Background(),
		"key1",
		map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0},
	)
	result1, err := client.ZCount(context.Background(), "key1", *zCountRange)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 4
	// 3
}

func ExampleClient_ZScan() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.ZAdd(
		context.Background(),
		"key1",
		map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0},
	)
	resCursor, resCol, err := client.ZScan(context.Background(), "key1", "0")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(resCursor)
	fmt.Println(resCol)

	// Output:
	// 4
	// 0
	// [one 1 two 2 three 3 four 4]
}

func ExampleClusterClient_ZScan() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	result, err := client.ZAdd(
		context.Background(),
		"key1",
		map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0},
	)
	resCursor, resCol, err := client.ZScan(context.Background(), "key1", "0")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(resCursor)
	fmt.Println(resCol)

	// Output:
	// 4
	// 0
	// [one 1 two 2 three 3 four 4]
}

func ExampleClient_ZScanWithOptions() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.ZAdd(
		context.Background(),
		"key1",
		map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0},
	)
	resCursor, resCol, err := client.ZScanWithOptions(
		context.Background(),
		"key1",
		"0",
		*options.NewZScanOptions().SetMatch("*"),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(resCursor)
	fmt.Println(resCol)

	// Output:
	// 4
	// 0
	// [one 1 two 2 three 3 four 4]
}

func ExampleClusterClient_ZScanWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	result, err := client.ZAdd(
		context.Background(),
		"key1",
		map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0},
	)
	resCursor, resCol, err := client.ZScanWithOptions(
		context.Background(),
		"key1",
		"0",
		*options.NewZScanOptions().SetMatch("*"),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(resCursor)
	fmt.Println(resCol)

	// Output:
	// 4
	// 0
	// [one 1 two 2 three 3 four 4]
}

func ExampleClient_ZRemRangeByLex() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result1, err := client.ZRemRangeByLex(context.Background(),
		"key1",
		*options.NewRangeByLexQuery(options.NewLexBoundary("a", false), options.NewLexBoundary("c", true)),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 4
	// 2
}

func ExampleClusterClient_ZRemRangeByLex() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result1, err := client.ZRemRangeByLex(context.Background(),
		"key1",
		*options.NewRangeByLexQuery(options.NewLexBoundary("a", false), options.NewLexBoundary("c", true)),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 4
	// 2
}

func ExampleClient_ZRemRangeByRank() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result1, err := client.ZRemRangeByRank(context.Background(), "key1", 1, 3)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 4
	// 3
}

func ExampleClusterClient_ZRemRangeByRank() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result1, err := client.ZRemRangeByRank(context.Background(), "key1", 1, 3)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 4
	// 3
}

func ExampleClient_ZRemRangeByScore() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result1, err := client.ZRemRangeByScore(context.Background(), "key1", *options.NewRangeByScoreQuery(
		options.NewInfiniteScoreBoundary(constants.NegativeInfinity),
		options.NewInfiniteScoreBoundary(constants.PositiveInfinity),
	))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 4
	// 4
}

func ExampleClusterClient_ZRemRangeByScore() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result1, err := client.ZRemRangeByScore(context.Background(), "key1", *options.NewRangeByScoreQuery(
		options.NewInfiniteScoreBoundary(constants.NegativeInfinity),
		options.NewInfiniteScoreBoundary(constants.PositiveInfinity),
	))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 4
	// 4
}

func ExampleClient_BZMPop() {
	var client *Client = getExampleClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.BZMPop(context.Background(), []string{"key1"}, constants.MAX, float64(0.5))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonSummary, _ := json.Marshal(result.Value())
	fmt.Println(string(jsonSummary))
	// Output: {"Key":"key1","MembersAndScores":[{"Member":"d","Score":4}]}
}

func ExampleClusterClient_BZMPop() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.BZMPop(context.Background(), []string{"key1"}, constants.MAX, float64(0.5))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonSummary, _ := json.Marshal(result.Value())
	fmt.Println(string(jsonSummary))
	// Output: {"Key":"key1","MembersAndScores":[{"Member":"d","Score":4}]}
}

func ExampleClient_BZMPopWithOptions() {
	var client *Client = getExampleClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})

	result, err := client.BZMPopWithOptions(
		context.Background(),
		[]string{"key1"},
		constants.MAX,
		0.1,
		*options.NewZMPopOptions().SetCount(2),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	kms := models.KeyWithArrayOfMembersAndScores{
		Key: "key1",
		MembersAndScores: []models.MemberAndScore{
			{Member: "d", Score: 4},
			{Member: "c", Score: 3},
		},
	}
	fmt.Println(kms.Key == result.Value().Key)
	// isEqual := CompareUnorderedSlices[models.MemberAndScore](
	// 	kms.MembersAndScores,
	// 	result.Value().MembersAndScores,
	// ) // helper function for comparing arrays and slices
	// fmt.Println(isEqual)

	// Output:
	// true
}

func ExampleClusterClient_BZMPopWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})

	result, err := client.BZMPopWithOptions(
		context.Background(),
		[]string{"key1"},
		constants.MAX,
		0.1,
		*options.NewZMPopOptions().SetCount(1),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonSummary, _ := json.Marshal(result.Value())
	fmt.Println(string(jsonSummary))

	// Output: {"Key":"key1","MembersAndScores":[{"Member":"d","Score":4}]}
}

func ExampleClient_ZRandMember() {
	var client *Client = getExampleClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.ZRandMember(context.Background(), "key2")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result.Value() == "")

	// Output: true
}

func ExampleClusterClient_ZRandMember() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.ZRandMember(context.Background(), "key2")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result.Value() == "")

	// Output: true
}

func ExampleClient_ZRandMemberWithCount() {
	var client *Client = getExampleClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.ZRandMemberWithCount(context.Background(), "key1", 4)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result)

	// Output: [d c b a]
}

func ExampleClusterClient_ZRandMemberWithCount() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.ZRandMemberWithCount(context.Background(), "key1", 4)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result)

	// Output: [d c b a]
}

func ExampleClient_ZRandMemberWithCountWithScores() {
	var client *Client = getExampleClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.ZRandMemberWithCountWithScores(context.Background(), "key1", 4)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result)

	// Output: [{d 4} {c 3} {b 2} {a 1}]
}

func ExampleClusterClient_ZRandMemberWithCountWithScores() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.ZRandMemberWithCountWithScores(context.Background(), "key1", 4)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result)

	// Output: [{d 4} {c 3} {b 2} {a 1}]
}

func ExampleClient_ZMScore() {
	var client *Client = getExampleClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	result, err := client.ZMScore(context.Background(), "key1", []string{"c", "b", "e"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result)

	// Output: [{3 false} {2.5 false} {0 true}]
}

func ExampleClusterClient_ZMScore() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	result, err := client.ZMScore(context.Background(), "key1", []string{"c", "b", "e"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result)

	// Output: [{3 false} {2.5 false} {0 true}]
}

func ExampleClient_ZInter() {
	var client *Client = getExampleClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd(context.Background(), "key2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})

	result, err := client.ZInter(context.Background(), options.KeyArray{
		Keys: []string{"key1", "key2"},
	})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: [b c d]
}

func ExampleClusterClient_ZInter() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.ZAdd(context.Background(), "{key}1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd(context.Background(), "{key}2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})

	result, err := client.ZInter(context.Background(), options.KeyArray{
		Keys: []string{"{key}1", "{key}2"},
	})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: [b c d]
}

func ExampleClient_ZInterWithScores() {
	var client *Client = getExampleClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd(context.Background(), "key2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZInterWithScores(context.Background(),
		options.KeyArray{
			Keys: []string{"key1", "key2"},
		},
		*options.NewZInterOptions().SetAggregate(options.AggregateSum),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: [{b 3.5} {c 5.5} {d 7}]
}

func ExampleClusterClient_ZInterWithScores() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.ZAdd(context.Background(), "{key}1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd(context.Background(), "{key}2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZInterWithScores(context.Background(),
		options.KeyArray{
			Keys: []string{"{key}1", "{key}2"},
		},
		*options.NewZInterOptions().SetAggregate(options.AggregateSum),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: [{b 3.5} {c 5.5} {d 7}]
}

func ExampleClient_ZInterStore() {
	var client *Client = getExampleClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd(context.Background(), "key2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZInterStore(context.Background(), "dest", options.KeyArray{
		Keys: []string{"key1", "key2"},
	})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 3
}

func ExampleClusterClient_ZInterStore() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.ZAdd(context.Background(), "{key}1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd(context.Background(), "{key}2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZInterStore(context.Background(), "{key}dest", options.KeyArray{
		Keys: []string{"{key}1", "{key}2"},
	})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 3
}

func ExampleClient_ZInterStoreWithOptions() {
	var client *Client = getExampleClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd(context.Background(), "key2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZInterStoreWithOptions(context.Background(),
		"dest",
		options.KeyArray{
			Keys: []string{"key1", "key2"},
		},
		*options.NewZInterOptions().SetAggregate(options.AggregateSum),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 3
}

func ExampleClusterClient_ZInterStoreWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.ZAdd(context.Background(), "{key}1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd(context.Background(), "{key}2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZInterStoreWithOptions(context.Background(),
		"{key}dest",
		options.KeyArray{
			Keys: []string{"{key}1", "{key}2"},
		},
		*options.NewZInterOptions().SetAggregate(options.AggregateSum),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 3
}

func ExampleClient_ZDiff() {
	var client *Client = getExampleClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd(context.Background(), "key2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZDiff(context.Background(), []string{"key1", "key2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: [a]
}

func ExampleClusterClient_ZDiff() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.ZAdd(context.Background(), "{key}1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd(context.Background(), "{key}2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZDiff(context.Background(), []string{"{key}1", "{key}2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: [a]
}

func ExampleClient_ZDiffWithScores() {
	var client *Client = getExampleClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd(context.Background(), "key2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZDiffWithScores(context.Background(), []string{"key1", "key2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: [{a 1}]
}

func ExampleClusterClient_ZDiffWithScores() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.ZAdd(context.Background(), "{key}1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd(context.Background(), "{key}2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZDiffWithScores(context.Background(), []string{"{key}1", "{key}2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: [{a 1}]
}

func ExampleClient_ZDiffStore() {
	var client *Client = getExampleClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd(context.Background(), "key2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZDiffStore(context.Background(), "dest", []string{"key1", "key2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 1
}

func ExampleClusterClient_ZDiffStore() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.ZAdd(context.Background(), "{key}1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd(context.Background(), "{key}2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZDiffStore(context.Background(), "{key}dest", []string{"{key}1", "{key}2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 1
}

func ExampleClient_ZUnion() {
	var client *Client = getExampleClient() // example helper function

	memberScoreMap1 := map[string]float64{
		"one": 1.0,
		"two": 2.0,
	}
	memberScoreMap2 := map[string]float64{
		"two":   3.5,
		"three": 3.0,
	}

	client.ZAdd(context.Background(), "key1", memberScoreMap1)
	client.ZAdd(context.Background(), "key2", memberScoreMap2)

	zUnionResult, _ := client.ZUnion(context.Background(), options.KeyArray{Keys: []string{"key1", "key2"}})
	fmt.Println(zUnionResult)

	// Output:
	// [one three two]
}

func ExampleClusterClient_ZUnion() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	memberScoreMap1 := map[string]float64{
		"one": 1.0,
		"two": 2.0,
	}
	memberScoreMap2 := map[string]float64{
		"two":   3.5,
		"three": 3.0,
	}

	client.ZAdd(context.Background(), "{key}1", memberScoreMap1)
	client.ZAdd(context.Background(), "{key}2", memberScoreMap2)

	zUnionResult, _ := client.ZUnion(context.Background(), options.KeyArray{Keys: []string{"{key}1", "{key}2"}})
	fmt.Println(zUnionResult)

	// Output:
	// [one three two]
}

func ExampleClient_ZUnionWithScores() {
	var client *Client = getExampleClient() // example helper function

	memberScoreMap1 := map[string]float64{
		"one": 1.0,
		"two": 2.0,
	}
	memberScoreMap2 := map[string]float64{
		"two":   3.5,
		"three": 3.0,
	}

	client.ZAdd(context.Background(), "key1", memberScoreMap1)
	client.ZAdd(context.Background(), "key2", memberScoreMap2)

	zUnionResult, _ := client.ZUnionWithScores(context.Background(),
		options.KeyArray{Keys: []string{"key1", "key2"}},
		options.NewZUnionOptionsBuilder().SetAggregate(options.AggregateSum),
	)
	fmt.Println(zUnionResult)

	// Output: [{one 1} {three 3} {two 5.5}]
}

func ExampleClusterClient_ZUnionWithScores() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	memberScoreMap1 := map[string]float64{
		"one": 1.0,
		"two": 2.0,
	}
	memberScoreMap2 := map[string]float64{
		"two":   3.5,
		"three": 3.0,
	}

	client.ZAdd(context.Background(), "{key}1", memberScoreMap1)
	client.ZAdd(context.Background(), "{key}2", memberScoreMap2)

	zUnionResult, _ := client.ZUnionWithScores(context.Background(),
		options.KeyArray{Keys: []string{"{key}1", "{key}2"}},
		options.NewZUnionOptionsBuilder().SetAggregate(options.AggregateSum),
	)
	fmt.Println(zUnionResult)

	// Output: [{one 1} {three 3} {two 5.5}]
}

func ExampleClient_ZUnionStore() {
	var client *Client = getExampleClient() // example helper function

	memberScoreMap1 := map[string]float64{
		"one": 1.0,
		"two": 2.0,
	}
	memberScoreMap2 := map[string]float64{
		"two":   3.5,
		"three": 3.0,
	}

	client.ZAdd(context.Background(), "key1", memberScoreMap1)
	client.ZAdd(context.Background(), "key2", memberScoreMap2)

	zUnionStoreResult, err := client.ZUnionStore(context.Background(),
		"dest",
		options.KeyArray{Keys: []string{"key1", "key2"}},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(zUnionStoreResult)

	// Output: 3
}

func ExampleClusterClient_ZUnionStore() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	memberScoreMap1 := map[string]float64{
		"one": 1.0,
		"two": 2.0,
	}
	memberScoreMap2 := map[string]float64{
		"two":   3.5,
		"three": 3.0,
	}

	client.ZAdd(context.Background(), "{key}1", memberScoreMap1)
	client.ZAdd(context.Background(), "{key}2", memberScoreMap2)

	zUnionStoreResult, err := client.ZUnionStore(context.Background(),
		"{key}dest",
		options.KeyArray{Keys: []string{"{key}1", "{key}2"}},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(zUnionStoreResult)

	// Output: 3
}

func ExampleClient_ZUnionStoreWithOptions() {
	var client *Client = getExampleClient() // example helper function

	memberScoreMap1 := map[string]float64{
		"one": 1.0,
		"two": 2.0,
	}
	memberScoreMap2 := map[string]float64{
		"two":   3.5,
		"three": 3.0,
	}

	client.ZAdd(context.Background(), "key1", memberScoreMap1)
	client.ZAdd(context.Background(), "key2", memberScoreMap2)

	zUnionStoreWithOptionsResult, err := client.ZUnionStoreWithOptions(context.Background(),
		"dest",
		options.KeyArray{Keys: []string{"key1", "key2"}},
		options.NewZUnionOptionsBuilder().SetAggregate(options.AggregateSum),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(zUnionStoreWithOptionsResult)

	// Output: 3
}

func ExampleClusterClient_ZUnionStoreWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	memberScoreMap1 := map[string]float64{
		"one": 1.0,
		"two": 2.0,
	}
	memberScoreMap2 := map[string]float64{
		"two":   3.5,
		"three": 3.0,
	}

	client.ZAdd(context.Background(), "{key}1", memberScoreMap1)
	client.ZAdd(context.Background(), "{key}2", memberScoreMap2)

	zUnionStoreWithOptionsResult, err := client.ZUnionStoreWithOptions(context.Background(),
		"{key}dest",
		options.KeyArray{Keys: []string{"{key}1", "{key}2"}},
		options.NewZUnionOptionsBuilder().SetAggregate(options.AggregateSum),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(zUnionStoreWithOptionsResult)

	// Output: 3
}

func ExampleClient_ZInterCard() {
	var client *Client = getExampleClient() // example helper function
	key1 := "{testkey}-1"
	key2 := "{testkey}-2"

	client.ZAdd(context.Background(), key1, map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	client.ZAdd(context.Background(), key2, map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "e": 4.0})

	res, err := client.ZInterCard(context.Background(), []string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(res)

	// Output:
	// 3
}

func ExampleClusterClient_ZInterCard() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key1 := "{testkey}-1"
	key2 := "{testkey}-2"

	client.ZAdd(context.Background(), key1, map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	client.ZAdd(context.Background(), key2, map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "e": 4.0})

	res, err := client.ZInterCard(context.Background(), []string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(res)

	// Output:
	// 3
}

func ExampleClient_ZInterCardWithOptions() {
	var client *Client = getExampleClient() // example helper function
	key1 := "{testkey}-1"
	key2 := "{testkey}-2"

	client.ZAdd(context.Background(), key1, map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	client.ZAdd(context.Background(), key2, map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "e": 4.0})

	res, err := client.ZInterCardWithOptions(context.Background(),
		[]string{key1, key2},
		options.NewZInterCardOptions().SetLimit(5),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(res)

	// Output:
	// 3
}

func ExampleClusterClient_ZInterCardWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key1 := "{testkey}-1"
	key2 := "{testkey}-2"

	client.ZAdd(context.Background(), key1, map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	client.ZAdd(context.Background(), key2, map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "e": 4.0})

	res, err := client.ZInterCardWithOptions(context.Background(),
		[]string{key1, key2},
		options.NewZInterCardOptions().SetLimit(5),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(res)

	// Output:
	// 3
}

func ExampleClient_ZLexCount() {
	var client *Client = getExampleClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})

	result, err := client.ZLexCount(context.Background(), "key1",
		options.NewRangeByLexQuery(
			options.NewLexBoundary("a", false),
			options.NewLexBoundary("c", true),
		),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// 2
}

func ExampleClusterClient_ZLexCount() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})

	result, err := client.ZLexCount(context.Background(), "key1",
		options.NewRangeByLexQuery(
			options.NewLexBoundary("a", false),
			options.NewLexBoundary("c", true),
		),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// 2
}

func ExampleClient_BZPopMax() {
	var client *Client = getExampleClient() // example helper function

	// Add members to the sorted set
	client.ZAdd(context.Background(), "mySortedSet", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0})

	// Pop the highest-score member
	res, err := client.BZPopMax(context.Background(), []string{"mySortedSet"}, 1.0)
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
		return
	}

	value := res.Value()
	fmt.Printf("{Key: %q, Member: %q, Score: %.1f}\n", value.Key, value.Member, value.Score)

	// Output: {Key: "mySortedSet", Member: "c", Score: 3.0}
}

func ExampleClusterClient_BZPopMax() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.ZAdd(context.Background(), "{key}SortedSet", map[string]float64{"x": 5.0, "y": 6.0, "z": 7.0})

	res, err := client.BZPopMax(context.Background(), []string{"{key}SortedSet"}, 1.0)
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
		return
	}

	value := res.Value()
	fmt.Printf("{Key: %q, Member: %q, Score: %.1f}\n", value.Key, value.Member, value.Score)

	// Output: {Key: "{key}SortedSet", Member: "z", Score: 7.0}
}

func ExampleClient_ZMPop() {
	var client *Client = getExampleClient() // example helper function

	// Add members to a sorted set
	client.ZAdd(context.Background(), "mySortedSet", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0})

	// Pop the lowest-score member
	res, err := client.ZMPop(context.Background(), []string{"mySortedSet"}, constants.MIN)
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
		return
	}

	value := res.Value()
	fmt.Printf("{Key: %q, MembersAndScores: [", value.Key)
	for i, member := range value.MembersAndScores {
		if i > 0 {
			fmt.Print(", ")
		}
		fmt.Printf("{Member: %q, Score: %.1f}", member.Member, member.Score)
	}
	fmt.Println("]}")

	// Output: {Key: "mySortedSet", MembersAndScores: [{Member: "a", Score: 1.0}]}
}

func ExampleClusterClient_ZMPop() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	// Add members to a sorted set
	client.ZAdd(context.Background(), "{key}sortedSet", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})

	// Pop the lowest-score member
	res, err := client.ZMPop(context.Background(), []string{"{key}sortedSet"}, constants.MIN)
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
		return
	}

	value := res.Value()
	fmt.Printf("{Key: %q, MembersAndScores: [", value.Key)
	for i, member := range value.MembersAndScores {
		if i > 0 {
			fmt.Print(", ")
		}
		fmt.Printf("{Member: %q, Score: %.1f}", member.Member, member.Score)
	}
	fmt.Println("]}")

	// Output: {Key: "{key}sortedSet", MembersAndScores: [{Member: "one", Score: 1.0}]}
}

func ExampleClient_ZMPopWithOptions() {
	var client *Client = getExampleClient() // example helper function

	client.ZAdd(context.Background(), "mySortedSet", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})

	opts := *options.NewZMPopOptions().SetCount(2)
	res, err := client.ZMPopWithOptions(context.Background(), []string{"mySortedSet"}, constants.MAX, opts)
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
		return
	}

	value := res.Value()

	sort.Slice(value.MembersAndScores, func(i, j int) bool {
		return value.MembersAndScores[i].Score > value.MembersAndScores[j].Score
	})

	fmt.Printf("{Key: %q, MembersAndScores: [", value.Key)
	for i, member := range value.MembersAndScores {
		if i > 0 {
			fmt.Print(", ")
		}
		fmt.Printf("{Member: %q, Score: %.1f}", member.Member, member.Score)
	}
	fmt.Println("]}")

	// Output: {Key: "mySortedSet", MembersAndScores: [{Member: "d", Score: 4.0}, {Member: "c", Score: 3.0}]}
}

func ExampleClusterClient_ZMPopWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.ZAdd(context.Background(), "{key}SortedSet", map[string]float64{"p": 10.0, "q": 20.0, "r": 30.0})

	opts := *options.NewZMPopOptions().SetCount(2)
	res, err := client.ZMPopWithOptions(context.Background(), []string{"{key}SortedSet"}, constants.MAX, opts)
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
		return
	}

	value := res.Value()

	// Ensure sorting of results for deterministic comparison
	sort.Slice(value.MembersAndScores, func(i, j int) bool {
		return value.MembersAndScores[i].Score > value.MembersAndScores[j].Score
	})

	fmt.Printf("{Key: %q, MembersAndScores: [", value.Key)
	for i, member := range value.MembersAndScores {
		if i > 0 {
			fmt.Print(", ")
		}
		fmt.Printf("{Member: %q, Score: %.1f}", member.Member, member.Score)
	}
	fmt.Println("]}")

	// Output: {Key: "{key}SortedSet", MembersAndScores: [{Member: "r", Score: 30.0}, {Member: "q", Score: 20.0}]}
}
