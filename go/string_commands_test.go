// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func ExampleClient_Set() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.Set(context.Background(), "my_key", "my_value")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleClusterClient_Set() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	result, err := client.Set(context.Background(), "my_key", "my_value")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleClient_SetWithOptions() {
	var client *Client = getExampleClient() // example helper function

	options := options.NewSetOptions().
		SetExpiry(options.NewExpiryIn(5 * time.Second))
	result, err := client.SetWithOptions(context.Background(), "my_key", "my_value", *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.Value())

	// Output: OK
}

func ExampleClusterClient_SetWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	options := options.NewSetOptions().
		SetExpiry(options.NewExpiryIn(5 * time.Second))
	result, err := client.SetWithOptions(context.Background(), "my_key", "my_value", *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.Value())

	// Output: OK
}

func ExampleClient_Get_keyexists() {
	var client *Client = getExampleClient() // example helper function

	client.Set(context.Background(), "my_key", "my_value")
	result, err := client.Get(context.Background(), "my_key")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.Value())

	// Output: my_value
}

func ExampleClusterClient_Get_keyexists() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.Set(context.Background(), "my_key", "my_value")
	result, err := client.Get(context.Background(), "my_key")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.Value())

	// Output: my_value
}

func ExampleClient_Get_keynotexists() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.Get(context.Background(), "my_key")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.IsNil()) // missing key returns nil result

	// Output: true
}

func ExampleClusterClient_Get_keynotexists() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	result, err := client.Get(context.Background(), "my_key")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.IsNil()) // missing key returns nil result

	// Output: true
}

func ExampleClient_GetEx() {
	var client *Client = getExampleClient() // example helper function

	client.Set(context.Background(), "my_key", "my_value")
	result, err := client.GetEx(context.Background(), "my_key")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.Value())
	ttl, _ := client.TTL(context.Background(), "my_key")
	fmt.Println(ttl)

	// Output:
	// my_value
	// -1
}

func ExampleClusterClient_GetEx() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.Set(context.Background(), "my_key", "my_value")
	result, err := client.GetEx(context.Background(), "my_key")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.Value())
	ttl, _ := client.TTL(context.Background(), "my_key")
	fmt.Println(ttl)

	// Output:
	// my_value
	// -1
}

func ExampleClient_GetExWithOptions() {
	var client *Client = getExampleClient() // example helper function

	client.Set(context.Background(), "my_key", "my_value")
	options := options.NewGetExOptions().
		SetExpiry(options.NewExpiryIn(5 * time.Second))
	result, err := client.GetExWithOptions(context.Background(), "my_key", *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.Value())
	ttl, _ := client.TTL(context.Background(), "my_key")
	fmt.Println(ttl)

	// Output:
	// my_value
	// 5
}

func ExampleClusterClient_GetExWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.Set(context.Background(), "my_key", "my_value")
	options := options.NewGetExOptions().
		SetExpiry(options.NewExpiryIn(5 * time.Second))
	result, err := client.GetExWithOptions(context.Background(), "my_key", *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.Value())
	ttl, _ := client.TTL(context.Background(), "my_key")
	fmt.Println(ttl)

	// Output:
	// my_value
	// 5
}

func ExampleClient_MSet() {
	var client *Client = getExampleClient() // example helper function

	keyValueMap := map[string]string{
		"key1": "value1",
		"key2": "value2",
	}
	result, err := client.MSet(context.Background(), keyValueMap)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleClusterClient_MSet() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	keyValueMap := map[string]string{
		"key1": "value1",
		"key2": "value2",
	}
	result, err := client.MSet(context.Background(), keyValueMap)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleClient_MGet() {
	var client *Client = getExampleClient() // example helper function

	client.MSet(
		context.Background(),
		map[string]string{"my_key1": "my_value1", "my_key2": "my_value2", "my_key3": "my_value3"},
	)
	keys := []string{"my_key1", "my_key2", "my_key3"}
	result, err := client.MGet(context.Background(), keys)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	for _, res := range result {
		fmt.Println(res.Value())
	}

	// Output:
	// my_value1
	// my_value2
	// my_value3
}

func ExampleClusterClient_MGet() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.MSet(
		context.Background(),
		map[string]string{"my_key1": "my_value1", "my_key2": "my_value2", "my_key3": "my_value3"},
	)
	keys := []string{"my_key1", "my_key2", "my_key3"}
	result, err := client.MGet(context.Background(), keys)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	for _, res := range result {
		fmt.Println(res.Value())
	}

	// Output:
	// my_value1
	// my_value2
	// my_value3
}

