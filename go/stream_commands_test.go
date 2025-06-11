// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"encoding/json"
	"fmt"
	"regexp"
	"strings"
	"time"

	"github.com/valkey-io/valkey-glide/go/v2/constants"

	"github.com/google/uuid"

	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func ExampleClient_XAdd() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.XAdd(context.Background(), "mystream", [][]string{
		{"key1", "value1"},
		{"key2", "value2"},
	})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	matches, _ := regexp.Match(
		`^\d{13}-0$`,
		[]byte(result),
	) // matches a number that is 13 digits long followed by "-0"
	fmt.Println(matches)

	// Output: true
}

func ExampleClusterClient_XAdd() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	result, err := client.XAdd(context.Background(), "mystream", [][]string{
		{"key1", "value1"},
		{"key2", "value2"},
	})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	matches, _ := regexp.Match(
		`^\d{13}-0$`,
		[]byte(result),
	) // matches a number that is 13 digits long followed by "-0"
	fmt.Println(matches)

	// Output: true
}

func ExampleClient_XAddWithOptions() {
	var client *Client = getExampleClient() // example helper function

	options := options.NewXAddOptions().
		SetId("1000-50")
	values := [][]string{
		{"key1", "value1"},
		{"key2", "value2"},
	}
	result, err := client.XAddWithOptions(context.Background(), "mystream", values, *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.Value())

	// Output: 1000-50
}

func ExampleClusterClient_XAddWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	options := options.NewXAddOptions().
		SetId("1000-50")
	values := [][]string{
		{"key1", "value1"},
		{"key2", "value2"},
	}
	result, err := client.XAddWithOptions(context.Background(), "mystream", values, *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.Value())

	// Output: 1000-50
}

func ExampleClient_XTrim() {
	var client *Client = getExampleClient() // example helper function

	client.XAdd(context.Background(), "mystream", [][]string{{"field1", "foo4"}, {"field2", "bar4"}})
	client.XAdd(context.Background(), "mystream", [][]string{{"field3", "foo4"}, {"field4", "bar4"}})

	count, err := client.XTrim(context.Background(), "mystream", *options.NewXTrimOptionsWithMaxLen(0).SetExactTrimming())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(count)

	// Output: 2
}

func ExampleClusterClient_XTrim() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.XAdd(context.Background(), "mystream", [][]string{{"field1", "foo4"}, {"field2", "bar4"}})
	client.XAdd(context.Background(), "mystream", [][]string{{"field3", "foo4"}, {"field4", "bar4"}})

	count, err := client.XTrim(context.Background(), "mystream", *options.NewXTrimOptionsWithMaxLen(0).SetExactTrimming())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(count)

	// Output: 2
}

func ExampleClient_XLen() {
	var client *Client = getExampleClient() // example helper function

	client.XAdd(context.Background(), "mystream", [][]string{{"field1", "foo4"}, {"field2", "bar4"}})
	count, err := client.XLen(context.Background(), "mystream")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(count)

	// Output: 1
}

func ExampleClusterClient_XLen() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.XAdd(context.Background(), "mystream", [][]string{{"field1", "foo4"}, {"field2", "bar4"}})
	count, err := client.XLen(context.Background(), "mystream")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(count)

	// Output: 1
}

func ExampleClient_XAutoClaim() {
	var client *Client = getExampleClient() // example helper function
	key := uuid.NewString()
	group := uuid.NewString()
	consumer := uuid.NewString()

	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(context.Background(), key, group, consumer)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId("0-1"),
	)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry2_field1", "entry2_value1"}},
		*options.NewXAddOptions().SetId("0-2"),
	)
	client.XReadGroup(context.Background(), group, consumer, map[string]string{key: ">"})

	response, err := client.XAutoClaim(context.Background(), key, group, consumer, 0, "0-1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output:
	// {0-0 map[0-1:[[entry1_field1 entry1_value1] [entry1_field2 entry1_value2]] 0-2:[[entry2_field1 entry2_value1]]] []}
}

func ExampleClusterClient_XAutoClaim() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := uuid.NewString()
	group := uuid.NewString()
	consumer := uuid.NewString()

	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(context.Background(), key, group, consumer)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId("0-1"),
	)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry2_field1", "entry2_value1"}},
		*options.NewXAddOptions().SetId("0-2"),
	)
	client.XReadGroup(context.Background(), group, consumer, map[string]string{key: ">"})

	response, err := client.XAutoClaim(context.Background(), key, group, consumer, 0, "0-1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output:
	// {0-0 map[0-1:[[entry1_field1 entry1_value1] [entry1_field2 entry1_value2]] 0-2:[[entry2_field1 entry2_value1]]] []}
}

func ExampleClient_XAutoClaimWithOptions() {
	var client *Client = getExampleClient() // example helper function
	key := uuid.NewString()
	group := uuid.NewString()
	consumer := uuid.NewString()

	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(context.Background(), key, group, consumer)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId("0-1"),
	)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry2_field1", "entry2_value1"}},
		*options.NewXAddOptions().SetId("0-2"),
	)
	client.XReadGroup(context.Background(), group, consumer, map[string]string{key: ">"})

	options := options.NewXAutoClaimOptions().SetCount(1)
	response, err := client.XAutoClaimWithOptions(context.Background(), key, group, consumer, 0, "0-1", *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: {0-2 map[0-1:[[entry1_field1 entry1_value1] [entry1_field2 entry1_value2]]] []}
}

func ExampleClusterClient_XAutoClaimWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := uuid.NewString()
	group := uuid.NewString()
	consumer := uuid.NewString()

	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(context.Background(), key, group, consumer)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId("0-1"),
	)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry2_field1", "entry2_value1"}},
		*options.NewXAddOptions().SetId("0-2"),
	)
	client.XReadGroup(context.Background(), group, consumer, map[string]string{key: ">"})

	options := options.NewXAutoClaimOptions().SetCount(1)
	response, err := client.XAutoClaimWithOptions(context.Background(), key, group, consumer, 0, "0-1", *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: {0-2 map[0-1:[[entry1_field1 entry1_value1] [entry1_field2 entry1_value2]]] []}
}

func ExampleClient_XAutoClaimJustId() {
	var client *Client = getExampleClient() // example helper function
	key := uuid.NewString()
	group := uuid.NewString()
	consumer := uuid.NewString()

	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(context.Background(), key, group, consumer)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId("0-1"),
	)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry2_field1", "entry2_value1"}},
		*options.NewXAddOptions().SetId("0-2"),
	)
	client.XReadGroup(context.Background(), group, consumer, map[string]string{key: ">"})

	response, err := client.XAutoClaimJustId(context.Background(), key, group, consumer, 0, "0-0")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: {0-0 [0-1 0-2] []}
}

