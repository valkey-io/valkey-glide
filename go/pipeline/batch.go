// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package pipeline

// #include "../lib.h"
import "C"

import (
	"fmt"
	"reflect"

	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/internal/errors"
	"github.com/valkey-io/valkey-glide/go/v2/internal/utils"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// TODO - move to internals
type Cmd struct {
	RequestType C.RequestType
	Args        []string
	// Response converter
	Converter func(any) any
}

// ====================

// BaseBatchOptions contains common options for both standalone and cluster batches.
type BaseBatchOptions struct {
	// Timeout for the batch execution in milliseconds.
	Timeout *uint32
}

// StandaloneBatchOptions contains options specific to standalone batches.
type StandaloneBatchOptions struct {
	BaseBatchOptions
}

// ClusterBatchOptions contains options specific to cluster batches.
type ClusterBatchOptions struct {
	BaseBatchOptions
	// Route defines the routing strategy for the batch.
	Route *config.Route
	// RetryStrategy defines the retry behavior for cluster batches.
	RetryStrategy *ClusterBatchRetryStrategy
}

// ClusterBatchRetryStrategy defines the retry behavior for cluster batches.
type ClusterBatchRetryStrategy struct {
	// RetryServerError indicates whether to retry on server errors.
	RetryServerError bool
	// RetryConnectionError indicates whether to retry on connection errors.
	RetryConnectionError bool
}

// NewClusterBatchRetryStrategy creates a new retry strategy for cluster batches.
//
// Returns:
//
//	A new ClusterBatchRetryStrategy instance.
func NewClusterBatchRetryStrategy() *ClusterBatchRetryStrategy {
	return &ClusterBatchRetryStrategy{false, false}
}

// WithRetryServerError configures whether to retry on server errors.
//
// Parameters:
//
//	retryServerError - If true, retry on server errors.
//
// Returns:
//
//	The updated ClusterBatchRetryStrategy instance.
func (cbrs *ClusterBatchRetryStrategy) WithRetryServerError(retryServerError bool) *ClusterBatchRetryStrategy {
	cbrs.RetryServerError = retryServerError
	return cbrs
}

// WithRetryConnectionError configures whether to retry on connection errors.
//
// Parameters:
//
//	retryConnectionError - If true, retry on connection errors.
//
// Returns:
//
//	The updated ClusterBatchRetryStrategy instance.
func (cbrs *ClusterBatchRetryStrategy) WithRetryConnectionError(retryConnectionError bool) *ClusterBatchRetryStrategy {
	cbrs.RetryConnectionError = retryConnectionError
	return cbrs
}

// NewStandaloneBatchOptions creates a new options instance for standalone batches.
//
// Returns:
//
//	A new StandaloneBatchOptions instance.
func NewStandaloneBatchOptions() *StandaloneBatchOptions {
	return &StandaloneBatchOptions{}
}

// TODO support duration

// WithTimeout sets the timeout for the batch execution.
//
// Parameters:
//
//	timeout - The timeout in milliseconds.
//
// Returns:
//
//	The updated StandaloneBatchOptions instance.
func (sbo *StandaloneBatchOptions) WithTimeout(timeout uint32) *StandaloneBatchOptions {
	sbo.Timeout = &timeout
	return sbo
}

// NewClusterBatchOptions creates a new options instance for cluster batches.
//
// Returns:
//
//	A new ClusterBatchOptions instance.
func NewClusterBatchOptions() *ClusterBatchOptions {
	return &ClusterBatchOptions{}
}

// WithTimeout sets the timeout for the batch execution.
//
// Parameters:
//
//	timeout - The timeout in milliseconds.
//
// Returns:
//
//	The updated ClusterBatchOptions instance.
func (cbo *ClusterBatchOptions) WithTimeout(timeout uint32) *ClusterBatchOptions {
	cbo.Timeout = &timeout
	return cbo
}

// TODO ensure only single node route is allowed (use config.NotMultiNode?)

// WithRoute sets the routing strategy for the batch.
//
// Parameters:
//
//	route - The routing strategy to use.
//
// Returns:
//
//	The updated ClusterBatchOptions instance.
func (cbo *ClusterBatchOptions) WithRoute(route config.Route) *ClusterBatchOptions {
	cbo.Route = &route
	return cbo
}

// WithRetryStrategy sets the retry strategy for the batch.
//
// Parameters:
//
//	retryStrategy - The retry strategy to use.
//
// Returns:
//
//	The updated ClusterBatchOptions instance.
func (cbo *ClusterBatchOptions) WithRetryStrategy(retryStrategy ClusterBatchRetryStrategy) *ClusterBatchOptions {
	cbo.RetryStrategy = &retryStrategy
	return cbo
}

// ====================

// TODO - move this struct and convert methods to internals
type BatchOptions struct {
	Timeout       *uint32
	Route         *config.Route
	RetryStrategy *ClusterBatchRetryStrategy
}

func (sbo StandaloneBatchOptions) Convert() BatchOptions {
	return BatchOptions{Timeout: sbo.Timeout}
}

func (cbo ClusterBatchOptions) Convert() BatchOptions {
	return BatchOptions{Timeout: cbo.Timeout, Route: cbo.Route, RetryStrategy: cbo.RetryStrategy}
}

// ====================

// TODO make private if possible
type Batch struct {
	Commands []Cmd
	IsAtomic bool
	Errors   []string // errors processing command args, spotted while batch is filled
}

// BaseBatch is the base structure for both standalone and cluster batch implementations.
type BaseBatch[T StandaloneBatch | ClusterBatch] struct {
	Batch
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

func (b Batch) Convert(response []any) ([]any, error) {
	if len(response) != len(b.Commands) {
		return nil, &errors.RequestError{
			Msg: fmt.Sprintf("Response misaligned: received %d responses for %d commands", len(response), len(b.Commands)),
		}
	}
	for i, res := range response {
		response[i] = b.Commands[i].Converter(res)
	}
	return response, nil
}

// ====================

// NewStandaloneBatch creates a new batch for standalone Valkey servers.
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
	b := StandaloneBatch{BaseBatch: BaseBatch[StandaloneBatch]{Batch: Batch{IsAtomic: isAtomic}}}
	b.self = &b
	return &b
}

// NewClusterBatch creates a new batch for clustered Valkey servers.
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
	b := ClusterBatch{BaseBatch: BaseBatch[ClusterBatch]{Batch: Batch{IsAtomic: isAtomic}}}
	b.self = &b
	return &b
}

