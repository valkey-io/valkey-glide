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
		return &errors.RequestError{
			Msg: fmt.Sprintf("Unexpected return type from Glide: got %v, expected %v", reflect.TypeOf(res), expectedType),
		}
	}
	b.Commands = append(b.Commands, Cmd{RequestType: request, Args: args, Converter: converterAndTypeChecker})
	return b.self
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