func ExampleClusterClient_XAutoClaimJustId() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := uuid.NewString()
	group := uuid.NewString()
	consumer := uuid.NewString()

	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(context.Background(), key, group, consumer)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId("0-1"),
	)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry2_field1", "entry2_value1"}},
		*options.NewXAddOptions().SetId("0-2"),
	)
	client.XReadGroup(context.Background(), group, consumer, map[string]string{key: ">"})

	response, err := client.XAutoClaimJustId(context.Background(), key, group, consumer, 0, "0-0")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: {0-0 [0-1 0-2] []}
}

func ExampleClient_XAutoClaimJustIdWithOptions() {
	var client *Client = getExampleClient() // example helper function
	key := uuid.NewString()
	group := uuid.NewString()
	consumer := uuid.NewString()

	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(context.Background(), key, group, consumer)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId("0-1"),
	)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry2_field1", "entry2_value1"}},
		*options.NewXAddOptions().SetId("0-2"),
	)
	client.XReadGroup(context.Background(), group, consumer, map[string]string{key: ">"})

	options := options.NewXAutoClaimOptions().SetCount(1)
	response, err := client.XAutoClaimJustIdWithOptions(context.Background(), key, group, consumer, 0, "0-1", *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: {0-2 [0-1] []}
}

func ExampleClusterClient_XAutoClaimJustIdWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := uuid.NewString()
	group := uuid.NewString()
	consumer := uuid.NewString()

	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(context.Background(), key, group, consumer)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId("0-1"),
	)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry2_field1", "entry2_value1"}},
		*options.NewXAddOptions().SetId("0-2"),
	)
	client.XReadGroup(context.Background(), group, consumer, map[string]string{key: ">"})

	options := options.NewXAutoClaimOptions().SetCount(1)
	response, err := client.XAutoClaimJustIdWithOptions(context.Background(), key, group, consumer, 0, "0-1", *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: {0-2 [0-1] []}
}

func ExampleClient_XReadGroup() {
	var client *Client = getExampleClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := uuid.NewString()
	consumer := uuid.NewString()

	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(context.Background(), key, group, consumer)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)

	response, err := client.XReadGroup(context.Background(), group, consumer, map[string]string{key: "0"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Check that we have the stream with the correct name in the map
	streamResponse, exists := response[key]
	if exists {
		fmt.Printf("Stream exists: %v\n", exists)
		fmt.Printf("Number of entries: %d\n", len(streamResponse.Entries))
	} else {
		fmt.Println("Stream does not exist")
	}

	// Output:
	// Stream exists: true
	// Number of entries: 0
}

func ExampleClusterClient_XReadGroup() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := uuid.NewString()
	consumer := uuid.NewString()

	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(context.Background(), key, group, consumer)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)

	response, err := client.XReadGroup(context.Background(), group, consumer, map[string]string{key: "0"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Check that we have the stream with the correct name in the map
	streamResponse, exists := response[key]
	if exists {
		fmt.Printf("Stream exists: %v\n", exists)
		fmt.Printf("Number of entries: %d\n", len(streamResponse.Entries))
	} else {
		fmt.Println("Stream does not exist")
	}

	// Output:
	// Stream exists: true
	// Number of entries: 0
}

func ExampleClient_XReadGroupWithOptions() {
	var client *Client = getExampleClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := uuid.NewString()
	consumer := uuid.NewString()

	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(context.Background(), key, group, consumer)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)

	options := options.NewXReadGroupOptions().SetNoAck()
	response, err := client.XReadGroupWithOptions(context.Background(), group, consumer, map[string]string{key: ">"}, *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Check that we have the stream with the correct name in the map
	streamResponse, exists := response[key]
	if exists {
		fmt.Printf("Stream exists: %v\n", exists)
		fmt.Printf("Number of entries: %d\n", len(streamResponse.Entries))
		if len(streamResponse.Entries) > 0 {
			entry := streamResponse.Entries[0]
			fmt.Printf("Entry ID: %s\n", entry.ID)
			fmt.Printf("Entry fields: %v\n", entry.Fields)
		}
	} else {
		fmt.Println("Stream does not exist")
	}

	// Output:
	// Stream exists: true
	// Number of entries: 1
	// Entry ID: 12345-1
	// Entry fields: map[entry1_field1:entry1_value1 entry1_field2:entry1_value2]
}

func ExampleClusterClient_XReadGroupWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := uuid.NewString()
	consumer := uuid.NewString()

	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(context.Background(), key, group, consumer)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)

	options := options.NewXReadGroupOptions().SetNoAck()
	response, err := client.XReadGroupWithOptions(context.Background(), group, consumer, map[string]string{key: ">"}, *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Check that we have the stream with the correct name in the map
	streamResponse, exists := response[key]
	if exists {
		fmt.Printf("Stream exists: %v\n", exists)
		fmt.Printf("Number of entries: %d\n", len(streamResponse.Entries))
		if len(streamResponse.Entries) > 0 {
			entry := streamResponse.Entries[0]
			fmt.Printf("Entry ID: %s\n", entry.ID)
			fmt.Printf("Entry fields: %v\n", entry.Fields)
		}
	} else {
		fmt.Println("Stream does not exist")
	}

	// Output:
	// Stream exists: true
	// Number of entries: 1
	// Entry ID: 12345-1
	// Entry fields: map[entry1_field1:entry1_value1 entry1_field2:entry1_value2]
}

func ExampleClient_XRead() {
	var client *Client = getExampleClient() // example helper function
	key := "12345"
	streamId := "12345-1"

	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"field1", "value1"}, {"field2", "value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)

	response, err := client.XRead(context.Background(), map[string]string{key: "0-0"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Check that we have the stream with the correct name in the map
	streamResponse, exists := response[key]
	if exists {
		fmt.Printf("Stream exists: %v\n", exists)
		fmt.Printf("Number of entries: %d\n", len(streamResponse.Entries))
		if len(streamResponse.Entries) > 0 {
			entry := streamResponse.Entries[0]
			fmt.Printf("Entry ID: %s\n", entry.ID)
			fmt.Printf("Entry fields: %v\n", entry.Fields)
		}
	} else {
		fmt.Println("Stream does not exist")
	}

	// Output:
	// Stream exists: true
	// Number of entries: 1
	// Entry ID: 12345-1
	// Entry fields: map[field1:value1 field2:value2]
}

func ExampleClusterClient_XRead() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "12345"
	streamId := "12345-1"

	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"field1", "value1"}, {"field2", "value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)

	response, err := client.XRead(context.Background(), map[string]string{key: "0-0"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Check that we have the stream with the correct name in the map
	streamResponse, exists := response[key]
	if exists {
		fmt.Printf("Stream exists: %v\n", exists)
		fmt.Printf("Number of entries: %d\n", len(streamResponse.Entries))
		if len(streamResponse.Entries) > 0 {
			entry := streamResponse.Entries[0]
			fmt.Printf("Entry ID: %s\n", entry.ID)
			fmt.Printf("Entry fields: %v\n", entry.Fields)
		}
	} else {
		fmt.Println("Stream does not exist")
	}

	// Output:
	// Stream exists: true
	// Number of entries: 1
	// Entry ID: 12345-1
	// Entry fields: map[field1:value1 field2:value2]
}

func ExampleClient_XReadWithOptions() {
	var client *Client = getExampleClient() // example helper function
	key := "12345"
	streambase := 1

	genStreamId := func(key string, base int, offset int) string { return fmt.Sprintf("%s-%d", key, base+offset) } // helper function to generate stream ids

	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"field1", "value1"}, {"field2", "value2"}},
		*options.NewXAddOptions().SetId(genStreamId(key, streambase, 0)),
	)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"field3", "value3"}, {"field4", "value4"}},
		*options.NewXAddOptions().SetId(genStreamId(key, streambase, 1)),
	)

	response, err := client.XReadWithOptions(context.Background(),
		map[string]string{key: genStreamId(key, streambase, 0)},
		*options.NewXReadOptions().SetCount(1),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Check that we have the stream with the correct name in the map
	streamResponse, exists := response[key]
	if exists {
		fmt.Printf("Stream exists: %v\n", exists)
		fmt.Printf("Number of entries: %d\n", len(streamResponse.Entries))
		if len(streamResponse.Entries) > 0 {
			entry := streamResponse.Entries[0]
			fmt.Printf("Entry ID: %s\n", entry.ID)
			fmt.Printf("Entry fields: %v\n", entry.Fields)
		}
	} else {
		fmt.Println("Stream does not exist")
	}

	// Output:
	// Stream exists: true
	// Number of entries: 1
	// Entry ID: 12345-2
	// Entry fields: map[field3:value3 field4:value4]
}

