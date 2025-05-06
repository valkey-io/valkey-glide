// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Pipeline;

/// <summary>
/// Batch implementation for cluster <see cref="GlideClusterClient" />. Batches allow the execution of a group
/// of commands in a single step.
/// <para />
/// Batch Response: An <c>array</c> of command responses is returned by the client <see cref="GlideClusterClient.Exec(ClusterBatch, bool)" />
/// and <see cref="GlideClusterClient.Exec(ClusterBatch, bool, Options.ClusterBatchOptions)" /> API, in the order they were given.
/// Each element in the array represents a command given to the <c>Batch</c>. The response for each command depends on the
/// executed Valkey command. Specific response types are documented alongside each method.
/// <para />
/// See the <see href="https://valkey.io/topics/transactions/">Valkey Transactions (Atomic Batches)</see>.<br />
/// See the <see href="https://valkey.io/topics/pipelining/">Valkey Pipelines (Non-Atomic Batches)</see>.
/// </summary>
/// <remarks>
/// <inheritdoc cref="GlideClusterClient.Exec(ClusterBatch, bool)" path="/remarks/example" />
/// </remarks>
/// <param name="isAtomic">
/// <inheritdoc cref="BaseBatch{T}.BaseBatch(bool)" />
/// </param>
public sealed class ClusterBatch(bool isAtomic) : BaseBatch<ClusterBatch>(isAtomic)
{
    // Cluster commands
}
