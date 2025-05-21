// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// #include "../lib.h"
import "C"

import (
	"github.com/valkey-io/valkey-glide/go/api/config"
	"github.com/valkey-io/valkey-glide/go/utils"
)

// TODO docs for the god of docs

// TODO - put in another file/package
type Cmd struct {
	requestType C.RequestType
	args        []string
}

// ====================

type BaseBatchOptions struct {
	timeout *uint32
}

type StandaloneBatchOptions struct {
	BaseBatchOptions
}

type ClusterBatchOptions struct {
	BaseBatchOptions
	route         *config.NotMultiNode
	retryStrategy ClusterBatchRetryStrategy
}

type ClusterBatchRetryStrategy struct {
	retryServerError, retryConnectionError bool
}

func NewClusterBatchRetryStrategy() *ClusterBatchRetryStrategy {
	return &ClusterBatchRetryStrategy{false, false}
}

func (cbrs *ClusterBatchRetryStrategy) WithRetryServerError(retryServerError bool) *ClusterBatchRetryStrategy {
	cbrs.retryServerError = retryServerError
	return cbrs
}

func (cbrs *ClusterBatchRetryStrategy) WithRetryConnectionError(retryConnectionError bool) *ClusterBatchRetryStrategy {
	cbrs.retryConnectionError = retryConnectionError
	return cbrs
}

func NewStandaloneBatchOptions() *StandaloneBatchOptions {
	return &StandaloneBatchOptions{}
}

// TODO support duration
func (sbo *StandaloneBatchOptions) WithTimeout(timeout uint32) *StandaloneBatchOptions {
	sbo.timeout = &timeout
	return sbo
}

func NewClusterBatchOptions() *ClusterBatchOptions {
	return &ClusterBatchOptions{}
}

func (cbo *ClusterBatchOptions) WithTimeout(timeout uint32) *ClusterBatchOptions {
	cbo.timeout = &timeout
	return cbo
}

func (cbo *ClusterBatchOptions) WithRoute(route config.NotMultiNode) *ClusterBatchOptions {
	cbo.route = &route
	return cbo
}

func (cbo *ClusterBatchOptions) WithRetryStrategy(retryStrategy ClusterBatchRetryStrategy) *ClusterBatchOptions {
	cbo.retryStrategy = retryStrategy
	return cbo
}

// ====================

type batchOptions struct {
	timeout       *uint32
	route         *config.NotMultiNode
	retryStrategy ClusterBatchRetryStrategy
}

func (sbo StandaloneBatchOptions) convert() batchOptions {
	return batchOptions{timeout: sbo.timeout}
}

func (cbo ClusterBatchOptions) convert() batchOptions {
	return batchOptions{timeout: cbo.timeout, route: cbo.route, retryStrategy: cbo.retryStrategy}
}

// ====================

type batch struct {
	commands []Cmd
	isAtomic bool
}

type BaseBatch[T StandaloneBatch | ClusterBatch] struct {
	batch
	self *T
	// TODO converters
}

type StandaloneBatch struct {
	BaseBatch[StandaloneBatch]
}

type ClusterBatch struct {
	BaseBatch[ClusterBatch]
}

func NewStandaloneBatch(isAtomic bool) *StandaloneBatch {
	b := StandaloneBatch{BaseBatch: BaseBatch[StandaloneBatch]{batch: batch{isAtomic: isAtomic}}}
	b.self = &b
	return &b
}

func NewClusterBatch(isAtomic bool) *ClusterBatch {
	b := ClusterBatch{BaseBatch: BaseBatch[ClusterBatch]{batch: batch{isAtomic: isAtomic}}}
	b.self = &b
	return &b
}

func (b *BaseBatch[T]) CustomCommand(args []string) *T {
	b.commands = append(b.commands, Cmd{requestType: C.CustomCommand, args: args})
	return b.self
}

func (b *BaseBatch[T]) Get(key string) *T {
	b.commands = append(b.commands, Cmd{requestType: C.Get, args: []string{key}})
	return b.self
}

func (b *BaseBatch[T]) Set(key string, value string) *T {
	b.commands = append(b.commands, Cmd{requestType: C.Set, args: []string{key, value}})
	return b.self
}

func (b *StandaloneBatch) Select(db int) *StandaloneBatch {
	b.commands = append(b.commands, Cmd{requestType: C.Select, args: []string{utils.IntToString(int64(db))}})
	return b
}

func (b *ClusterBatch) Ping(msg string) *ClusterBatch {
	b.commands = append(b.commands, Cmd{requestType: C.Ping, args: []string{msg}})
	return b
}
