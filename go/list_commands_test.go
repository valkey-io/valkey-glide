// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/valkey-io/valkey-glide/go/v2/constants"

	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func ExampleClient_LPush() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.LPush(context.Background(), "my_list", []string{"value1", "value2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleClusterClient_LPush() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.LPush(context.Background(), "my_list", []string{"value1", "value2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleClient_LPop() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.LPush(context.Background(), "my_list", []string{"value1", "value2"})
	result1, err := client.LPop(context.Background(), "my_list")
	result2, err := client.LPop(context.Background(), "non_existent")
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
	result, err := client.LPush(context.Background(), "my_list", []string{"value1", "value2"})
	result1, err := client.LPop(context.Background(), "my_list")
	result2, err := client.LPop(context.Background(), "non_existent")
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
	result, err := client.LPush(context.Background(), "my_list", []string{"value1", "value2"})
	result1, err := client.LPopCount(context.Background(), "my_list", 2)
	result2, err := client.LPop(context.Background(), "non_existent")
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
	result, err := client.LPush(context.Background(), "my_list", []string{"value1", "value2"})
	result1, err := client.LPopCount(context.Background(), "my_list", 2)
	result2, err := client.LPop(context.Background(), "non_existent")
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
	result, err := client.RPush(context.Background(), "my_list", []string{"a", "b", "c", "d", "e", "e"})
	result1, err := client.LPos(context.Background(), "my_list", "e")
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
	result, err := client.RPush(context.Background(), "my_list", []string{"a", "b", "c", "d", "e", "e"})
	result1, err := client.LPos(context.Background(), "my_list", "e")
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
	result, err := client.RPush(context.Background(), "my_list", []string{"a", "b", "c", "d", "e", "e"})
	result1, err := client.LPosWithOptions(context.Background(),
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
	result, err := client.RPush(context.Background(), "my_list", []string{"a", "b", "c", "d", "e", "e"})
	result1, err := client.LPosWithOptions(context.Background(),
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
	result, err := client.RPush(context.Background(), "my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.LPosCount(context.Background(), "my_list", "e", 3)
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
	result, err := client.RPush(context.Background(), "my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.LPosCount(context.Background(), "my_list", "e", 3)
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
	result, err := client.RPush(context.Background(), "my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.LPosCountWithOptions(context.Background(), "my_list", "e", 1, *options.NewLPosOptions().SetRank(2))
	result2, err := client.LPosCountWithOptions(context.Background(), "my_list", "e", 3,
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
	result, err := client.RPush(context.Background(), "my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.LPosCountWithOptions(context.Background(), "my_list", "e", 1, *options.NewLPosOptions().SetRank(2))
	result2, err := client.LPosCountWithOptions(context.Background(), "my_list", "e", 3,
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
	result, err := client.RPush(context.Background(), "my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// 7
}

func ExampleClusterClient_RPush() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.RPush(context.Background(), "my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// 7
}

func ExampleClient_LRange() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.RPush(context.Background(), "my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.LRange(context.Background(), "my_list", 0, 2)
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
	result, err := client.RPush(context.Background(), "my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.LRange(context.Background(), "my_list", 0, 2)
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
	result, err := client.RPush(context.Background(), "my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.LIndex(context.Background(), "my_list", 3)
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
	result, err := client.RPush(context.Background(), "my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.LIndex(context.Background(), "my_list", 3)
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
	result, err := client.RPush(context.Background(), "my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.LTrim(context.Background(), "my_list", 0, 4)
	result2, err := client.LRange(context.Background(), "my_list", 0, -1)
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
	result, err := client.RPush(context.Background(), "my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.LTrim(context.Background(), "my_list", 0, 4)
	result2, err := client.LRange(context.Background(), "my_list", 0, -1)
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
	result, err := client.RPush(context.Background(), "my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.LLen(context.Background(), "my_list")
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
	result, err := client.RPush(context.Background(), "my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.LLen(context.Background(), "my_list")
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
	result, err := client.RPush(context.Background(), "my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.LRem(context.Background(), "my_list", 2, "e")
	result2, err := client.LRange(context.Background(), "my_list", 0, -1)
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
	result, err := client.RPush(context.Background(), "my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.LRem(context.Background(), "my_list", 2, "e")
	result2, err := client.LRange(context.Background(), "my_list", 0, -1)
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
	result, err := client.RPush(context.Background(), "my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.RPop(context.Background(), "my_list")
	result2, err := client.RPop(context.Background(), "non_existing_key")
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
	result, err := client.RPush(context.Background(), "my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.RPop(context.Background(), "my_list")
	result2, err := client.RPop(context.Background(), "non_existing_key")
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
	result, err := client.RPush(context.Background(), "my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.RPopCount(context.Background(), "my_list", 4)
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
	result, err := client.RPush(context.Background(), "my_list", []string{"a", "b", "c", "d", "e", "e", "e"})
	result1, err := client.RPopCount(context.Background(), "my_list", 4)
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
	client.Del(context.Background(), []string{"my_list"})
	result, err := client.RPush(context.Background(), "my_list", []string{"hello", "world"})
	result1, err := client.LInsert(context.Background(), "my_list", constants.Before, "world", "there")
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
	client.Del(context.Background(), []string{"my_list"})
	result, err := client.RPush(context.Background(), "my_list", []string{"hello", "world"})
	result1, err := client.LInsert(context.Background(), "my_list", constants.Before, "world", "there")
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
	result, err := client.RPush(context.Background(), "list_a", []string{"a", "b", "c", "d", "e"})
	result1, err := client.RPush(context.Background(), "list_b", []string{"f", "g", "h", "i", "j"})
	result2, err := client.BLPop(context.Background(), []string{"list_a", "list_b"}, 500*time.Millisecond)
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
	result, err := client.RPush(context.Background(), "{list}-a", []string{"a", "b", "c", "d", "e"})
	result1, err := client.RPush(context.Background(), "{list}-b", []string{"f", "g", "h", "i", "j"})
	result2, err := client.BLPop(context.Background(), []string{"{list}-a", "{list}-b"}, 500*time.Millisecond)
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
	client.Del(context.Background(), []string{"my_list", "list_a", "list_b"})
	result, err := client.RPush(context.Background(), "list_a", []string{"a", "b", "c", "d", "e"})
	result1, err := client.RPush(context.Background(), "list_b", []string{"f", "g", "h", "i", "j"})
	result2, err := client.BRPop(context.Background(), []string{"list_a", "list_b"}, 500*time.Millisecond)
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
	client.Del(context.Background(), []string{"my_list", "{list}-a", "{list}-b"})
	result, err := client.RPush(context.Background(), "{list}-a", []string{"a", "b", "c", "d", "e"})
	result1, err := client.RPush(context.Background(), "{list}-b", []string{"f", "g", "h", "i", "j"})
	result2, err := client.BRPop(context.Background(), []string{"{list}-a", "{list}-b"}, 500*time.Millisecond)
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
	result, err := client.RPush(context.Background(), "my_list", []string{"value1"})
	result1, err := client.RPushX(context.Background(), "my_list", []string{"value2", "value3"})
	result2, err := client.LRange(context.Background(), "my_list", 0, -1)
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
	result, err := client.RPush(context.Background(), "my_list", []string{"value1"})
	result1, err := client.RPushX(context.Background(), "my_list", []string{"value2", "value3"})
	result2, err := client.LRange(context.Background(), "my_list", 0, -1)
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
	result, err := client.RPush(context.Background(), "my_list", []string{"value1"})
	result1, err := client.LPushX(context.Background(), "my_list", []string{"value2", "value3"})
	result2, err := client.LRange(context.Background(), "my_list", 0, -1)
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
	result, err := client.RPush(context.Background(), "my_list", []string{"value1"})
	result1, err := client.LPushX(context.Background(), "my_list", []string{"value2", "value3"})
	result2, err := client.LRange(context.Background(), "my_list", 0, -1)
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
	result, err := client.LPush(context.Background(), "my_list", []string{"one", "two", "three"})
	result1, err := client.LMPop(context.Background(), []string{"my_list"}, constants.Left)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	jsonResult1, err := json.Marshal(result1)
	fmt.Println(string(jsonResult1))

	// Output:
	// 3
	// [{"Key":"my_list","Values":["three"]}]
}

func ExampleClusterClient_LMPop() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.LPush(context.Background(), "my_list", []string{"one", "two", "three"})
	result1, err := client.LMPop(context.Background(), []string{"my_list"}, constants.Left)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	jsonResult1, err := json.Marshal(result1)
	fmt.Println(string(jsonResult1))

	// Output:
	// 3
	// [{"Key":"my_list","Values":["three"]}]
}

func ExampleClient_LMPopCount() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.LPush(context.Background(), "my_list", []string{"one", "two", "three"})
	result1, err := client.LMPopCount(context.Background(), []string{"my_list"}, constants.Left, 2)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	jsonResult1, err := json.Marshal(result1)
	fmt.Println(string(jsonResult1))

	// Output:
	// 3
	// [{"Key":"my_list","Values":["three","two"]}]
}

func ExampleClusterClient_LMPopCount() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.LPush(context.Background(), "my_list", []string{"one", "two", "three"})
	result1, err := client.LMPopCount(context.Background(), []string{"my_list"}, constants.Left, 2)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	jsonResult1, err := json.Marshal(result1)
	fmt.Println(string(jsonResult1))

	// Output:
	// 3
	// [{"Key":"my_list","Values":["three","two"]}]
}

func ExampleClient_BLMPop() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.LPush(context.Background(), "my_list", []string{"one", "two", "three"})
	result1, err := client.BLMPop(context.Background(), []string{"my_list"}, constants.Left, 100*time.Millisecond)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	jsonResult1, err := json.Marshal(result1)
	fmt.Println(string(jsonResult1))

	// Output:
	// 3
	// [{"Key":"my_list","Values":["three"]}]
}

func ExampleClusterClient_BLMPop() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.LPush(context.Background(), "my_list", []string{"one", "two", "three"})
	result1, err := client.BLMPop(context.Background(), []string{"my_list"}, constants.Left, 100*time.Millisecond)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	jsonResult1, err := json.Marshal(result1)
	fmt.Println(string(jsonResult1))

	// Output:
	// 3
	// [{"Key":"my_list","Values":["three"]}]
}

func ExampleClient_BLMPopCount() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.LPush(context.Background(), "my_list", []string{"one", "two", "three"})
	result1, err := client.BLMPopCount(context.Background(), []string{"my_list"}, constants.Left, 2, 100*time.Millisecond)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	jsonResult1, err := json.Marshal(result1)
	fmt.Println(string(jsonResult1))

	// Output:
	// 3
	// [{"Key":"my_list","Values":["three","two"]}]
}

func ExampleClusterClient_BLMPopCount() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.LPush(context.Background(), "my_list", []string{"one", "two", "three"})
	result1, err := client.BLMPopCount(context.Background(), []string{"my_list"}, constants.Left, 2, 100*time.Millisecond)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	jsonResult1, err := json.Marshal(result1)
	fmt.Println(string(jsonResult1))

	// Output:
	// 3
	// [{"Key":"my_list","Values":["three","two"]}]
}

func ExampleClient_LSet() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.LPush(context.Background(), "my_list", []string{"one", "two", "three"})
	result1, err := client.LSet(context.Background(), "my_list", 1, "someOtherValue")
	result2, err := client.LRange(context.Background(), "my_list", 0, -1)
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

	result, err := client.LPush(context.Background(), "my_list", []string{"one", "two", "three"})
	result1, err := client.LSet(context.Background(), "my_list", 1, "someOtherValue")
	result2, err := client.LRange(context.Background(), "my_list", 0, -1)
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
	result, err := client.LPush(context.Background(), "my_list1", []string{"two", "one"})
	result1, err := client.LPush(context.Background(), "my_list2", []string{"four", "three"})
	result2, err := client.LMove(context.Background(), "my_list1", "my_list2", constants.Left, constants.Left)
	result3, err := client.LRange(context.Background(), "my_list1", 0, -1)
	result4, err := client.LRange(context.Background(), "my_list2", 0, -1)
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
	result, err := client.LPush(context.Background(), "{list}-1", []string{"two", "one"})
	result1, err := client.LPush(context.Background(), "{list}-2", []string{"four", "three"})
	result2, err := client.LMove(context.Background(), "{list}-1", "{list}-2", constants.Left, constants.Left)
	result3, err := client.LRange(context.Background(), "{list}-1", 0, -1)
	result4, err := client.LRange(context.Background(), "{list}-2", 0, -1)
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
	result, err := client.LPush(context.Background(), "my_list1", []string{"two", "one"})
	result1, err := client.LPush(context.Background(), "my_list2", []string{"four", "three"})
	result2, err := client.BLMove(
		context.Background(),
		"my_list1",
		"my_list2",
		constants.Left,
		constants.Left,
		100*time.Millisecond,
	)
	result3, err := client.LRange(context.Background(), "my_list1", 0, -1)
	result4, err := client.LRange(context.Background(), "my_list2", 0, -1)
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
	result, err := client.LPush(context.Background(), "{list}-1", []string{"two", "one"})
	result1, err := client.LPush(context.Background(), "{list}-2", []string{"four", "three"})
	result2, err := client.BLMove(
		context.Background(),
		"{list}-1",
		"{list}-2",
		constants.Left,
		constants.Left,
		100*time.Millisecond,
	)
	result3, err := client.LRange(context.Background(), "{list}-1", 0, -1)
	result4, err := client.LRange(context.Background(), "{list}-2", 0, -1)
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
