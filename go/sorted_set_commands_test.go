// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"encoding/json"
	"fmt"
	"sort"

	"github.com/valkey-io/valkey-glide/go/v2/constants"

	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func ExampleClient_ZAdd() {
	var client *Client = getExampleGlideClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 3
}

func ExampleClusterClient_ZAdd() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 3
}

func ExampleClient_ZAddWithOptions() {
	var client *Client = getExampleGlideClient() // example helper function

	opts, err := options.NewZAddOptions().SetChanged(true)
	result, err := client.ZAddWithOptions(
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	opts, err := options.NewZAddOptions().SetChanged(true)
	result, err := client.ZAddWithOptions(
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
	var client *Client = getExampleGlideClient() // example helper function

	result, err := client.ZAddIncr("key1", "one", 1.0)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// {1 false}
}

func ExampleClusterClient_ZAddIncr() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.ZAddIncr("key1", "one", 1.0)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// {1 false}
}

func ExampleClient_ZAddIncrWithOptions() {
	var client *Client = getExampleGlideClient() // example helper function

	opts, err := options.NewZAddOptions().SetChanged(true) // should return an error
	result, err := client.ZAddIncrWithOptions("key1", "one", 1.0, *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// Glide example failed with an error:  incr cannot be set when changed is true
	// {0 true}
}

func ExampleClusterClient_ZAddIncrWithOptions() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	opts, err := options.NewZAddOptions().SetChanged(true) // should return an error
	result, err := client.ZAddIncrWithOptions("key1", "one", 1.0, *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// Glide example failed with an error:  incr cannot be set when changed is true
	// {0 true}
}

func ExampleClient_ZIncrBy() {
	var client *Client = getExampleGlideClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZIncrBy("key1", 3.0, "two")
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZIncrBy("key1", 3.0, "two")
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
	var client *Client = getExampleGlideClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZPopMin("key1")
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZPopMin("key1")
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
	var client *Client = getExampleGlideClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	opts := options.NewZPopOptions().SetCount(2)
	result1, err := client.ZPopMinWithOptions("key1", *opts)
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	opts := options.NewZPopOptions().SetCount(2)
	result1, err := client.ZPopMinWithOptions("key1", *opts)
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
	var client *Client = getExampleGlideClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZPopMax("key1")
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZPopMax("key1")
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
	var client *Client = getExampleGlideClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	opts := options.NewZPopOptions().SetCount(2)
	result1, err := client.ZPopMaxWithOptions("key1", *opts)
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	opts := options.NewZPopOptions().SetCount(2)
	result1, err := client.ZPopMaxWithOptions("key1", *opts)
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
	var client *Client = getExampleGlideClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZRem("key1", []string{"one", "two", "nonMember"})
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZRem("key1", []string{"one", "two", "nonMember"})
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
	var client *Client = getExampleGlideClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZCard("key1")
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZCard("key1")
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
	var client *Client = getExampleGlideClient() // example helper function

	zaddResult1, err := client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 1.5})
	zaddResult2, err := client.ZAdd("key2", map[string]float64{"c": 2.0})
	result1, err := client.BZPopMin([]string{"key1", "key2"}, 0.5)
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	zaddResult1, err := client.ZAdd("{key}1", map[string]float64{"a": 1.0, "b": 1.5})
	zaddResult2, err := client.ZAdd("{key}2", map[string]float64{"c": 2.0})
	result1, err := client.BZPopMin([]string{"{key}1", "{key}2"}, 0.5)
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
	var client *Client = getExampleGlideClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZRange("key1", options.NewRangeByIndexQuery(0, -1)) // Ascending order

	// Retrieve members within a score range in descending order
	query := options.NewRangeByScoreQuery(
		options.NewScoreBoundary(3, false),
		options.NewInfiniteScoreBoundary(constants.NegativeInfinity)).SetReverse()
	result2, err := client.ZRange("key1", query)
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZRange("key1", options.NewRangeByIndexQuery(0, -1)) // Ascending order

	// Retrieve members within a score range in descending order
	query := options.NewRangeByScoreQuery(
		options.NewScoreBoundary(3, false),
		options.NewInfiniteScoreBoundary(constants.NegativeInfinity)).SetReverse()
	result2, err := client.ZRange("key1", query)
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
	var client *Client = getExampleGlideClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZRangeWithScores("key1", options.NewRangeByIndexQuery(0, -1))

	query := options.NewRangeByScoreQuery(
		options.NewScoreBoundary(3, false),
		options.NewInfiniteScoreBoundary(constants.NegativeInfinity)).SetReverse()
	result2, err := client.ZRangeWithScores("key1", query)
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZRangeWithScores("key1", options.NewRangeByIndexQuery(0, -1))

	query := options.NewRangeByScoreQuery(
		options.NewScoreBoundary(3, false),
		options.NewInfiniteScoreBoundary(constants.NegativeInfinity)).SetReverse()
	result2, err := client.ZRangeWithScores("key1", query)
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
	var client *Client = getExampleGlideClient() // example helper function

	client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	query := options.NewRangeByScoreQuery(
		options.NewScoreBoundary(3, false),
		options.NewInfiniteScoreBoundary(constants.NegativeInfinity)).SetReverse()
	result, err := client.ZRangeStore("dest", "key1", query)
	// `result` contains members which have scores within the range of negative infinity to 3, in descending order
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleClusterClient_ZRangeStore() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd("{key}1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	query := options.NewRangeByScoreQuery(
		options.NewScoreBoundary(3, false),
		options.NewInfiniteScoreBoundary(constants.NegativeInfinity)).SetReverse()
	result, err := client.ZRangeStore("{key}dest", "{key}1", query)
	// `result` contains members which have scores within the range of negative infinity to 3, in descending order
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleClient_ZRank() {
	var client *Client = getExampleGlideClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZRank("key1", "two")
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZRank("key1", "two")
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
	var client *Client = getExampleGlideClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	resRank, resScore, err := client.ZRankWithScore("key1", "two")
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	resRank, resScore, err := client.ZRankWithScore("key1", "two")
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
	var client *Client = getExampleGlideClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0})
	result1, err := client.ZRevRank("key1", "two")
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0})
	result1, err := client.ZRevRank("key1", "two")
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
	var client *Client = getExampleGlideClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0})
	resRank, resScore, err := client.ZRevRankWithScore("key1", "two")
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0})
	resRank, resScore, err := client.ZRevRankWithScore("key1", "two")
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
	var client *Client = getExampleGlideClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0})
	result1, err := client.ZScore("key1", "three")
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0})
	result1, err := client.ZScore("key1", "three")
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
	var client *Client = getExampleGlideClient() // example helper function

	zCountRange := options.NewZCountRange(
		options.NewInclusiveScoreBoundary(2.0),
		options.NewInfiniteScoreBoundary(constants.PositiveInfinity),
	)
	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0})
	result1, err := client.ZCount("key1", *zCountRange)
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	zCountRange := options.NewZCountRange(
		options.NewInclusiveScoreBoundary(2.0),
		options.NewInfiniteScoreBoundary(constants.PositiveInfinity),
	)
	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0})
	result1, err := client.ZCount("key1", *zCountRange)
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
	var client *Client = getExampleGlideClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0})
	resCursor, resCol, err := client.ZScan("key1", "0")
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0})
	resCursor, resCol, err := client.ZScan("key1", "0")
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
	var client *Client = getExampleGlideClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0})
	resCursor, resCol, err := client.ZScanWithOptions("key1", "0", *options.NewZScanOptions().SetMatch("*"))
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0})
	resCursor, resCol, err := client.ZScanWithOptions("key1", "0", *options.NewZScanOptions().SetMatch("*"))
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
	var client *Client = getExampleGlideClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result1, err := client.ZRemRangeByLex(
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result1, err := client.ZRemRangeByLex(
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
	var client *Client = getExampleGlideClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result1, err := client.ZRemRangeByRank("key1", 1, 3)
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result1, err := client.ZRemRangeByRank("key1", 1, 3)
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
	var client *Client = getExampleGlideClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result1, err := client.ZRemRangeByScore("key1", *options.NewRangeByScoreQuery(
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result1, err := client.ZRemRangeByScore("key1", *options.NewRangeByScoreQuery(
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
	var client *Client = getExampleGlideClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.BZMPop([]string{"key1"}, constants.MAX, float64(0.5))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonSummary, _ := json.Marshal(result.Value())
	fmt.Println(string(jsonSummary))
	// Output: {"Key":"key1","MembersAndScores":[{"Member":"d","Score":4}]}
}

func ExampleClusterClient_BZMPop() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.BZMPop([]string{"key1"}, constants.MAX, float64(0.5))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonSummary, _ := json.Marshal(result.Value())
	fmt.Println(string(jsonSummary))
	// Output: {"Key":"key1","MembersAndScores":[{"Member":"d","Score":4}]}
}

func ExampleClient_BZMPopWithOptions() {
	var client *Client = getExampleGlideClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})

	result, err := client.BZMPopWithOptions([]string{"key1"}, constants.MAX, 0.1, *options.NewZMPopOptions().SetCount(2))
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})

	result, err := client.BZMPopWithOptions([]string{"key1"}, constants.MAX, 0.1, *options.NewZMPopOptions().SetCount(1))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonSummary, _ := json.Marshal(result.Value())
	fmt.Println(string(jsonSummary))

	// Output: {"Key":"key1","MembersAndScores":[{"Member":"d","Score":4}]}
}

