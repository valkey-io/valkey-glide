// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"fmt"
	"time"

	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func ExampleClient_Del() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.Set(context.Background(), "key1", "someValue")
	result1, err := client.Set(context.Background(), "key2", "someValue")
	result2, err := client.Del(context.Background(), []string{"key1", "key2", "key3"})
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.Set(context.Background(), "key1", "someValue")
	result1, err := client.Set(context.Background(), "key2", "someValue")
	result2, err := client.Del(context.Background(), []string{"key1", "key2", "key3"})
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
	var client *Client = getExampleClient() // example helper function
	result, err := client.Set(context.Background(), "key1", "someValue")
	result1, err := client.Set(context.Background(), "key2", "someValue")
	result2, err := client.Exists(context.Background(), []string{"key1", "key2", "key3"})
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.Set(context.Background(), "key1", "someValue")
	result1, err := client.Set(context.Background(), "key2", "someValue")
	result2, err := client.Exists(context.Background(), []string{"key1", "key2", "key3"})
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
	var client *Client = getExampleClient() // example helper function
	result, err := client.Set(context.Background(), "key", "someValue")
	result1, err := client.ExpireAt(context.Background(), "key", time.Now().Add(1*time.Second))
	result2, err := client.Expire(context.Background(), "key", 1*time.Second)
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.Set(context.Background(), "key", "someValue")
	result1, err := client.ExpireAt(context.Background(), "key", time.Now().Add(1*time.Second))
	result2, err := client.Expire(context.Background(), "key", 1*time.Second)
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
	var client *Client = getExampleClient() // example helper function
	result, err := client.Set(context.Background(), "key", "someValue")
	result1, err := client.ExpireWithOptions(context.Background(), "key", 1*time.Second, constants.HasNoExpiry)
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.Set(context.Background(), "key", "someValue")
	result1, err := client.ExpireWithOptions(context.Background(), "key", 1*time.Second, constants.HasNoExpiry)
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
	var client *Client = getExampleClient() // example helper function
	result, err := client.Set(context.Background(), "key", "someValue")
	result1, err := client.ExpireAt(context.Background(), "key", time.Now().Add(1*time.Second))
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.Set(context.Background(), "key", "someValue")
	result1, err := client.ExpireAt(context.Background(), "key", time.Now().Add(1*time.Second))
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
	var client *Client = getExampleClient() // example helper function
	result, err := client.Set(context.Background(), "key", "someValue")
	result1, err := client.ExpireAtWithOptions(
		context.Background(),
		"key",
		time.Now().Add(1*time.Second),
		constants.HasNoExpiry,
	)
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.Set(context.Background(), "key", "someValue")
	result1, err := client.ExpireAtWithOptions(
		context.Background(),
		"key",
		time.Now().Add(1*time.Second),
		constants.HasNoExpiry,
	)
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
	var client *Client = getExampleClient() // example helper function
	result, err := client.Set(context.Background(), "key", "someValue")
	result1, err := client.PExpire(context.Background(), "key", 5000*time.Millisecond)
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.Set(context.Background(), "key", "someValue")
	result1, err := client.PExpire(context.Background(), "key", 5000*time.Millisecond)
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
	var client *Client = getExampleClient() // example helper function
	result, err := client.Set(context.Background(), "key", "someValue")
	result1, err := client.PExpireWithOptions(context.Background(), "key", 5000*time.Millisecond, constants.HasNoExpiry)
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.Set(context.Background(), "key", "someValue")
	result1, err := client.PExpireWithOptions(context.Background(), "key", 5000*time.Millisecond, constants.HasNoExpiry)
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
	var client *Client = getExampleClient() // example helper function
	result, err := client.Set(context.Background(), "key", "someValue")
	result1, err := client.PExpireAt(context.Background(), "key", time.Now().Add(10000*time.Millisecond))
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.Set(context.Background(), "key", "someValue")
	result1, err := client.PExpireAt(context.Background(), "key", time.Now().Add(10000*time.Millisecond))
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
	var client *Client = getExampleClient() // example helper function
	result, err := client.Set(context.Background(), "key", "someValue")
	result1, err := client.PExpireAtWithOptions(
		context.Background(),
		"key",
		time.Now().Add(10000*time.Millisecond),
		constants.HasNoExpiry,
	)
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.Set(context.Background(), "key", "someValue")
	result1, err := client.PExpireAtWithOptions(
		context.Background(),
		"key",
		time.Now().Add(10000*time.Millisecond),
		constants.HasNoExpiry,
	)
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
	var client *Client = getExampleClient() // example helper function
	result, err := client.Set(context.Background(), "key", "someValue")
	result1, err := client.ExpireTime(context.Background(), "key")
	_, err = client.ExpireAt(
		context.Background(),
		"key",
		time.Now().Add(10*time.Second),
	) // ExpireTime("key") returns proper unix timestamp in seconds
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.Set(context.Background(), "key", "someValue")
	result1, err := client.ExpireTime(context.Background(), "key")
	_, err = client.ExpireAt(
		context.Background(),
		"key",
		time.Now().Add(10*time.Second),
	) // ExpireTime("key") returns proper unix timestamp in seconds
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
	var client *Client = getExampleClient() // example helper function
	result, err := client.Set(context.Background(), "key", "someValue")
	result1, err := client.PExpireTime(context.Background(), "key")
	_, err = client.PExpireAt(
		context.Background(),
		"key",
		time.Now().Add(10000*time.Millisecond),
	) // PExpireTime("key") returns proper unix time in milliseconds
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.Set(context.Background(), "key", "someValue")
	result1, err := client.PExpireTime(context.Background(), "key")
	_, err = client.PExpireAt(
		context.Background(),
		"key",
		time.Now().Add(10000*time.Millisecond),
	) // PExpireTime("key") returns proper unix time in milliseconds
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
	var client *Client = getExampleClient() // example helper function
	result, err := client.Set(context.Background(), "key", "someValue")
	result1, err := client.TTL(context.Background(), "key")
	_, err = client.ExpireAt(
		context.Background(),
		"key",
		time.Now().Add(10*time.Second),
	) // TTL("key") returns proper TTL in seconds
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.Set(context.Background(), "key", "someValue")
	result1, err := client.TTL(context.Background(), "key")
	_, err = client.ExpireAt(
		context.Background(),
		"key",
		time.Now().Add(10*time.Second),
	) // TTL("key") returns proper TTL in seconds
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
	var client *Client = getExampleClient() // example helper function
	result, err := client.Set(context.Background(), "key", "someValue")
	result1, err := client.PTTL(context.Background(), "key")
	_, err = client.PExpireAt(
		context.Background(),
		"key",
		time.Now().Add(10000*time.Millisecond),
	) // PTTL("key") returns proper TTL in milliseconds
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.Set(context.Background(), "key", "someValue")
	result1, err := client.PTTL(context.Background(), "key")
	_, err = client.PExpireAt(
		context.Background(),
		"key",
		time.Now().Add(10000*time.Millisecond),
	) // PTTL("key") returns proper TTL in milliseconds
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
	var client *Client = getExampleClient() // example helper function
	result, err := client.Set(context.Background(), "key1", "someValue")
	result1, err := client.Set(context.Background(), "key2", "someValue")
	result2, err := client.Unlink(context.Background(), []string{"key1", "key2", "key3"})
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.Set(context.Background(), "key1", "someValue")
	result1, err := client.Set(context.Background(), "key2", "someValue")
	result2, err := client.Unlink(context.Background(), []string{"key1", "key2", "key3"})
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
	var client *Client = getExampleClient() // example helper function
	result, err := client.Set(context.Background(), "key1", "someValue")
	result1, err := client.Set(context.Background(), "key2", "someValue")
	result2, err := client.Touch(context.Background(), []string{"key1", "key2", "key3"})
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.Set(context.Background(), "key1", "someValue")
	result1, err := client.Set(context.Background(), "key2", "someValue")
	result2, err := client.Touch(context.Background(), []string{"key1", "key2", "key3"})
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
	var client *Client = getExampleClient() // example helper function
	result, err := client.Set(context.Background(), "key1", "someValue")
	result1, err := client.Type(context.Background(), "key1")
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.Set(context.Background(), "key1", "someValue")
	result1, err := client.Type(context.Background(), "key1")
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
	var client *Client = getExampleClient() // example helper function
	result, err := client.Set(context.Background(), "key1", "someValue")
	result1, err := client.Rename(context.Background(), "key1", "key2")
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.Set(context.Background(), "{key}1", "someValue")
	result1, err := client.Rename(context.Background(), "{key}1", "{key}2")
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
	var client *Client = getExampleClient() // example helper function
	result, err := client.Set(context.Background(), "key1", "someValue")
	result1, err := client.RenameNX(context.Background(), "key1", "key2")
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.Set(context.Background(), "{key}1", "someValue")
	result1, err := client.RenameNX(context.Background(), "{key}1", "{key}2")
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
	var client *Client = getExampleClient() // example helper function
	result, err := client.Set(context.Background(), "key1", "someValue")
	result1, err := client.ExpireAt(context.Background(), "key1", time.Now().Add(10*time.Second))
	result2, err := client.Persist(context.Background(), "key1")
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.Set(context.Background(), "key1", "someValue")
	result1, err := client.ExpireAt(context.Background(), "key1", time.Now().Add(10*time.Second))
	result2, err := client.Persist(context.Background(), "key1")
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
	var client *Client = getExampleClient() // example helper function
	result, err := client.Set(context.Background(), "key1", "someValue")
	dump, err := client.Dump(context.Background(), "key1")
	result1, err := client.Del(context.Background(), []string{"key1"})
	result2, err := client.Restore(context.Background(), "key1", 0, dump.Value())
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.Set(context.Background(), "key1", "someValue")
	dump, err := client.Dump(context.Background(), "key1")
	result1, err := client.Del(context.Background(), []string{"key1"})
	result2, err := client.Restore(context.Background(), "key1", 0, dump.Value())
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
	var client *Client = getExampleClient() // example helper function
	result, err := client.Set(context.Background(), "key1", "someValue")
	dump, err := client.Dump(context.Background(), "key1")
	result1, err := client.Del(context.Background(), []string{"key1"})
	opts := options.NewRestoreOptions().SetReplace().SetABSTTL().SetEviction(constants.FREQ, 10)
	result2, err := client.RestoreWithOptions(context.Background(), "key1", 0, dump.Value(), *opts)
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.Set(context.Background(), "key1", "someValue")
	dump, err := client.Dump(context.Background(), "key1")
	result1, err := client.Del(context.Background(), []string{"key1"})
	opts := options.NewRestoreOptions().SetReplace().SetABSTTL().SetEviction(constants.FREQ, 10)
	result2, err := client.RestoreWithOptions(context.Background(), "key1", 0, dump.Value(), *opts)
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
	var client *Client = getExampleClient() // example helper function
	result, err := client.Set(context.Background(), "key1", "someValue")
	result1, err := client.ObjectEncoding(context.Background(), "key1")
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.Set(context.Background(), "key1", "someValue")
	result1, err := client.ObjectEncoding(context.Background(), "key1")
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
	var client *Client = getExampleClient() // example helper function
	result, err := client.Set(context.Background(), "key1", "someValue")
	result1, err := client.Dump(context.Background(), "key1") // Contains serialized value of the data stored at key1
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.Set(context.Background(), "key1", "someValue")
	result1, err := client.Dump(context.Background(), "key1") // Contains serialized value of the data stored at key1
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
	var client *Client = getExampleClient() // example helper function

	client.ConfigSet(context.Background(), map[string]string{"maxmemory-policy": "allkeys-lfu"}) // example configuration
	client.Set(context.Background(), "key1", "someValue")
	client.Set(context.Background(), "key1", "someOtherValue")

	result, err := client.ObjectFreq(context.Background(), "key1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: {6 false}
}

