// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"context"
	"encoding/json"
	"fmt"
	"sort"

	"github.com/valkey-io/valkey-glide/go/api/options"
)

func ExampleGlideClient_ZAdd() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 3
}

func ExampleGlideClusterClient_ZAdd() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 3
}

func ExampleGlideClient_ZAddWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZAddWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZAddIncr() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	result, err := client.ZAddIncr(context.Background(), "key1", "one", 1.0)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// {1 false}
}

func ExampleGlideClusterClient_ZAddIncr() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.ZAddIncr(context.Background(), "key1", "one", 1.0)
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
	result, err := client.ZAddIncrWithOptions(context.Background(), "key1", "one", 1.0, *opts)
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
	result, err := client.ZAddIncrWithOptions(context.Background(), "key1", "one", 1.0, *opts)
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

func ExampleGlideClusterClient_ZIncrBy() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZPopMin() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZPopMin() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZPopMinWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZPopMinWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZPopMax() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZPopMax() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZPopMaxWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZPopMaxWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZRem() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZRem() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZCard() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZCard() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_BZPopMin() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_BZPopMin() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZRange() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZRange(context.Background(), "key1", options.NewRangeByIndexQuery(0, -1)) // Ascending order

	// Retrieve members within a score range in descending order
	query := options.NewRangeByScoreQuery(
		options.NewScoreBoundary(3, false),
		options.NewInfiniteScoreBoundary(options.NegativeInfinity)).SetReverse()
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

func ExampleGlideClusterClient_ZRange() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZRange(context.Background(), "key1", options.NewRangeByIndexQuery(0, -1)) // Ascending order

	// Retrieve members within a score range in descending order
	query := options.NewRangeByScoreQuery(
		options.NewScoreBoundary(3, false),
		options.NewInfiniteScoreBoundary(options.NegativeInfinity)).SetReverse()
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

func ExampleGlideClient_ZRangeWithScores() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZRangeWithScores(context.Background(), "key1", options.NewRangeByIndexQuery(0, -1))

	query := options.NewRangeByScoreQuery(
		options.NewScoreBoundary(3, false),
		options.NewInfiniteScoreBoundary(options.NegativeInfinity)).SetReverse()
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

func ExampleGlideClusterClient_ZRangeWithScores() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	result1, err := client.ZRangeWithScores(context.Background(), "key1", options.NewRangeByIndexQuery(0, -1))

	query := options.NewRangeByScoreQuery(
		options.NewScoreBoundary(3, false),
		options.NewInfiniteScoreBoundary(options.NegativeInfinity)).SetReverse()
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

func ExampleGlideClient_ZRangeStore() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	query := options.NewRangeByScoreQuery(
		options.NewScoreBoundary(3, false),
		options.NewInfiniteScoreBoundary(options.NegativeInfinity)).SetReverse()
	result, err := client.ZRangeStore(context.Background(), "dest", "key1", query)
	// `result` contains members which have scores within the range of negative infinity to 3, in descending order
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleGlideClusterClient_ZRangeStore() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd(context.Background(), "{key}1", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	query := options.NewRangeByScoreQuery(
		options.NewScoreBoundary(3, false),
		options.NewInfiniteScoreBoundary(options.NegativeInfinity)).SetReverse()
	result, err := client.ZRangeStore(context.Background(), "{key}dest", "{key}1", query)
	// `result` contains members which have scores within the range of negative infinity to 3, in descending order
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleGlideClient_ZRank() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZRank() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZRankWithScore() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZRankWithScore() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZRevRank() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZRevRank() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZRevRankWithScore() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZRevRankWithScore() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZScore() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZScore() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZCount() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	zCountRange := options.NewZCountRange(
		options.NewInclusiveScoreBoundary(2.0),
		options.NewInfiniteScoreBoundary(options.PositiveInfinity),
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

func ExampleGlideClusterClient_ZCount() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	zCountRange := options.NewZCountRange(
		options.NewInclusiveScoreBoundary(2.0),
		options.NewInfiniteScoreBoundary(options.PositiveInfinity),
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

func ExampleGlideClient_ZScan() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZScan() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZScanWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZScanWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZRemRangeByLex() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZRemRangeByLex() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZRemRangeByRank() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZRemRangeByRank() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZRemRangeByScore() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result1, err := client.ZRemRangeByScore(context.Background(), "key1", *options.NewRangeByScoreQuery(
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

	result, err := client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result1, err := client.ZRemRangeByScore(context.Background(), "key1", *options.NewRangeByScoreQuery(
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

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.BZMPop(context.Background(), []string{"key1"}, options.MAX, float64(0.5))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonSummary, _ := json.Marshal(result.Value())
	fmt.Println(string(jsonSummary))
	// Output: {"Key":"key1","MembersAndScores":[{"Member":"d","Score":4}]}
}

func ExampleGlideClusterClient_BZMPop() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.BZMPop(context.Background(), []string{"key1"}, options.MAX, float64(0.5))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonSummary, _ := json.Marshal(result.Value())
	fmt.Println(string(jsonSummary))
	// Output: {"Key":"key1","MembersAndScores":[{"Member":"d","Score":4}]}
}

func ExampleGlideClient_BZMPopWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})

	result, err := client.BZMPopWithOptions(
		context.Background(),
		[]string{"key1"},
		options.MAX,
		0.1,
		*options.NewZMPopOptions().SetCount(2),
	)
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

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})

	result, err := client.BZMPopWithOptions(
		context.Background(),
		[]string{"key1"},
		options.MAX,
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

func ExampleGlideClient_ZRandMember() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.ZRandMember(context.Background(), "key2")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result.Value() == "")

	// Output: true
}

