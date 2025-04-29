// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"encoding/json"
	"fmt"

	"github.com/valkey-io/valkey-glide/go/api/options"
)

func ExampleGlideClient_ZAdd() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 3
}

func ExampleGlideClusterClient_ZAdd() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 3
}

func ExampleGlideClient_ZAddWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZAddWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZAddIncr() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	result, err := client.ZAddIncr("key1", "one", 1.0)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// {1 false}
}

func ExampleGlideClusterClient_ZAddIncr() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.ZAddIncr("key1", "one", 1.0)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// {1 false}
}

func ExampleGlideClient_ZAddIncrWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZAddIncrWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZIncrBy() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZIncrBy() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZPopMin() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZPopMin() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZPopMinWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZPopMinWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZPopMax() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZPopMax() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZPopMaxWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZPopMaxWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZRem() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZRem() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZCard() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZCard() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_BZPopMin() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_BZPopMin() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZRange() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZRange("key1", options.NewRangeByIndexQuery(0, -1)) // Ascending order

	// Retrieve members within a score range in descending order
	query := options.NewRangeByScoreQuery(
		options.NewScoreBoundary(3, false),
		options.NewInfiniteScoreBoundary(options.NegativeInfinity)).SetReverse()
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

func ExampleGlideClusterClient_ZRange() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZRange("key1", options.NewRangeByIndexQuery(0, -1)) // Ascending order

	// Retrieve members within a score range in descending order
	query := options.NewRangeByScoreQuery(
		options.NewScoreBoundary(3, false),
		options.NewInfiniteScoreBoundary(options.NegativeInfinity)).SetReverse()
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

func ExampleGlideClient_ZRangeWithScores() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZRangeWithScores("key1", options.NewRangeByIndexQuery(0, -1))

	query := options.NewRangeByScoreQuery(
		options.NewScoreBoundary(3, false),
		options.NewInfiniteScoreBoundary(options.NegativeInfinity)).SetReverse()
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

func ExampleGlideClusterClient_ZRangeWithScores() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZRangeWithScores("key1", options.NewRangeByIndexQuery(0, -1))

	query := options.NewRangeByScoreQuery(
		options.NewScoreBoundary(3, false),
		options.NewInfiniteScoreBoundary(options.NegativeInfinity)).SetReverse()
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

func ExampleGlideClient_ZRank() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZRank() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZRankWithScore() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZRankWithScore() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZRevRank() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZRevRank() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZRevRankWithScore() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZRevRankWithScore() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZScore() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZScore() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZCount() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	zCountRange := options.NewZCountRange(
		options.NewInclusiveScoreBoundary(2.0),
		options.NewInfiniteScoreBoundary(options.PositiveInfinity),
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

func ExampleGlideClusterClient_ZCount() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	zCountRange := options.NewZCountRange(
		options.NewInclusiveScoreBoundary(2.0),
		options.NewInfiniteScoreBoundary(options.PositiveInfinity),
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

func ExampleGlideClient_ZScan() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZScan() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZScanWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZScanWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZRemRangeByLex() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZRemRangeByLex() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZRemRangeByRank() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZRemRangeByRank() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZRemRangeByScore() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result1, err := client.ZRemRangeByScore("key1", *options.NewRangeByScoreQuery(
		options.NewInfiniteScoreBoundary(options.NegativeInfinity),
		options.NewInfiniteScoreBoundary(options.PositiveInfinity),
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

func ExampleGlideClusterClient_ZRemRangeByScore() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result1, err := client.ZRemRangeByScore("key1", *options.NewRangeByScoreQuery(
		options.NewInfiniteScoreBoundary(options.NegativeInfinity),
		options.NewInfiniteScoreBoundary(options.PositiveInfinity),
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

func ExampleGlideClient_BZMPop() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.BZMPop([]string{"key1"}, options.MAX, float64(0.5))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonSummary, _ := json.Marshal(result.Value())
	fmt.Println(string(jsonSummary))
	// Output: {"Key":"key1","MembersAndScores":[{"Member":"d","Score":4}]}
}

func ExampleGlideClusterClient_BZMPop() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.BZMPop([]string{"key1"}, options.MAX, float64(0.5))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonSummary, _ := json.Marshal(result.Value())
	fmt.Println(string(jsonSummary))
	// Output: {"Key":"key1","MembersAndScores":[{"Member":"d","Score":4}]}
}

func ExampleGlideClient_BZMPopWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})

	result, err := client.BZMPopWithOptions([]string{"key1"}, options.MAX, 0.1, *options.NewZMPopOptions().SetCount(2))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	kms := KeyWithArrayOfMembersAndScores{
		Key: "key1",
		MembersAndScores: []MemberAndScore{
			{Member: "d", Score: 4},
			{Member: "c", Score: 3},
		},
	}
	fmt.Println(kms.Key == result.Value().Key)
	// isEqual := CompareUnorderedSlices[MemberAndScore](
	// 	kms.MembersAndScores,
	// 	result.Value().MembersAndScores,
	// ) // helper function for comparing arrays and slices
	// fmt.Println(isEqual)

	// Output:
	// true
}

func ExampleGlideClusterClient_BZMPopWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})

	result, err := client.BZMPopWithOptions([]string{"key1"}, options.MAX, 0.1, *options.NewZMPopOptions().SetCount(1))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonSummary, _ := json.Marshal(result.Value())
	fmt.Println(string(jsonSummary))

	// Output: {"Key":"key1","MembersAndScores":[{"Member":"d","Score":4}]}
}