func ExampleClusterClient_ObjectFreq() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	// TODO: Once ConfigSet and ConfigGet are implemented, replace CustomCommand
	client.CustomCommand(context.Background(), []string{"CONFIG", "SET", "maxmemory-policy", "allkeys-lfu"})
	result, err := client.Set(context.Background(), "key1", "someValue")
	_, err = client.Set(context.Background(), "key1", "someOtherValue")
	result1, err := client.ObjectFreq(context.Background(), "key1")
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
	var client *Client = getExampleClient()                                                      // example helper function
	client.ConfigSet(context.Background(), map[string]string{"maxmemory-policy": "allkeys-lru"}) // example configuration
	result, err := client.Set(context.Background(), "key1", "someValue")
	result1, err := client.ObjectIdleTime(context.Background(), "key1")
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
	var client *ClusterClient = getExampleClusterClient() // example helper function

	// TODO: Once ConfigSet and ConfigGet are implemented, replace CustomCommand
	client.CustomCommand(context.Background(), []string{"CONFIG", "SET", "maxmemory-policy", "allkeys-lru"})
	result, err := client.Set(context.Background(), "key1", "someValue")
	result1, err := client.ObjectIdleTime(context.Background(), "key1")
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
	var client *Client = getExampleClient() // example helper function
	_, err := client.ConfigSet(
		context.Background(),
		map[string]string{"maxmemory-policy": "allkeys-lru"},
	) // example configuration
	result, err := client.Set(context.Background(), "key1", "someValue")
	result1, err := client.ObjectRefCount(context.Background(), "key1")
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	client.CustomCommand(context.Background(), []string{"CONFIG", "SET", "maxmemory-policy", "allkeys-lru"})
	result, err := client.Set(context.Background(), "key1", "someValue")
	result1, err := client.ObjectRefCount(context.Background(), "key1")
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
	var client *Client = getExampleClient() // example helper function
	result, err := client.LPush(context.Background(), "key1", []string{"1", "3", "2", "4"})
	result1, err := client.Sort(context.Background(), "key1")
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.LPush(context.Background(), "key1", []string{"1", "3", "2", "4"})
	result1, err := client.Sort(context.Background(), "key1")
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
	var client *Client = getExampleClient() // example helper function
	opts := options.NewSortOptions().SetByPattern("weight_*").SetIsAlpha(false).SetOrderBy(options.ASC)
	client.Set(context.Background(), "weight_item1", "3")
	client.Set(context.Background(), "weight_item2", "1")
	client.Set(context.Background(), "weight_item3", "2")
	result, err := client.LPush(context.Background(), "key1", []string{"item1", "item2", "item3"})
	result1, err := client.SortWithOptions(context.Background(), "key1", *opts)
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	opts := options.NewSortOptions().SetIsAlpha(false).SetOrderBy(options.ASC)
	result, err := client.LPush(context.Background(), "key1", []string{"3", "1", "2"})
	result1, err := client.SortWithOptions(context.Background(), "key1", *opts)
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
	var client *Client = getExampleClient() // example helper function
	result, err := client.LPush(context.Background(), "key1", []string{"1", "3", "2", "4"})
	result1, err := client.SortStore(context.Background(), "key1", "key1_store")
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.LPush(context.Background(), "{key}1", []string{"1", "3", "2", "4"})
	result1, err := client.SortStore(context.Background(), "{key}1", "{key}2")
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
	var client *Client = getExampleClient() // example helper function
	opts := options.NewSortOptions().SetByPattern("weight_*").SetIsAlpha(false).SetOrderBy(options.ASC)
	client.Set(context.Background(), "weight_item1", "3")
	client.Set(context.Background(), "weight_item2", "1")
	client.Set(context.Background(), "weight_item3", "2")
	result, err := client.LPush(context.Background(), "key1", []string{"item1", "item2", "item3"})
	result1, err := client.SortStoreWithOptions(context.Background(), "key1", "key1_store", *opts)
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	opts := options.NewSortOptions().SetIsAlpha(false).SetOrderBy(options.ASC)
	result, err := client.LPush(context.Background(), "{key}1", []string{"3", "1", "2"})
	result1, err := client.SortStoreWithOptions(context.Background(), "{key}1", "{key}2", *opts)
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
	var client *Client = getExampleClient() // example helper function
	result, err := client.LPush(context.Background(), "key1", []string{"1", "3", "2", "4"})
	result1, err := client.SortReadOnly(context.Background(), "key1")
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.LPush(context.Background(), "key1", []string{"1", "3", "2", "4"})
	result1, err := client.SortReadOnly(context.Background(), "key1")
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
	var client *Client = getExampleClient() // example helper function
	opts := options.NewSortOptions().SetByPattern("weight_*").SetIsAlpha(false).SetOrderBy(options.ASC)
	client.Set(context.Background(), "weight_item1", "3")
	client.Set(context.Background(), "weight_item2", "1")
	client.Set(context.Background(), "weight_item3", "2")
	result, err := client.LPush(context.Background(), "key1", []string{"item1", "item2", "item3"})
	result1, err := client.SortReadOnlyWithOptions(context.Background(), "key1", *opts)
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	opts := options.NewSortOptions().SetIsAlpha(false).SetOrderBy(options.ASC)
	result, err := client.LPush(context.Background(), "key1", []string{"3", "1", "2"})
	result1, err := client.SortReadOnlyWithOptions(context.Background(), "key1", *opts)
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
	var client *Client = getExampleClient() // example helper function
	client.Set(context.Background(), "key1", "someValue")
	result, err := client.Wait(context.Background(), 2, 1*time.Second)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Wait returns different results each time. Check it is the proper return type instead
	fmt.Println(result < 10)

	// Output:
	// true
}

