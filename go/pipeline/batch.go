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

// TODO docs

// TODO - move to internals
type Cmd struct {
	RequestType C.RequestType
	Args        []string
}

// ====================

type BaseBatchOptions struct {
	Timeout *uint32
}

type StandaloneBatchOptions struct {
	BaseBatchOptions
}

type ClusterBatchOptions struct {
	BaseBatchOptions
	Route         *config.Route
	RetryStrategy *ClusterBatchRetryStrategy
}

type ClusterBatchRetryStrategy struct {
	RetryServerError, RetryConnectionError bool
}

func NewClusterBatchRetryStrategy() *ClusterBatchRetryStrategy {
	return &ClusterBatchRetryStrategy{false, false}
}

func (cbrs *ClusterBatchRetryStrategy) WithRetryServerError(retryServerError bool) *ClusterBatchRetryStrategy {
	cbrs.RetryServerError = retryServerError
	return cbrs
}

func (cbrs *ClusterBatchRetryStrategy) WithRetryConnectionError(retryConnectionError bool) *ClusterBatchRetryStrategy {
	cbrs.RetryConnectionError = retryConnectionError
	return cbrs
}

func NewStandaloneBatchOptions() *StandaloneBatchOptions {
	return &StandaloneBatchOptions{}
}

// TODO support duration
func (sbo *StandaloneBatchOptions) WithTimeout(timeout uint32) *StandaloneBatchOptions {
	sbo.Timeout = &timeout
	return sbo
}

func NewClusterBatchOptions() *ClusterBatchOptions {
	return &ClusterBatchOptions{}
}

func (cbo *ClusterBatchOptions) WithTimeout(timeout uint32) *ClusterBatchOptions {
	cbo.Timeout = &timeout
	return cbo
}

// ensure only single node route is allowed (use config.NotMultiNode?)
func (cbo *ClusterBatchOptions) WithRoute(route config.Route) *ClusterBatchOptions {
	cbo.Route = &route
	return cbo
}

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
	// TODO make private
	Converters []func(any) any
}

type BaseBatch[T StandaloneBatch | ClusterBatch] struct {
	Batch
	self *T
}

type StandaloneBatch struct {
	BaseBatch[StandaloneBatch]
}

type ClusterBatch struct {
	BaseBatch[ClusterBatch]
}

// ====================

func (b Batch) Convert(response []any) ([]any, error) {
	if len(response) != len(b.Converters) {
		return nil, &errors.RequestError{Msg: "Converters misaligned"}
	}
	for i, res := range response {
		response[i] = b.Converters[i](res)
	}
	return response, nil
}

// ====================

func NewStandaloneBatch(isAtomic bool) *StandaloneBatch {
	b := StandaloneBatch{BaseBatch: BaseBatch[StandaloneBatch]{Batch: Batch{IsAtomic: isAtomic}}}
	b.self = &b
	return &b
}

func NewClusterBatch(isAtomic bool) *ClusterBatch {
	b := ClusterBatch{BaseBatch: BaseBatch[ClusterBatch]{Batch: Batch{IsAtomic: isAtomic}}}
	b.self = &b
	return &b
}

// Add a cmd to batch without response type checking nor conversion
func (b *BaseBatch[T]) addCmd(request C.RequestType, args []string) *T {
	b.Commands = append(b.Commands, Cmd{RequestType: request, Args: args})
	b.Converters = append(b.Converters, func(res any) any { return res })
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
	b.Commands = append(b.Commands, Cmd{RequestType: request, Args: args})
	b.Converters = append(b.Converters, converterAndTypeChecker)
	return b.self
}

func (b *BaseBatch[T]) CustomCommand(args []string) *T {
	return b.addCmd(C.CustomCommand, args)
}

func (b *BaseBatch[T]) Get(key string) *T {
	return b.addCmdAndTypeChecker(C.Get, []string{key}, reflect.String, true)
}

func (b *BaseBatch[T]) Set(key string, value string) *T {
	return b.addCmdAndTypeChecker(C.Set, []string{key, value}, reflect.String, false)
}

func (b *StandaloneBatch) Select(db int) *StandaloneBatch {
	return b.addCmdAndTypeChecker(C.Select, []string{utils.IntToString(int64(db))}, reflect.String, false)
}