func ExampleClusterClient_XReadWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "12345"
	streambase := 1

	genStreamId := func(key string, base int, offset int) string { return fmt.Sprintf("%s-%d", key, base+offset) } // helper function to generate stream ids

	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"field1", "value1"}, {"field2", "value2"}},
		*options.NewXAddOptions().SetId(genStreamId(key, streambase, 0)),
	)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"field3", "value3"}, {"field4", "value4"}},
		*options.NewXAddOptions().SetId(genStreamId(key, streambase, 1)),
	)

	response, err := client.XReadWithOptions(context.Background(),
		map[string]string{key: genStreamId(key, streambase, 0)},
		*options.NewXReadOptions().SetCount(1),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Check that we have the stream with the correct name in the map
	streamResponse, exists := response[key]
	if exists {
		fmt.Printf("Stream exists: %v\n", exists)
		fmt.Printf("Number of entries: %d\n", len(streamResponse.Entries))
		if len(streamResponse.Entries) > 0 {
			entry := streamResponse.Entries[0]
			fmt.Printf("Entry ID: %s\n", entry.ID)
			fmt.Printf("Entry fields: %v\n", entry.Fields)
		}
	} else {
		fmt.Println("Stream does not exist")
	}

	// Output:
	// Stream exists: true
	// Number of entries: 1
	// Entry ID: 12345-2
	// Entry fields: map[field3:value3 field4:value4]
}

func ExampleClient_XDel() {
	var client *Client = getExampleClient() // example helper function
	key := "12345"

	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"field1", "value1"}, {"field2", "value2"}},
		*options.NewXAddOptions().SetId("0-1"),
	)

	count, err := client.XDel(context.Background(), key, []string{"0-1", "0-2", "0-3"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(count)

	// Output: 1
}

func ExampleClusterClient_XDel() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "12345"

	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"field1", "value1"}, {"field2", "value2"}},
		*options.NewXAddOptions().SetId("0-1"),
	)

	count, err := client.XDel(context.Background(), key, []string{"0-1", "0-2", "0-3"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(count)

	// Output: 1
}

func ExampleClient_XPending() {
	var client *Client = getExampleClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"
	consumer := "c12345"

	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(context.Background(), key, group, consumer)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XReadGroup(context.Background(), group, consumer, map[string]string{key: ">"})

	summary, err := client.XPending(context.Background(), key, group)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonSummary, _ := json.Marshal(summary)
	fmt.Println(string(jsonSummary))

	// Output: {"NumOfMessages":1,"StartId":{},"EndId":{},"ConsumerMessages":[{"ConsumerName":"c12345","MessageCount":1}]}
}

func ExampleClusterClient_XPending() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"
	consumer := "c12345"

	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(context.Background(), key, group, consumer)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XReadGroup(context.Background(), group, consumer, map[string]string{key: ">"})

	summary, err := client.XPending(context.Background(), key, group)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonSummary, _ := json.Marshal(summary)
	fmt.Println(string(jsonSummary))

	// Output: {"NumOfMessages":1,"StartId":{},"EndId":{},"ConsumerMessages":[{"ConsumerName":"c12345","MessageCount":1}]}
}

func ExampleClient_XPendingWithOptions() {
	var client *Client = getExampleClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"
	consumer := "c12345"

	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(context.Background(), key, group, consumer)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XReadGroup(context.Background(), group, consumer, map[string]string{key: ">"})

	details, err := client.XPendingWithOptions(context.Background(),
		key,
		group,
		*options.NewXPendingOptions("-", "+", 10).SetConsumer(consumer),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonDetails, _ := json.Marshal(details)

	// Since IdleTime can vary, check that output has all fields
	fields := []string{"\"Id\"", "\"ConsumerName\"", "\"IdleTime\"", "\"DeliveryCount\""}
	hasFields := true
	jsonStr := string(jsonDetails)

	for _, field := range fields {
		hasFields = strings.Contains(jsonStr, field)
		if !hasFields {
			break
		}
	}
	fmt.Println(hasFields)

	// Output: true
}

func ExampleClusterClient_XPendingWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"
	consumer := "c12345"

	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(context.Background(), key, group, consumer)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XReadGroup(context.Background(), group, consumer, map[string]string{key: ">"})

	details, err := client.XPendingWithOptions(context.Background(),
		key,
		group,
		*options.NewXPendingOptions("-", "+", 10).SetConsumer(consumer),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonDetails, _ := json.Marshal(details)

	// Since IdleTime can vary, check that output has all fields
	fields := []string{"\"Id\"", "\"ConsumerName\"", "\"IdleTime\"", "\"DeliveryCount\""}
	hasFields := true
	jsonStr := string(jsonDetails)

	for _, field := range fields {
		hasFields = strings.Contains(jsonStr, field)
		if !hasFields {
			break
		}
	}
	fmt.Println(hasFields)

	// Output: true
}

func ExampleClient_XGroupSetId() {
	var client *Client = getExampleClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"
	consumer := "c12345"

	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(context.Background(), key, group, consumer)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XReadGroup(context.Background(), group, consumer, map[string]string{key: ">"})
	client.XAck(context.Background(), key, group, []string{streamId}) // ack the message and remove it from the pending list

	client.XGroupSetId(
		context.Background(),
		key,
		group,
		"0-0",
	) // reset the last acknowledged message to 0-0
	client.XReadGroup(context.Background(), group, consumer, map[string]string{key: ">"}) // read the group again

	summary, err := client.XPending(
		context.Background(),
		key,
		group,
	) // get the pending messages, which should include the entry we previously acked
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonSummary, _ := json.Marshal(summary)
	fmt.Println(string(jsonSummary))

	// Output: {"NumOfMessages":1,"StartId":{},"EndId":{},"ConsumerMessages":[{"ConsumerName":"c12345","MessageCount":1}]}
}

func ExampleClusterClient_XGroupSetId() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"
	consumer := "c12345"

	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(context.Background(), key, group, consumer)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XReadGroup(context.Background(), group, consumer, map[string]string{key: ">"})
	client.XAck(context.Background(), key, group, []string{streamId}) // ack the message and remove it from the pending list

	client.XGroupSetId(
		context.Background(),
		key,
		group,
		"0-0",
	) // reset the last acknowledged message to 0-0
	client.XReadGroup(context.Background(), group, consumer, map[string]string{key: ">"}) // read the group again

	summary, err := client.XPending(
		context.Background(),
		key,
		group,
	) // get the pending messages, which should include the entry we previously acked
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonSummary, _ := json.Marshal(summary)
	fmt.Println(string(jsonSummary))

	// Output: {"NumOfMessages":1,"StartId":{},"EndId":{},"ConsumerMessages":[{"ConsumerName":"c12345","MessageCount":1}]}
}