func ExampleClusterClient_Wait() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	client.Set(context.Background(), "key1", "someValue")
	result, err := client.Wait(context.Background(), 2, 1*time.Second)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result < 10)

	// Output:
	// true
}

func ExampleClient_Copy() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.Set(context.Background(), "key1", "someValue")
	result1, err := client.Copy(context.Background(), "key1", "key2")
	result2, err := client.Get(context.Background(), "key2")
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
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.Set(context.Background(), "{key}1", "someValue")
	result1, err := client.Copy(context.Background(), "{key}1", "{key}2")
	result2, err := client.Get(context.Background(), "{key}2")
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
	var client *Client = getExampleClient() // example helper function
	client.Set(context.Background(), "key1", "someValue")

	opts := options.NewCopyOptions().SetReplace()
	client.CopyWithOptions(context.Background(), "key1", "key2", *opts)

	result, err := client.Get(context.Background(), "key2")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.Value())

	// Output: someValue
}

func ExampleClusterClient_CopyWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.Set(context.Background(), "{key}1", "someValue")

	opts := options.NewCopyOptions().SetReplace()
	client.CopyWithOptions(context.Background(), "{key}1", "{key}2", *opts)

	result, err := client.Get(context.Background(), "{key}2")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result.Value())

	// Output: someValue
}

func ExampleClusterClient_CopyWithOptions_dbDestination() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.Set(context.Background(), "{key}1", "someValue")

	opts := options.NewCopyOptions().SetDBDestination(1)
	client.CopyWithOptions(context.Background(), "{key}1", "{key}2", *opts)

	client.Select(context.Background(), 1)
	result, err := client.Get(context.Background(), "{key}2")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result.Value())

	// Output: someValue
}
