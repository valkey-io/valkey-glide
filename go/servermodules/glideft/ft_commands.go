// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glideft

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/models"
)

const (
	ftCreateCommand      = "FT.CREATE"
	ftDropIndexCommand   = "FT.DROPINDEX"
	ftListCommand        = "FT._LIST"
	ftSearchCommand      = "FT.SEARCH"
	ftAggregateCommand   = "FT.AGGREGATE"
	ftInfoCommand        = "FT.INFO"
	ftExplainCommand     = "FT.EXPLAIN"
	ftExplainCLICommand  = "FT.EXPLAINCLI"
	ftAliasAddCommand    = "FT.ALIASADD"
	ftAliasDelCommand    = "FT.ALIASDEL"
	ftAliasUpdateCommand = "FT.ALIASUPDATE"
	ftAliasListCommand   = "FT._ALIASLIST"
)

// standaloneClient is the interface for standalone client FT operations.
type standaloneClient interface {
	CustomCommand(ctx context.Context, args []string) (any, error)
}

// clusterClient is the interface for cluster client FT operations.
type clusterClient interface {
	CustomCommand(ctx context.Context, args []string) (models.ClusterValue[any], error)
	CustomCommandWithRoute(ctx context.Context, args []string, route config.Route) (models.ClusterValue[any], error)
}

func execStandalone(client standaloneClient, ctx context.Context, args []string) (any, error) {
	return client.CustomCommand(ctx, args)
}

func execCluster(client clusterClient, ctx context.Context, args []string) (any, error) {
	result, err := client.CustomCommand(ctx, args)
	if err != nil {
		return nil, err
	}

	if result.IsSingleValue() {
		return result.SingleValue(), nil
	}
	return result.MultiValue(), nil
}

// execClusterPrimaryPinned routes args to the PRIMARY of indexName's slot, regardless of the
// client's ReadFrom configuration. Used by FT.CURSOR READ/DEL and any WITHCURSOR-opening
// FT.AGGREGATE/FT.HYBRID -- a cursor is a per-process, node-local resource on the server that
// opened it, so a read that lands on a different node (a replica a PREFER_REPLICA/AZ-affinity
// client would otherwise have used) fails with "Cursor not found".
func execClusterPrimaryPinned(client clusterClient, ctx context.Context, indexName string, args []string) (any, error) {
	route := config.NewSlotKeyRoute(config.SlotTypePrimary, indexName)
	result, err := client.CustomCommandWithRoute(ctx, args, route)
	if err != nil {
		return nil, err
	}
	if result.IsSingleValue() {
		return result.SingleValue(), nil
	}
	return result.MultiValue(), nil
}
