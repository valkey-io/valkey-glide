// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"fmt"
	"sort"
	"time"

	"github.com/valkey-io/valkey-glide/go/v2/config"
)

func ExampleClient_Publish() {
	var publisher *Client = getExampleGlideClient() // example helper function
	defer closeAllClients()

	// Create a subscriber with subscription
	subscriber := getExampleClientWithSubscription(config.ExactChannelMode, "my_channel")
	queue, err := subscriber.GetQueue()
	if err != nil {
		fmt.Println("Failed to get queue: ", err)
		return
	}

	// Allow subscription to establish
	time.Sleep(100 * time.Millisecond)

	// Publish a message
	result, err := publisher.Publish("my_channel", "Hello, World!")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Wait for and print the received message
	msg := <-queue.WaitForMessage()
	fmt.Println(msg.Message)

	// Output:
	// 1
	// Hello, World!
}

func ExampleClusterClient_Publish() {
	var publisher *ClusterClient = getExampleGlideClusterClient() // example helper function
	defer closeAllClients()

	// Create a subscriber with subscription
	subscriber := getExampleClusterClientWithSubscription(config.ExactClusterChannelMode, "my_channel")
	queue, err := subscriber.GetQueue()
	if err != nil {
		fmt.Println("Failed to get queue: ", err)
		return
	}

	// Allow subscription to establish
	time.Sleep(100 * time.Millisecond)

	// Publish a message
	result, err := publisher.Publish("my_channel", "Hello, World!", false)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Wait for and print the received message
	msg := <-queue.WaitForMessage()
	fmt.Println(msg.Message)

	// Output:
	// 1
	// Hello, World!
}

func ExampleClient_PubSubChannels() {
	var publisher *Client = getExampleGlideClient() // example helper function
	defer closeAllClients()

	// Create subscribers with subscriptions
	getExampleClientWithSubscription(config.ExactChannelMode, "channel1")

	// Allow subscriptions to establish
	time.Sleep(100 * time.Millisecond)

	result, err := publisher.PubSubChannels()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: [channel1]
}

func ExampleClusterClient_PubSubChannels() {
	var publisher *ClusterClient = getExampleGlideClusterClient() // example helper function
	defer closeAllClients()

	// Create subscribers with subscriptions
	getExampleClusterClientWithSubscription(config.ExactClusterChannelMode, "channel1")

	// Allow subscriptions to establish
	time.Sleep(100 * time.Millisecond)

	result, err := publisher.PubSubChannels()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: [channel1]
}

func ExampleClient_PubSubChannelsWithPattern() {
	var publisher *Client = getExampleGlideClient() // example helper function
	defer closeAllClients()

	// Create subscribers with subscriptions to different channels
	getExampleClientWithSubscription(config.ExactChannelMode, "news.sports")
	getExampleClientWithSubscription(config.ExactChannelMode, "news.weather")
	getExampleClientWithSubscription(config.ExactChannelMode, "events.local")

	// Allow subscriptions to establish
	time.Sleep(100 * time.Millisecond)

	// Get channels matching the "news.*" pattern
	result, err := publisher.PubSubChannelsWithPattern("news.*")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	sort.Strings(result)
	fmt.Println(result)

	// Output: [news.sports news.weather]
}

func ExampleClusterClient_PubSubChannelsWithPattern() {
	var publisher *ClusterClient = getExampleGlideClusterClient() // example helper function
	defer closeAllClients()

	// Create subscribers with subscriptions to different channels
	getExampleClusterClientWithSubscription(config.ExactClusterChannelMode, "news.sports")
	getExampleClusterClientWithSubscription(config.ExactClusterChannelMode, "news.weather")
	getExampleClusterClientWithSubscription(config.ExactClusterChannelMode, "events.local")
	// Allow subscriptions to establish
	time.Sleep(100 * time.Millisecond)

	// Get channels matching the "news.*" pattern
	result, err := publisher.PubSubChannelsWithPattern("news.*")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	sort.Strings(result)
	fmt.Println(result)

	// Output: [news.sports news.weather]
}

func ExampleClusterClient_PubSubShardChannels() {
	var publisher *ClusterClient = getExampleGlideClusterClient() // example helper function
	defer closeAllClients()

	// Create subscribers with subscriptions
	getExampleClusterClientWithSubscription(config.ShardedClusterChannelMode, "channel1")

	// Allow subscriptions to establish
	time.Sleep(100 * time.Millisecond)

	result, err := publisher.PubSubShardChannels()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: [channel1]
}

func ExampleClusterClient_PubSubShardChannelsWithPattern() {
	var publisher *ClusterClient = getExampleGlideClusterClient() // example helper function
	defer closeAllClients()

	// Create subscribers with subscriptions to different channels
	getExampleClusterClientWithSubscription(config.ShardedClusterChannelMode, "news.sports")
	getExampleClusterClientWithSubscription(config.ShardedClusterChannelMode, "news.weather")
	getExampleClusterClientWithSubscription(config.ShardedClusterChannelMode, "events.local")
	// Allow subscriptions to establish
	time.Sleep(100 * time.Millisecond)

	// Get channels matching the "news.*" pattern
	result, err := publisher.PubSubShardChannelsWithPattern("news.*")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	sort.Strings(result)
	fmt.Println(result)

	// Output: [news.sports news.weather]
}