func ExampleClient_XGroupSetIdWithOptions() {
	var client *Client = getExampleClient() // example helper function
	key := "12345"
	streamId1 := "12345-1"
	streamId2 := "12345-2"
	group := "g12345"
	consumer := "c12345"

	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(context.Background(), key, group, consumer)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"field1", "value1"}, {"field2", "value2"}},
		*options.NewXAddOptions().SetId(streamId1),
	)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"field3", "value3"}, {"field4", "value4"}},
		*options.NewXAddOptions().SetId(streamId2),
	)
	client.XReadGroup(context.Background(), group, consumer, map[string]string{key: ">"})
	client.XAck(
		context.Background(),
		key,
		group,
		[]string{streamId1, streamId2},
	) // ack the message and remove it from the pending list

	opts := options.NewXGroupSetIdOptionsOptions().SetEntriesRead(1)
	client.XGroupSetIdWithOptions(
		context.Background(),
		key,
		group,
		"0-0",
		*opts,
	) // reset the last acknowledged message to 0-0
	client.XReadGroup(context.Background(), group, consumer, map[string]string{key: ">"}) // read the group again

	summary, err := client.XPending(
		context.Background(),
		key,
		group,
	) // get the pending messages, which should include the entry we previously acked
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonSummary, _ := json.Marshal(summary)
	fmt.Println(string(jsonSummary))

	// Output: {"NumOfMessages":2,"StartId":{},"EndId":{},"ConsumerMessages":[{"ConsumerName":"c12345","MessageCount":2}]}
}

func ExampleClusterClient_XGroupSetIdWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "12345"
	streamId1 := "12345-1"
	streamId2 := "12345-2"
	group := "g12345"
	consumer := "c12345"

	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(context.Background(), key, group, consumer)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"field1", "value1"}, {"field2", "value2"}},
		*options.NewXAddOptions().SetId(streamId1),
	)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"field3", "value3"}, {"field4", "value4"}},
		*options.NewXAddOptions().SetId(streamId2),
	)
	client.XReadGroup(context.Background(), group, consumer, map[string]string{key: ">"})
	client.XAck(
		context.Background(),
		key,
		group,
		[]string{streamId1, streamId2},
	) // ack the message and remove it from the pending list

	opts := options.NewXGroupSetIdOptionsOptions().SetEntriesRead(1)
	client.XGroupSetIdWithOptions(
		context.Background(),
		key,
		group,
		"0-0",
		*opts,
	) // reset the last acknowledged message to 0-0
	client.XReadGroup(context.Background(), group, consumer, map[string]string{key: ">"}) // read the group again

	summary, err := client.XPending(
		context.Background(),
		key,
		group,
	) // get the pending messages, which should include the entry we previously acked
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonSummary, _ := json.Marshal(summary)
	fmt.Println(string(jsonSummary))

	// Output: {"NumOfMessages":2,"StartId":{},"EndId":{},"ConsumerMessages":[{"ConsumerName":"c12345","MessageCount":2}]}
}

func ExampleClient_XGroupCreate() {
	var client *Client = getExampleClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"

	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"field1", "value1"}, {"field2", "value2"}},
		*options.NewXAddOptions().SetId(streamId),
	) // This will create the stream if it does not exist

	response, err := client.XGroupCreate(context.Background(), key, group, "0") // create the group (no MKSTREAM needed)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: OK
}

func ExampleClusterClient_XGroupCreate() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"

	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"field1", "value1"}, {"field2", "value2"}},
		*options.NewXAddOptions().SetId(streamId),
	) // This will create the stream if it does not exist

	response, err := client.XGroupCreate(context.Background(), key, group, "0") // create the group (no MKSTREAM needed)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: OK
}

func ExampleClient_XGroupCreateWithOptions() {
	var client *Client = getExampleClient() // example helper function
	key := "12345"
	group := "g12345"

	opts := options.NewXGroupCreateOptions().SetMakeStream()
	response, err := client.XGroupCreateWithOptions(
		context.Background(),
		key,
		group,
		"0",
		*opts,
	) // create the group (no MKSTREAM needed)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: OK
}

func ExampleClusterClient_XGroupCreateWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "12345"
	group := "g12345"

	opts := options.NewXGroupCreateOptions().SetMakeStream()
	response, err := client.XGroupCreateWithOptions(
		context.Background(),
		key,
		group,
		"0",
		*opts,
	) // create the group (no MKSTREAM needed)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: OK
}

func ExampleClient_XGroupDestroy() {
	var client *Client = getExampleClient() // example helper function
	key := "12345"
	group := "g12345"

	opts := options.NewXGroupCreateOptions().SetMakeStream()
	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *opts) // create the group (no MKSTREAM needed)

	success, err := client.XGroupDestroy(context.Background(), key, group) // destroy the group
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(success)

	// Output: true
}

func ExampleClusterClient_XGroupDestroy() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "12345"
	group := "g12345"

	opts := options.NewXGroupCreateOptions().SetMakeStream()
	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *opts) // create the group (no MKSTREAM needed)

	success, err := client.XGroupDestroy(context.Background(), key, group) // destroy the group
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(success)

	// Output: true
}

func ExampleClient_XGroupCreateConsumer() {
	var client *Client = getExampleClient() // example helper function
	key := "12345"
	group := "g12345"
	consumer := "c12345"

	opts := options.NewXGroupCreateOptions().SetMakeStream()
	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *opts) // create the group (no MKSTREAM needed)

	success, err := client.XGroupCreateConsumer(context.Background(), key, group, consumer)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(success)

	// Output: true
}