func ExampleClient_MSetNX() {
	var client *Client = getExampleClient() // example helper function

	keyValueMap := map[string]string{"my_key1": "my_value1", "my_key2": "my_value2"}
	result, err := client.MSetNX(context.Background(), keyValueMap)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	client.Set(context.Background(), "my_key3", "my_value3")
	result, _ = client.MSetNX(context.Background(), map[string]string{"my_key3": "my_value3"})
	fmt.Println(result)

	// Output:
	// true
	// false
}

func ExampleClusterClient_MSetNX() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	keyValueMap := map[string]string{"{my_key}1": "my_value1", "{my_key}2": "my_value2"}
	result, err := client.MSetNX(context.Background(), keyValueMap)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	client.Set(context.Background(), "{my_key}3", "my_value3")
	result, _ = client.MSetNX(context.Background(), map[string]string{"{my_key}3": "my_value3"})
	fmt.Println(result)

	// Output:
	// true
	// false
}

func ExampleClient_Incr() {
	var client *Client = getExampleClient() // example helper function

	client.Set(context.Background(), "my_key", "1")
	result, err := client.Incr(context.Background(), "my_key")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleClusterClient_Incr() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.Set(context.Background(), "my_key", "1")
	result, err := client.Incr(context.Background(), "my_key")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleClient_IncrBy() {
	var client *Client = getExampleClient() // example helper function

	client.Set(context.Background(), "my_key", "5")
	result, err := client.IncrBy(context.Background(), "my_key", 5)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: 10
}

func ExampleClusterClient_IncrBy() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.Set(context.Background(), "my_key", "5")
	result, err := client.IncrBy(context.Background(), "my_key", 5)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	// Output: 10
}

func ExampleClient_IncrByFloat() {
	var client *Client = getExampleClient() // example helper function

	client.Set(context.Background(), "my_key", "1")
	result, err := client.IncrByFloat(context.Background(), "my_key", 5.5)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 6.5
}

func ExampleClusterClient_IncrByFloat() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.Set(context.Background(), "my_key", "1")
	result, err := client.IncrByFloat(context.Background(), "my_key", 5.5)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 6.5
}

func ExampleClient_Decr() {
	var client *Client = getExampleClient() // example helper function

	client.Set(context.Background(), "my_key", "0")
	result, err := client.Decr(context.Background(), "my_key")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: -1
}

func ExampleClusterClient_Decr() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.Set(context.Background(), "my_key", "0")
	result, err := client.Decr(context.Background(), "my_key")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: -1
}

func ExampleClient_DecrBy() {
	var client *Client = getExampleClient() // example helper function

	client.Set(context.Background(), "my_key", "20")
	result, err := client.DecrBy(context.Background(), "my_key", 5)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 15
}

func ExampleClusterClient_DecrBy() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.Set(context.Background(), "my_key", "20")
	result, err := client.DecrBy(context.Background(), "my_key", 5)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 15
}

func ExampleClient_Strlen() {
	var client *Client = getExampleClient() // example helper function

	client.Set(context.Background(), "my_key", "my_value")
	result, err := client.Strlen(context.Background(), "my_key")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 8
}

func ExampleClusterClient_Strlen() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.Set(context.Background(), "my_key", "my_value")
	result, err := client.Strlen(context.Background(), "my_key")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 8
}

func ExampleClient_SetRange_one() {
	var client *Client = getExampleClient() // example helper function

	client.Set(context.Background(), "my_key", "my_value")
	result, err := client.SetRange(context.Background(), "my_key", 3, "example")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	value, _ := client.Get(context.Background(), "my_key")
	fmt.Println(value.Value())

	// Output:
	// 10
	// my_example
}

