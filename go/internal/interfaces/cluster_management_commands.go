// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package interfaces

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// ClusterManagementCommands supports commands for the "Cluster Management" group for cluster clients.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#cluster
type ClusterManagementCommands interface {
	// ClusterInfo returns information about the state of the cluster.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   ctx - The context for controlling the command execution.
	//
	// Return value:
	//   A string containing cluster information.
	//
	// [valkey.io]: https://valkey.io/commands/cluster-info/
	ClusterInfo(ctx context.Context) (string, error)

	// ClusterInfoWithRoute returns information about the state of the cluster with routing options.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   ctx - The context for controlling the command execution.
	//   route - Specifies the routing configuration for the command.
	//
	// Return value:
	//   A ClusterValue containing cluster information.
	//
	// [valkey.io]: https://valkey.io/commands/cluster-info/
	ClusterInfoWithRoute(ctx context.Context, route options.RouteOption) (models.ClusterValue[string], error)

	// ClusterNodes returns the cluster configuration as seen by the node.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   ctx - The context for controlling the command execution.
	//
	// Return value:
	//   A string containing cluster nodes information in the cluster config format.
	//
	// [valkey.io]: https://valkey.io/commands/cluster-nodes/
	ClusterNodes(ctx context.Context) (string, error)

	// ClusterNodesWithRoute returns the cluster configuration with routing options.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   ctx - The context for controlling the command execution.
	//   route - Specifies the routing configuration for the command.
	//
	// Return value:
	//   A ClusterValue containing cluster nodes information.
	//
	// [valkey.io]: https://valkey.io/commands/cluster-nodes/
	ClusterNodesWithRoute(ctx context.Context, route options.RouteOption) (models.ClusterValue[string], error)

	// ClusterShards returns the mapping of cluster slots to shards.
	// Each shard contains information about the primary and replicas.
	//
	// Since: Valkey 7.0 and above.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   ctx - The context for controlling the command execution.
	//
	// Return value:
	//   An array of maps representing each shard with slots and node information.
	//
	// [valkey.io]: https://valkey.io/commands/cluster-shards/
	ClusterShards(ctx context.Context) ([]map[string]any, error)

	// ClusterShardsWithRoute returns the mapping of cluster slots to shards with routing options.
	//
	// Since: Valkey 7.0 and above.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   ctx - The context for controlling the command execution.
	//   route - Specifies the routing configuration for the command.
	//
	// Return value:
	//   A ClusterValue containing shard information.
	//
	// [valkey.io]: https://valkey.io/commands/cluster-shards/
	ClusterShardsWithRoute(ctx context.Context, route options.RouteOption) (models.ClusterValue[[]map[string]any], error)

	// ClusterKeySlot returns the hash slot for a given key.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   ctx - The context for controlling the command execution.
	//   key - The key to get the hash slot for.
	//
	// Return value:
	//   The hash slot number for the key (0-16383).
	//
	// [valkey.io]: https://valkey.io/commands/cluster-keyslot/
	ClusterKeySlot(ctx context.Context, key string) (int64, error)

	// ClusterMyId returns the node ID of the current node.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   ctx - The context for controlling the command execution.
	//
	// Return value:
	//   The node ID of the current node.
	//
	// [valkey.io]: https://valkey.io/commands/cluster-myid/
	ClusterMyId(ctx context.Context) (string, error)

	// ClusterMyIdWithRoute returns the node ID with routing options.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   ctx - The context for controlling the command execution.
	//   route - Specifies the routing configuration for the command.
	//
	// Return value:
	//   A ClusterValue containing the node ID.
	//
	// [valkey.io]: https://valkey.io/commands/cluster-myid/
	ClusterMyIdWithRoute(ctx context.Context, route options.RouteOption) (models.ClusterValue[string], error)

	// ClusterMyShardId returns the shard ID of the current node.
	//
	// Since: Valkey 7.2 and above.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   ctx - The context for controlling the command execution.
	//
	// Return value:
	//   The shard ID of the current node.
	//
	// [valkey.io]: https://valkey.io/commands/cluster-myshardid/
	ClusterMyShardId(ctx context.Context) (string, error)

	// ClusterMyShardIdWithRoute returns the shard ID with routing options.
	//
	// Since: Valkey 7.2 and above.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   ctx - The context for controlling the command execution.
	//   route - Specifies the routing configuration for the command.
	//
	// Return value:
	//   A ClusterValue containing the shard ID.
	//
	// [valkey.io]: https://valkey.io/commands/cluster-myshardid/
	ClusterMyShardIdWithRoute(ctx context.Context, route options.RouteOption) (models.ClusterValue[string], error)

	// ClusterGetKeysInSlot returns an array of keys in the specified hash slot.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   ctx - The context for controlling the command execution.
	//   slot - The hash slot number (0-16383).
	//   count - Maximum number of keys to return.
	//
	// Return value:
	//   An array of keys in the specified slot.
	//
	// [valkey.io]: https://valkey.io/commands/cluster-getkeysinslot/
	ClusterGetKeysInSlot(ctx context.Context, slot int64, count int64) ([]string, error)

	// ClusterCountKeysInSlot returns the number of keys in the specified hash slot.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   ctx - The context for controlling the command execution.
	//   slot - The hash slot number (0-16383).
	//
	// Return value:
	//   The number of keys in the specified slot.
	//
	// [valkey.io]: https://valkey.io/commands/cluster-countkeysinslot/
	ClusterCountKeysInSlot(ctx context.Context, slot int64) (int64, error)

	// ClusterLinks returns information about all TCP links between cluster nodes.
	//
	// Since: Valkey 7.0 and above.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   ctx - The context for controlling the command execution.
	//
	// Return value:
	//   An array of maps containing link information.
	//
	// [valkey.io]: https://valkey.io/commands/cluster-links/
	ClusterLinks(ctx context.Context) ([]map[string]any, error)

	// ClusterLinksWithRoute returns link information with routing options.
	//
	// Since: Valkey 7.0 and above.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   ctx - The context for controlling the command execution.
	//   route - Specifies the routing configuration for the command.
	//
	// Return value:
	//   A ClusterValue containing link information.
	//
	// [valkey.io]: https://valkey.io/commands/cluster-links/
	ClusterLinksWithRoute(ctx context.Context, route options.RouteOption) (models.ClusterValue[[]map[string]any], error)
}
