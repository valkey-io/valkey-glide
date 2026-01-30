// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

// #cgo LDFLAGS: -lglide_ffi
// #include "lib.h"
import "C"
import (
	"context"
	"fmt"
)

// Subscribe subscribes the client to the specified channels (lazy, non-blocking).
// This command updates the client's internal desired subscription state without waiting
// for server confirmation. It returns immediately after updating the local state.
// The client will attempt to subscribe asynchronously in the background.
//
// Note: Use GetSubscriptions() to verify the actual server-side subscription state.
//
// Parameters:
//   ctx - The context for the operation.
//   channels - A slice of channel names to subscribe to.
//
// Return value:
//   An error if the operation fails.
//
// Example:
//   err := client.Subscribe(ctx, []string{"channel1", "channel2"})
func (client *baseClient) Subscribe(ctx context.Context, channels []string) error {
	_, err := client.executeCommand(ctx, C.Subscribe, channels)
	return err
}

// SubscribeBlocking subscribes the client to the specified channels (blocking).
// This command updates the client's internal desired subscription state and waits
// for server confirmation.
//
// Parameters:
//   ctx - The context for the operation.
//   channels - A slice of channel names to subscribe to.
//   timeoutMs - Maximum time in milliseconds to wait for server confirmation.
//               A value of 0 blocks indefinitely until confirmation.
//
// Return value:
//   An error if the operation fails or times out.
//
// Example:
//   err := client.SubscribeBlocking(ctx, []string{"channel1"}, 5000)
func (client *baseClient) SubscribeBlocking(ctx context.Context, channels []string, timeoutMs int) error {
	args := append(channels, fmt.Sprintf("%d", timeoutMs))
	_, err := client.executeCommand(ctx, C.SubscribeBlocking, args)
	return err
}

// PSubscribe subscribes the client to the specified patterns (lazy, non-blocking).
// This command updates the client's internal desired subscription state without waiting
// for server confirmation. It returns immediately after updating the local state.
//
// Parameters:
//   ctx - The context for the operation.
//   patterns - A slice of patterns to subscribe to (e.g., []string{"news.*"}).
//
// Return value:
//   An error if the operation fails.
//
// Example:
//   err := client.PSubscribe(ctx, []string{"news.*", "updates.*"})
func (client *baseClient) PSubscribe(ctx context.Context, patterns []string) error {
	_, err := client.executeCommand(ctx, C.PSubscribe, patterns)
	return err
}

// PSubscribeBlocking subscribes the client to the specified patterns (blocking).
// This command updates the client's internal desired subscription state and waits
// for server confirmation.
//
// Parameters:
//   ctx - The context for the operation.
//   patterns - A slice of patterns to subscribe to.
//   timeoutMs - Maximum time in milliseconds to wait for server confirmation.
//
// Return value:
//   An error if the operation fails or times out.
//
// Example:
//   err := client.PSubscribeBlocking(ctx, []string{"news.*"}, 5000)
func (client *baseClient) PSubscribeBlocking(ctx context.Context, patterns []string, timeoutMs int) error {
	args := append(patterns, fmt.Sprintf("%d", timeoutMs))
	_, err := client.executeCommand(ctx, C.PSubscribeBlocking, args)
	return err
}

// Unsubscribe unsubscribes the client from the specified channels (lazy, non-blocking).
// If no channels are specified, unsubscribes from all exact channels.
//
// Parameters:
//   ctx - The context for the operation.
//   channels - A slice of channel names to unsubscribe from. Empty slice unsubscribes from all.
//
// Return value:
//   An error if the operation fails.
//
// Example:
//   err := client.Unsubscribe(ctx, []string{"channel1"})
//   err := client.Unsubscribe(ctx, []string{}) // Unsubscribe from all
func (client *baseClient) Unsubscribe(ctx context.Context, channels []string) error {
	_, err := client.executeCommand(ctx, C.Unsubscribe, channels)
	return err
}

// UnsubscribeBlocking unsubscribes the client from the specified channels (blocking).
// If no channels are specified, unsubscribes from all exact channels.
//
// Parameters:
//   ctx - The context for the operation.
//   channels - A slice of channel names to unsubscribe from.
//   timeoutMs - Maximum time in milliseconds to wait for server confirmation.
//
// Return value:
//   An error if the operation fails or times out.
//
// Example:
//   err := client.UnsubscribeBlocking(ctx, []string{"channel1"}, 5000)
func (client *baseClient) UnsubscribeBlocking(ctx context.Context, channels []string, timeoutMs int) error {
	args := append(channels, fmt.Sprintf("%d", timeoutMs))
	_, err := client.executeCommand(ctx, C.UnsubscribeBlocking, args)
	return err
}

// PUnsubscribe unsubscribes the client from the specified patterns (lazy, non-blocking).
// If no patterns are specified, unsubscribes from all patterns.
//
// Parameters:
//   ctx - The context for the operation.
//   patterns - A slice of patterns to unsubscribe from. Empty slice unsubscribes from all.
//
// Return value:
//   An error if the operation fails.
//
// Example:
//   err := client.PUnsubscribe(ctx, []string{"news.*"})
func (client *baseClient) PUnsubscribe(ctx context.Context, patterns []string) error {
	_, err := client.executeCommand(ctx, C.PUnsubscribe, patterns)
	return err
}

// PUnsubscribeBlocking unsubscribes the client from the specified patterns (blocking).
// If no patterns are specified, unsubscribes from all patterns.
//
// Parameters:
//   ctx - The context for the operation.
//   patterns - A slice of patterns to unsubscribe from.
//   timeoutMs - Maximum time in milliseconds to wait for server confirmation.
//
// Return value:
//   An error if the operation fails or times out.
//
// Example:
//   err := client.PUnsubscribeBlocking(ctx, []string{"news.*"}, 5000)
func (client *baseClient) PUnsubscribeBlocking(ctx context.Context, patterns []string, timeoutMs int) error {
	args := append(patterns, fmt.Sprintf("%d", timeoutMs))
	_, err := client.executeCommand(ctx, C.PUnsubscribeBlocking, args)
	return err
}
