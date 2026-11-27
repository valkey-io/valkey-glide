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

func unwrapClusterValue(result models.ClusterValue[any], err error) (any, error) {
	if err != nil {
		return nil, err
	}
	if result.IsSingleValue() {
		return result.SingleValue(), nil
	}
	return result.MultiValue(), nil
}

func execCluster(client clusterClient, ctx context.Context, args []string) (any, error) {
	return unwrapClusterValue(client.CustomCommand(ctx, args))
}

func execClusterPrimaryPinned(client clusterClient, ctx context.Context, indexName string, args []string) (any, error) {
	route := config.NewSlotKeyRoute(config.SlotTypePrimary, indexName)
	return unwrapClusterValue(client.CustomCommandWithRoute(ctx, args, route))
}
