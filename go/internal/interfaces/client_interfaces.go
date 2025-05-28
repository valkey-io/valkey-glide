// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package interfaces

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/v2/pipeline"
)

type BaseClientCommands interface {
	StringCommands
	HashCommands
	ListCommands
	SetCommands
	StreamCommands
	SortedSetCommands
	HyperLogLogCommands
	GenericBaseCommands
	BitmapCommands
	GeoSpatialCommands
	ScriptingAndFunctionBaseCommands
	PubSubCommands
	// Close terminates the client by closing all associated resources.
	Close()
}

type GlideClientCommands interface {
	BaseClientCommands
	GenericCommands
	ServerManagementCommands
	BitmapCommands
	ConnectionManagementCommands
	ScriptingAndFunctionStandaloneCommands
	PubSubStandaloneCommands

	Exec(ctx context.Context, batch pipeline.StandaloneBatch, raiseOnError bool) ([]any, error)
	ExecWithOptions(
		ctx context.Context,
		batch pipeline.StandaloneBatch,
		raiseOnError bool,
		options pipeline.StandaloneBatchOptions,
	) ([]any, error)
}

type GlideClusterClientCommands interface {
	BaseClientCommands
	GenericClusterCommands
	ServerManagementClusterCommands
	ConnectionManagementClusterCommands
	ScriptingAndFunctionClusterCommands
	PubSubClusterCommands

	Exec(ctx context.Context, batch pipeline.ClusterBatch, raiseOnError bool) ([]any, error)
	ExecWithOptions(
		ctx context.Context,
		batch pipeline.ClusterBatch,
		raiseOnError bool,
		options pipeline.ClusterBatchOptions,
	) ([]any, error)
}
