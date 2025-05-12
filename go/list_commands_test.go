// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"fmt"

	"github.com/valkey-io/valkey-glide/go/v2/constants"

	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func ExampleClient_LPush() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.LPush("my_list", []string{"value1", "value2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleClusterClient_LPush() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.LPush("my_list", []string{"value1", "value2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleClient_LPop() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.LPush("my_list", []string{"value1", "value2"})
	result1, err := client.LPop("my_list")
	result2, err := client.LPop("non_existent")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2.IsNil())

	// Output:
	// 2
	// {value2 false}
	// true
}

func ExampleClusterClient_LPop() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.LPush("my_list", []string{"value1", "value2"})
	result1, err := client.LPop("my_list")
	result2, err := client.LPop("non_existent")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2.IsNil())

	// Output:
	// 2
	// {value2 false}
	// true
}

func ExampleClient_LPopCount() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.LPush("my_list", []string{"value1", "value2"})
	result1, err := client.LPopCount("my_list", 2)
	result2, err := client.LPop("non_existent")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2.IsNil())

	// Output:
	// 2
	// [value2 value1]
	// true
}

func ExampleClusterClient_LPopCount() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.LPush("my_list", []string{"value1", "value2"})
	result1, err := client.LPopCount("my_list", 2)
	result2, err := client.LPop("non_existent")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2.IsNil())

	// Output:
	// 2
	// [value2 value1]
	// true
}

func ExampleClient_LPos() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e"})
	result1, err := client.LPos("my_list", "e")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 6
	// {4 false}
}

func ExampleClusterClient_LPos() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e"})
	result1, err := client.LPos("my_list", "e")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 6
	// {4 false}
}

func ExampleClient_LPosWithOptions() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e"})
	result1, err := client.LPosWithOptions(
		"my_list",
		"e",
		*options.NewLPosOptions().SetRank(2),
	) // (Returns the second occurrence of the element "e")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 6
	// {5 false}
}

func ExampleClusterClient_LPosWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e"})
	result1, err := client.LPosWithOptions(
		"my_list",
		"e",
		*options.NewLPosOptions().SetRank(2),
	) // (Returns the second occurrence of the element "e")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 6
	// {5 false}
}

func ExampleClient_LPosCount() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.LPosCount("my_list", "e", 3)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 7
	// [4 5 6]
}

func ExampleClusterClient_LPosCount() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.LPosCount("my_list", "e", 3)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 7
	// [4 5 6]
}

func ExampleClient_LPosCountWithOptions() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.LPosCountWithOptions("my_list", "e", 1, *options.NewLPosOptions().SetRank(2))
	result2, err := client.LPosCountWithOptions("my_list", "e", 3,
		*options.NewLPosOptions().SetRank(2).SetMaxLen(1000))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// 7
	// [5]
	// [5 6]
}

func ExampleClusterClient_LPosCountWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.LPosCountWithOptions("my_list", "e", 1, *options.NewLPosOptions().SetRank(2))
	result2, err := client.LPosCountWithOptions("my_list", "e", 3,
		*options.NewLPosOptions().SetRank(2).SetMaxLen(1000))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// 7
	// [5]
	// [5 6]
}

func ExampleClient_RPush() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// 7
}

func ExampleClusterClient_RPush() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// 7
}

func ExampleClient_LRange() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.LRange("my_list", 0, 2)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 7
	// [a b c]
}

func ExampleClusterClient_LRange() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.LRange("my_list", 0, 2)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 7
	// [a b c]
}

func ExampleClient_LIndex() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.LIndex("my_list", 3)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 7
	// {d false}
}

func ExampleClusterClient_LIndex() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.LIndex("my_list", 3)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 7
	// {d false}
}

func ExampleClient_LTrim() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.LTrim("my_list", 0, 4)
	result2, err := client.LRange("my_list", 0, -1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// 7
	// OK
	// [a b c d e]
}

func ExampleClusterClient_LTrim() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.LTrim("my_list", 0, 4)
	result2, err := client.LRange("my_list", 0, -1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// 7
	// OK
	// [a b c d e]
}

func ExampleClient_LLen() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.LLen("my_list")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 7
	// 7
}

func ExampleClusterClient_LLen() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.LLen("my_list")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 7
	// 7
}

func ExampleClient_LRem() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.LRem("my_list", 2, "e")
	result2, err := client.LRange("my_list", 0, -1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// 7
	// 2
	// [a b c d e]
}

