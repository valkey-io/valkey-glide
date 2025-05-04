// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"fmt"
	"sort"
	"time"
)

func ExampleGlideClient_Publish() {
	var publisher *GlideClient = getExampleGlideClient() // example helper function
	defer closeAllClients()

	// Create a subscriber with subscription
	subscriber := getExampleGlideClientWithSubscription(ExactChannelMode, "my_channel")
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

func ExampleGlideClusterClient_Publish() {
	var publisher *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	defer closeAllClients()

	// Create a subscriber with subscription
	subscriber := getExampleGlideClusterClientWithSubscription(ExactClusterChannelMode, "my_channel")
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

func ExampleGlideClient_PubSubChannels() {
	var publisher *GlideClient = getExampleGlideClient() // example helper function
	defer closeAllClients()

	// Create subscribers with subscriptions
	getExampleGlideClientWithSubscription(ExactChannelMode, "channel1")

	// Allow subscriptions to establish
	time.Sleep(100 * time.Millisecond)

	result, err := publisher.PubSubChannels()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: [channel1]
}

func ExampleGlideClusterClient_PubSubChannels() {
	var publisher *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	defer closeAllClients()

	// Create subscribers with subscriptions
	getExampleGlideClusterClientWithSubscription(ExactClusterChannelMode, "channel1")

	// Allow subscriptions to establish
	time.Sleep(100 * time.Millisecond)

	result, err := publisher.PubSubChannels()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: [channel1]
}

func ExampleGlideClient_PubSubChannelsWithPattern() {
	var publisher *GlideClient = getExampleGlideClient() // example helper function
	defer closeAllClients()

	// Create subscribers with subscriptions to different channels
	getExampleGlideClientWithSubscription(ExactChannelMode, "news.sports")
	getExampleGlideClientWithSubscription(ExactChannelMode, "news.weather")
	getExampleGlideClientWithSubscription(ExactChannelMode, "events.local")

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

func ExampleGlideClusterClient_PubSubChannelsWithPattern() {
	var publisher *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	defer closeAllClients()

	// Create subscribers with subscriptions to different channels
	getExampleGlideClusterClientWithSubscription(ExactClusterChannelMode, "news.sports")
	getExampleGlideClusterClientWithSubscription(ExactClusterChannelMode, "news.weather")
	getExampleGlideClusterClientWithSubscription(ExactClusterChannelMode, "events.local")
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

func ExampleGlideClusterClient_PubSubShardChannels() {
	var publisher *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	defer closeAllClients()

	// Create subscribers with subscriptions
	getExampleGlideClusterClientWithSubscription(ShardedClusterChannelMode, "channel1")

	// Allow subscriptions to establish
	time.Sleep(100 * time.Millisecond)

	result, err := publisher.PubSubShardChannels()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: [channel1]
}

func ExampleGlideClusterClient_PubSubShardChannelsWithPattern() {
	var publisher *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	defer closeAllClients()

	// Create subscribers with subscriptions to different channels
	getExampleGlideClusterClientWithSubscription(ShardedClusterChannelMode, "news.sports")
	getExampleGlideClusterClientWithSubscription(ShardedClusterChannelMode, "news.weather")
	getExampleGlideClusterClientWithSubscription(ShardedClusterChannelMode, "events.local")
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

func ExampleGlideClient_PubSubNumPat() {
	var publisher *GlideClient = getExampleGlideClient() // example helper function
	defer closeAllClients()

	// Create subscribers with subscriptions
	getExampleGlideClientWithSubscription(PatternChannelMode, "news.*")
	getExampleGlideClientWithSubscription(PatternChannelMode, "events.*")

	// Allow subscriptions to establish
	time.Sleep(100 * time.Millisecond)

	result, err := publisher.PubSubNumPat()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleGlideClusterClient_PubSubNumPat() {
	var publisher *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	defer closeAllClients()

	// Create subscribers with subscriptions
	getExampleGlideClusterClientWithSubscription(PatternClusterChannelMode, "news.*")
	getExampleGlideClusterClientWithSubscription(PatternClusterChannelMode, "events.*")

	// Allow subscriptions to establish
	time.Sleep(100 * time.Millisecond)

	result, err := publisher.PubSubNumPat()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleGlideClient_PubSubNumSub() {
	var publisher *GlideClient = getExampleGlideClient() // example helper function
	defer closeAllClients()

	// Create subscribers with subscriptions to different channels
	getExampleGlideClientWithSubscription(ExactChannelMode, "news.sports")
	getExampleGlideClientWithSubscription(ExactChannelMode, "news.weather")
	// Second subscriber to same channel
	getExampleGlideClientWithSubscription(ExactChannelMode, "news.weather")
	getExampleGlideClientWithSubscription(ExactChannelMode, "events.local")

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

func ExampleGlideClusterClient_PubSubNumSub() {
	var publisher *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	defer closeAllClients()

	// Create subscribers with subscriptions to different channels
	getExampleGlideClusterClientWithSubscription(ExactClusterChannelMode, "news.sports")
	getExampleGlideClusterClientWithSubscription(ExactClusterChannelMode, "news.weather")
	// Second subscriber to same channel
	getExampleGlideClusterClientWithSubscription(ExactClusterChannelMode, "news.weather")
	getExampleGlideClusterClientWithSubscription(ExactClusterChannelMode, "events.local")

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

func ExampleGlideClusterClient_PubSubShardNumSub() {
	var publisher *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	defer closeAllClients()

	// Create subscribers with subscriptions to different channels
	getExampleGlideClusterClientWithSubscription(ShardedClusterChannelMode, "news.sports")
	getExampleGlideClusterClientWithSubscription(ShardedClusterChannelMode, "news.weather")
	// Second subscriber to same channel
	getExampleGlideClusterClientWithSubscription(ShardedClusterChannelMode, "news.weather")
	getExampleGlideClusterClientWithSubscription(ShardedClusterChannelMode, "events.local")

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