func ExampleGlideClusterClient_ZRandMember() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.ZRandMember(context.Background(), "key2")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result.Value() == "")

	// Output: true
}

func ExampleGlideClient_ZRandMemberWithCount() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.ZRandMemberWithCount(context.Background(), "key1", 4)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result)

	// Output: [d c b a]
}

func ExampleGlideClusterClient_ZRandMemberWithCount() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.ZRandMemberWithCount(context.Background(), "key1", 4)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result)

	// Output: [d c b a]
}

func ExampleGlideClient_ZRandMemberWithCountWithScores() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.ZRandMemberWithCountWithScores(context.Background(), "key1", 4)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result)

	// Output: [{d 4} {c 3} {b 2} {a 1}]
}

func ExampleGlideClusterClient_ZRandMemberWithCountWithScores() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
	result, err := client.ZRandMemberWithCountWithScores(context.Background(), "key1", 4)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result)

	// Output: [{d 4} {c 3} {b 2} {a 1}]
}

func ExampleGlideClient_ZMScore() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	result, err := client.ZMScore(context.Background(), "key1", []string{"c", "b", "e"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result)

	// Output: [{3 false} {2.5 false} {0 true}]
}

func ExampleGlideClusterClient_ZMScore() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	result, err := client.ZMScore(context.Background(), "key1", []string{"c", "b", "e"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// If the key does not exist, an empty string is returned
	fmt.Println(result)

	// Output: [{3 false} {2.5 false} {0 true}]
}

func ExampleGlideClient_ZInter() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZInter() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZInterWithScores() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZInterWithScores() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZInterStore() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZInterStore() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZInterStoreWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZInterStoreWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZDiff() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd(context.Background(), "key2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZDiff(context.Background(), []string{"key1", "key2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: [a]
}

func ExampleGlideClusterClient_ZDiff() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd(context.Background(), "{key}1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd(context.Background(), "{key}2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZDiff(context.Background(), []string{"{key}1", "{key}2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: [a]
}

func ExampleGlideClient_ZDiffWithScores() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd(context.Background(), "key2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZDiffWithScores(context.Background(), []string{"key1", "key2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: [{a 1}]
}

func ExampleGlideClusterClient_ZDiffWithScores() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd(context.Background(), "{key}1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd(context.Background(), "{key}2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZDiffWithScores(context.Background(), []string{"{key}1", "{key}2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: [{a 1}]
}

func ExampleGlideClient_ZDiffStore() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	client.ZAdd(context.Background(), "key1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd(context.Background(), "key2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZDiffStore(context.Background(), "dest", []string{"key1", "key2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 1
}

func ExampleGlideClusterClient_ZDiffStore() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd(context.Background(), "{key}1", map[string]float64{"a": 1.0, "b": 2.5, "c": 3.0, "d": 4.0})
	client.ZAdd(context.Background(), "{key}2", map[string]float64{"b": 1.0, "c": 2.5, "d": 3.0, "e": 4.0})
	result, err := client.ZDiffStore(context.Background(), "{key}dest", []string{"{key}1", "{key}2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 1
}

func ExampleGlideClient_ZUnion() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZUnion() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZUnionWithScores() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZUnionWithScores() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZUnionStore() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZUnionStore() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZUnionStoreWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZUnionStoreWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZInterCard() {
	var client *GlideClient = getExampleGlideClient() // example helper function
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

func ExampleGlideClusterClient_ZInterCard() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
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

func ExampleGlideClient_ZInterCardWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function
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

func ExampleGlideClusterClient_ZInterCardWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
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

func ExampleGlideClient_ZLexCount() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_ZLexCount() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_BZPopMax() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_BZPopMax() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_ZMPop() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	// Add members to a sorted set
	client.ZAdd(context.Background(), "mySortedSet", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0})

	// Pop the lowest-score member
	res, err := client.ZMPop(context.Background(), []string{"mySortedSet"}, options.MIN)
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

func ExampleGlideClusterClient_ZMPop() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	// Add members to a sorted set
	client.ZAdd(context.Background(), "{key}sortedSet", map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})

	// Pop the lowest-score member
	res, err := client.ZMPop(context.Background(), []string{"{key}sortedSet"}, options.MIN)
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

func ExampleGlideClient_ZMPopWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	client.ZAdd(context.Background(), "mySortedSet", map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})

	opts := *options.NewZPopOptions().SetCount(2)
	res, err := client.ZMPopWithOptions(context.Background(), []string{"mySortedSet"}, options.MAX, opts)
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

func ExampleGlideClusterClient_ZMPopWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	client.ZAdd(context.Background(), "{key}SortedSet", map[string]float64{"p": 10.0, "q": 20.0, "r": 30.0})

	opts := *options.NewZPopOptions().SetCount(2)
	res, err := client.ZMPopWithOptions(context.Background(), []string{"{key}SortedSet"}, options.MAX, opts)
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