func ExampleClusterClient_SetRange_one() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.Set(context.Background(), "my_key", "my_value")
	result, err := client.SetRange(context.Background(), "my_key", 3, "example")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	value, _ := client.Get(context.Background(), "my_key")
	fmt.Println(value.Value())

	// Output:
	// 10
	// my_example
}

func ExampleClient_SetRange_two() {
	var client *Client = getExampleClient() // example helper function

	client.Set(context.Background(), "my_key", "愛") // "愛" is a single character in UTF-8, but 3 bytes long
	result, err := client.SetRange(context.Background(), "my_key", 1, "a")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 3
}

func ExampleClusterClient_SetRange_two() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.Set(context.Background(), "my_key", "愛") // "愛" is a single character in UTF-8, but 3 bytes long
	result, err := client.SetRange(context.Background(), "my_key", 1, "a")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 3
}

func ExampleClient_GetRange_one() {
	var client *Client = getExampleClient() // example helper function

	client.Set(context.Background(), "my_key", "Welcome to Valkey Glide!")
	result, err := client.GetRange(context.Background(), "my_key", 0, 7)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: Welcome
}

func ExampleClusterClient_GetRange_one() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.Set(context.Background(), "my_key", "Welcome to Valkey Glide!")
	result, err := client.GetRange(context.Background(), "my_key", 0, 7)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: Welcome
}

func ExampleClient_GetRange_two() {
	var client *Client = getExampleClient() // example helper function

	client.Set(context.Background(), "my_key", "愛")
	fmt.Println([]byte("愛")) // "愛" is a single character in UTF-8, but 3 bytes long
	result, err := client.GetRange(context.Background(), "my_key", 0, 1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println([]byte(result))

	// Output:
	// [230 132 155]
	// [230 132]
}

func ExampleClusterClient_GetRange_two() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.Set(context.Background(), "my_key", "愛")
	fmt.Println([]byte("愛")) // "愛" is a single character in UTF-8, but 3 bytes long
	result, err := client.GetRange(context.Background(), "my_key", 0, 1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println([]byte(result))

	// Output:
	// [230 132 155]
	// [230 132]
}

func ExampleClient_Append() {
	var client *Client = getExampleClient() // example helper function

	client.Set(context.Background(), "my_key", "my_valu")
	result, err := client.Append(context.Background(), "my_key", "e")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	value, _ := client.Get(context.Background(), "my_key")
	fmt.Println(value.Value())

	// Output:
	// 8
	// my_value
}

func ExampleClusterClient_Append() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.Set(context.Background(), "my_key", "my_valu")
	result, err := client.Append(context.Background(), "my_key", "e")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	value, _ := client.Get(context.Background(), "my_key")
	fmt.Println(value.Value())

	// Output:
	// 8
	// my_value
}

func ExampleClient_LCS() {
	var client *Client = getExampleClient() // example helper function

	client.MSet(context.Background(), map[string]string{"my_key1": "oh my gosh", "my_key2": "hello world"})
	result, err := client.LCS(context.Background(), "my_key1", "my_key2")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.MatchString)

	// LCS is only available in 7.0 and above. It will fail in any server < 7.0

	// Output: h o
}

func ExampleClusterClient_LCS() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.MSet(context.Background(), map[string]string{"{my_key}1": "oh my gosh", "{my_key}2": "hello world"})
	result, err := client.LCS(context.Background(), "{my_key}1", "{my_key}2")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.MatchString)

	// LCS is only available in 7.0 and above. It will fail in any release < 7.0

	// Output: h o
}

func ExampleClient_GetDel() {
	var client *Client = getExampleClient() // example helper function

	client.Set(context.Background(), "my_key", "my_value")
	result, err := client.GetDel(context.Background(), "my_key") // return value and delete key
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.Value())
	value, _ := client.Get(context.Background(), "my_key") // key should be missing
	fmt.Println(value.IsNil())

	// Output:
	// my_value
	// true
}