func ExampleGlideClient_ZRandMember() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.ZRandMember("key2")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result.Value() == "")

	// Output: true
}

func ExampleGlideClusterClient_ZRandMember() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.ZRandMember("key2")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result.Value() == "")

	// Output: true
}

func ExampleGlideClient_ZRandMemberWithCount() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.ZRandMemberWithCount("key1", 4)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result)

	// Output: [d c b a]
}

func ExampleGlideClusterClient_ZRandMemberWithCount() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.ZRandMemberWithCount("key1", 4)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result)

	// Output: [d c b a]
}

func ExampleGlideClient_ZRandMemberWithCountWithScores() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.ZRandMemberWithCountWithScores("key1", 4)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result)

	// Output: [{d 4} {c 3} {b 2} {a 1}]
}

func ExampleGlideClusterClient_ZRandMemberWithCountWithScores() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.ZRandMemberWithCountWithScores("key1", 4)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result)

	// Output: [{d 4} {c 3} {b 2} {a 1}]
}

func ExampleGlideClient_ZMScore() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	result, err := client.ZMScore("key1", []string{"c", "b", "e"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result)

	// Output: [{3 false} {2.5 false} {0 true}]
}

func ExampleGlideClusterClient_ZMScore() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	result, err := client.ZMScore("key1", []string{"c", "b", "e"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result)

	// Output: [{3 false} {2.5 false} {0 true}]
}

func ExampleGlideClient_ZInter() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZInter() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZInterWithScores() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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
	// Output: map[b:3.5 c:5.5 d:7]
}

func ExampleGlideClusterClient_ZInterWithScores() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

	// Output: map[b:3.5 c:5.5 d:7]
}

func ExampleGlideClient_ZInterStore() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZInterStore() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZInterStoreWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZInterStoreWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZDiff() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd("key2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZDiff([]string{"key1", "key2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: [a]
}

func ExampleGlideClusterClient_ZDiff() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd("{key}1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd("{key}2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZDiff([]string{"{key}1", "{key}2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: [a]
}

func ExampleGlideClient_ZDiffWithScores() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd("key2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZDiffWithScores([]string{"key1", "key2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: map[a:1]
}

func ExampleGlideClusterClient_ZDiffWithScores() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd("{key}1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd("{key}2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZDiffWithScores([]string{"{key}1", "{key}2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: map[a:1]
}

func ExampleGlideClient_ZDiffStore() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	client.ZAdd("key1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd("key2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZDiffStore("dest", []string{"key1", "key2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 1
}

func ExampleGlideClusterClient_ZDiffStore() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd("{key}1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd("{key}2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZDiffStore("{key}dest", []string{"{key}1", "{key}2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 1
}
