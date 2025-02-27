// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"fmt"

	"github.com/valkey-io/valkey-glide/go/api/options"
)

func ExampleGlideClient_SAdd() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "my_set"

	result, err := client.SAdd(key, []string{"member1", "member2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleGlideClusterClient_SAdd() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "my_set"

	result, err := client.SAdd(key, []string{"member1", "member2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleGlideClient_SRem() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "my_set"

	client.SAdd(key, []string{"member1", "member2"})
	result, err := client.SRem(key, []string{"member1", "member2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleGlideClusterClient_SRem() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "my_set"

	client.SAdd(key, []string{"member1", "member2", "member3", "member4", "member5"})
	result, err := client.SRem(key, []string{"member1", "member2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleGlideClient_SMembers() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "my_set"

	client.SAdd(key, []string{"member1", "member2"})

	result, err := client.SMembers(key)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: map[member1:{} member2:{}]
}

func ExampleGlideClusterClient_SMembers() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "my_set"

	client.SAdd(key, []string{"member1", "member2"})

	result, err := client.SMembers(key)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: map[member1:{} member2:{}]
}

func ExampleGlideClient_SCard() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "my_set"

	client.SAdd(key, []string{"member1", "member2"})

	result, err := client.SCard(key)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: 2
}

func ExampleGlideClusterClient_SCard() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "my_set"

	client.SAdd(key, []string{"member1", "member2"})

	result, err := client.SCard(key)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: 2
}

func ExampleGlideClient_SIsMember() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "my_set"

	client.SAdd(key, []string{"member1", "member2"})

	result, err := client.SIsMember(key, "member1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: true
}

func ExampleGlideClusterClient_SIsMember() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "my_set"

	client.SAdd(key, []string{"member1", "member2"})

	result, err := client.SIsMember(key, "member1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: true
}

func ExampleGlideClient_SDiff() {
	var client *GlideClient = getExampleGlideClient() // example helper function
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

func ExampleGlideClusterClient_SDiff() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
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

func ExampleGlideClient_SDiffStore() {
	var client *GlideClient = getExampleGlideClient() // example helper function
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

func ExampleGlideClusterClient_SDiffStore() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
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

func ExampleGlideClient_SInter() {
	var client *GlideClient = getExampleGlideClient() // example helper function
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

func ExampleGlideClusterClient_SInter() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
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

func ExampleGlideClient_SInterStore() {
	var client *GlideClient = getExampleGlideClient() // example helper function
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

func ExampleGlideClusterClient_SInterStore() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
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

func ExampleGlideClient_SInterCard() {
	var client *GlideClient = getExampleGlideClient() // example helper function
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

func ExampleGlideClusterClient_SInterCard() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
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

func ExampleGlideClient_SInterCardLimit() {
	var client *GlideClient = getExampleGlideClient() // example helper function
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

func ExampleGlideClusterClient_SInterCardLimit() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
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

func ExampleGlideClient_SRandMember() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "my_set"

	client.SAdd(key, []string{"member1", "member2"})

	result, err := client.SRandMember(key)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.IsNil()) // Unable to test for a random value so just check if it is not nil

	// Output: false
}

func ExampleGlideClusterClient_SRandMember() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "my_set"

	client.SAdd(key, []string{"member1", "member2"})

	result, err := client.SRandMember(key)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.IsNil()) // Unable to test for a random value so just check if it is not nil

	// Output: false
}

func ExampleGlideClient_SPop() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "my_set"

	client.SAdd(key, []string{"member1", "member2"})

	result, err := client.SPop(key)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.IsNil())
	// Output: false
}

func ExampleGlideClusterClient_SPop() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "my_set"

	client.SAdd(key, []string{"member1", "member2"})

	result, err := client.SPop(key)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.IsNil())
	// Output: false
}

func ExampleGlideClient_SMIsMember() {
	var client *GlideClient = getExampleGlideClient() // example helper function
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

func ExampleGlideClusterClient_SMIsMember() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
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

func ExampleGlideClient_SUnionStore() {
	var client *GlideClient = getExampleGlideClient() // example helper function
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

func ExampleGlideClusterClient_SUnionStore() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
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

func ExampleGlideClient_SUnion() {
	var client *GlideClient = getExampleGlideClient() // example helper function
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

func ExampleGlideClusterClient_SUnion() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
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

func ExampleGlideClient_SScan() {
	var client *GlideClient = getExampleGlideClient() // example helper function
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

func ExampleGlideClusterClient_SScan() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
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

func ExampleGlideClient_SScanWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function
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

func ExampleGlideClusterClient_SScanWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
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

func ExampleGlideClient_SMove() {
	var client *GlideClient = getExampleGlideClient() // example helper function
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

func ExampleGlideClusterClient_SMove() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
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