func ExampleClient_ZRandMember() {
	var client *Client = getExampleGlideClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.ZRandMember("key2")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result.Value() == "")

	// Output: true
}

func ExampleClusterClient_ZRandMember() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.ZRandMember("key2")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result.Value() == "")

	// Output: true
}

func ExampleClient_ZRandMemberWithCount() {
	var client *Client = getExampleGlideClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.ZRandMemberWithCount("key1", 4)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result)

	// Output: [d c b a]
}

func ExampleClusterClient_ZRandMemberWithCount() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.ZRandMemberWithCount("key1", 4)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result)

	// Output: [d c b a]
}

func ExampleClient_ZRandMemberWithCountWithScores() {
	var client *Client = getExampleGlideClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.ZRandMemberWithCountWithScores("key1", 4)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result)

	// Output: [{d 4} {c 3} {b 2} {a 1}]
}

func ExampleClusterClient_ZRandMemberWithCountWithScores() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.ZRandMemberWithCountWithScores("key1", 4)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result)

	// Output: [{d 4} {c 3} {b 2} {a 1}]
}

func ExampleClient_ZMScore() {
	var client *Client = getExampleGlideClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	result, err := client.ZMScore("key1", []string{"c", "b", "e"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result)

	// Output: [{3 false} {2.5 false} {0 true}]
}

func ExampleClusterClient_ZMScore() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	result, err := client.ZMScore("key1", []string{"c", "b", "e"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result)

	// Output: [{3 false} {2.5 false} {0 true}]
}

func ExampleClient_ZInter() {
	var client *Client = getExampleGlideClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd("key2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})

	result, err := client.ZInter(options.KeyArray{
		Keys: []string{"key1", "key2"},
	})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: [b c d]
}

func ExampleClusterClient_ZInter() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd("{key}1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd("{key}2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})

	result, err := client.ZInter(options.KeyArray{
		Keys: []string{"{key}1", "{key}2"},
	})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: [b c d]
}