// Add a cmd to batch without response type checking nor conversion
func (b *BaseBatch[T]) addCmd(request C.RequestType, args []string) *T {
	b.Commands = append(b.Commands, Cmd{RequestType: request, Args: args, Converter: func(res any) any { return res }})
	return b.self
}

func (b *BaseBatch[T]) addError(command string, err error) *T {
	b.Errors = append(b.Errors, fmt.Sprintf("Error processing arguments for %d's command ('%s'): %s",
		len(b.Commands)+len(b.Errors)+1, command, err))
	return b.self
}

// Add a cmd to batch with type checker but without response type conversion
func (b *BaseBatch[T]) addCmdAndTypeChecker(
	request C.RequestType,
	args []string,
	expectedType reflect.Kind,
	isNilable bool,
) *T {
	return b.addCmdAndConverter(request, args, expectedType, isNilable, func(res any) any { return res })
}

// Add a cmd to batch with type checker and with response type conversion
func (b *BaseBatch[T]) addCmdAndConverter(
	request C.RequestType,
	args []string,
	expectedType reflect.Kind,
	isNilable bool,
	converter func(res any) any,
) *T {
	converterAndTypeChecker := func(res any) any {
		if res == nil {
			if isNilable {
				return nil
			}
			return &errors.RequestError{
				Msg: fmt.Sprintf("Unexpected return type from Glide: got nil, expected %v", expectedType),
			}
		}
		if reflect.TypeOf(res).Kind() == expectedType {
			return converter(res)
		}
		// data lost even though it was incorrect
		// TODO maybe still return the data?
		return &errors.RequestError{
			Msg: fmt.Sprintf("Unexpected return type from Glide: got %v, expected %v", reflect.TypeOf(res), expectedType),
		}
	}
	b.Commands = append(b.Commands, Cmd{RequestType: request, Args: args, Converter: converterAndTypeChecker})
	return b.self
}