func ExampleClusterClient_LRem() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.LRem("my_list", 2, "e")
	result2, err := client.LRange("my_list", 0, -1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// 7
	// 2
	// [a b c d e]
}

func ExampleClient_RPop() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.RPop("my_list")
	result2, err := client.RPop("non_existing_key")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2.IsNil())

	// Output:
	// 7
	// {e false}
	// true
}

func ExampleClusterClient_RPop() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.RPop("my_list")
	result2, err := client.RPop("non_existing_key")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2.IsNil())

	// Output:
	// 7
	// {e false}
	// true
}

func ExampleClient_RPopCount() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.RPopCount("my_list", 4)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 7
	// [e e e d]
}

func ExampleClusterClient_RPopCount() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.RPush("my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.RPopCount("my_list", 4)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 7
	// [e e e d]
}

func ExampleClient_LInsert() {
	var client *Client = getExampleClient() // example helper function
	client.Del([]string{"my_list"})
	result, err := client.RPush("my_list", []string{"hello", "world"})
	result1, err := client.LInsert("my_list", constants.Before, "world", "there")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 2
	// 3
}

func ExampleClusterClient_LInsert() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	client.Del([]string{"my_list"})
	result, err := client.RPush("my_list", []string{"hello", "world"})
	result1, err := client.LInsert("my_list", constants.Before, "world", "there")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 2
	// 3
}

func ExampleClient_BLPop() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.RPush("list_a", []string{"a", "b", "c", "d", "e"})
	result1, err := client.RPush("list_b", []string{"f", "g", "h", "i", "j"})
	result2, err := client.BLPop([]string{"list_a", "list_b"}, 0.5)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// 5
	// 5
	// [list_a a]
}

func ExampleClusterClient_BLPop() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.RPush("{list}-a", []string{"a", "b", "c", "d", "e"})
	result1, err := client.RPush("{list}-b", []string{"f", "g", "h", "i", "j"})
	result2, err := client.BLPop([]string{"{list}-a", "{list}-b"}, 0.5)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// 5
	// 5
	// [{list}-a a]
}

func ExampleClient_BRPop() {
	var client *Client = getExampleClient() // example helper function
	client.Del([]string{"my_list", "list_a", "list_b"})
	result, err := client.RPush("list_a", []string{"a", "b", "c", "d", "e"})
	result1, err := client.RPush("list_b", []string{"f", "g", "h", "i", "j"})
	result2, err := client.BRPop([]string{"list_a", "list_b"}, 0.5)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// 5
	// 5
	// [list_a e]
}

func ExampleClusterClient_BRPop() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	client.Del([]string{"my_list", "{list}-a", "{list}-b"})
	result, err := client.RPush("{list}-a", []string{"a", "b", "c", "d", "e"})
	result1, err := client.RPush("{list}-b", []string{"f", "g", "h", "i", "j"})
	result2, err := client.BRPop([]string{"{list}-a", "{list}-b"}, 0.5)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// 5
	// 5
	// [{list}-a e]
}

func ExampleClient_RPushX() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.RPush("my_list", []string{"value1"})
	result1, err := client.RPushX("my_list", []string{"value2", "value3"})
	result2, err := client.LRange("my_list", 0, -1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// 1
	// 3
	// [value1 value2 value3]
}

func ExampleClusterClient_RPushX() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.RPush("my_list", []string{"value1"})
	result1, err := client.RPushX("my_list", []string{"value2", "value3"})
	result2, err := client.LRange("my_list", 0, -1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// 1
	// 3
	// [value1 value2 value3]
}

func ExampleClient_LPushX() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.RPush("my_list", []string{"value1"})
	result1, err := client.LPushX("my_list", []string{"value2", "value3"})
	result2, err := client.LRange("my_list", 0, -1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// 1
	// 3
	// [value3 value2 value1]
}

func ExampleClusterClient_LPushX() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.RPush("my_list", []string{"value1"})
	result1, err := client.LPushX("my_list", []string{"value2", "value3"})
	result2, err := client.LRange("my_list", 0, -1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// 1
	// 3
	// [value3 value2 value1]
}

func ExampleClient_LMPop() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.LPush("my_list", []string{"one", "two", "three"})
	result1, err := client.LMPop([]string{"my_list"}, constants.Left)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 3
	// map[my_list:[three]]
}