func ExampleClusterClient_XGroupCreateConsumer() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "12345"
	group := "g12345"
	consumer := "c12345"

	opts := options.NewXGroupCreateOptions().SetMakeStream()
	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *opts) // create the group (no MKSTREAM needed)

	success, err := client.XGroupCreateConsumer(context.Background(), key, group, consumer)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(success)

	// Output: true
}

func ExampleClient_XGroupDelConsumer() {
	var client *Client = getExampleClient() // example helper function
	key := "12345"
	group := "g12345"
	consumer := "c12345"
	streamId := "12345-1"

	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"field1", "value1"}, {"field2", "value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XGroupCreate(context.Background(), key, group, "0")
	client.XGroupCreateConsumer(context.Background(), key, group, consumer)
	client.XReadGroup(context.Background(), group, consumer, map[string]string{key: ">"})

	count, err := client.XGroupDelConsumer(context.Background(), key, group, consumer)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println("Consumer deleted. Messages pending:", count)

	// Output:
	// Consumer deleted. Messages pending: 1
}

func ExampleClusterClient_XGroupDelConsumer() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "12345"
	group := "g12345"
	consumer := "c12345"
	streamId := "12345-1"

	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"field1", "value1"}, {"field2", "value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XGroupCreate(context.Background(), key, group, "0")
	client.XGroupCreateConsumer(context.Background(), key, group, consumer)
	client.XReadGroup(context.Background(), group, consumer, map[string]string{key: ">"})

	count, err := client.XGroupDelConsumer(context.Background(), key, group, consumer)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println("Consumer deleted. Messages pending:", count)

	// Output:
	// Consumer deleted. Messages pending: 1
}

func ExampleClient_XAck() {
	var client *Client = getExampleClient() // example helper function
	key := "12345"
	group := "g12345"
	consumer := "c12345"

	streamId, _ := client.XAdd(context.Background(),
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
	)
	client.XGroupCreate(context.Background(), key, group, "0")
	client.XGroupCreateConsumer(context.Background(), key, group, consumer)
	client.XReadGroup(context.Background(), group, consumer, map[string]string{key: ">"})

	count, err := client.XAck(
		context.Background(),
		key,
		group,
		[]string{streamId},
	) // ack the message and remove it from the pending list
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(count)

	// Output: 1
}

func ExampleClusterClient_XAck() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "12345"
	group := "g12345"
	consumer := "c12345"

	streamId, _ := client.XAdd(context.Background(),
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
	)
	client.XGroupCreate(context.Background(), key, group, "0")
	client.XGroupCreateConsumer(context.Background(), key, group, consumer)
	client.XReadGroup(context.Background(), group, consumer, map[string]string{key: ">"})

	count, err := client.XAck(
		context.Background(),
		key,
		group,
		[]string{streamId},
	) // ack the message and remove it from the pending list
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(count)

	// Output: 1
}