func ExampleClient_PubSubNumPat() {
	var publisher *Client = getExampleGlideClient() // example helper function
	defer closeAllClients()

	// Create subscribers with subscriptions
	getExampleClientWithSubscription(config.PatternChannelMode, "news.*")
	getExampleClientWithSubscription(config.PatternChannelMode, "events.*")

	// Allow subscriptions to establish
	time.Sleep(100 * time.Millisecond)

	result, err := publisher.PubSubNumPat()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleClusterClient_PubSubNumPat() {
	var publisher *ClusterClient = getExampleGlideClusterClient() // example helper function
	defer closeAllClients()

	// Create subscribers with subscriptions
	getExampleClusterClientWithSubscription(config.PatternClusterChannelMode, "news.*")
	getExampleClusterClientWithSubscription(config.PatternClusterChannelMode, "events.*")

	// Allow subscriptions to establish
	time.Sleep(100 * time.Millisecond)

	result, err := publisher.PubSubNumPat()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleClient_PubSubNumSub() {
	var publisher *Client = getExampleGlideClient() // example helper function
	defer closeAllClients()

	// Create subscribers with subscriptions to different channels
	getExampleClientWithSubscription(config.ExactChannelMode, "news.sports")
	getExampleClientWithSubscription(config.ExactChannelMode, "news.weather")
	// Second subscriber to same channel
	getExampleClientWithSubscription(config.ExactChannelMode, "news.weather")
	getExampleClientWithSubscription(config.ExactChannelMode, "events.local")

	// Allow subscriptions to establish
	time.Sleep(100 * time.Millisecond)

	// Get subscriber counts for specific channels
	result, err := publisher.PubSubNumSub("news.sports", "news.weather", "events.local")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Sort the channels for consistent output
	channels := make([]string, 0, len(result))
	for channel := range result {
		channels = append(channels, channel)
	}
	sort.Strings(channels)

	// Print results in sorted order
	for _, channel := range channels {
		fmt.Printf("%s: %d\n", channel, result[channel])
	}

	// Output:
	// events.local: 1
	// news.sports: 1
	// news.weather: 2
}

func ExampleClusterClient_PubSubNumSub() {
	var publisher *ClusterClient = getExampleGlideClusterClient() // example helper function
	defer closeAllClients()

	// Create subscribers with subscriptions to different channels
	getExampleClusterClientWithSubscription(config.ExactClusterChannelMode, "news.sports")
	getExampleClusterClientWithSubscription(config.ExactClusterChannelMode, "news.weather")
	// Second subscriber to same channel
	getExampleClusterClientWithSubscription(config.ExactClusterChannelMode, "news.weather")
	getExampleClusterClientWithSubscription(config.ExactClusterChannelMode, "events.local")

	// Allow subscriptions to establish
	time.Sleep(100 * time.Millisecond)

	// Get subscriber counts for specific channels
	result, err := publisher.PubSubNumSub("news.sports", "news.weather", "events.local")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Sort the channels for consistent output
	channels := make([]string, 0, len(result))
	for channel := range result {
		channels = append(channels, channel)
	}
	sort.Strings(channels)

	// Print results in sorted order
	for _, channel := range channels {
		fmt.Printf("%s: %d\n", channel, result[channel])
	}

	// Output:
	// events.local: 1
	// news.sports: 1
	// news.weather: 2
}

func ExampleClusterClient_PubSubShardNumSub() {
	var publisher *ClusterClient = getExampleGlideClusterClient() // example helper function
	defer closeAllClients()

	// Create subscribers with subscriptions to different channels
	getExampleClusterClientWithSubscription(config.ShardedClusterChannelMode, "news.sports")
	getExampleClusterClientWithSubscription(config.ShardedClusterChannelMode, "news.weather")
	// Second subscriber to same channel
	getExampleClusterClientWithSubscription(config.ShardedClusterChannelMode, "news.weather")
	getExampleClusterClientWithSubscription(config.ShardedClusterChannelMode, "events.local")

	// Allow subscriptions to establish
	time.Sleep(100 * time.Millisecond)

	// Get subscriber counts for specific channels
	result, err := publisher.PubSubShardNumSub("news.sports", "news.weather", "events.local")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Sort the channels for consistent output
	channels := make([]string, 0, len(result))
	for channel := range result {
		channels = append(channels, channel)
	}
	sort.Strings(channels)

	// Print results in sorted order
	for _, channel := range channels {
		fmt.Printf("%s: %d\n", channel, result[channel])
	}

	// Output:
	// events.local: 1
	// news.sports: 1
	// news.weather: 2
}
