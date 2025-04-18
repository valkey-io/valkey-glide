// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Pipeline;

// TODO docs for the god of docs
public sealed class ClusterBatch : BaseBatch<ClusterBatch>
{
    public ClusterBatch(bool isAtomic) : base(isAtomic)
    {
        // Cluster commands
    }
}