func ExampleClient_XClaim() {
	var client *Client = getExampleClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"
	consumer1 := "c12345-1"
	consumer2 := "c12345-2"

	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(context.Background(), key, group, consumer1)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XReadGroup(context.Background(), group, consumer1, map[string]string{key: ">"})

	result, err := client.XPendingWithOptions(context.Background(),
		key,
		group,
		*options.NewXPendingOptions("-", "+", 10).SetConsumer(consumer1),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	if len(result) == 0 {
		fmt.Println("No pending messages")
		return
	}

	response, err := client.XClaim(
		context.Background(),
		key,
		group,
		consumer2,
		time.Duration(result[0].IdleTime)*time.Millisecond,
		[]string{result[0].Id},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Printf("Claimed %d message\n", len(response))

	// Access fields from the claimed message
	for id, claimResponse := range response {
		fmt.Printf("Message ID: %s has %d fields\n", id, len(claimResponse.Fields))
	}

	// Output: Claimed 1 message
	// Message ID: 12345-1 has 2 fields
}

func ExampleClusterClient_XClaim() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"
	consumer1 := "c12345-1"
	consumer2 := "c12345-2"

	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(context.Background(), key, group, consumer1)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XReadGroup(context.Background(), group, consumer1, map[string]string{key: ">"})

	result, err := client.XPendingWithOptions(context.Background(),
		key,
		group,
		*options.NewXPendingOptions("-", "+", 10).SetConsumer(consumer1),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	if len(result) == 0 {
		fmt.Println("No pending messages")
		return
	}

	response, err := client.XClaim(
		context.Background(),
		key,
		group,
		consumer2,
		time.Duration(result[0].IdleTime)*time.Millisecond,
		[]string{result[0].Id},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Printf("Claimed %d message\n", len(response))

	// Access fields from the claimed message
	for id, claimResponse := range response {
		fmt.Printf("Message ID: %s has %d fields\n", id, len(claimResponse.Fields))
	}

	// Output: Claimed 1 message
	// Message ID: 12345-1 has 2 fields
}

func ExampleClient_XClaimWithOptions() {
	var client *Client = getExampleClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"
	consumer1 := "c12345-1"
	consumer2 := "c12345-2"

	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(context.Background(), key, group, consumer1)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XReadGroup(context.Background(), group, consumer1, map[string]string{key: ">"})

	result, err := client.XPendingWithOptions(context.Background(),
		key,
		group,
		*options.NewXPendingOptions("-", "+", 10).SetConsumer(consumer1),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	if len(result) == 0 {
		fmt.Println("No pending messages")
		return
	}

	opts := options.NewXClaimOptions().SetRetryCount(3)
	response, err := client.XClaimWithOptions(
		context.Background(),
		key,
		group,
		consumer2,
		time.Duration(result[0].IdleTime)*time.Millisecond,
		[]string{result[0].Id},
		*opts,
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Printf("Claimed %d message\n", len(response))

	// Access fields from the claimed message
	for id, _ := range response {
		fmt.Printf("Message ID: %s with retry count: %d\n", id, 3)
	}

	// Output: Claimed 1 message
	// Message ID: 12345-1 with retry count: 3
}

func ExampleClusterClient_XClaimWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"
	consumer1 := "c12345-1"
	consumer2 := "c12345-2"

	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(context.Background(), key, group, consumer1)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XReadGroup(context.Background(), group, consumer1, map[string]string{key: ">"})

	result, err := client.XPendingWithOptions(context.Background(),
		key,
		group,
		*options.NewXPendingOptions("-", "+", 10).SetConsumer(consumer1),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	if len(result) == 0 {
		fmt.Println("No pending messages")
		return
	}

	opts := options.NewXClaimOptions().SetRetryCount(3)
	response, err := client.XClaimWithOptions(
		context.Background(),
		key,
		group,
		consumer2,
		time.Duration(result[0].IdleTime)*time.Millisecond,
		[]string{result[0].Id},
		*opts,
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Printf("Claimed %d message\n", len(response))

	// Output: Claimed 1 message
}

func ExampleClient_XClaimJustId() {
	var client *Client = getExampleClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"
	consumer1 := "c12345-1"
	consumer2 := "c12345-2"

	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(context.Background(), key, group, consumer1)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XReadGroup(context.Background(), group, consumer1, map[string]string{key: ">"})

	result, err := client.XPendingWithOptions(context.Background(),
		key,
		group,
		*options.NewXPendingOptions("-", "+", 10).SetConsumer(consumer1),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	if len(result) == 0 {
		fmt.Println("No pending messages")
		return
	}

	response, err := client.XClaimJustId(
		context.Background(),
		key,
		group,
		consumer2,
		time.Duration(result[0].IdleTime)*time.Millisecond,
		[]string{result[0].Id},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: [12345-1]
}

func ExampleClusterClient_XClaimJustId() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"
	consumer1 := "c12345-1"
	consumer2 := "c12345-2"

	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(context.Background(), key, group, consumer1)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XReadGroup(context.Background(), group, consumer1, map[string]string{key: ">"})

	result, err := client.XPendingWithOptions(context.Background(),
		key,
		group,
		*options.NewXPendingOptions("-", "+", 10).SetConsumer(consumer1),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	if len(result) == 0 {
		fmt.Println("No pending messages")
		return
	}

	response, err := client.XClaimJustId(
		context.Background(),
		key,
		group,
		consumer2,
		time.Duration(result[0].IdleTime)*time.Millisecond,
		[]string{result[0].Id},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: [12345-1]
}

func ExampleClient_XClaimJustIdWithOptions() {
	var client *Client = getExampleClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"
	consumer1 := "c12345-1"
	consumer2 := "c12345-2"

	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(context.Background(), key, group, consumer1)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XReadGroup(context.Background(), group, consumer1, map[string]string{key: ">"})

	result, err := client.XPendingWithOptions(context.Background(),
		key,
		group,
		*options.NewXPendingOptions("-", "+", 10).SetConsumer(consumer1),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	if len(result) == 0 {
		fmt.Println("No pending messages")
		return
	}

	opts := options.NewXClaimOptions().SetRetryCount(3)
	response, err := client.XClaimJustIdWithOptions(
		context.Background(),
		key,
		group,
		consumer2,
		time.Duration(result[0].IdleTime)*time.Millisecond,
		[]string{result[0].Id},
		*opts,
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: [12345-1]
}

func ExampleClusterClient_XClaimJustIdWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"
	consumer1 := "c12345-1"
	consumer2 := "c12345-2"

	client.XGroupCreateWithOptions(context.Background(), key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(context.Background(), key, group, consumer1)
	client.XAddWithOptions(context.Background(),
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XReadGroup(context.Background(), group, consumer1, map[string]string{key: ">"})

	result, err := client.XPendingWithOptions(context.Background(),
		key,
		group,
		*options.NewXPendingOptions("-", "+", 10).SetConsumer(consumer1),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	if len(result) == 0 {
		fmt.Println("No pending messages")
		return
	}

	opts := options.NewXClaimOptions().SetRetryCount(3)
	response, err := client.XClaimJustIdWithOptions(
		context.Background(),
		key,
		group,
		consumer2,
		time.Duration(result[0].IdleTime)*time.Millisecond,
		[]string{result[0].Id},
		*opts,
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: [12345-1]
}

func ExampleClient_XRange() {
	var client *Client = getExampleClient() // example helper function
	key := "12345"

	client.XAdd(context.Background(), key, [][]string{{"field1", "value1"}})
	client.XAdd(context.Background(), key, [][]string{{"field2", "value2"}})

	response, err := client.XRange(context.Background(), key,
		options.NewInfiniteStreamBoundary(constants.NegativeInfinity),
		options.NewInfiniteStreamBoundary(constants.PositiveInfinity))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(len(response))

	// Output: 2
}

func ExampleClusterClient_XRange() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "12345"

	client.XAdd(context.Background(), key, [][]string{{"field1", "value1"}})
	client.XAdd(context.Background(), key, [][]string{{"field2", "value2"}})

	response, err := client.XRange(context.Background(), key,
		options.NewInfiniteStreamBoundary(constants.NegativeInfinity),
		options.NewInfiniteStreamBoundary(constants.PositiveInfinity))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(len(response))

	// Output: 2
}

func ExampleClient_XRangeWithOptions() {
	var client *Client = getExampleClient() // example helper function
	key := "12345"

	streamId1, _ := client.XAdd(context.Background(), key, [][]string{{"field1", "value1"}})
	streamId2, _ := client.XAdd(context.Background(), key, [][]string{{"field2", "value2"}})

	response, err := client.XRangeWithOptions(context.Background(), key,
		options.NewStreamBoundary(streamId1, true),
		options.NewStreamBoundary(streamId2, true),
		*options.NewXRangeOptions().SetCount(1))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(len(response))

	// Output: 1
}

func ExampleClusterClient_XRangeWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "12345"

	streamId1, _ := client.XAdd(context.Background(), key, [][]string{{"field1", "value1"}})
	streamId2, _ := client.XAdd(context.Background(), key, [][]string{{"field2", "value2"}})

	response, err := client.XRangeWithOptions(context.Background(), key,
		options.NewStreamBoundary(streamId1, true),
		options.NewStreamBoundary(streamId2, true),
		*options.NewXRangeOptions().SetCount(1))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(len(response))

	// Output: 1
}

func ExampleClient_XRevRange() {
	var client *Client = getExampleClient() // example helper function
	key := "12345"
	streamId1 := "12345-1"
	streamId2 := "12345-2"

	client.XAddWithOptions(
		context.Background(),
		key,
		[][]string{{"field1", "value1"}},
		*options.NewXAddOptions().SetId(streamId1),
	)
	client.XAddWithOptions(
		context.Background(),
		key,
		[][]string{{"field2", "value2"}},
		*options.NewXAddOptions().SetId(streamId2),
	)

	response, err := client.XRevRange(context.Background(), key,
		options.NewStreamBoundary(streamId2, true),
		options.NewStreamBoundary(streamId1, true))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: [{12345-2 [[field2 value2]]} {12345-1 [[field1 value1]]}]
}

func ExampleClusterClient_XRevRange() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "12345"
	streamId1 := "12345-1"
	streamId2 := "12345-2"

	client.XAddWithOptions(
		context.Background(),
		key,
		[][]string{{"field1", "value1"}},
		*options.NewXAddOptions().SetId(streamId1),
	)
	client.XAddWithOptions(
		context.Background(),
		key,
		[][]string{{"field2", "value2"}},
		*options.NewXAddOptions().SetId(streamId2),
	)

	response, err := client.XRevRange(context.Background(), key,
		options.NewStreamBoundary(streamId2, true),
		options.NewStreamBoundary(streamId1, true))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: [{12345-2 [[field2 value2]]} {12345-1 [[field1 value1]]}]
}

func ExampleClient_XRevRangeWithOptions() {
	var client *Client = getExampleClient() // example helper function
	key := "12345"
	streamId1 := "12345-1"
	streamId2 := "12345-2"

	client.XAddWithOptions(
		context.Background(),
		key,
		[][]string{{"field1", "value1"}},
		*options.NewXAddOptions().SetId(streamId1),
	)
	client.XAddWithOptions(
		context.Background(),
		key,
		[][]string{{"field2", "value2"}},
		*options.NewXAddOptions().SetId(streamId2),
	)

	response, err := client.XRevRangeWithOptions(context.Background(), key,
		options.NewStreamBoundary(streamId2, true),
		options.NewStreamBoundary(streamId1, true),
		*options.NewXRangeOptions().SetCount(2))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: [{12345-2 [[field2 value2]]} {12345-1 [[field1 value1]]}]
}

func ExampleClusterClient_XRevRangeWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "12345"
	streamId1 := "12345-1"
	streamId2 := "12345-2"

	client.XAddWithOptions(
		context.Background(),
		key,
		[][]string{{"field1", "value1"}},
		*options.NewXAddOptions().SetId(streamId1),
	)
	client.XAddWithOptions(
		context.Background(),
		key,
		[][]string{{"field2", "value2"}},
		*options.NewXAddOptions().SetId(streamId2),
	)

	response, err := client.XRevRangeWithOptions(context.Background(), key,
		options.NewStreamBoundary(streamId2, true),
		options.NewStreamBoundary(streamId1, true),
		*options.NewXRangeOptions().SetCount(2))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: [{12345-2 [[field2 value2]]} {12345-1 [[field1 value1]]}]
}

func ExampleClient_XInfoStream() {
	var client *Client = getExampleClient() // example helper function
	key := "12345"
	streamId1 := "12345-1"

	client.XAddWithOptions(
		context.Background(),
		key,
		[][]string{{"field1", "value1"}},
		*options.NewXAddOptions().SetId(streamId1),
	)
	response, err := client.XInfoStream(context.Background(), key)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Response Structure is as follows:
	// {
	//   "entries-added": 1,
	//   "first-entry": [
	//     "12345-1",
	//     [
	//       "field1",
	//       "value1"
	//     ]
	//   ],
	//   "groups": 0,
	//   "last-entry": [
	//     "12345-1",
	//     [
	//       "field1",
	//       "value1"
	//     ]
	//   ],
	//   "last-generated-id": "12345-1",
	//   "length": 1,
	//   "max-deleted-entry-id": "0-0",
	//   "radix-tree-keys": 1,
	//   "radix-tree-nodes": 2,
	//   "recorded-first-entry-id": "12345-1"
	// }

	// Output a few entries from the return object.
	fmt.Printf("Entries Added: %d\n", response.EntriesAdded)
	fmt.Printf("Groups:  %d\n", response.Groups)
	fmt.Printf("Last generated Id: %s\n", response.LastGeneratedID)
	fmt.Printf("Length: %d\n", response.Length)

	// Output:
	// Entries Added: 1
	// Groups: 0
	// Last generated Id: 12345-1
	// Length: 1
}

func ExampleClusterClient_XInfoStream() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "12345"
	streamId1 := "12345-1"

	client.XAddWithOptions(
		context.Background(),
		key,
		[][]string{{"field1", "value1"}},
		*options.NewXAddOptions().SetId(streamId1),
	)
	response, err := client.XInfoStream(context.Background(), key)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Response Structure is as follows:
	// {
	//   "entries-added": 1,
	//   "first-entry": [
	//     "12345-1",
	//     [
	//       "field1",
	//       "value1"
	//     ]
	//   ],
	//   "groups": 0,
	//   "last-entry": [
	//     "12345-1",
	//     [
	//       "field1",
	//       "value1"
	//     ]
	//   ],
	//   "last-generated-id": "12345-1",
	//   "length": 1,
	//   "max-deleted-entry-id": "0-0",
	//   "radix-tree-keys": 1,
	//   "radix-tree-nodes": 2,
	//   "recorded-first-entry-id": "12345-1"
	// }

	// Output a few entries from the return object.
	fmt.Printf("Entries Added: %d\n", response.EntriesAdded)
	fmt.Printf("Groups:  %d\n", response.Groups)
	fmt.Printf("Last generated Id: %s\n", response.LastGeneratedID)
	fmt.Printf("Length: %d\n", response.Length)

	// Output:
	// Entries Added: 1
	// Groups: 0
	// Last generated Id: 12345-1
	// Length: 1
}

func ExampleClient_XInfoStreamFullWithOptions() {
	var client *Client = getExampleClient() // example helper function
	key := "12345"

	for i := 1; i <= 5; i++ {
		field := fmt.Sprintf("field%d", i)
		value := fmt.Sprintf("value%d", i)
		streamId := fmt.Sprintf("%s-%d", key, i)

		client.XAddWithOptions(
			context.Background(),
			key,
			[][]string{{field, value}},
			*options.NewXAddOptions().SetId(streamId),
		)
	}

	options := options.NewXInfoStreamOptionsOptions().SetCount(2)
	response, err := client.XInfoStreamFullWithOptions(context.Background(), key, options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	jsonResult, _ := json.MarshalIndent(response, "", "  ")

	fmt.Println(string(jsonResult))
	// Output:
	// {
	//   "entries": [
	//     [
	//       "12345-1",
	//       [
	//         "field1",
	//         "value1"
	//       ]
	//     ],
	//     [
	//       "12345-2",
	//       [
	//         "field2",
	//         "value2"
	//       ]
	//     ]
	//   ],
	//   "entries-added": 5,
	//   "groups": null,
	//   "last-generated-id": "12345-5",
	//   "length": 5,
	//   "max-deleted-entry-id": "0-0",
	//   "radix-tree-keys": 1,
	//   "radix-tree-nodes": 2,
	//   "recorded-first-entry-id": "12345-1"
	// }
}

func ExampleClusterClient_XInfoStreamFullWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "12345"

	for i := 1; i <= 5; i++ {
		field := fmt.Sprintf("field%d", i)
		value := fmt.Sprintf("value%d", i)
		streamId := fmt.Sprintf("%s-%d", key, i)

		client.XAddWithOptions(
			context.Background(),
			key,
			[][]string{{field, value}},
			*options.NewXAddOptions().SetId(streamId),
		)
	}

	options := options.NewXInfoStreamOptionsOptions().SetCount(2)
	response, err := client.XInfoStreamFullWithOptions(context.Background(), key, options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	jsonResult, _ := json.MarshalIndent(response, "", "  ")

	fmt.Println(string(jsonResult))
	// Output:
	// {
	//   "entries": [
	//     [
	//       "12345-1",
	//       [
	//         "field1",
	//         "value1"
	//       ]
	//     ],
	//     [
	//       "12345-2",
	//       [
	//         "field2",
	//         "value2"
	//       ]
	//     ]
	//   ],
	//   "entries-added": 5,
	//   "groups": null,
	//   "last-generated-id": "12345-5",
	//   "length": 5,
	//   "max-deleted-entry-id": "0-0",
	//   "radix-tree-keys": 1,
	//   "radix-tree-nodes": 2,
	//   "recorded-first-entry-id": "12345-1"
	// }
}

func ExampleClient_XInfoConsumers() {
	var client *Client = getExampleClient() // example helper function
	key := uuid.NewString()
	group := "myGroup"

	// create an empty stream with a group
	client.XGroupCreateWithOptions(context.Background(), key, group, "0-0", *options.NewXGroupCreateOptions().SetMakeStream())
	// add couple of entries
	client.XAddWithOptions(
		context.Background(),
		key,
		[][]string{{"e1_f1", "e1_v1"}, {"e1_f2", "e1_v2"}},
		*options.NewXAddOptions().SetId("0-1"),
	)
	client.XAddWithOptions(
		context.Background(),
		key,
		[][]string{{"e2_f1", "e2_v1"}, {"e2_f2", "e2_v2"}},
		*options.NewXAddOptions().SetId("0-2"),
	)
	// read them
	client.XReadGroup(context.Background(), group, "myConsumer", map[string]string{key: ">"})
	// get the info
	time.Sleep(100 * time.Millisecond)
	response, err := client.XInfoConsumers(context.Background(), key, group)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Expanded:
	fmt.Printf("Consumer name:  %s\n", response[0].Name)
	fmt.Printf("PEL count:      %d\n", response[0].Pending)
	// exact values of `Idle` and `Inactive` depend on timing
	fmt.Printf("Idle > 0:       %t\n", response[0].Idle > 0)
	fmt.Printf("Inactive > 0:   %t\n", response[0].Inactive.Value() > 0) // Added in version 7.0.0
	// Output:
	// Consumer name:  myConsumer
	// PEL count:      2
	// Idle > 0:       true
	// Inactive > 0:   true
}

func ExampleClusterClient_XInfoConsumers() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := uuid.NewString()
	group := "myGroup"
	consumer := "myConsumer"

	// create an empty stream with a group
	client.XGroupCreateWithOptions(context.Background(), key, group, "0-0", *options.NewXGroupCreateOptions().SetMakeStream())
	// add couple of entries
	client.XAddWithOptions(
		context.Background(),
		key,
		[][]string{{"e1_f1", "e1_v1"}, {"e1_f2", "e1_v2"}},
		*options.NewXAddOptions().SetId("0-1"),
	)
	client.XAddWithOptions(
		context.Background(),
		key,
		[][]string{{"e2_f1", "e2_v1"}, {"e2_f2", "e2_v2"}},
		*options.NewXAddOptions().SetId("0-2"),
	)
	// read them
	client.XReadGroupWithOptions(
		context.Background(),
		group,
		consumer,
		map[string]string{key: ">"},
		*options.NewXReadGroupOptions().SetCount(1),
	)
	// get the info
	time.Sleep(100 * time.Millisecond)
	response, err := client.XInfoConsumers(context.Background(), key, group)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Expanded:
	fmt.Printf("Consumer name:  %s\n", response[0].Name)
	fmt.Printf("PEL count:      %d\n", response[0].Pending)
	// exact values of `Idle` and `Inactive` depend on timing
	fmt.Printf("Idle > 0:       %t\n", response[0].Idle > 0)
	fmt.Printf("Inactive > 0:   %t\n", response[0].Inactive.Value() > 0) // Added in version 7.0.0
	// Output:
	// Consumer name:  myConsumer
	// PEL count:      1
	// Idle > 0:       true
	// Inactive > 0:   true
}

func ExampleClient_XInfoGroups() {
	var client *Client = getExampleClient() // example helper function
	key := uuid.NewString()
	group := "myGroup"

	// create an empty stream with a group
	client.XGroupCreateWithOptions(context.Background(), key, group, "0-0", *options.NewXGroupCreateOptions().SetMakeStream())
	// add couple of entries
	client.XAddWithOptions(
		context.Background(),
		key,
		[][]string{{"e1_f1", "e1_v1"}, {"e1_f2", "e1_v2"}},
		*options.NewXAddOptions().SetId("0-1"),
	)
	client.XAddWithOptions(
		context.Background(),
		key,
		[][]string{{"e2_f1", "e2_v1"}, {"e2_f2", "e2_v2"}},
		*options.NewXAddOptions().SetId("0-2"),
	)
	// read them
	client.XReadGroup(context.Background(), group, "myConsumer", map[string]string{key: ">"})
	// get the info
	response, err := client.XInfoGroups(context.Background(), key)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(response)
	// Expanded:
	fmt.Printf("Group name:             %s\n", response[0].Name)
	fmt.Printf("Consumers count:        %d\n", response[0].Consumers)
	fmt.Printf("PEL count:              %d\n", response[0].Pending)
	fmt.Printf("Last delivered message: %s\n", response[0].LastDeliveredId)
	fmt.Printf("Entries read:           %d\n", response[0].EntriesRead.Value()) // Added in version 7.0.0
	fmt.Printf("Lag:                    %d\n", response[0].Lag.Value())         // Added in version 7.0.0
	// Output:
	// [{myGroup 1 2 0-2 {2 false} {0 false}}]
	// Group name:             myGroup
	// Consumers count:        1
	// PEL count:              2
	// Last delivered message: 0-2
	// Entries read:           2
	// Lag:                    0
}

func ExampleClusterClient_XInfoGroups() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := uuid.NewString()
	group := "myGroup"

	// create an empty stream with a group
	client.XGroupCreateWithOptions(context.Background(), key, group, "0-0", *options.NewXGroupCreateOptions().SetMakeStream())
	// add couple of entries
	client.XAddWithOptions(
		context.Background(),
		key,
		[][]string{{"e1_f1", "e1_v1"}, {"e1_f2", "e1_v2"}},
		*options.NewXAddOptions().SetId("0-1"),
	)
	client.XAddWithOptions(
		context.Background(),
		key,
		[][]string{{"e2_f1", "e2_v1"}, {"e2_f2", "e2_v2"}},
		*options.NewXAddOptions().SetId("0-2"),
	)
	// read them
	client.XReadGroup(context.Background(), group, "myConsumer", map[string]string{key: ">"})
	// get the info
	response, err := client.XInfoGroups(context.Background(), key)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(response)
	// Expanded:
	fmt.Printf("Group name:             %s\n", response[0].Name)
	fmt.Printf("Consumers count:        %d\n", response[0].Consumers)
	fmt.Printf("PEL count:              %d\n", response[0].Pending)
	fmt.Printf("Last delivered message: %s\n", response[0].LastDeliveredId)
	fmt.Printf("Entries read:           %d\n", response[0].EntriesRead.Value()) // Added in version 7.0.0
	fmt.Printf("Lag:                    %d\n", response[0].Lag.Value())         // Added in version 7.0.0
	// Output:
	// [{myGroup 1 2 0-2 {2 false} {0 false}}]
	// Group name:             myGroup
	// Consumers count:        1
	// PEL count:              2
	// Last delivered message: 0-2
	// Entries read:           2
	// Lag:                    0
}
