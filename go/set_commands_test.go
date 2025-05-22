// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"fmt"

	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func ExampleClient_SAdd() {
	var client *Client = getExampleClient() // example helper function
	key := "my_set"

	result, err := client.SAdd(context.Background(), key, []string{"member1", "member2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleClusterClient_SAdd() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "my_set"

	result, err := client.SAdd(context.Background(), key, []string{"member1", "member2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleClient_SRem() {
	var client *Client = getExampleClient() // example helper function
	key := "my_set"

	client.SAdd(context.Background(), key, []string{"member1", "member2"})
	result, err := client.SRem(context.Background(), key, []string{"member1", "member2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleClusterClient_SRem() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "my_set"

	client.SAdd(context.Background(), key, []string{"member1", "member2", "member3", "member4", "member5"})
	result, err := client.SRem(context.Background(), key, []string{"member1", "member2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleClient_SMembers() {
	var client *Client = getExampleClient() // example helper function
	key := "my_set"

	client.SAdd(context.Background(), key, []string{"member1", "member2"})

	result, err := client.SMembers(context.Background(), key)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: map[member1:{} member2:{}]
}

func ExampleClusterClient_SMembers() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "my_set"

	client.SAdd(context.Background(), key, []string{"member1", "member2"})

	result, err := client.SMembers(context.Background(), key)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: map[member1:{} member2:{}]
}

func ExampleClient_SCard() {
	var client *Client = getExampleClient() // example helper function
	key := "my_set"

	client.SAdd(context.Background(), key, []string{"member1", "member2"})

	result, err := client.SCard(context.Background(), key)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: 2
}

func ExampleClusterClient_SCard() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "my_set"

	client.SAdd(context.Background(), key, []string{"member1", "member2"})

	result, err := client.SCard(context.Background(), key)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: 2
}

func ExampleClient_SIsMember() {
	var client *Client = getExampleClient() // example helper function
	key := "my_set"

	client.SAdd(context.Background(), key, []string{"member1", "member2"})

	result, err := client.SIsMember(context.Background(), key, "member1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: true
}

func ExampleClusterClient_SIsMember() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "my_set"

	client.SAdd(context.Background(), key, []string{"member1", "member2"})

	result, err := client.SIsMember(context.Background(), key, "member1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: true
}

func ExampleClient_SDiff() {
	var client *Client = getExampleClient() // example helper function
	key1 := "my_set_1"
	key2 := "my_set_2"

	client.SAdd(context.Background(), key1, []string{"member1", "member2"})
	client.SAdd(context.Background(), key2, []string{"member2"})

	result, err := client.SDiff(context.Background(), []string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: map[member1:{}]
}

func ExampleClusterClient_SDiff() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key1 := "{set}1"
	key2 := "{set}2"

	client.SAdd(context.Background(), key1, []string{"member1", "member2"})
	client.SAdd(context.Background(), key2, []string{"member2"})

	result, err := client.SDiff(context.Background(), []string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: map[member1:{}]
}

func ExampleClient_SDiffStore() {
	var client *Client = getExampleClient() // example helper function
	key1 := "my_set_1"
	key2 := "my_set_2"
	destination := "my_set_diff"

	client.SAdd(context.Background(), key1, []string{"member1", "member2"})
	client.SAdd(context.Background(), key2, []string{"member2"})

	result, err := client.SDiffStore(context.Background(), destination, []string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: 1
}

func ExampleClusterClient_SDiffStore() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key1 := "{set}1"
	key2 := "{set}2"
	destination := "{set}3"

	client.SAdd(context.Background(), key1, []string{"member1", "member2"})
	client.SAdd(context.Background(), key2, []string{"member2"})

	result, err := client.SDiffStore(context.Background(), destination, []string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: 1
}

func ExampleClient_SInter() {
	var client *Client = getExampleClient() // example helper function
	key1 := "my_set_1"
	key2 := "my_set_2"

	client.SAdd(context.Background(), key1, []string{"member1", "member2"})
	client.SAdd(context.Background(), key2, []string{"member2"})

	result, err := client.SInter(context.Background(), []string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: map[member2:{}]
}

func ExampleClusterClient_SInter() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key1 := "{set}1"
	key2 := "{set}2"

	client.SAdd(context.Background(), key1, []string{"member1", "member2"})
	client.SAdd(context.Background(), key2, []string{"member2"})

	result, err := client.SInter(context.Background(), []string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: map[member2:{}]
}

func ExampleClient_SInterStore() {
	var client *Client = getExampleClient() // example helper function
	key1 := "my_set_1"
	key2 := "my_set_2"
	destination := "my_set_inter"

	client.SAdd(context.Background(), key1, []string{"member1", "member2"})
	client.SAdd(context.Background(), key2, []string{"member2"})

	result, err := client.SInterStore(context.Background(), destination, []string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: 1
}

func ExampleClusterClient_SInterStore() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key1 := "{set}1"
	key2 := "{set}2"
	destination := "{set}3"

	client.SAdd(context.Background(), key1, []string{"member1", "member2"})
	client.SAdd(context.Background(), key2, []string{"member2"})

	result, err := client.SInterStore(context.Background(), destination, []string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: 1
}

func ExampleClient_SInterCard() {
	var client *Client = getExampleClient() // example helper function
	key1 := "my_set_1"
	key2 := "my_set_2"

	client.SAdd(context.Background(), key1, []string{"member1", "member2"})
	client.SAdd(context.Background(), key2, []string{"member2"})

	result, err := client.SInterCard(context.Background(), []string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: 1
}

func ExampleClusterClient_SInterCard() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key1 := "{set}1"
	key2 := "{set}2"

	client.SAdd(context.Background(), key1, []string{"member1", "member2"})
	client.SAdd(context.Background(), key2, []string{"member2"})

	result, err := client.SInterCard(context.Background(), []string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: 1
}

func ExampleClient_SInterCardLimit() {
	var client *Client = getExampleClient() // example helper function
	key1 := "my_set_1"
	key2 := "my_set_2"
	limit := int64(1)

	client.SAdd(context.Background(), key1, []string{"member1", "member2", "member3"})
	client.SAdd(context.Background(), key2, []string{"member2", "member3"})

	result, err := client.SInterCardLimit(context.Background(), []string{key1, key2}, limit)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: 1
}

func ExampleClusterClient_SInterCardLimit() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key1 := "{set}1"
	key2 := "{set}2"
	limit := int64(1)

	client.SAdd(context.Background(), key1, []string{"member1", "member2", "member3"})
	client.SAdd(context.Background(), key2, []string{"member2", "member3"})

	result, err := client.SInterCardLimit(context.Background(), []string{key1, key2}, limit)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: 1
}

func ExampleClient_SRandMember() {
	var client *Client = getExampleClient() // example helper function
	key := "my_set"

	client.SAdd(context.Background(), key, []string{"member1", "member2"})

	result, err := client.SRandMember(context.Background(), key)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.IsNil()) // Unable to test for a random value so just check if it is not nil

	// Output: false
}

func ExampleClusterClient_SRandMember() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "my_set"

	client.SAdd(context.Background(), key, []string{"member1", "member2"})

	result, err := client.SRandMember(context.Background(), key)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.IsNil()) // Unable to test for a random value so just check if it is not nil

	// Output: false
}

func ExampleClient_SPop() {
	var client *Client = getExampleClient() // example helper function
	key := "my_set"

	client.SAdd(context.Background(), key, []string{"member1", "member2"})

	result, err := client.SPop(context.Background(), key)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.IsNil())
	// Output: false
}

func ExampleClusterClient_SPop() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "my_set"

	client.SAdd(context.Background(), key, []string{"member1", "member2"})

	result, err := client.SPop(context.Background(), key)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.IsNil())
	// Output: false
}

func ExampleClient_SMIsMember() {
	var client *Client = getExampleClient() // example helper function
	key := "my_set"

	members := []string{"member1", "member2"}
	client.SAdd(context.Background(), key, members)

	memberTest := []string{"member1", "member2", "member3"}
	result, err := client.SMIsMember(context.Background(), key, memberTest)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: [true true false]
}

func ExampleClusterClient_SMIsMember() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "my_set"

	members := []string{"member1", "member2"}
	client.SAdd(context.Background(), key, members)

	memberTest := []string{"member1", "member2", "member3"}
	result, err := client.SMIsMember(context.Background(), key, memberTest)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: [true true false]
}

func ExampleClient_SUnionStore() {
	var client *Client = getExampleClient() // example helper function
	key1 := "my_set_1"
	key2 := "my_set_2"
	destination := "my_set_union"

	client.SAdd(context.Background(), key1, []string{"member1", "member2"})
	client.SAdd(context.Background(), key2, []string{"member2", "member3"})

	result, err := client.SUnionStore(context.Background(), destination, []string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: 3
}

func ExampleClusterClient_SUnionStore() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key1 := "{set}1"
	key2 := "{set}2"
	destination := "{set}3"

	client.SAdd(context.Background(), key1, []string{"member1", "member2"})
	client.SAdd(context.Background(), key2, []string{"member2", "member3"})

	result, err := client.SUnionStore(context.Background(), destination, []string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: 3
}

func ExampleClient_SUnion() {
	var client *Client = getExampleClient() // example helper function
	key1 := "my_set_1"
	key2 := "my_set_2"

	client.SAdd(context.Background(), key1, []string{"member1", "member2"})
	client.SAdd(context.Background(), key2, []string{"member2", "member3"})

	result, err := client.SUnion(context.Background(), []string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: map[member1:{} member2:{} member3:{}]
}

func ExampleClusterClient_SUnion() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key1 := "{set}1"
	key2 := "{set}2"

	client.SAdd(context.Background(), key1, []string{"member1", "member2"})
	client.SAdd(context.Background(), key2, []string{"member2", "member3"})

	result, err := client.SUnion(context.Background(), []string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: map[member1:{} member2:{} member3:{}]
}

func ExampleClient_SScan() {
	var client *Client = getExampleClient() // example helper function
	key := "my_set"
	client.SAdd(context.Background(), key, []string{"member1", "member2"})
	cursor := "0"
	nextCursor, result, err := client.SScan(context.Background(), key, cursor)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(nextCursor, len(result)) // [member1 member2]
	// Output: 0 2
}

func ExampleClusterClient_SScan() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "my_set"
	client.SAdd(context.Background(), key, []string{"member1", "member2"})
	cursor := "0"
	nextCursor, result, err := client.SScan(context.Background(), key, cursor)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(nextCursor, len(result)) // [member1 member2]
	// Output: 0 2
}

func ExampleClient_SScanWithOptions() {
	var client *Client = getExampleClient() // example helper function
	key := "my_set"
	client.SAdd(context.Background(), key, []string{"member1", "member2", "item3"})
	cursor := "0"
	options := options.NewBaseScanOptions().SetMatch("mem*")
	nextCursor, result, err := client.SScanWithOptions(context.Background(), key, cursor, *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(nextCursor, len(result)) // [member1 member2]
	// Output: 0 2
}

func ExampleClusterClient_SScanWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "my_set"
	client.SAdd(context.Background(), key, []string{"member1", "member2", "item3"})
	cursor := "0"
	options := options.NewBaseScanOptions().SetMatch("mem*")
	nextCursor, result, err := client.SScanWithOptions(context.Background(), key, cursor, *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(nextCursor, len(result)) // [member1 member2]
	// Output: 0 2
}

func ExampleClient_SMove() {
	var client *Client = getExampleClient() // example helper function
	source := "my_set_1"
	destination := "my_set_2"
	member := "member1"

	client.SAdd(context.Background(), source, []string{member})

	result, err := client.SMove(context.Background(), source, destination, member)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: true
}

func ExampleClusterClient_SMove() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	source := "{set}1"
	destination := "{set}2"
	member := "member1"

	client.SAdd(context.Background(), source, []string{member})

	result, err := client.SMove(context.Background(), source, destination, member)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: true
}
