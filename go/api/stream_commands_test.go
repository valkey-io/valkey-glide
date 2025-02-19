// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"encoding/json"
	"fmt"
	"regexp"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/valkey-io/valkey-glide/go/api/options"
)

func ExampleGlideClient_XAdd() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	result, err := client.XAdd("mystream", [][]string{
		{"key1", "value1"},
		{"key2", "value2"},
	})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	matches, _ := regexp.Match(
		`^\d{13}-0$`,
		[]byte(result.Value()),
	) // matches a number that is 13 digits long followed by "-0"
	fmt.Println(matches)

	// Output: true
}

func ExampleGlideClusterClient_XAdd() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.XAdd("mystream", [][]string{
		{"key1", "value1"},
		{"key2", "value2"},
	})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	matches, _ := regexp.Match(
		`^\d{13}-0$`,
		[]byte(result.Value()),
	) // matches a number that is 13 digits long followed by "-0"
	fmt.Println(matches)

	// Output: true
}

func ExampleGlideClient_XAddWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	options := options.NewXAddOptions().
		SetId("1000-50")
	values := [][]string{
		{"key1", "value1"},
		{"key2", "value2"},
	}
	result, err := client.XAddWithOptions("mystream", values, *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.Value())

	// Output: 1000-50
}

func ExampleGlideClusterClient_XAddWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	options := options.NewXAddOptions().
		SetId("1000-50")
	values := [][]string{
		{"key1", "value1"},
		{"key2", "value2"},
	}
	result, err := client.XAddWithOptions("mystream", values, *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.Value())

	// Output: 1000-50
}

func ExampleGlideClient_XTrim() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	client.XAdd("mystream", [][]string{{"field1", "foo4"}, {"field2", "bar4"}})
	client.XAdd("mystream", [][]string{{"field3", "foo4"}, {"field4", "bar4"}})

	count, err := client.XTrim("mystream", *options.NewXTrimOptionsWithMaxLen(0).SetExactTrimming())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(count)

	// Output: 2
}

func ExampleGlideClusterClient_XTrim() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	client.XAdd("mystream", [][]string{{"field1", "foo4"}, {"field2", "bar4"}})
	client.XAdd("mystream", [][]string{{"field3", "foo4"}, {"field4", "bar4"}})

	count, err := client.XTrim("mystream", *options.NewXTrimOptionsWithMaxLen(0).SetExactTrimming())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(count)

	// Output: 2
}

func ExampleGlideClient_XLen() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	client.XAdd("mystream", [][]string{{"field1", "foo4"}, {"field2", "bar4"}})
	count, err := client.XLen("mystream")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(count)

	// Output: 1
}

func ExampleGlideClusterClient_XLen() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	client.XAdd("mystream", [][]string{{"field1", "foo4"}, {"field2", "bar4"}})
	count, err := client.XLen("mystream")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(count)

	// Output: 1
}

