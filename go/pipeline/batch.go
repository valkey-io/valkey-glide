// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package pipeline

// #include "../lib.h"
import "C"

import (
	"fmt"
	"reflect"

	"github.com/valkey-io/valkey-glide/go/v2/internal"
	"github.com/valkey-io/valkey-glide/go/v2/internal/utils"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// BaseBatch is the base structure for both standalone and cluster batch implementations.
type BaseBatch[T StandaloneBatch | ClusterBatch] struct {
	internal.Batch
	self *T
}

// StandaloneBatch is the batch implementation for standalone Valkey servers.
// Batches allow the execution of a group of commands in a single step.
//
// Batch Response:
//
//	An array of command responses is returned by the client exec command, in the order they were given.
//	Each element in the array represents a command given to the batch.
//	The response for each command depends on the executed Valkey command.
//	Specific response types are documented alongside each method.
type StandaloneBatch struct {
	BaseBatch[StandaloneBatch]
}

// ClusterBatch is the batch implementation for clustered Valkey servers.
// Batches allow the execution of a group of commands in a single step.
//
// Batch Response:
//
//	An array of command responses is returned by the client exec command, in the order they were given.
//	Each element in the array represents a command given to the batch.
//	The response for each command depends on the executed Valkey command.
//	Specific response types are documented alongside each method.
type ClusterBatch struct {
	BaseBatch[ClusterBatch]
}

// ====================

// Create a new batch for standalone Valkey servers.
//
// Parameters:
//
//	isAtomic - Indicates whether the batch is atomic or non-atomic.
//	  If `true`, the batch will be executed as an atomic transaction.
//	  If `false`, the batch will be executed as a non-atomic pipeline.
//
// Returns:
//
//	A new StandaloneBatch instance.
func NewStandaloneBatch(isAtomic bool) *StandaloneBatch {
	b := StandaloneBatch{BaseBatch: BaseBatch[StandaloneBatch]{Batch: internal.Batch{IsAtomic: isAtomic}}}
	b.self = &b
	return &b
}

// Create a new batch for clustered Valkey servers.
//
// Parameters:
//
//	isAtomic - Indicates whether the batch is atomic or non-atomic.
//	  If `true`, the batch will be executed as an atomic transaction.
//	  If `false`, the batch will be executed as a non-atomic pipeline.
//
// Returns:
//
//	A new ClusterBatch instance.
func NewClusterBatch(isAtomic bool) *ClusterBatch {
	b := ClusterBatch{BaseBatch: BaseBatch[ClusterBatch]{Batch: internal.Batch{IsAtomic: isAtomic}}}
	b.self = &b
	return &b
}

// Add a cmd to batch without response type checking nor conversion
func (b *BaseBatch[T]) addCmd(request C.RequestType, args []string) *T {
	b.Batch.Commands = append(
		b.Batch.Commands,
		internal.MakeCmd(uint32(request), args, func(res any) (any, error) { return res, nil }),
	)
	return b.self
}

func (b *BaseBatch[T]) addError(command string, err error) *T {
	b.Batch.Errors = append(b.Batch.Errors, fmt.Errorf("error processing arguments for %d'th command ('%s'): %w",
		len(b.Batch.Commands)+len(b.Batch.Errors)+1, command, err))
	return b.self
}

// Add a cmd to batch with type checker but without response type conversion
func (b *BaseBatch[T]) addCmdAndTypeChecker(
	request C.RequestType,
	args []string,
	expectedType reflect.Kind,
	isNilable bool,
) *T {
	return b.addCmdAndConverter(request, args, expectedType, isNilable, func(res any) (any, error) { return res, nil })
}

// Add a cmd to batch with type checker and with response type conversion
func (b *BaseBatch[T]) addCmdAndConverter(
	request C.RequestType,
	args []string,
	expectedType reflect.Kind,
	isNilable bool,
	converter func(res any) (any, error),
) *T {
	converterAndTypeChecker := func(res any) (any, error) {
		return internal.ConverterAndTypeChecker(res, expectedType, isNilable, converter)
	}
	b.Batch.Commands = append(b.Batch.Commands, internal.MakeCmd(uint32(request), args, converterAndTypeChecker))
	return b.self
}

// Changes the currently selected database.
//
// WARNING: This command is NOT RECOMMENDED for production use.
// Upon reconnection, the client will revert to the database_id specified
// in the client configuration (default: 0), NOT the database selected
// via this command.
//
// RECOMMENDED APPROACH: Use the database_id parameter in client
// configuration instead.
//
// For details see [valkey.io].
//
// Parameters:
//
//	index - The index of the database to select.
//
// Command Response:
//
//	A simple "OK" response.
//
// [valkey.io]: https://valkey.io/commands/select/
func (b *StandaloneBatch) Select(index int64) *StandaloneBatch {
	return b.addCmdAndTypeChecker(C.Select, []string{utils.IntToString(index)}, reflect.String, false)
}

// Moves key from the currently selected database to the database specified by `dbIndex`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key to move.
//	dbIndex - The index of the database to move key to.
//
// Command Response:
//
//	`true` if `key` was moved, or `false` if the `key` already exists in the destination
//	database or does not exist in the source database.
//
// [valkey.io]: https://valkey.io/commands/move/
func (b *StandaloneBatch) Move(key string, dbIndex int64) *StandaloneBatch {
	return b.addCmdAndTypeChecker(C.Move, []string{key, utils.IntToString(dbIndex)}, reflect.Bool, false)
}

// Iterates incrementally over a database for matching keys.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	cursor - The cursor that points to the next iteration of results. A value of 0
//			 indicates the start of the search.
//
// Command Response:
//
//	An object which holds the next cursor and the subset of the hash held by `key`.
//	The cursor will return `false` from `IsFinished()` method on the last iteration of the subset.
//	The data array in the result is always an array of matched keys from the database.
//
// [valkey.io]: https://valkey.io/commands/scan/
func (b *StandaloneBatch) Scan(cursor int64) *StandaloneBatch {
	return b.addCmdAndConverter(C.Scan, []string{utils.IntToString(cursor)}, reflect.Slice, false, internal.ConvertScanResult)
}

// Iterates incrementally over a database for matching keys.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	cursor - The cursor that points to the next iteration of results. A value of 0
//			 indicates the start of the search.
//	scanOptions - Additional command parameters, see [ScanOptions] for more details.
//
// Command Response:
//
//	An object which holds the next cursor and the subset of the hash held by `key`.
//	The cursor will return `false` from `IsFinished()` method on the last iteration of the subset.
//	The data array in the result is always an array of matched keys from the database.
//
// [valkey.io]: https://valkey.io/commands/scan/
func (b *StandaloneBatch) ScanWithOptions(cursor int64, scanOptions options.ScanOptions) *StandaloneBatch {
	optionArgs, err := scanOptions.ToArgs()
	if err != nil {
		return b.addError("ScanWithOptions", err)
	}
	return b.addCmdAndConverter(
		C.Scan,
		append([]string{utils.IntToString(cursor)}, optionArgs...),
		reflect.Slice,
		false,
		internal.ConvertScanResult,
	)
}

// Select changes the currently selected database on cluster nodes.
//
// WARNING: This command is NOT RECOMMENDED for production use.
// Upon reconnection, nodes will revert to the database_id specified
// in the client configuration (default: 0), NOT the database selected
// via this command.
//
// RECOMMENDED APPROACH: Use the database_id parameter in client
// configuration instead.
//
// CLUSTER BEHAVIOR: This command routes to all nodes by default
// to maintain consistency across the cluster.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	index - The index of the database to select.
//
// Command Response:
//
//	A simple "OK" response.
//
// [valkey.io]: https://valkey.io/commands/select/
func (b *ClusterBatch) Select(index int64) *ClusterBatch {
	return b.addCmdAndTypeChecker(C.Select, []string{utils.IntToString(index)}, reflect.String, false)
}

// Posts a message to the specified sharded channel. Returns the number of clients that received the message.
//
// Channel can be any string, but common patterns include using "." to create namespaces like
// "news.sports" or "news.weather".
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	channel - The channel to publish the message to.
//	message - The message to publish.
//
// Command Response:
//
//	The number of clients that received the message.
//
// [valkey.io]: https://valkey.io/commands/publish/
func (b *ClusterBatch) SPublish(channel string, message string) *ClusterBatch {
	return b.addCmdAndTypeChecker(C.SPublish, []string{channel, message}, reflect.Int64, false)
}

// Returns a list of all sharded channels.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Command Response:
//
//	A list of shard channels.
//
// [valkey.io]: https://valkey.io/commands/pubsub-shard-channels
func (b *ClusterBatch) PubSubShardChannels() *ClusterBatch {
	return b.addCmdAndConverter(C.PubSubShardChannels, []string{}, reflect.Slice, false, internal.ConvertArrayOf[string])
}

// Returns a list of all sharded channels that match the given pattern.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	pattern - A glob-style pattern to match active shard channels.
//
// Command Response:
//
//	A list of shard channels that match the given pattern.
//
// [valkey.io]: https://valkey.io/commands/pubsub-shard-channels-with-pattern
func (b *ClusterBatch) PubSubShardChannelsWithPattern(pattern string) *ClusterBatch {
	return b.addCmdAndConverter(
		C.PubSubShardChannels,
		[]string{pattern},
		reflect.Slice,
		false,
		internal.ConvertArrayOf[string],
	)
}

// Returns the number of subscribers for a sharded channel.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	channels - The channel to get the number of subscribers for.
//
// Command Response:
//
//	The number of subscribers for the sharded channel.
//
// [valkey.io]: https://valkey.io/commands/pubsub-shard-numsub
func (b *ClusterBatch) PubSubShardNumSub(channels ...string) *ClusterBatch {
	return b.addCmdAndConverter(C.PubSubShardNumSub, channels, reflect.Map, false, internal.ConvertMapOf[int64])
}