func ExampleClient_ZInterWithScores() {
	var client *Client = getExampleGlideClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd("key2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZInterWithScores(
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd("{key}1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd("{key}2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZInterWithScores(
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
	var client *Client = getExampleGlideClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd("key2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZInterStore("dest", options.KeyArray{
		Keys: []string{"key1", "key2"},
	})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 3
}

func ExampleClusterClient_ZInterStore() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd("{key}1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd("{key}2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZInterStore("{key}dest", options.KeyArray{
		Keys: []string{"{key}1", "{key}2"},
	})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 3
}

func ExampleClient_ZInterStoreWithOptions() {
	var client *Client = getExampleGlideClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd("key2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZInterStoreWithOptions(
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd("{key}1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd("{key}2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZInterStoreWithOptions(
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
	var client *Client = getExampleGlideClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd("key2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZDiff([]string{"key1", "key2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: [a]
}

func ExampleClusterClient_ZDiff() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd("{key}1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd("{key}2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZDiff([]string{"{key}1", "{key}2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: [a]
}

func ExampleClient_ZDiffWithScores() {
	var client *Client = getExampleGlideClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd("key2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZDiffWithScores([]string{"key1", "key2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: [{a 1}]
}

func ExampleClusterClient_ZDiffWithScores() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd("{key}1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd("{key}2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZDiffWithScores([]string{"{key}1", "{key}2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: [{a 1}]
}

func ExampleClient_ZDiffStore() {
	var client *Client = getExampleGlideClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd("key2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZDiffStore("dest", []string{"key1", "key2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 1
}

func ExampleClusterClient_ZDiffStore() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd("{key}1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd("{key}2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZDiffStore("{key}dest", []string{"{key}1", "{key}2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 1
}

func ExampleClient_ZUnion() {
	var client *Client = getExampleGlideClient() // example helper function

	memberScoreMap1 := map[string]float64{
		"one": 1.0,
		"two": 2.0,
	}
	memberScoreMap2 := map[string]float64{
		"two":   3.5,
		"three": 3.0,
	}

	client.ZAdd("key1", memberScoreMap1)
	client.ZAdd("key2", memberScoreMap2)

	zUnionResult, _ := client.ZUnion(options.KeyArray{Keys: []string{"key1", "key2"}})
	fmt.Println(zUnionResult)

	// Output:
	// [one three two]
}

func ExampleClusterClient_ZUnion() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	memberScoreMap1 := map[string]float64{
		"one": 1.0,
		"two": 2.0,
	}
	memberScoreMap2 := map[string]float64{
		"two":   3.5,
		"three": 3.0,
	}

	client.ZAdd("{key}1", memberScoreMap1)
	client.ZAdd("{key}2", memberScoreMap2)

	zUnionResult, _ := client.ZUnion(options.KeyArray{Keys: []string{"{key}1", "{key}2"}})
	fmt.Println(zUnionResult)

	// Output:
	// [one three two]
}

func ExampleClient_ZUnionWithScores() {
	var client *Client = getExampleGlideClient() // example helper function

	memberScoreMap1 := map[string]float64{
		"one": 1.0,
		"two": 2.0,
	}
	memberScoreMap2 := map[string]float64{
		"two":   3.5,
		"three": 3.0,
	}

	client.ZAdd("key1", memberScoreMap1)
	client.ZAdd("key2", memberScoreMap2)

	zUnionResult, _ := client.ZUnionWithScores(
		options.KeyArray{Keys: []string{"key1", "key2"}},
		options.NewZUnionOptionsBuilder().SetAggregate(options.AggregateSum),
	)
	fmt.Println(zUnionResult)

	// Output: [{one 1} {three 3} {two 5.5}]
}

func ExampleClusterClient_ZUnionWithScores() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	memberScoreMap1 := map[string]float64{
		"one": 1.0,
		"two": 2.0,
	}
	memberScoreMap2 := map[string]float64{
		"two":   3.5,
		"three": 3.0,
	}

	client.ZAdd("{key}1", memberScoreMap1)
	client.ZAdd("{key}2", memberScoreMap2)

	zUnionResult, _ := client.ZUnionWithScores(
		options.KeyArray{Keys: []string{"{key}1", "{key}2"}},
		options.NewZUnionOptionsBuilder().SetAggregate(options.AggregateSum),
	)
	fmt.Println(zUnionResult)

	// Output: [{one 1} {three 3} {two 5.5}]
}

func ExampleClient_ZUnionStore() {
	var client *Client = getExampleGlideClient() // example helper function

	memberScoreMap1 := map[string]float64{
		"one": 1.0,
		"two": 2.0,
	}
	memberScoreMap2 := map[string]float64{
		"two":   3.5,
		"three": 3.0,
	}

	client.ZAdd("key1", memberScoreMap1)
	client.ZAdd("key2", memberScoreMap2)

	zUnionStoreResult, err := client.ZUnionStore(
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	memberScoreMap1 := map[string]float64{
		"one": 1.0,
		"two": 2.0,
	}
	memberScoreMap2 := map[string]float64{
		"two":   3.5,
		"three": 3.0,
	}

	client.ZAdd("{key}1", memberScoreMap1)
	client.ZAdd("{key}2", memberScoreMap2)

	zUnionStoreResult, err := client.ZUnionStore(
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
	var client *Client = getExampleGlideClient() // example helper function

	memberScoreMap1 := map[string]float64{
		"one": 1.0,
		"two": 2.0,
	}
	memberScoreMap2 := map[string]float64{
		"two":   3.5,
		"three": 3.0,
	}

	client.ZAdd("key1", memberScoreMap1)
	client.ZAdd("key2", memberScoreMap2)

	zUnionStoreWithOptionsResult, err := client.ZUnionStoreWithOptions(
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	memberScoreMap1 := map[string]float64{
		"one": 1.0,
		"two": 2.0,
	}
	memberScoreMap2 := map[string]float64{
		"two":   3.5,
		"three": 3.0,
	}

	client.ZAdd("{key}1", memberScoreMap1)
	client.ZAdd("{key}2", memberScoreMap2)

	zUnionStoreWithOptionsResult, err := client.ZUnionStoreWithOptions(
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
	var client *Client = getExampleGlideClient() // example helper function
	key1 := "{testkey}-1"
	key2 := "{testkey}-2"

	client.ZAdd(key1, map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	client.ZAdd(key2, map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "e": 4.0})

	res, err := client.ZInterCard([]string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(res)

	// Output:
	// 3
}

func ExampleClusterClient_ZInterCard() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	key1 := "{testkey}-1"
	key2 := "{testkey}-2"

	client.ZAdd(key1, map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	client.ZAdd(key2, map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "e": 4.0})

	res, err := client.ZInterCard([]string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(res)

	// Output:
	// 3
}

func ExampleClient_ZInterCardWithOptions() {
	var client *Client = getExampleGlideClient() // example helper function
	key1 := "{testkey}-1"
	key2 := "{testkey}-2"

	client.ZAdd(key1, map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	client.ZAdd(key2, map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "e": 4.0})

	res, err := client.ZInterCardWithOptions(
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	key1 := "{testkey}-1"
	key2 := "{testkey}-2"

	client.ZAdd(key1, map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	client.ZAdd(key2, map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "e": 4.0})

	res, err := client.ZInterCardWithOptions(
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
	var client *Client = getExampleGlideClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})

	result, err := client.ZLexCount("key1",
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})

	result, err := client.ZLexCount("key1",
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
	var client *Client = getExampleGlideClient() // example helper function

	// Add members to the sorted set
	client.ZAdd("mySortedSet", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0})

	// Pop the highest-score member
	res, err := client.BZPopMax([]string{"mySortedSet"}, 1.0)
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
		return
	}

	value := res.Value()
	fmt.Printf("{Key: %q, Member: %q, Score: %.1f}\n", value.Key, value.Member, value.Score)

	// Output: {Key: "mySortedSet", Member: "c", Score: 3.0}
}

func ExampleClusterClient_BZPopMax() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd("{key}SortedSet", map[string]float64{"x": 5.0, "y": 6.0, "z": 7.0})

	res, err := client.BZPopMax([]string{"{key}SortedSet"}, 1.0)
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
		return
	}

	value := res.Value()
	fmt.Printf("{Key: %q, Member: %q, Score: %.1f}\n", value.Key, value.Member, value.Score)

	// Output: {Key: "{key}SortedSet", Member: "z", Score: 7.0}
}

func ExampleClient_ZMPop() {
	var client *Client = getExampleGlideClient() // example helper function

	// Add members to a sorted set
	client.ZAdd("mySortedSet", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0})

	// Pop the lowest-score member
	res, err := client.ZMPop([]string{"mySortedSet"}, constants.MIN)
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	// Add members to a sorted set
	client.ZAdd("{key}sortedSet", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})

	// Pop the lowest-score member
	res, err := client.ZMPop([]string{"{key}sortedSet"}, constants.MIN)
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
	var client *Client = getExampleGlideClient() // example helper function

	client.ZAdd("mySortedSet", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})

	opts := *options.NewZPopOptions().SetCount(2)
	res, err := client.ZMPopWithOptions([]string{"mySortedSet"}, constants.MAX, opts)
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
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd("{key}SortedSet", map[string]float64{"p": 10.0, "q": 20.0, "r": 30.0})

	opts := *options.NewZPopOptions().SetCount(2)
	res, err := client.ZMPopWithOptions([]string{"{key}SortedSet"}, constants.MAX, opts)
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