// Changes the currently selected database.
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
//	An Array of Objects. The first element is always the cursor for the next
//	iteration of results. "0" will be the cursor returned on the last iteration
//	of the scan. The second element is always an Array of matched keys from the database.
//
// [valkey.io]: https://valkey.io/commands/scan/
func (b *StandaloneBatch) Scan(cursor int64) *StandaloneBatch {
	return b.addCmdAndTypeChecker(C.Scan, []string{utils.IntToString(cursor)}, reflect.Slice, false)
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
//	An Array of Objects. The first element is always the cursor for the next
//	iteration of results. "0" will be the cursor returned on the last iteration
//	of the scan. The second element is always an Array of matched keys from the database.
//
// [valkey.io]: https://valkey.io/commands/scan/
func (b *StandaloneBatch) ScanWithOptions(cursor int64, scanOptions options.ScanOptions) *StandaloneBatch {
	optionArgs, err := scanOptions.ToArgs()
	if err != nil {
		return b.addError("ScanWithOptions", err)
	}
	return b.addCmdAndTypeChecker(C.Scan, append([]string{utils.IntToString(cursor)}, optionArgs...), reflect.Slice, false)
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
	return b.addCmdAndTypeChecker(C.PubSubShardChannels, []string{}, reflect.Slice, false)
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
	return b.addCmdAndTypeChecker(C.PubSubShardChannels, []string{pattern}, reflect.Slice, false)
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
	return b.addCmdAndTypeChecker(C.PubSubShardNumSub, channels, reflect.Map, false)
}

// ==========================
// Bitmap Commands
// ==========================

// Sets or clears the bit at offset in the string value stored at key.
// The offset is a zero-based index, with `0` being the first element of
// the list, `1` being the next element, and so on. The offset must be
// less than `2^32` and greater than or equal to `0` If a key is
// non-existent then the bit at offset is set to value and the preceding
// bits are set to `0`.
//
// Parameters:
//
//	key - The key of the string.
//	offset - The index of the bit to be set.
//	value - The bit value to set at offset The value must be `0` or `1`.
//
// Command Response:
//
//	The bit value that was previously stored at offset.
//
// [valkey.io]: https://valkey.io/commands/setbit/
func (b *BaseBatch[T]) SetBit(key string, offset int64, value int64) *T {
	return b.addCmdAndTypeChecker(
		C.SetBit,
		[]string{key, utils.IntToString(offset), utils.IntToString(value)},
		reflect.Int64,
		false,
	)
}

// Returns the bit value at offset in the string value stored at key.
//
//	offset should be greater than or equal to zero.
//
// Parameters:
//
//	key - The key of the string.
//	offset - The index of the bit to return.
//
// Command Response:
//
//	The bit at offset of the string. Returns zero if the key is empty or if the positive
//	offset exceeds the length of the string.
//
// [valkey.io]: https://valkey.io/commands/getbit/
func (b *BaseBatch[T]) GetBit(key string, offset int64) *T {
	return b.addCmdAndTypeChecker(C.GetBit, []string{key, utils.IntToString(offset)}, reflect.Int64, false)
}

// Counts the number of set bits (population counting) in a string stored at key.
//
// Parameters:
//
//	key - The key for the string to count the set bits of.
//
// Command Response:
//
//	The number of set bits in the string. Returns zero if the key is missing as it is
//	treated as an empty string.
//
// [valkey.io]: https://valkey.io/commands/bitcount/
func (b *BaseBatch[T]) BitCount(key string) *T {
	return b.addCmdAndTypeChecker(C.BitCount, []string{key}, reflect.Int64, false)
}

// Counts the number of set bits (population counting) in a string stored at key. The
// offsets start and end are zero-based indexes, with `0` being the first element of the
// list, `1` being the next element and so on. These offsets can also be negative numbers
// indicating offsets starting at the end of the list, with `-1` being the last element
// of the list, `-2` being the penultimate, and so on.
//
// Parameters:
//
//	key - The key for the string to count the set bits of.
//	options - The offset options - see [options.BitOffsetOptions].
//
// Command Response:
//
//	The number of set bits in the string interval specified by start, end, and options.
//	Returns zero if the key is missing as it is treated as an empty string.
//
// [valkey.io]: https://valkey.io/commands/bitcount/
func (b *BaseBatch[T]) BitCountWithOptions(key string, options options.BitCountOptions) *T {
	optArgs, _ := options.ToArgs()
	return b.addCmdAndTypeChecker(C.BitCount, append([]string{key}, optArgs...), reflect.Int64, false)
}

// Returns the position of the first bit matching the given bit value.
//
// Parameters:
//
//	key - The key of the string.
//	bit - The bit value to match. The value must be 0 or 1.
//
// Command Response:
//
//	The position of the first occurrence matching bit in the binary value of
//	the string held at key. If bit is not found, a -1 is returned.
//
// [valkey.io]: https://valkey.io/commands/bitpos/
func (b *BaseBatch[T]) BitPos(key string, bit int64) *T {
	return b.addCmdAndTypeChecker(C.BitPos, []string{key, utils.IntToString(bit)}, reflect.Int64, false)
}

// Returns the position of the first bit matching the given bit value.
//
// Parameters:
//
//	key - The key of the string.
//	bit - The bit value to match. The value must be 0 or 1.
//	bitposOptions  - The [BitPosOptions] type.
//
// Command Response:
//
//	The position of the first occurrence matching bit in the binary value of
//	the string held at key. If bit is not found, a -1 is returned.
//
// [valkey.io]: https://valkey.io/commands/bitpos/
func (b *BaseBatch[T]) BitPosWithOptions(key string, bit int64, options options.BitPosOptions) *T {
	optArgs, _ := options.ToArgs()
	return b.addCmdAndTypeChecker(C.BitPos, append([]string{key, utils.IntToString(bit)}, optArgs...), reflect.Int64, false)
}

// Reads or modifies the array of bits representing the string that is held at key
// based on the specified sub commands.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key          -  The key of the string.
//	subCommands  -  The subCommands to be performed on the binary value of the string at
//	                key, which could be any of the following:
//	                  - [BitFieldGet].
//	                  - [BitFieldSet].
//	                  - [BitFieldIncrby].
//	                  - [BitFieldOverflow].
//		            Use `options.NewBitFieldGet()` to specify a  BitField GET command.
//		            Use `options.NewBitFieldSet()` to specify a BitField SET command.
//		            Use `options.NewBitFieldIncrby()` to specify a BitField INCRYBY command.
//		            Use `options.BitFieldOverflow()` to specify a BitField OVERFLOW command.
//
// Command Response:
//
//	Result from the executed subcommands.
//	  - BitFieldGet returns the value in the binary representation of the string.
//	  - BitFieldSet returns the previous value before setting the new value in the binary representation.
//	  - BitFieldIncrBy returns the updated value after increasing or decreasing the bits.
//	  - BitFieldOverflow controls the behavior of subsequent operations and returns
//	    a result based on the specified overflow type (WRAP, SAT, FAIL).
//
// [valkey.io]: https://valkey.io/commands/bitfield/
func (b *BaseBatch[T]) BitField(key string, subCommands []options.BitFieldSubCommands) *T {
	args := make([]string, 0, 10)
	args = append(args, key)

	for _, cmd := range subCommands {
		cmdArgs, _ := cmd.ToArgs()
		args = append(args, cmdArgs...)
	}
	// TODO: fix to use an expected type?
	return b.addCmd(C.BitField, args)
}

// Reads the array of bits representing the string that is held at key
// based on the specified  sub commands.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key          -  The key of the string.
//	subCommands  -  The read-only subCommands to be performed on the binary value
//	                of the string at key, which could be:
//	                  - [BitFieldGet].
//		            Use `options.NewBitFieldGet()` to specify a BitField GET command.
//
// Command Response:
//
//	Result from the executed GET subcommands.
//	  - BitFieldGet returns the value in the binary representation of the string.
//
// [valkey.io]: https://valkey.io/commands/bitfield_ro/
func (b *BaseBatch[T]) BitFieldRO(key string, commands []options.BitFieldROCommands) *T {
	args := make([]string, 0, 10)
	args = append(args, key)

	for _, cmd := range commands {
		cmdArgs, _ := cmd.ToArgs()
		args = append(args, cmdArgs...)
	}

	// TODO: fix to use an expected type?
	return b.addCmd(C.BitFieldReadOnly, args)
}

// Perform a bitwise operation between multiple keys (containing string values) and store the result in the destination.
//
// Note:
//
// When in cluster mode, `destination` and all `keys` must map to the same hash slot.
//
// Parameters:
//
//	bitwiseOperation - The bitwise operation to perform.
//	destination      - The key that will store the resulting string.
//	keys             - The list of keys to perform the bitwise operation on.
//
// Command Response:
//
//	The size of the string stored in destination.
//
// [valkey.io]: https://valkey.io/commands/bitop/
func (b *BaseBatch[T]) BitOp(bitwiseOperation options.BitOpType, destination string, keys []string) *T {
	bitOp, _ := options.NewBitOp(bitwiseOperation, destination, keys)
	args, _ := bitOp.ToArgs()
	return b.addCmdAndTypeChecker(C.BitOp, args, reflect.Int64, false)
}
