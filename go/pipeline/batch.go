// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package pipeline

// #include "../lib.h"
import "C"

import (
	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/internal/utils"
)

// TODO docs for the god of docs

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
	Route         *config.NotMultiNode
	RetryStrategy ClusterBatchRetryStrategy
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

func (cbo *ClusterBatchOptions) WithRoute(route config.NotMultiNode) *ClusterBatchOptions {
	cbo.Route = &route
	return cbo
}

func (cbo *ClusterBatchOptions) WithRetryStrategy(retryStrategy ClusterBatchRetryStrategy) *ClusterBatchOptions {
	cbo.RetryStrategy = retryStrategy
	return cbo
}

// ====================

// TODO - move this struct and convert methods to internals
type BatchOptions struct {
	Timeout       *uint32
	Route         *config.NotMultiNode
	RetryStrategy ClusterBatchRetryStrategy
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

type BaseBatch[T StandaloneBatch | ClusterBatch] struct {
	Batch
	self *T
	// TODO converters
}

type StandaloneBatch struct {
	BaseBatch[StandaloneBatch]
}

type ClusterBatch struct {
	BaseBatch[ClusterBatch]
}

// ====================

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

func (b *BaseBatch[T]) CustomCommand(args []string) *T {
	b.Commands = append(b.Commands, Cmd{RequestType: C.CustomCommand, Args: args})
	return b.self
}

func (b *BaseBatch[T]) Get(key string) *T {
	b.Commands = append(b.Commands, Cmd{RequestType: C.Get, Args: []string{key}})
	return b.self
}

func (b *BaseBatch[T]) Set(key string, value string) *T {
	b.Commands = append(b.Commands, Cmd{RequestType: C.Set, Args: []string{key, value}})
	return b.self
}

func (b *StandaloneBatch) Select(db int) *StandaloneBatch {
	b.Commands = append(b.Commands, Cmd{RequestType: C.Select, Args: []string{utils.IntToString(int64(db))}})
	return b
}

func (b *ClusterBatch) Ping(msg string) *ClusterBatch {
	b.Commands = append(b.Commands, Cmd{RequestType: C.Ping, Args: []string{msg}})
	return b
}