func ExampleGlideClient_XAutoClaim() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := uuid.NewString()
	group := uuid.NewString()
	consumer := uuid.NewString()

	client.XGroupCreateWithOptions(key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(key, group, consumer)
	client.XAddWithOptions(
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId("0-1"),
	)
	client.XAddWithOptions(
		key,
		[][]string{{"entry2_field1", "entry2_value1"}},
		*options.NewXAddOptions().SetId("0-2"),
	)
	client.XReadGroup(group, consumer, map[string]string{key: ">"})

	response, err := client.XAutoClaim(key, group, consumer, 0, "0-1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output:
	// {0-0 map[0-1:[[entry1_field1 entry1_value1] [entry1_field2 entry1_value2]] 0-2:[[entry2_field1 entry2_value1]]] []}
}

func ExampleGlideClusterClient_XAutoClaim() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := uuid.NewString()
	group := uuid.NewString()
	consumer := uuid.NewString()

	client.XGroupCreateWithOptions(key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(key, group, consumer)
	client.XAddWithOptions(
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId("0-1"),
	)
	client.XAddWithOptions(
		key,
		[][]string{{"entry2_field1", "entry2_value1"}},
		*options.NewXAddOptions().SetId("0-2"),
	)
	client.XReadGroup(group, consumer, map[string]string{key: ">"})

	response, err := client.XAutoClaim(key, group, consumer, 0, "0-1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output:
	// {0-0 map[0-1:[[entry1_field1 entry1_value1] [entry1_field2 entry1_value2]] 0-2:[[entry2_field1 entry2_value1]]] []}
}

func ExampleGlideClient_XAutoClaimWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := uuid.NewString()
	group := uuid.NewString()
	consumer := uuid.NewString()

	client.XGroupCreateWithOptions(key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(key, group, consumer)
	client.XAddWithOptions(
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId("0-1"),
	)
	client.XAddWithOptions(
		key,
		[][]string{{"entry2_field1", "entry2_value1"}},
		*options.NewXAddOptions().SetId("0-2"),
	)
	client.XReadGroup(group, consumer, map[string]string{key: ">"})

	options := options.NewXAutoClaimOptions().SetCount(1)
	response, err := client.XAutoClaimWithOptions(key, group, consumer, 0, "0-1", *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: {0-2 map[0-1:[[entry1_field1 entry1_value1] [entry1_field2 entry1_value2]]] []}
}

func ExampleGlideClusterClient_XAutoClaimWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := uuid.NewString()
	group := uuid.NewString()
	consumer := uuid.NewString()

	client.XGroupCreateWithOptions(key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(key, group, consumer)
	client.XAddWithOptions(
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId("0-1"),
	)
	client.XAddWithOptions(
		key,
		[][]string{{"entry2_field1", "entry2_value1"}},
		*options.NewXAddOptions().SetId("0-2"),
	)
	client.XReadGroup(group, consumer, map[string]string{key: ">"})

	options := options.NewXAutoClaimOptions().SetCount(1)
	response, err := client.XAutoClaimWithOptions(key, group, consumer, 0, "0-1", *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: {0-2 map[0-1:[[entry1_field1 entry1_value1] [entry1_field2 entry1_value2]]] []}
}

func ExampleGlideClient_XAutoClaimJustId() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := uuid.NewString()
	group := uuid.NewString()
	consumer := uuid.NewString()

	client.XGroupCreateWithOptions(key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(key, group, consumer)
	client.XAddWithOptions(
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId("0-1"),
	)
	client.XAddWithOptions(
		key,
		[][]string{{"entry2_field1", "entry2_value1"}},
		*options.NewXAddOptions().SetId("0-2"),
	)
	client.XReadGroup(group, consumer, map[string]string{key: ">"})

	response, err := client.XAutoClaimJustId(key, group, consumer, 0, "0-0")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: {0-0 [0-1 0-2] []}
}

func ExampleGlideClusterClient_XAutoClaimJustId() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := uuid.NewString()
	group := uuid.NewString()
	consumer := uuid.NewString()

	client.XGroupCreateWithOptions(key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(key, group, consumer)
	client.XAddWithOptions(
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId("0-1"),
	)
	client.XAddWithOptions(
		key,
		[][]string{{"entry2_field1", "entry2_value1"}},
		*options.NewXAddOptions().SetId("0-2"),
	)
	client.XReadGroup(group, consumer, map[string]string{key: ">"})

	response, err := client.XAutoClaimJustId(key, group, consumer, 0, "0-0")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: {0-0 [0-1 0-2] []}
}

func ExampleGlideClient_XAutoClaimJustIdWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := uuid.NewString()
	group := uuid.NewString()
	consumer := uuid.NewString()

	client.XGroupCreateWithOptions(key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(key, group, consumer)
	client.XAddWithOptions(
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId("0-1"),
	)
	client.XAddWithOptions(
		key,
		[][]string{{"entry2_field1", "entry2_value1"}},
		*options.NewXAddOptions().SetId("0-2"),
	)
	client.XReadGroup(group, consumer, map[string]string{key: ">"})

	options := options.NewXAutoClaimOptions().SetCount(1)
	response, err := client.XAutoClaimJustIdWithOptions(key, group, consumer, 0, "0-1", *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: {0-2 [0-1] []}
}

func ExampleGlideClusterClient_XAutoClaimJustIdWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := uuid.NewString()
	group := uuid.NewString()
	consumer := uuid.NewString()

	client.XGroupCreateWithOptions(key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(key, group, consumer)
	client.XAddWithOptions(
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId("0-1"),
	)
	client.XAddWithOptions(
		key,
		[][]string{{"entry2_field1", "entry2_value1"}},
		*options.NewXAddOptions().SetId("0-2"),
	)
	client.XReadGroup(group, consumer, map[string]string{key: ">"})

	options := options.NewXAutoClaimOptions().SetCount(1)
	response, err := client.XAutoClaimJustIdWithOptions(key, group, consumer, 0, "0-1", *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: {0-2 [0-1] []}
}

func ExampleGlideClient_XReadGroup() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := uuid.NewString()
	consumer := uuid.NewString()

	client.XGroupCreateWithOptions(key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(key, group, consumer)
	client.XAddWithOptions(
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)

	response, err := client.XReadGroup(group, consumer, map[string]string{key: "0"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: map[12345:map[]]
}

func ExampleGlideClusterClient_XReadGroup() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := uuid.NewString()
	consumer := uuid.NewString()

	client.XGroupCreateWithOptions(key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(key, group, consumer)
	client.XAddWithOptions(
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)

	response, err := client.XReadGroup(group, consumer, map[string]string{key: "0"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: map[12345:map[]]
}

func ExampleGlideClient_XReadGroupWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := uuid.NewString()
	consumer := uuid.NewString()

	client.XGroupCreateWithOptions(key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(key, group, consumer)
	client.XAddWithOptions(
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)

	options := options.NewXReadGroupOptions().SetNoAck()
	response, err := client.XReadGroupWithOptions(group, consumer, map[string]string{key: ">"}, *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: map[12345:map[12345-1:[[entry1_field1 entry1_value1] [entry1_field2 entry1_value2]]]]
}

func ExampleGlideClusterClient_XReadGroupWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := uuid.NewString()
	consumer := uuid.NewString()

	client.XGroupCreateWithOptions(key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(key, group, consumer)
	client.XAddWithOptions(
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)

	options := options.NewXReadGroupOptions().SetNoAck()
	response, err := client.XReadGroupWithOptions(group, consumer, map[string]string{key: ">"}, *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: map[12345:map[12345-1:[[entry1_field1 entry1_value1] [entry1_field2 entry1_value2]]]]
}

func ExampleGlideClient_XRead() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "12345"
	streamId := "12345-1"

	client.XAddWithOptions(
		key,
		[][]string{{"field1", "value1"}, {"field2", "value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)

	response, err := client.XRead(map[string]string{key: "0-0"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: map[12345:map[12345-1:[[field1 value1] [field2 value2]]]]
}

func ExampleGlideClusterClient_XRead() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "12345"
	streamId := "12345-1"

	client.XAddWithOptions(
		key,
		[][]string{{"field1", "value1"}, {"field2", "value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)

	response, err := client.XRead(map[string]string{key: "0-0"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: map[12345:map[12345-1:[[field1 value1] [field2 value2]]]]
}

func ExampleGlideClient_XReadWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "12345"
	streambase := 1

	genStreamId := func(key string, base int, offset int) string { return fmt.Sprintf("%s-%d", key, base+offset) } // helper function to generate stream ids

	client.XAddWithOptions(
		key,
		[][]string{{"field1", "value1"}, {"field2", "value2"}},
		*options.NewXAddOptions().SetId(genStreamId(key, streambase, 0)),
	)
	client.XAddWithOptions(
		key,
		[][]string{{"field3", "value3"}, {"field4", "value4"}},
		*options.NewXAddOptions().SetId(genStreamId(key, streambase, 1)),
	)

	response, err := client.XReadWithOptions(
		map[string]string{key: genStreamId(key, streambase, 0)},
		*options.NewXReadOptions().SetCount(1),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: map[12345:map[12345-2:[[field3 value3] [field4 value4]]]]
}

func ExampleGlideClusterClient_XReadWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "12345"
	streambase := 1

	genStreamId := func(key string, base int, offset int) string { return fmt.Sprintf("%s-%d", key, base+offset) } // helper function to generate stream ids

	client.XAddWithOptions(
		key,
		[][]string{{"field1", "value1"}, {"field2", "value2"}},
		*options.NewXAddOptions().SetId(genStreamId(key, streambase, 0)),
	)
	client.XAddWithOptions(
		key,
		[][]string{{"field3", "value3"}, {"field4", "value4"}},
		*options.NewXAddOptions().SetId(genStreamId(key, streambase, 1)),
	)

	response, err := client.XReadWithOptions(
		map[string]string{key: genStreamId(key, streambase, 0)},
		*options.NewXReadOptions().SetCount(1),
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: map[12345:map[12345-2:[[field3 value3] [field4 value4]]]]
}

func ExampleGlideClient_XDel() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "12345"

	client.XAddWithOptions(
		key,
		[][]string{{"field1", "value1"}, {"field2", "value2"}},
		*options.NewXAddOptions().SetId("0-1"),
	)

	count, err := client.XDel(key, []string{"0-1", "0-2", "0-3"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(count)

	// Output: 1
}

func ExampleGlideClusterClient_XDel() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "12345"

	client.XAddWithOptions(
		key,
		[][]string{{"field1", "value1"}, {"field2", "value2"}},
		*options.NewXAddOptions().SetId("0-1"),
	)

	count, err := client.XDel(key, []string{"0-1", "0-2", "0-3"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(count)

	// Output: 1
}

func ExampleGlideClient_XPending() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"
	consumer := "c12345"

	client.XGroupCreateWithOptions(key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(key, group, consumer)
	client.XAddWithOptions(
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XReadGroup(group, consumer, map[string]string{key: ">"})

	summary, err := client.XPending(key, group)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonSummary, _ := json.Marshal(summary)
	fmt.Println(string(jsonSummary))

	// Output: {"NumOfMessages":1,"StartId":{},"EndId":{},"ConsumerMessages":[{"ConsumerName":"c12345","MessageCount":1}]}
}

func ExampleGlideClusterClient_XPending() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"
	consumer := "c12345"

	client.XGroupCreateWithOptions(key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(key, group, consumer)
	client.XAddWithOptions(
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XReadGroup(group, consumer, map[string]string{key: ">"})

	summary, err := client.XPending(key, group)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonSummary, _ := json.Marshal(summary)
	fmt.Println(string(jsonSummary))

	// Output: {"NumOfMessages":1,"StartId":{},"EndId":{},"ConsumerMessages":[{"ConsumerName":"c12345","MessageCount":1}]}
}

func ExampleGlideClient_XPendingWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"
	consumer := "c12345"

	client.XGroupCreateWithOptions(key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(key, group, consumer)
	client.XAddWithOptions(
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XReadGroup(group, consumer, map[string]string{key: ">"})

	details, err := client.XPendingWithOptions(
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

func ExampleGlideClusterClient_XPendingWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"
	consumer := "c12345"

	client.XGroupCreateWithOptions(key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(key, group, consumer)
	client.XAddWithOptions(
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XReadGroup(group, consumer, map[string]string{key: ">"})

	details, err := client.XPendingWithOptions(
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

func ExampleGlideClient_XGroupSetId() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"
	consumer := "c12345"

	client.XGroupCreateWithOptions(key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(key, group, consumer)
	client.XAddWithOptions(
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XReadGroup(group, consumer, map[string]string{key: ">"})
	client.XAck(key, group, []string{streamId}) // ack the message and remove it from the pending list

	client.XGroupSetId(key, group, "0-0")                           // reset the last acknowledged message to 0-0
	client.XReadGroup(group, consumer, map[string]string{key: ">"}) // read the group again

	summary, err := client.XPending(key, group) // get the pending messages, which should include the entry we previously acked
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonSummary, _ := json.Marshal(summary)
	fmt.Println(string(jsonSummary))

	// Output: {"NumOfMessages":1,"StartId":{},"EndId":{},"ConsumerMessages":[{"ConsumerName":"c12345","MessageCount":1}]}
}

func ExampleGlideClusterClient_XGroupSetId() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"
	consumer := "c12345"

	client.XGroupCreateWithOptions(key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(key, group, consumer)
	client.XAddWithOptions(
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XReadGroup(group, consumer, map[string]string{key: ">"})
	client.XAck(key, group, []string{streamId}) // ack the message and remove it from the pending list

	client.XGroupSetId(key, group, "0-0")                           // reset the last acknowledged message to 0-0
	client.XReadGroup(group, consumer, map[string]string{key: ">"}) // read the group again

	summary, err := client.XPending(key, group) // get the pending messages, which should include the entry we previously acked
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonSummary, _ := json.Marshal(summary)
	fmt.Println(string(jsonSummary))

	// Output: {"NumOfMessages":1,"StartId":{},"EndId":{},"ConsumerMessages":[{"ConsumerName":"c12345","MessageCount":1}]}
}

func ExampleGlideClient_XGroupSetIdWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "12345"
	streamId1 := "12345-1"
	streamId2 := "12345-2"
	group := "g12345"
	consumer := "c12345"

	client.XGroupCreateWithOptions(key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(key, group, consumer)
	client.XAddWithOptions(
		key,
		[][]string{{"field1", "value1"}, {"field2", "value2"}},
		*options.NewXAddOptions().SetId(streamId1),
	)
	client.XAddWithOptions(
		key,
		[][]string{{"field3", "value3"}, {"field4", "value4"}},
		*options.NewXAddOptions().SetId(streamId2),
	)
	client.XReadGroup(group, consumer, map[string]string{key: ">"})
	client.XAck(key, group, []string{streamId1, streamId2}) // ack the message and remove it from the pending list

	opts := options.NewXGroupSetIdOptionsOptions().SetEntriesRead(1)
	client.XGroupSetIdWithOptions(key, group, "0-0", *opts)         // reset the last acknowledged message to 0-0
	client.XReadGroup(group, consumer, map[string]string{key: ">"}) // read the group again

	summary, err := client.XPending(key, group) // get the pending messages, which should include the entry we previously acked
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonSummary, _ := json.Marshal(summary)
	fmt.Println(string(jsonSummary))

	// Output: {"NumOfMessages":2,"StartId":{},"EndId":{},"ConsumerMessages":[{"ConsumerName":"c12345","MessageCount":2}]}
}

func ExampleGlideClusterClient_XGroupSetIdWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "12345"
	streamId1 := "12345-1"
	streamId2 := "12345-2"
	group := "g12345"
	consumer := "c12345"

	client.XGroupCreateWithOptions(key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(key, group, consumer)
	client.XAddWithOptions(
		key,
		[][]string{{"field1", "value1"}, {"field2", "value2"}},
		*options.NewXAddOptions().SetId(streamId1),
	)
	client.XAddWithOptions(
		key,
		[][]string{{"field3", "value3"}, {"field4", "value4"}},
		*options.NewXAddOptions().SetId(streamId2),
	)
	client.XReadGroup(group, consumer, map[string]string{key: ">"})
	client.XAck(key, group, []string{streamId1, streamId2}) // ack the message and remove it from the pending list

	opts := options.NewXGroupSetIdOptionsOptions().SetEntriesRead(1)
	client.XGroupSetIdWithOptions(key, group, "0-0", *opts)         // reset the last acknowledged message to 0-0
	client.XReadGroup(group, consumer, map[string]string{key: ">"}) // read the group again

	summary, err := client.XPending(key, group) // get the pending messages, which should include the entry we previously acked
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonSummary, _ := json.Marshal(summary)
	fmt.Println(string(jsonSummary))

	// Output: {"NumOfMessages":2,"StartId":{},"EndId":{},"ConsumerMessages":[{"ConsumerName":"c12345","MessageCount":2}]}
}

func ExampleGlideClient_XGroupCreate() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"

	client.XAddWithOptions(
		key,
		[][]string{{"field1", "value1"}, {"field2", "value2"}},
		*options.NewXAddOptions().SetId(streamId),
	) // This will create the stream if it does not exist

	response, err := client.XGroupCreate(key, group, "0") // create the group (no MKSTREAM needed)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: OK
}

func ExampleGlideClusterClient_XGroupCreate() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"

	client.XAddWithOptions(
		key,
		[][]string{{"field1", "value1"}, {"field2", "value2"}},
		*options.NewXAddOptions().SetId(streamId),
	) // This will create the stream if it does not exist

	response, err := client.XGroupCreate(key, group, "0") // create the group (no MKSTREAM needed)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: OK
}

func ExampleGlideClient_XGroupCreateWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "12345"
	group := "g12345"

	opts := options.NewXGroupCreateOptions().SetMakeStream()
	response, err := client.XGroupCreateWithOptions(key, group, "0", *opts) // create the group (no MKSTREAM needed)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: OK
}

func ExampleGlideClusterClient_XGroupCreateWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "12345"
	group := "g12345"

	opts := options.NewXGroupCreateOptions().SetMakeStream()
	response, err := client.XGroupCreateWithOptions(key, group, "0", *opts) // create the group (no MKSTREAM needed)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: OK
}

func ExampleGlideClient_XGroupDestroy() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "12345"
	group := "g12345"

	opts := options.NewXGroupCreateOptions().SetMakeStream()
	client.XGroupCreateWithOptions(key, group, "0", *opts) // create the group (no MKSTREAM needed)

	success, err := client.XGroupDestroy(key, group) // destroy the group
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(success)

	// Output: true
}

func ExampleGlideClusterClient_XGroupDestroy() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "12345"
	group := "g12345"

	opts := options.NewXGroupCreateOptions().SetMakeStream()
	client.XGroupCreateWithOptions(key, group, "0", *opts) // create the group (no MKSTREAM needed)

	success, err := client.XGroupDestroy(key, group) // destroy the group
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(success)

	// Output: true
}

func ExampleGlideClient_XGroupCreateConsumer() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "12345"
	group := "g12345"
	consumer := "c12345"

	opts := options.NewXGroupCreateOptions().SetMakeStream()
	client.XGroupCreateWithOptions(key, group, "0", *opts) // create the group (no MKSTREAM needed)

	success, err := client.XGroupCreateConsumer(key, group, consumer)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(success)

	// Output: true
}

func ExampleGlideClusterClient_XGroupCreateConsumer() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "12345"
	group := "g12345"
	consumer := "c12345"

	opts := options.NewXGroupCreateOptions().SetMakeStream()
	client.XGroupCreateWithOptions(key, group, "0", *opts) // create the group (no MKSTREAM needed)

	success, err := client.XGroupCreateConsumer(key, group, consumer)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(success)

	// Output: true
}

func ExampleGlideClient_XGroupDelConsumer() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "12345"
	group := "g12345"
	consumer := "c12345"
	streamId := "12345-1"

	client.XAddWithOptions(
		key,
		[][]string{{"field1", "value1"}, {"field2", "value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XGroupCreate(key, group, "0")
	client.XGroupCreateConsumer(key, group, consumer)
	client.XReadGroup(group, consumer, map[string]string{key: ">"})

	count, err := client.XGroupDelConsumer(key, group, consumer)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println("Consumer deleted. Messages pending:", count)

	// Output:
	// Consumer deleted. Messages pending: 1
}

func ExampleGlideClusterClient_XGroupDelConsumer() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "12345"
	group := "g12345"
	consumer := "c12345"
	streamId := "12345-1"

	client.XAddWithOptions(
		key,
		[][]string{{"field1", "value1"}, {"field2", "value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XGroupCreate(key, group, "0")
	client.XGroupCreateConsumer(key, group, consumer)
	client.XReadGroup(group, consumer, map[string]string{key: ">"})

	count, err := client.XGroupDelConsumer(key, group, consumer)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println("Consumer deleted. Messages pending:", count)

	// Output:
	// Consumer deleted. Messages pending: 1
}

func ExampleGlideClient_XAck() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "12345"
	group := "g12345"
	consumer := "c12345"

	streamId, _ := client.XAdd(
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
	)
	client.XGroupCreate(key, group, "0")
	client.XGroupCreateConsumer(key, group, consumer)
	client.XReadGroup(group, consumer, map[string]string{key: ">"})

	count, err := client.XAck(key, group, []string{streamId.Value()}) // ack the message and remove it from the pending list
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(count)

	// Output: 1
}

func ExampleGlideClusterClient_XAck() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "12345"
	group := "g12345"
	consumer := "c12345"

	streamId, _ := client.XAdd(
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
	)
	client.XGroupCreate(key, group, "0")
	client.XGroupCreateConsumer(key, group, consumer)
	client.XReadGroup(group, consumer, map[string]string{key: ">"})

	count, err := client.XAck(key, group, []string{streamId.Value()}) // ack the message and remove it from the pending list
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(count)

	// Output: 1
}

func ExampleGlideClient_XClaim() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"
	consumer1 := "c12345-1"
	consumer2 := "c12345-2"

	client.XGroupCreateWithOptions(key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(key, group, consumer1)
	client.XAddWithOptions(
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XReadGroup(group, consumer1, map[string]string{key: ">"})

	result, err := client.XPendingWithOptions(
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

	response, err := client.XClaim(key, group, consumer2, result[0].IdleTime, []string{result[0].Id})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Printf("Claimed %d message\n", len(response))

	// Output: Claimed 1 message
}

func ExampleGlideClusterClient_XClaim() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"
	consumer1 := "c12345-1"
	consumer2 := "c12345-2"

	client.XGroupCreateWithOptions(key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(key, group, consumer1)
	client.XAddWithOptions(
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XReadGroup(group, consumer1, map[string]string{key: ">"})

	result, err := client.XPendingWithOptions(
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

	response, err := client.XClaim(key, group, consumer2, result[0].IdleTime, []string{result[0].Id})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Printf("Claimed %d message\n", len(response))

	// Output: Claimed 1 message
}

func ExampleGlideClient_XClaimWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"
	consumer1 := "c12345-1"
	consumer2 := "c12345-2"

	client.XGroupCreateWithOptions(key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(key, group, consumer1)
	client.XAddWithOptions(
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XReadGroup(group, consumer1, map[string]string{key: ">"})

	result, err := client.XPendingWithOptions(
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
	response, err := client.XClaimWithOptions(key, group, consumer2, result[0].IdleTime, []string{result[0].Id}, *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Printf("Claimed %d message\n", len(response))

	// Output: Claimed 1 message
}

func ExampleGlideClusterClient_XClaimWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"
	consumer1 := "c12345-1"
	consumer2 := "c12345-2"

	client.XGroupCreateWithOptions(key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(key, group, consumer1)
	client.XAddWithOptions(
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XReadGroup(group, consumer1, map[string]string{key: ">"})

	result, err := client.XPendingWithOptions(
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
	response, err := client.XClaimWithOptions(key, group, consumer2, result[0].IdleTime, []string{result[0].Id}, *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Printf("Claimed %d message\n", len(response))

	// Output: Claimed 1 message
}

func ExampleGlideClient_XClaimJustId() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"
	consumer1 := "c12345-1"
	consumer2 := "c12345-2"

	client.XGroupCreateWithOptions(key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(key, group, consumer1)
	client.XAddWithOptions(
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XReadGroup(group, consumer1, map[string]string{key: ">"})

	result, err := client.XPendingWithOptions(
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

	response, err := client.XClaimJustId(key, group, consumer2, result[0].IdleTime, []string{result[0].Id})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: [12345-1]
}

func ExampleGlideClusterClient_XClaimJustId() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"
	consumer1 := "c12345-1"
	consumer2 := "c12345-2"

	client.XGroupCreateWithOptions(key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(key, group, consumer1)
	client.XAddWithOptions(
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XReadGroup(group, consumer1, map[string]string{key: ">"})

	result, err := client.XPendingWithOptions(
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

	response, err := client.XClaimJustId(key, group, consumer2, result[0].IdleTime, []string{result[0].Id})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: [12345-1]
}

func ExampleGlideClient_XClaimJustIdWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"
	consumer1 := "c12345-1"
	consumer2 := "c12345-2"

	client.XGroupCreateWithOptions(key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(key, group, consumer1)
	client.XAddWithOptions(
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XReadGroup(group, consumer1, map[string]string{key: ">"})

	result, err := client.XPendingWithOptions(
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
	response, err := client.XClaimJustIdWithOptions(key, group, consumer2, result[0].IdleTime, []string{result[0].Id}, *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: [12345-1]
}

func ExampleGlideClusterClient_XClaimJustIdWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "12345"
	streamId := "12345-1"
	group := "g12345"
	consumer1 := "c12345-1"
	consumer2 := "c12345-2"

	client.XGroupCreateWithOptions(key, group, "0", *options.NewXGroupCreateOptions().SetMakeStream())
	client.XGroupCreateConsumer(key, group, consumer1)
	client.XAddWithOptions(
		key,
		[][]string{{"entry1_field1", "entry1_value1"}, {"entry1_field2", "entry1_value2"}},
		*options.NewXAddOptions().SetId(streamId),
	)
	client.XReadGroup(group, consumer1, map[string]string{key: ">"})

	result, err := client.XPendingWithOptions(
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
	response, err := client.XClaimJustIdWithOptions(key, group, consumer2, result[0].IdleTime, []string{result[0].Id}, *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: [12345-1]
}

func ExampleGlideClient_XRange() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "12345"

	client.XAdd(key, [][]string{{"field1", "value1"}})
	client.XAdd(key, [][]string{{"field2", "value2"}})

	response, err := client.XRange(key,
		options.NewInfiniteStreamBoundary(options.NegativeInfinity),
		options.NewInfiniteStreamBoundary(options.PositiveInfinity))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(len(response))

	// Output: 2
}

func ExampleGlideClusterClient_XRange() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "12345"

	client.XAdd(key, [][]string{{"field1", "value1"}})
	client.XAdd(key, [][]string{{"field2", "value2"}})

	response, err := client.XRange(key,
		options.NewInfiniteStreamBoundary(options.NegativeInfinity),
		options.NewInfiniteStreamBoundary(options.PositiveInfinity))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(len(response))

	// Output: 2
}

func ExampleGlideClient_XRangeWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "12345"

	streamId1, _ := client.XAdd(key, [][]string{{"field1", "value1"}})
	streamId2, _ := client.XAdd(key, [][]string{{"field2", "value2"}})

	response, err := client.XRangeWithOptions(key,
		options.NewStreamBoundary(streamId1.Value(), true),
		options.NewStreamBoundary(streamId2.Value(), true),
		*options.NewXRangeOptions().SetCount(1))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(len(response))

	// Output: 1
}

func ExampleGlideClusterClient_XRangeWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "12345"

	streamId1, _ := client.XAdd(key, [][]string{{"field1", "value1"}})
	streamId2, _ := client.XAdd(key, [][]string{{"field2", "value2"}})

	response, err := client.XRangeWithOptions(key,
		options.NewStreamBoundary(streamId1.Value(), true),
		options.NewStreamBoundary(streamId2.Value(), true),
		*options.NewXRangeOptions().SetCount(1))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(len(response))

	// Output: 1
}

func ExampleGlideClient_XRevRange() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "12345"
	streamId1 := "12345-1"
	streamId2 := "12345-2"

	client.XAddWithOptions(key, [][]string{{"field1", "value1"}}, *options.NewXAddOptions().SetId(streamId1))
	client.XAddWithOptions(key, [][]string{{"field2", "value2"}}, *options.NewXAddOptions().SetId(streamId2))

	response, err := client.XRevRange(key,
		options.NewStreamBoundary(streamId2, true),
		options.NewStreamBoundary(streamId1, true))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: [{12345-2 [[field2 value2]]} {12345-1 [[field1 value1]]}]
}

func ExampleGlideClusterClient_XRevRange() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "12345"
	streamId1 := "12345-1"
	streamId2 := "12345-2"

	client.XAddWithOptions(key, [][]string{{"field1", "value1"}}, *options.NewXAddOptions().SetId(streamId1))
	client.XAddWithOptions(key, [][]string{{"field2", "value2"}}, *options.NewXAddOptions().SetId(streamId2))

	response, err := client.XRevRange(key,
		options.NewStreamBoundary(streamId2, true),
		options.NewStreamBoundary(streamId1, true))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: [{12345-2 [[field2 value2]]} {12345-1 [[field1 value1]]}]
}

func ExampleGlideClient_XRevRangeWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "12345"
	streamId1 := "12345-1"
	streamId2 := "12345-2"

	client.XAddWithOptions(key, [][]string{{"field1", "value1"}}, *options.NewXAddOptions().SetId(streamId1))
	client.XAddWithOptions(key, [][]string{{"field2", "value2"}}, *options.NewXAddOptions().SetId(streamId2))

	response, err := client.XRevRangeWithOptions(key,
		options.NewStreamBoundary(streamId2, true),
		options.NewStreamBoundary(streamId1, true),
		*options.NewXRangeOptions().SetCount(2))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: [{12345-2 [[field2 value2]]} {12345-1 [[field1 value1]]}]
}

func ExampleGlideClusterClient_XRevRangeWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "12345"
	streamId1 := "12345-1"
	streamId2 := "12345-2"

	client.XAddWithOptions(key, [][]string{{"field1", "value1"}}, *options.NewXAddOptions().SetId(streamId1))
	client.XAddWithOptions(key, [][]string{{"field2", "value2"}}, *options.NewXAddOptions().SetId(streamId2))

	response, err := client.XRevRangeWithOptions(key,
		options.NewStreamBoundary(streamId2, true),
		options.NewStreamBoundary(streamId1, true),
		*options.NewXRangeOptions().SetCount(2))
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: [{12345-2 [[field2 value2]]} {12345-1 [[field1 value1]]}]
}

func ExampleGlideClient_XInfoStream() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "12345"
	streamId1 := "12345-1"

	client.XAddWithOptions(key, [][]string{{"field1", "value1"}}, *options.NewXAddOptions().SetId(streamId1))
	response, err := client.XInfoStream(key)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonResult, _ := json.MarshalIndent(response, "", "  ")

	fmt.Println(string(jsonResult))
	// Output:
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
}

func ExampleGlideClusterClient_XInfoStream() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "12345"
	streamId1 := "12345-1"

	client.XAddWithOptions(key, [][]string{{"field1", "value1"}}, *options.NewXAddOptions().SetId(streamId1))
	response, err := client.XInfoStream(key)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	jsonResult, _ := json.MarshalIndent(response, "", "  ")

	fmt.Println(string(jsonResult))
	// Output:
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
}

func ExampleGlideClient_XInfoStreamFullWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "12345"

	for i := 1; i <= 5; i++ {
		field := fmt.Sprintf("field%d", i)
		value := fmt.Sprintf("value%d", i)
		streamId := fmt.Sprintf("%s-%d", key, i)

		client.XAddWithOptions(key, [][]string{{field, value}}, *options.NewXAddOptions().SetId(streamId))
	}

	options := options.NewXInfoStreamOptionsOptions().SetCount(2)
	response, err := client.XInfoStreamFullWithOptions(key, options)
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

func ExampleGlideClusterClient_XInfoStreamFullWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "12345"

	for i := 1; i <= 5; i++ {
		field := fmt.Sprintf("field%d", i)
		value := fmt.Sprintf("value%d", i)
		streamId := fmt.Sprintf("%s-%d", key, i)

		client.XAddWithOptions(key, [][]string{{field, value}}, *options.NewXAddOptions().SetId(streamId))
	}

	options := options.NewXInfoStreamOptionsOptions().SetCount(2)
	response, err := client.XInfoStreamFullWithOptions(key, options)
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

func ExampleGlideClient_XInfoConsumers() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := uuid.NewString()
	group := "myGroup"

	// create an empty stream with a group
	client.XGroupCreateWithOptions(key, group, "0-0", *options.NewXGroupCreateOptions().SetMakeStream())
	// add couple of entries
	client.XAddWithOptions(key, [][]string{{"e1_f1", "e1_v1"}, {"e1_f2", "e1_v2"}}, *options.NewXAddOptions().SetId("0-1"))
	client.XAddWithOptions(key, [][]string{{"e2_f1", "e2_v1"}, {"e2_f2", "e2_v2"}}, *options.NewXAddOptions().SetId("0-2"))
	// read them
	client.XReadGroup(group, "myConsumer", map[string]string{key: ">"})
	// get the info
	time.Sleep(100 * time.Millisecond)
	response, err := client.XInfoConsumers(key, group)
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

func ExampleGlideClusterClient_XInfoConsumers() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := uuid.NewString()
	group := "myGroup"
	consumer := "myConsumer"

	// create an empty stream with a group
	client.XGroupCreateWithOptions(key, group, "0-0", *options.NewXGroupCreateOptions().SetMakeStream())
	// add couple of entries
	client.XAddWithOptions(key, [][]string{{"e1_f1", "e1_v1"}, {"e1_f2", "e1_v2"}}, *options.NewXAddOptions().SetId("0-1"))
	client.XAddWithOptions(key, [][]string{{"e2_f1", "e2_v1"}, {"e2_f2", "e2_v2"}}, *options.NewXAddOptions().SetId("0-2"))
	// read them
	client.XReadGroupWithOptions(group, consumer, map[string]string{key: ">"}, *options.NewXReadGroupOptions().SetCount(1))
	// get the info
	time.Sleep(100 * time.Millisecond)
	response, err := client.XInfoConsumers(key, group)
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

func ExampleGlideClient_XInfoGroups() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := uuid.NewString()
	group := "myGroup"

	// create an empty stream with a group
	client.XGroupCreateWithOptions(key, group, "0-0", *options.NewXGroupCreateOptions().SetMakeStream())
	// add couple of entries
	client.XAddWithOptions(key, [][]string{{"e1_f1", "e1_v1"}, {"e1_f2", "e1_v2"}}, *options.NewXAddOptions().SetId("0-1"))
	client.XAddWithOptions(key, [][]string{{"e2_f1", "e2_v1"}, {"e2_f2", "e2_v2"}}, *options.NewXAddOptions().SetId("0-2"))
	// read them
	client.XReadGroup(group, "myConsumer", map[string]string{key: ">"})
	// get the info
	response, err := client.XInfoGroups(key)
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

func ExampleGlideClusterClient_XInfoGroups() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := uuid.NewString()
	group := "myGroup"

	// create an empty stream with a group
	client.XGroupCreateWithOptions(key, group, "0-0", *options.NewXGroupCreateOptions().SetMakeStream())
	// add couple of entries
	client.XAddWithOptions(key, [][]string{{"e1_f1", "e1_v1"}, {"e1_f2", "e1_v2"}}, *options.NewXAddOptions().SetId("0-1"))
	client.XAddWithOptions(key, [][]string{{"e2_f1", "e2_v1"}, {"e2_f2", "e2_v2"}}, *options.NewXAddOptions().SetId("0-2"))
	// read them
	client.XReadGroup(group, "myConsumer", map[string]string{key: ">"})
	// get the info
	response, err := client.XInfoGroups(key)
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
