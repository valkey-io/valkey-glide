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
		return &errors.RequestError{
			Msg: fmt.Sprintf("Unexpected return type from Glide: got %v, expected %v", reflect.TypeOf(res), expectedType),
		}
	}
	b.Commands = append(b.Commands, Cmd{RequestType: request, Args: args, Converter: converterAndTypeChecker})
	return b.self
}

// CustomCommand executes a single command, specified by args, without checking inputs. Every part of the command,
// including the command name and subcommands, should be added as a separate value in args. The returning value depends on
// the executed command.
//
// See [Valkey GLIDE Wiki] for details on the restrictions and limitations of the custom command API.
//
// This function should only be used for single-response commands. Commands that don't return complete response and awaits
// (such as SUBSCRIBE), or that return potentially more than a single response (such as XREAD), or that change the client's
// behavior (such as entering pub/sub mode on RESP2 connections) shouldn't be called using this function.
//
// Parameters:
//
//	args - Arguments for the custom command.
//
// Command Response:
//
//	The returned value for the custom command.
//
// [Valkey GLIDE Wiki]: https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command
func (b *BaseBatch[T]) CustomCommand(args []string) *T {
	return b.addCmd(C.CustomCommand, args)
}

// Get retrieves the value associated with the given key, or `nil` if no such key exists.
//
// For details see [valkey.io].
//
// Parameters:
//
//	key - The key to retrieve from the database.
//
// Command Response:
//
//	If key exists, returns the value of key.
//	Otherwise, returns `nil`.
//
// [valkey.io]: https://valkey.io/commands/get/
func (b *BaseBatch[T]) Get(key string) *T {
	return b.addCmdAndTypeChecker(C.Get, []string{key}, reflect.String, true)
}

// Set sets the given key with the given value.
//
// For details see [valkey.io].
//
// Parameters:
//
//	key - The key to store.
//	value - The value to store with the given key.
//
// Command Response:
//
//	If the value is successfully set, returns OK.
//
// [valkey.io]: https://valkey.io/commands/set/
func (b *BaseBatch[T]) Set(key string, value string) *T {
	return b.addCmdAndTypeChecker(C.Set, []string{key, value}, reflect.String, false)
}

// Select changes the currently selected database.
// This method is only available for StandaloneBatch.
//
// For details see [valkey.io].
//
// Parameters:
//
//	db - The index of the database to select.
//
// Command Response:
//
//	A simple "OK" response.
//
// [valkey.io]: https://valkey.io/commands/select/
func (b *StandaloneBatch) Select(db int) *StandaloneBatch {
	return b.addCmdAndTypeChecker(C.Select, []string{utils.IntToString(int64(db))}, reflect.String, false)
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
