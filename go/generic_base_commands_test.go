// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"fmt"
	"time"

	"github.com/valkey-io/valkey-glide/go/v2/constants"

	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func ExampleClient_Del() {
	var client *Client = getExampleGlideClient() // example helper function
	result, err := client.Set("key1", "someValue")
	result1, err := client.Set("key2", "someValue")
	result2, err := client.Del([]string{"key1", "key2", "key3"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// OK
	// OK
	// 2
}

func ExampleClusterClient_Del() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Set("key1", "someValue")
	result1, err := client.Set("key2", "someValue")
	result2, err := client.Del([]string{"key1", "key2", "key3"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// OK
	// OK
	// 2
}

func ExampleClient_Exists() {
	var client *Client = getExampleGlideClient() // example helper function
	result, err := client.Set("key1", "someValue")
	result1, err := client.Set("key2", "someValue")
	result2, err := client.Exists([]string{"key1", "key2", "key3"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// OK
	// OK
	// 2
}

func ExampleClusterClient_Exists() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Set("key1", "someValue")
	result1, err := client.Set("key2", "someValue")
	result2, err := client.Exists([]string{"key1", "key2", "key3"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// OK
	// OK
	// 2
}

func ExampleClient_Expire() {
	var client *Client = getExampleGlideClient() // example helper function
	result, err := client.Set("key", "someValue")
	result1, err := client.ExpireAt("key", time.Now().Unix()+1)
	result2, err := client.Expire("key", 1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// OK
	// true
	// true
}

func ExampleClusterClient_Expire() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Set("key", "someValue")
	result1, err := client.ExpireAt("key", time.Now().Unix()+1)
	result2, err := client.Expire("key", 1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// OK
	// true
	// true
}

func ExampleClient_ExpireWithOptions() {
	var client *Client = getExampleGlideClient() // example helper function
	result, err := client.Set("key", "someValue")
	result1, err := client.ExpireWithOptions("key", 1, constants.HasNoExpiry)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// true
}

func ExampleClusterClient_ExpireWithOptions() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Set("key", "someValue")
	result1, err := client.ExpireWithOptions("key", 1, constants.HasNoExpiry)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// true
}

func ExampleClient_ExpireAt() {
	var client *Client = getExampleGlideClient() // example helper function
	result, err := client.Set("key", "someValue")
	result1, err := client.ExpireAt("key", time.Now().Unix()+1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// true
}

func ExampleClusterClient_ExpireAt() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Set("key", "someValue")
	result1, err := client.ExpireAt("key", time.Now().Unix()+1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// true
}

func ExampleClient_ExpireAtWithOptions() {
	var client *Client = getExampleGlideClient() // example helper function
	result, err := client.Set("key", "someValue")
	result1, err := client.ExpireAtWithOptions("key", time.Now().Unix()+1, constants.HasNoExpiry)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// true
}

func ExampleClusterClient_ExpireAtWithOptions() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Set("key", "someValue")
	result1, err := client.ExpireAtWithOptions("key", time.Now().Unix()+1, constants.HasNoExpiry)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// true
}

func ExampleClient_PExpire() {
	var client *Client = getExampleGlideClient() // example helper function
	result, err := client.Set("key", "someValue")
	result1, err := client.PExpire("key", int64(5*1000))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// true
}

func ExampleClusterClient_PExpire() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Set("key", "someValue")
	result1, err := client.PExpire("key", int64(5*1000))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// true
}

func ExampleClient_PExpireWithOptions() {
	var client *Client = getExampleGlideClient() // example helper function
	result, err := client.Set("key", "someValue")
	result1, err := client.PExpireWithOptions("key", int64(5*1000), constants.HasNoExpiry)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// true
}

func ExampleClusterClient_PExpireWithOptions() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Set("key", "someValue")
	result1, err := client.PExpireWithOptions("key", int64(5*1000), constants.HasNoExpiry)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// true
}

func ExampleClient_PExpireAt() {
	var client *Client = getExampleGlideClient() // example helper function
	result, err := client.Set("key", "someValue")
	result1, err := client.PExpireAt("key", time.Now().Unix()*1000)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// true
}

func ExampleClusterClient_PExpireAt() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Set("key", "someValue")
	result1, err := client.PExpireAt("key", time.Now().Unix()*1000)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// true
}

func ExampleClient_PExpireAtWithOptions() {
	var client *Client = getExampleGlideClient() // example helper function
	result, err := client.Set("key", "someValue")
	result1, err := client.PExpireAtWithOptions("key", time.Now().Unix()*1000, constants.HasNoExpiry)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// true
}

func ExampleClusterClient_PExpireAtWithOptions() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Set("key", "someValue")
	result1, err := client.PExpireAtWithOptions("key", time.Now().Unix()*1000, constants.HasNoExpiry)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// true
}

func ExampleClient_ExpireTime() {
	var client *Client = getExampleGlideClient() // example helper function
	result, err := client.Set("key", "someValue")
	result1, err := client.ExpireTime("key")
	_, err = client.ExpireAt("key", time.Now().Unix()*1000) // ExpireTime("key") returns proper unix timestamp in seconds
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// -1
}

func ExampleClusterClient_ExpireTime() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Set("key", "someValue")
	result1, err := client.ExpireTime("key")
	_, err = client.ExpireAt("key", time.Now().Unix()*1000) // ExpireTime("key") returns proper unix timestamp in seconds
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// -1
}

func ExampleClient_PExpireTime() {
	var client *Client = getExampleGlideClient() // example helper function
	result, err := client.Set("key", "someValue")
	result1, err := client.PExpireTime("key")
	_, err = client.PExpireAt("key", time.Now().Unix()*1000) // PExpireTime("key") returns proper unix time in milliseconds
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// -1
}

func ExampleClusterClient_PExpireTime() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Set("key", "someValue")
	result1, err := client.PExpireTime("key")
	_, err = client.PExpireAt("key", time.Now().Unix()*1000) // PExpireTime("key") returns proper unix time in milliseconds
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// -1
}