func ExampleClusterClient_LMPop() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.LPush("my_list", []string{"one", "two", "three"})
	result1, err := client.LMPop([]string{"my_list"}, constants.Left)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 3
	// map[my_list:[three]]
}

func ExampleClient_LMPopCount() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.LPush("my_list", []string{"one", "two", "three"})
	result1, err := client.LMPopCount([]string{"my_list"}, constants.Left, 2)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 3
	// map[my_list:[three two]]
}

func ExampleClusterClient_LMPopCount() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.LPush("my_list", []string{"one", "two", "three"})
	result1, err := client.LMPopCount([]string{"my_list"}, constants.Left, 2)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 3
	// map[my_list:[three two]]
}

func ExampleClient_BLMPop() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.LPush("my_list", []string{"one", "two", "three"})
	result1, err := client.BLMPop([]string{"my_list"}, constants.Left, 0.1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 3
	// map[my_list:[three]]
}

func ExampleClusterClient_BLMPop() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.LPush("my_list", []string{"one", "two", "three"})
	result1, err := client.BLMPop([]string{"my_list"}, constants.Left, 0.1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 3
	// map[my_list:[three]]
}

func ExampleClient_BLMPopCount() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.LPush("my_list", []string{"one", "two", "three"})
	result1, err := client.BLMPopCount([]string{"my_list"}, constants.Left, 2, 0.1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 3
	// map[my_list:[three two]]
}

func ExampleClusterClient_BLMPopCount() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.LPush("my_list", []string{"one", "two", "three"})
	result1, err := client.BLMPopCount([]string{"my_list"}, constants.Left, 2, 0.1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 3
	// map[my_list:[three two]]
}

func ExampleClient_LSet() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.LPush("my_list", []string{"one", "two", "three"})
	result1, err := client.LSet("my_list", 1, "someOtherValue")
	result2, err := client.LRange("my_list", 0, -1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// 3
	// OK
	// [three someOtherValue one]
}

func ExampleClusterClient_LSet() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	result, err := client.LPush("my_list", []string{"one", "two", "three"})
	result1, err := client.LSet("my_list", 1, "someOtherValue")
	result2, err := client.LRange("my_list", 0, -1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// 3
	// OK
	// [three someOtherValue one]
}

func ExampleClient_LMove() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.LPush("my_list1", []string{"two", "one"})
	result1, err := client.LPush("my_list2", []string{"four", "three"})
	result2, err := client.LMove("my_list1", "my_list2", constants.Left, constants.Left)
	result3, err := client.LRange("my_list1", 0, -1)
	result4, err := client.LRange("my_list2", 0, -1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)
	fmt.Println(result3)
	fmt.Println(result4)

	// Output:
	// 2
	// 2
	// {one false}
	// [two]
	// [one three four]
}

func ExampleClusterClient_LMove() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.LPush("{list}-1", []string{"two", "one"})
	result1, err := client.LPush("{list}-2", []string{"four", "three"})
	result2, err := client.LMove("{list}-1", "{list}-2", constants.Left, constants.Left)
	result3, err := client.LRange("{list}-1", 0, -1)
	result4, err := client.LRange("{list}-2", 0, -1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)
	fmt.Println(result3)
	fmt.Println(result4)

	// Output:
	// 2
	// 2
	// {one false}
	// [two]
	// [one three four]
}

func ExampleClient_BLMove() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.LPush("my_list1", []string{"two", "one"})
	result1, err := client.LPush("my_list2", []string{"four", "three"})
	result2, err := client.BLMove("my_list1", "my_list2", constants.Left, constants.Left, 0.1)
	result3, err := client.LRange("my_list1", 0, -1)
	result4, err := client.LRange("my_list2", 0, -1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)
	fmt.Println(result3)
	fmt.Println(result4)

	// Output:
	// 2
	// 2
	// {one false}
	// [two]
	// [one three four]
}

func ExampleClusterClient_BLMove() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.LPush("{list}-1", []string{"two", "one"})
	result1, err := client.LPush("{list}-2", []string{"four", "three"})
	result2, err := client.BLMove("{list}-1", "{list}-2", constants.Left, constants.Left, 0.1)
	result3, err := client.LRange("{list}-1", 0, -1)
	result4, err := client.LRange("{list}-2", 0, -1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)
	fmt.Println(result3)
	fmt.Println(result4)

	// Output:
	// 2
	// 2
	// {one false}
	// [two]
	// [one three four]
}
