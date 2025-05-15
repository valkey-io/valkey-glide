// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"fmt"

	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func ExampleClient_SAdd() {
	var client *Client = getExampleGlideClient() // example helper function
	key := "my_set"

	result, err := client.SAdd(key, []string{"member1", "member2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleClusterClient_SAdd() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	key := "my_set"

	result, err := client.SAdd(key, []string{"member1", "member2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleClient_SRem() {
	var client *Client = getExampleGlideClient() // example helper function
	key := "my_set"

	client.SAdd(key, []string{"member1", "member2"})
	result, err := client.SRem(key, []string{"member1", "member2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleClusterClient_SRem() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	key := "my_set"

	client.SAdd(key, []string{"member1", "member2", "member3", "member4", "member5"})
	result, err := client.SRem(key, []string{"member1", "member2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleClient_SMembers() {
	var client *Client = getExampleGlideClient() // example helper function
	key := "my_set"

	client.SAdd(key, []string{"member1", "member2"})

	result, err := client.SMembers(key)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: map[member1:{} member2:{}]
}

func ExampleClusterClient_SMembers() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	key := "my_set"

	client.SAdd(key, []string{"member1", "member2"})

	result, err := client.SMembers(key)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: map[member1:{} member2:{}]
}

func ExampleClient_SCard() {
	var client *Client = getExampleGlideClient() // example helper function
	key := "my_set"

	client.SAdd(key, []string{"member1", "member2"})

	result, err := client.SCard(key)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: 2
}

func ExampleClusterClient_SCard() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	key := "my_set"

	client.SAdd(key, []string{"member1", "member2"})

	result, err := client.SCard(key)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: 2
}

func ExampleClient_SIsMember() {
	var client *Client = getExampleGlideClient() // example helper function
	key := "my_set"

	client.SAdd(key, []string{"member1", "member2"})

	result, err := client.SIsMember(key, "member1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: true
}

func ExampleClusterClient_SIsMember() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	key := "my_set"

	client.SAdd(key, []string{"member1", "member2"})

	result, err := client.SIsMember(key, "member1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: true
}

func ExampleClient_SDiff() {
	var client *Client = getExampleGlideClient() // example helper function
	key1 := "my_set_1"
	key2 := "my_set_2"

	client.SAdd(key1, []string{"member1", "member2"})
	client.SAdd(key2, []string{"member2"})

	result, err := client.SDiff([]string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: map[member1:{}]
}

func ExampleClusterClient_SDiff() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	key1 := "{set}1"
	key2 := "{set}2"

	client.SAdd(key1, []string{"member1", "member2"})
	client.SAdd(key2, []string{"member2"})

	result, err := client.SDiff([]string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: map[member1:{}]
}

func ExampleClient_SDiffStore() {
	var client *Client = getExampleGlideClient() // example helper function
	key1 := "my_set_1"
	key2 := "my_set_2"
	destination := "my_set_diff"

	client.SAdd(key1, []string{"member1", "member2"})
	client.SAdd(key2, []string{"member2"})

	result, err := client.SDiffStore(destination, []string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: 1
}

func ExampleClusterClient_SDiffStore() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	key1 := "{set}1"
	key2 := "{set}2"
	destination := "{set}3"

	client.SAdd(key1, []string{"member1", "member2"})
	client.SAdd(key2, []string{"member2"})

	result, err := client.SDiffStore(destination, []string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: 1
}

func ExampleClient_SInter() {
	var client *Client = getExampleGlideClient() // example helper function
	key1 := "my_set_1"
	key2 := "my_set_2"

	client.SAdd(key1, []string{"member1", "member2"})
	client.SAdd(key2, []string{"member2"})

	result, err := client.SInter([]string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: map[member2:{}]
}

func ExampleClusterClient_SInter() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	key1 := "{set}1"
	key2 := "{set}2"

	client.SAdd(key1, []string{"member1", "member2"})
	client.SAdd(key2, []string{"member2"})

	result, err := client.SInter([]string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: map[member2:{}]
}

func ExampleClient_SInterStore() {
	var client *Client = getExampleGlideClient() // example helper function
	key1 := "my_set_1"
	key2 := "my_set_2"
	destination := "my_set_inter"

	client.SAdd(key1, []string{"member1", "member2"})
	client.SAdd(key2, []string{"member2"})

	result, err := client.SInterStore(destination, []string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: 1
}

func ExampleClusterClient_SInterStore() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	key1 := "{set}1"
	key2 := "{set}2"
	destination := "{set}3"

	client.SAdd(key1, []string{"member1", "member2"})
	client.SAdd(key2, []string{"member2"})

	result, err := client.SInterStore(destination, []string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: 1
}

func ExampleClient_SInterCard() {
	var client *Client = getExampleGlideClient() // example helper function
	key1 := "my_set_1"
	key2 := "my_set_2"

	client.SAdd(key1, []string{"member1", "member2"})
	client.SAdd(key2, []string{"member2"})

	result, err := client.SInterCard([]string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: 1
}

func ExampleClusterClient_SInterCard() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	key1 := "{set}1"
	key2 := "{set}2"

	client.SAdd(key1, []string{"member1", "member2"})
	client.SAdd(key2, []string{"member2"})

	result, err := client.SInterCard([]string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: 1
}

func ExampleClient_SInterCardLimit() {
	var client *Client = getExampleGlideClient() // example helper function
	key1 := "my_set_1"
	key2 := "my_set_2"
	limit := int64(1)

	client.SAdd(key1, []string{"member1", "member2", "member3"})
	client.SAdd(key2, []string{"member2", "member3"})

	result, err := client.SInterCardLimit([]string{key1, key2}, limit)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: 1
}

func ExampleClusterClient_SInterCardLimit() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	key1 := "{set}1"
	key2 := "{set}2"
	limit := int64(1)

	client.SAdd(key1, []string{"member1", "member2", "member3"})
	client.SAdd(key2, []string{"member2", "member3"})

	result, err := client.SInterCardLimit([]string{key1, key2}, limit)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: 1
}

func ExampleClient_SRandMember() {
	var client *Client = getExampleGlideClient() // example helper function
	key := "my_set"

	client.SAdd(key, []string{"member1", "member2"})

	result, err := client.SRandMember(key)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.IsNil()) // Unable to test for a random value so just check if it is not nil

	// Output: false
}

func ExampleClusterClient_SRandMember() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	key := "my_set"

	client.SAdd(key, []string{"member1", "member2"})

	result, err := client.SRandMember(key)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.IsNil()) // Unable to test for a random value so just check if it is not nil

	// Output: false
}

func ExampleClient_SPop() {
	var client *Client = getExampleGlideClient() // example helper function
	key := "my_set"

	client.SAdd(key, []string{"member1", "member2"})

	result, err := client.SPop(key)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.IsNil())
	// Output: false
}

func ExampleClusterClient_SPop() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	key := "my_set"

	client.SAdd(key, []string{"member1", "member2"})

	result, err := client.SPop(key)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.IsNil())
	// Output: false
}

func ExampleClient_SMIsMember() {
	var client *Client = getExampleGlideClient() // example helper function
	key := "my_set"

	members := []string{"member1", "member2"}
	client.SAdd(key, members)

	memberTest := []string{"member1", "member2", "member3"}
	result, err := client.SMIsMember(key, memberTest)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: [true true false]
}

func ExampleClusterClient_SMIsMember() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	key := "my_set"

	members := []string{"member1", "member2"}
	client.SAdd(key, members)

	memberTest := []string{"member1", "member2", "member3"}
	result, err := client.SMIsMember(key, memberTest)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: [true true false]
}

func ExampleClient_SUnionStore() {
	var client *Client = getExampleGlideClient() // example helper function
	key1 := "my_set_1"
	key2 := "my_set_2"
	destination := "my_set_union"

	client.SAdd(key1, []string{"member1", "member2"})
	client.SAdd(key2, []string{"member2", "member3"})

	result, err := client.SUnionStore(destination, []string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: 3
}

func ExampleClusterClient_SUnionStore() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	key1 := "{set}1"
	key2 := "{set}2"
	destination := "{set}3"

	client.SAdd(key1, []string{"member1", "member2"})
	client.SAdd(key2, []string{"member2", "member3"})

	result, err := client.SUnionStore(destination, []string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: 3
}

func ExampleClient_SUnion() {
	var client *Client = getExampleGlideClient() // example helper function
	key1 := "my_set_1"
	key2 := "my_set_2"

	client.SAdd(key1, []string{"member1", "member2"})
	client.SAdd(key2, []string{"member2", "member3"})

	result, err := client.SUnion([]string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: map[member1:{} member2:{} member3:{}]
}

func ExampleClusterClient_SUnion() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	key1 := "{set}1"
	key2 := "{set}2"

	client.SAdd(key1, []string{"member1", "member2"})
	client.SAdd(key2, []string{"member2", "member3"})

	result, err := client.SUnion([]string{key1, key2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: map[member1:{} member2:{} member3:{}]
}

func ExampleClient_SScan() {
	var client *Client = getExampleGlideClient() // example helper function
	key := "my_set"
	client.SAdd(key, []string{"member1", "member2"})
	cursor := "0"
	result, nextCursor, err := client.SScan(key, cursor)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result, nextCursor)
	// Output: 0 [member1 member2]
}

func ExampleClusterClient_SScan() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	key := "my_set"
	client.SAdd(key, []string{"member1", "member2"})
	cursor := "0"
	result, nextCursor, err := client.SScan(key, cursor)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result, nextCursor)
	// Output: 0 [member1 member2]
}

func ExampleClient_SScanWithOptions() {
	var client *Client = getExampleGlideClient() // example helper function
	key := "my_set"
	client.SAdd(key, []string{"member1", "member2", "item3"})
	cursor := "0"
	options := options.NewBaseScanOptions().SetMatch("mem*")
	result, nextCursor, err := client.SScanWithOptions(key, cursor, *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result, nextCursor)
	// Output: 0 [member1 member2]
}

func ExampleClusterClient_SScanWithOptions() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	key := "my_set"
	client.SAdd(key, []string{"member1", "member2", "item3"})
	cursor := "0"
	options := options.NewBaseScanOptions().SetMatch("mem*")
	result, nextCursor, err := client.SScanWithOptions(key, cursor, *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result, nextCursor)
	// Output: 0 [member1 member2]
}

func ExampleClient_SMove() {
	var client *Client = getExampleGlideClient() // example helper function
	source := "my_set_1"
	destination := "my_set_2"
	member := "member1"

	client.SAdd(source, []string{member})

	result, err := client.SMove(source, destination, member)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: true
}

func ExampleClusterClient_SMove() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	source := "{set}1"
	destination := "{set}2"
	member := "member1"

	client.SAdd(source, []string{member})

	result, err := client.SMove(source, destination, member)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: true
}
