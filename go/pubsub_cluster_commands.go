// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

// #cgo LDFLAGS: -lglide_ffi
// #include "lib.h"
import "C"
import (
	"context"
	"fmt"
)

// SSubscribe subscribes the client to the specified sharded channels (lazy, non-blocking).
// This command updates the client's internal desired subscription state without waiting
// for server confirmation. It returns immediately after updating the local state.
//
// Sharded pubsub is only available in cluster mode and requires Redis 7.0+.
//
// Parameters:
//   ctx - The context for the operation.
//   channels - A slice of sharded channel names to subscribe to.
//
// Return value:
//   An error if the operation fails.
//
// Example:
//   err := client.SSubscribe(ctx, []string{"shard_channel1"})
func (client *ClusterClient) SSubscribe(ctx context.Context, channels []string) error {
	_, err := client.executeCommand(ctx, C.SSubscribe, channels)
	return err
}

// SSubscribeBlocking subscribes the client to the specified sharded channels (blocking).
// This command updates the client's internal desired subscription state and waits
// for server confirmation.
//
// Sharded pubsub is only available in cluster mode and requires Redis 7.0+.
//
// Parameters:
//   ctx - The context for the operation.
//   channels - A slice of sharded channel names to subscribe to.
//   timeoutMs - Maximum time in milliseconds to wait for server confirmation.
//
// Return value:
//   An error if the operation fails or times out.
//
// Example:
//   err := client.SSubscribeBlocking(ctx, []string{"shard_channel1"}, 5000)
func (client *ClusterClient) SSubscribeBlocking(ctx context.Context, channels []string, timeoutMs int) error {
	args := append(channels, fmt.Sprintf("%d", timeoutMs))
	_, err := client.executeCommand(ctx, C.SSubscribeBlocking, args)
	return err
}

// SUnsubscribe unsubscribes the client from the specified sharded channels (lazy, non-blocking).
// If no channels are specified, unsubscribes from all sharded channels.
//
// Parameters:
//   ctx - The context for the operation.
//   channels - A slice of sharded channel names to unsubscribe from. Empty slice unsubscribes from all.
//
// Return value:
//   An error if the operation fails.
//
// Example:
//   err := client.SUnsubscribe(ctx, []string{"shard_channel1"})
func (client *ClusterClient) SUnsubscribe(ctx context.Context, channels []string) error {
	_, err := client.executeCommand(ctx, C.SUnsubscribe, channels)
	return err
}

// SUnsubscribeBlocking unsubscribes the client from the specified sharded channels (blocking).
// If no channels are specified, unsubscribes from all sharded channels.
//
// Parameters:
//   ctx - The context for the operation.
//   channels - A slice of sharded channel names to unsubscribe from.
//   timeoutMs - Maximum time in milliseconds to wait for server confirmation.
//
// Return value:
//   An error if the operation fails or times out.
//
// Example:
//   err := client.SUnsubscribeBlocking(ctx, []string{"shard_channel1"}, 5000)
func (client *ClusterClient) SUnsubscribeBlocking(ctx context.Context, channels []string, timeoutMs int) error {
	args := append(channels, fmt.Sprintf("%d", timeoutMs))
	_, err := client.executeCommand(ctx, C.SUnsubscribeBlocking, args)
	return err
}