func ExampleClusterClient_GetDel() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.Set(context.Background(), "my_key", "my_value")
	result, err := client.GetDel(context.Background(), "my_key") // return value and delete key
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.Value())
	value, _ := client.Get(context.Background(), "my_key") // key should be missing
	fmt.Println(value.IsNil())

	// Output:
	// my_value
	// true
}

func ExampleClient_LCSLen() {
	var client *Client = getExampleClient() // example helper function

	client.Set(context.Background(), "my_key1", "ohmytext")
	client.Set(context.Background(), "my_key2", "mynewtext")

	result, err := client.LCSLen(context.Background(), "my_key1", "my_key2")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.Len)

	// LCS is only available in 7.0 and above. It will fail in any server < 7.0

	// Output: 6
}

func ExampleClusterClient_LCSLen() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.Set(context.Background(), "{my_key}1", "ohmytext")
	client.Set(context.Background(), "{my_key}2", "mynewtext")

	result, err := client.LCSLen(context.Background(), "{my_key}1", "{my_key}2")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.Len)

	// LCS is only available in 7.0 and above. It will fail in any release < 7.0

	// Output: 6
}

func ExampleClient_LCSWithOptions() {
	var client *Client = getExampleClient() // example helper function

	client.Set(context.Background(), "my_key1", "ohmytext")
	client.Set(context.Background(), "my_key2", "mynewtext")

	// Basic LCS IDX without additional options
	opts := options.NewLCSIdxOptions()
	result1, err := client.LCSWithOptions(context.Background(), "my_key1", "my_key2", *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonRes1, _ := json.Marshal(result1)
	fmt.Println("Basic LCS result:", string(jsonRes1))

	// LCS IDX with MINMATCHLEN = 4
	optsWithMin := options.NewLCSIdxOptions()
	optsWithMin.SetMinMatchLen(4)
	result2, err := client.LCSWithOptions(context.Background(), "my_key1", "my_key2", *optsWithMin)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonRes2, _ := json.Marshal(result2)
	fmt.Println("With MinMatchLen 4:", string(jsonRes2))

	// LCS is only available in 7.0 and above. It will fail in any server < 7.0

	// Output:
	// Basic LCS result: {"MatchString":"","Matches":[{"Key1":{"Start":4,"End":7},"Key2":{"Start":5,"End":8},"MatchLen":4},{"Key1":{"Start":2,"End":3},"Key2":{"Start":0,"End":1},"MatchLen":2}],"Len":6}
	// With MinMatchLen 4: {"MatchString":"","Matches":[{"Key1":{"Start":4,"End":7},"Key2":{"Start":5,"End":8},"MatchLen":4}],"Len":6}
}

func ExampleClusterClient_LCSWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.Set(context.Background(), "{key}1", "ohmytext")
	client.Set(context.Background(), "{key}2", "mynewtext")

	// Basic LCS IDX without additional options
	opts := options.NewLCSIdxOptions()
	result1, err := client.LCSWithOptions(context.Background(), "{key}1", "{key}2", *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonRes1, _ := json.Marshal(result1)
	fmt.Println("Basic LCS result:", string(jsonRes1))

	// LCS IDX with MINMATCHLEN = 4
	optsWithMin := options.NewLCSIdxOptions()
	optsWithMin.SetMinMatchLen(4)
	result2, err := client.LCSWithOptions(context.Background(), "{key}1", "{key}2", *optsWithMin)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonRes2, _ := json.Marshal(result2)
	fmt.Println("With MinMatchLen 4:", string(jsonRes2))

	// LCS is only available in 7.0 and above. It will fail in any server < 7.0

	// Output:
	// Basic LCS result: {"MatchString":"","Matches":[{"Key1":{"Start":4,"End":7},"Key2":{"Start":5,"End":8},"MatchLen":4},{"Key1":{"Start":2,"End":3},"Key2":{"Start":0,"End":1},"MatchLen":2}],"Len":6}
	// With MinMatchLen 4: {"MatchString":"","Matches":[{"Key1":{"Start":4,"End":7},"Key2":{"Start":5,"End":8},"MatchLen":4}],"Len":6}
}
