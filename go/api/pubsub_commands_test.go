// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"fmt"
	"time"
)

func ExampleGlideClient_Publish() {
	var publisher *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClient_PubSubChannels() {
	var publisher *GlideClient = getExampleGlideClient() // example helper function

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

// func ExampleGlideClusterClient_PubSubChannels() {
// 	var publisher *GlideClusterClient = getExampleGlideClusterClient() // example helper function

// 	// Create subscribers with subscriptions
// 	subscriber1 := getExampleGlideClusterClientWithSubscription(ExactMode, "channel1")
// 	subscriber2 := getExampleGlideClusterClientWithSubscription(ExactMode, "channel2")

// 	// Allow subscriptions to establish
// 	time.Sleep(100 * time.Millisecond)

// 	// Publish messages to create channels
// 	publisher.Publish("channel1", "message1")
// 	publisher.Publish("channel2", "message2")

// 	result, err := publisher.PubSubChannels()
// 	if err != nil {
// 		fmt.Println("Glide example failed with an error: ", err)
// 	}
// 	fmt.Println(result)

// 	// Output: [channel1 channel2]
// }