func ExampleClient_TTL() {
	var client *Client = getExampleGlideClient() // example helper function
	result, err := client.Set("key", "someValue")
	result1, err := client.TTL("key")
	_, err = client.ExpireAt("key", time.Now().Unix()*1000) // TTL("key") returns proper TTL in seconds
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// -1
}

func ExampleClusterClient_TTL() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Set("key", "someValue")
	result1, err := client.TTL("key")
	_, err = client.ExpireAt("key", time.Now().Unix()*1000) // TTL("key") returns proper TTL in seconds
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// -1
}

func ExampleClient_PTTL() {
	var client *Client = getExampleGlideClient() // example helper function
	result, err := client.Set("key", "someValue")
	result1, err := client.PTTL("key")
	_, err = client.PExpireAt("key", time.Now().Unix()*100000) // PTTL("key") returns proper TTL in milliseconds
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// -1
}

func ExampleClusterClient_PTTL() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Set("key", "someValue")
	result1, err := client.PTTL("key")
	_, err = client.PExpireAt("key", time.Now().Unix()*100000) // PTTL("key") returns proper TTL in milliseconds
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// -1
}

func ExampleClient_Unlink() {
	var client *Client = getExampleGlideClient() // example helper function
	result, err := client.Set("key1", "someValue")
	result1, err := client.Set("key2", "someValue")
	result2, err := client.Unlink([]string{"key1", "key2", "key3"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// OK
	// OK
	// 2
}

func ExampleClusterClient_Unlink() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Set("key1", "someValue")
	result1, err := client.Set("key2", "someValue")
	result2, err := client.Unlink([]string{"key1", "key2", "key3"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// OK
	// OK
	// 2
}

func ExampleClient_Touch() {
	var client *Client = getExampleGlideClient() // example helper function
	result, err := client.Set("key1", "someValue")
	result1, err := client.Set("key2", "someValue")
	result2, err := client.Touch([]string{"key1", "key2", "key3"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// OK
	// OK
	// 2
}

func ExampleClusterClient_Touch() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Set("key1", "someValue")
	result1, err := client.Set("key2", "someValue")
	result2, err := client.Touch([]string{"key1", "key2", "key3"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// OK
	// OK
	// 2
}

func ExampleClient_Type() {
	var client *Client = getExampleGlideClient() // example helper function
	result, err := client.Set("key1", "someValue")
	result1, err := client.Type("key1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// string
}

func ExampleClusterClient_Type() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Set("key1", "someValue")
	result1, err := client.Type("key1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// string
}

func ExampleClient_Rename() {
	var client *Client = getExampleGlideClient() // example helper function
	result, err := client.Set("key1", "someValue")
	result1, err := client.Rename("key1", "key2")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// OK
}

func ExampleClusterClient_Rename() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Set("{key}1", "someValue")
	result1, err := client.Rename("{key}1", "{key}2")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// OK
}

func ExampleClient_RenameNX() {
	var client *Client = getExampleGlideClient() // example helper function
	result, err := client.Set("key1", "someValue")
	result1, err := client.RenameNX("key1", "key2")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// true
}

func ExampleClusterClient_RenameNX() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Set("{key}1", "someValue")
	result1, err := client.RenameNX("{key}1", "{key}2")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// true
}

func ExampleClient_Persist() {
	var client *Client = getExampleGlideClient() // example helper function
	result, err := client.Set("key1", "someValue")
	result1, err := client.ExpireAt("key1", time.Now().Unix()*1000)
	result2, err := client.Persist("key1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// OK
	// true
	// true
}

func ExampleClusterClient_Persist() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Set("key1", "someValue")
	result1, err := client.ExpireAt("key1", time.Now().Unix()*1000)
	result2, err := client.Persist("key1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// OK
	// true
	// true
}

func ExampleClient_Restore() {
	var client *Client = getExampleGlideClient() // example helper function
	result, err := client.Set("key1", "someValue")
	dump, err := client.Dump("key1")
	result1, err := client.Del([]string{"key1"})
	result2, err := client.Restore("key1", 0, dump.Value())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// OK
	// 1
	// OK
}

func ExampleClusterClient_Restore() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Set("key1", "someValue")
	dump, err := client.Dump("key1")
	result1, err := client.Del([]string{"key1"})
	result2, err := client.Restore("key1", 0, dump.Value())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// OK
	// 1
	// OK
}

func ExampleClient_RestoreWithOptions() {
	var client *Client = getExampleGlideClient() // example helper function
	result, err := client.Set("key1", "someValue")
	dump, err := client.Dump("key1")
	result1, err := client.Del([]string{"key1"})
	opts := options.NewRestoreOptions().SetReplace().SetABSTTL().SetEviction(constants.FREQ, 10)
	result2, err := client.RestoreWithOptions("key1", 0, dump.Value(), *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// OK
	// 1
	// OK
}

func ExampleClusterClient_RestoreWithOptions() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Set("key1", "someValue")
	dump, err := client.Dump("key1")
	result1, err := client.Del([]string{"key1"})
	opts := options.NewRestoreOptions().SetReplace().SetABSTTL().SetEviction(constants.FREQ, 10)
	result2, err := client.RestoreWithOptions("key1", 0, dump.Value(), *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// OK
	// 1
	// OK
}

func ExampleClient_ObjectEncoding() {
	var client *Client = getExampleGlideClient() // example helper function
	result, err := client.Set("key1", "someValue")
	result1, err := client.ObjectEncoding("key1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// {embstr false}
}

func ExampleClusterClient_ObjectEncoding() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Set("key1", "someValue")
	result1, err := client.ObjectEncoding("key1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// {embstr false}
}

func ExampleClient_Dump() {
	var client *Client = getExampleGlideClient() // example helper function
	result, err := client.Set("key1", "someValue")
	result1, err := client.Dump("key1") // Contains serialized value of the data stored at key1
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1.IsNil())

	// Output:
	// OK
	// false
}

func ExampleClusterClient_Dump() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Set("key1", "someValue")
	result1, err := client.Dump("key1") // Contains serialized value of the data stored at key1
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1.IsNil())

	// Output:
	// OK
	// false
}

func ExampleClient_ObjectFreq() {
	var client *Client = getExampleGlideClient() // example helper function

	client.ConfigSet(map[string]string{"maxmemory-policy": "allkeys-lfu"}) // example configuration
	client.Set("key1", "someValue")
	client.Set("key1", "someOtherValue")

	result, err := client.ObjectFreq("key1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: {6 false}
}

func ExampleClusterClient_ObjectFreq() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	// TODO: Once ConfigSet and ConfigGet are implemented, replace CustomCommand
	client.CustomCommand([]string{"CONFIG", "SET", "maxmemory-policy", "allkeys-lfu"})
	result, err := client.Set("key1", "someValue")
	_, err = client.Set("key1", "someOtherValue")
	result1, err := client.ObjectFreq("key1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// {6 false}
}

func ExampleClient_ObjectIdleTime() {
	var client *Client = getExampleGlideClient()                           // example helper function
	client.ConfigSet(map[string]string{"maxmemory-policy": "allkeys-lru"}) // example configuration
	result, err := client.Set("key1", "someValue")
	result1, err := client.ObjectIdleTime("key1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// {0 false}
}

func ExampleClusterClient_ObjectIdleTime() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	// TODO: Once ConfigSet and ConfigGet are implemented, replace CustomCommand
	client.CustomCommand([]string{"CONFIG", "SET", "maxmemory-policy", "allkeys-lru"})
	result, err := client.Set("key1", "someValue")
	result1, err := client.ObjectIdleTime("key1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// {0 false}
}

func ExampleClient_ObjectRefCount() {
	var client *Client = getExampleGlideClient()                                     // example helper function
	_, err := client.ConfigSet(map[string]string{"maxmemory-policy": "allkeys-lru"}) // example configuration
	result, err := client.Set("key1", "someValue")
	result1, err := client.ObjectRefCount("key1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// {1 false}
}

func ExampleClusterClient_ObjectRefCount() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	client.CustomCommand([]string{"CONFIG", "SET", "maxmemory-policy", "allkeys-lru"})
	result, err := client.Set("key1", "someValue")
	result1, err := client.ObjectRefCount("key1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// OK
	// {1 false}
}

func ExampleClient_Sort() {
	var client *Client = getExampleGlideClient() // example helper function
	result, err := client.LPush("key1", []string{"1", "3", "2", "4"})
	result1, err := client.Sort("key1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 4
	// [{1 false} {2 false} {3 false} {4 false}]
}

func ExampleClusterClient_Sort() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.LPush("key1", []string{"1", "3", "2", "4"})
	result1, err := client.Sort("key1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 4
	// [{1 false} {2 false} {3 false} {4 false}]
}

func ExampleClient_SortWithOptions() {
	var client *Client = getExampleGlideClient() // example helper function
	opts := options.NewSortOptions().SetByPattern("weight_*").SetIsAlpha(false).SetOrderBy(options.ASC)
	client.Set("weight_item1", "3")
	client.Set("weight_item2", "1")
	client.Set("weight_item3", "2")
	result, err := client.LPush("key1", []string{"item1", "item2", "item3"})
	result1, err := client.SortWithOptions("key1", *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 3
	// [{item2 false} {item3 false} {item1 false}]
}

func ExampleClusterClient_SortWithOptions() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	opts := options.NewSortOptions().SetIsAlpha(false).SetOrderBy(options.ASC)
	result, err := client.LPush("key1", []string{"3", "1", "2"})
	result1, err := client.SortWithOptions("key1", *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 3
	// [{1 false} {2 false} {3 false}]
}

func ExampleClient_SortStore() {
	var client *Client = getExampleGlideClient() // example helper function
	result, err := client.LPush("key1", []string{"1", "3", "2", "4"})
	result1, err := client.SortStore("key1", "key1_store")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 4
	// 4
}

func ExampleClusterClient_SortStore() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.LPush("{key}1", []string{"1", "3", "2", "4"})
	result1, err := client.SortStore("{key}1", "{key}2")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 4
	// 4
}

func ExampleClient_SortStoreWithOptions() {
	var client *Client = getExampleGlideClient() // example helper function
	opts := options.NewSortOptions().SetByPattern("weight_*").SetIsAlpha(false).SetOrderBy(options.ASC)
	client.Set("weight_item1", "3")
	client.Set("weight_item2", "1")
	client.Set("weight_item3", "2")
	result, err := client.LPush("key1", []string{"item1", "item2", "item3"})
	result1, err := client.SortStoreWithOptions("key1", "key1_store", *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 3
	// 3
}

func ExampleClusterClient_SortStoreWithOptions() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	opts := options.NewSortOptions().SetIsAlpha(false).SetOrderBy(options.ASC)
	result, err := client.LPush("{key}1", []string{"3", "1", "2"})
	result1, err := client.SortStoreWithOptions("{key}1", "{key}2", *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 3
	// 3
}

func ExampleClient_SortReadOnly() {
	var client *Client = getExampleGlideClient() // example helper function
	result, err := client.LPush("key1", []string{"1", "3", "2", "4"})
	result1, err := client.SortReadOnly("key1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 4
	// [{1 false} {2 false} {3 false} {4 false}]
}

func ExampleClusterClient_SortReadOnly() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.LPush("key1", []string{"1", "3", "2", "4"})
	result1, err := client.SortReadOnly("key1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 4
	// [{1 false} {2 false} {3 false} {4 false}]
}

func ExampleClient_SortReadOnlyWithOptions() {
	var client *Client = getExampleGlideClient() // example helper function
	opts := options.NewSortOptions().SetByPattern("weight_*").SetIsAlpha(false).SetOrderBy(options.ASC)
	client.Set("weight_item1", "3")
	client.Set("weight_item2", "1")
	client.Set("weight_item3", "2")
	result, err := client.LPush("key1", []string{"item1", "item2", "item3"})
	result1, err := client.SortReadOnlyWithOptions("key1", *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 3
	// [{item2 false} {item3 false} {item1 false}]
}

func ExampleClusterClient_SortReadOnlyWithOptions() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	opts := options.NewSortOptions().SetIsAlpha(false).SetOrderBy(options.ASC)
	result, err := client.LPush("key1", []string{"3", "1", "2"})
	result1, err := client.SortReadOnlyWithOptions("key1", *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 3
	// [{1 false} {2 false} {3 false}]
}

func ExampleClient_Wait() {
	var client *Client = getExampleGlideClient() // example helper function
	client.Set("key1", "someValue")
	result, err := client.Wait(2, 1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Wait returns different results each time. Check it is the proper return type instead
	fmt.Println(result < 10)

	// Output:
	// true
}

func ExampleClusterClient_Wait() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	client.Set("key1", "someValue")
	result, err := client.Wait(2, 1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result < 10)

	// Output:
	// true
}

func ExampleClient_Copy() {
	var client *Client = getExampleGlideClient() // example helper function
	result, err := client.Set("key1", "someValue")
	result1, err := client.Copy("key1", "key2")
	result2, err := client.Get("key2")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// OK
	// true
	// {someValue false}
}

func ExampleClusterClient_Copy() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Set("{key}1", "someValue")
	result1, err := client.Copy("{key}1", "{key}2")
	result2, err := client.Get("{key}2")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(result2)

	// Output:
	// OK
	// true
	// {someValue false}
}

func ExampleClient_CopyWithOptions() {
	var client *Client = getExampleGlideClient() // example helper function
	client.Set("key1", "someValue")

	opts := options.NewCopyOptions().SetReplace()
	client.CopyWithOptions("key1", "key2", *opts)

	result, err := client.Get("key2")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.Value())

	// Output: someValue
}

func ExampleClusterClient_CopyWithOptions() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function

	client.Set("{key}1", "someValue")

	opts := options.NewCopyOptions().SetReplace()
	client.CopyWithOptions("{key}1", "{key}2", *opts)

	result, err := client.Get("{key}2")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result.Value())

	// Output: someValue
}
