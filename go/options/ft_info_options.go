// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

// FtInfoScope controls the scope of information returned by FT.INFO.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/ft.info/
type FtInfoScope string

const (
	// FtInfoScopeLocal returns per-node (local shard) index information.
	FtInfoScopeLocal FtInfoScope = "LOCAL"
	// FtInfoScopePrimary returns aggregated information from the primary coordinator.
	// Requires the coordinator module to be enabled.
	FtInfoScopePrimary FtInfoScope = "PRIMARY"
	// FtInfoScopeCluster returns cluster-wide aggregated index information.
	// Requires the coordinator module to be enabled.
	FtInfoScopeCluster FtInfoScope = "CLUSTER"
)

// FtInfoShardScope controls which shards participate in the FT.INFO query.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/ft.info/
type FtInfoShardScope string

const (
	// FtInfoShardScopeAllShards queries all shards (default).
	FtInfoShardScopeAllShards FtInfoShardScope = "ALLSHARDS"
	// FtInfoShardScopeSomeShards queries only a subset of shards.
	FtInfoShardScopeSomeShards FtInfoShardScope = "SOMESHARDS"
)

// FtInfoConsistencyMode controls consistency requirements for the FT.INFO query.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/ft.info/
type FtInfoConsistencyMode string

const (
	// FtInfoConsistencyConsistent requires consistent results across shards.
	FtInfoConsistencyConsistent FtInfoConsistencyMode = "CONSISTENT"
	// FtInfoConsistencyInconsistent allows inconsistent (faster) results.
	FtInfoConsistencyInconsistent FtInfoConsistencyMode = "INCONSISTENT"
)

// FtInfoOptions holds optional arguments for the FT.INFO command.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/ft.info/
type FtInfoOptions struct {
	// Scope controls the scope of information returned.
	Scope FtInfoScope
	// ShardScope controls which shards participate in the query.
	ShardScope FtInfoShardScope
	// Consistency controls consistency requirements.
	Consistency FtInfoConsistencyMode
}

// ToArgs returns the command arguments for FtInfoOptions.
func (o *FtInfoOptions) ToArgs() []string {
	args := []string{}
	if o.Scope != "" {
		args = append(args, string(o.Scope))
	}
	if o.ShardScope != "" {
		args = append(args, string(o.ShardScope))
	}
	if o.Consistency != "" {
		args = append(args, string(o.Consistency))
	}
	return args
}
