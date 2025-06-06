// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package pipeline

import (
	"time"

	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/internal"
)

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

// Create a new retry strategy for cluster batches.
//
// Returns:
//
//	A new ClusterBatchRetryStrategy instance.
func NewClusterBatchRetryStrategy() *ClusterBatchRetryStrategy {
	return &ClusterBatchRetryStrategy{false, false}
}

// Configure whether to retry on server errors.
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

// Configure whether to retry on connection errors.
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

// Create a new options instance for standalone batches.
//
// Returns:
//
//	A new StandaloneBatchOptions instance.
func NewStandaloneBatchOptions() *StandaloneBatchOptions {
	return &StandaloneBatchOptions{}
}

// Set the timeout for the batch execution.
//
// Parameters:
//
//	timeout - The batch timeout.
//
// Returns:
//
//	The updated StandaloneBatchOptions instance.
func (sbo *StandaloneBatchOptions) WithTimeout(timeout time.Duration) *StandaloneBatchOptions {
	to := uint32(timeout.Milliseconds())
	sbo.Timeout = &to
	return sbo
}

// Create a new options instance for cluster batches.
//
// Returns:
//
//	A new ClusterBatchOptions instance.
func NewClusterBatchOptions() *ClusterBatchOptions {
	return &ClusterBatchOptions{}
}

// Set the timeout for the batch execution.
//
// Parameters:
//
//	timeout - The batch timeout.
//
// Returns:
//
//	The updated ClusterBatchOptions instance.
func (cbo *ClusterBatchOptions) WithTimeout(timeout time.Duration) *ClusterBatchOptions {
	to := uint32(timeout.Milliseconds())
	cbo.Timeout = &to
	return cbo
}

// TODO ensure only single node route is allowed (use config.NotMultiNode?)

// Set the routing strategy for the batch.
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

// Set the retry strategy for the batch.
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

func (sbo StandaloneBatchOptions) Convert() internal.BatchOptions {
	return internal.BatchOptions{Timeout: sbo.Timeout}
}

func (cbo ClusterBatchOptions) Convert() internal.BatchOptions {
	opts := internal.BatchOptions{Timeout: cbo.Timeout, Route: cbo.Route, RetryServerError: nil, RetryConnectionError: nil}
	if cbo.RetryStrategy != nil {
		opts.RetryServerError = &cbo.RetryStrategy.RetryServerError
		opts.RetryConnectionError = &cbo.RetryStrategy.RetryConnectionError
	}
	return opts
}
