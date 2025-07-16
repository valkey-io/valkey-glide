// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Internals;

namespace Valkey.Glide.Pipeline;

/// <summary>
/// Batch implementation for standalone <see cref="GlideClient" />. Batches allow the execution of a group
/// of commands in a single step.
/// <para />
/// Batch Response: An <c>array</c> of command responses is returned by the client <see cref="GlideClient.Exec(Batch, bool)" />
/// and <see cref="GlideClient.Exec(Batch, bool, Options.BatchOptions)" /> API, in the order they were given. Each element
/// in the array represents a command given to the <c>Batch</c>. The response for each command depends on the executed
/// Valkey command. Specific response types are documented alongside each method.
/// <para />
/// See the <see href="https://valkey.io/topics/transactions/">Valkey Transactions (Atomic Batches)</see>.<br />
/// See the <see href="https://valkey.io/topics/pipelining/">Valkey Pipelines (Non-Atomic Batches)</see>.
/// </summary>
/// <remarks>
/// Standalone Batches are executed on the primary node.
/// <inheritdoc cref="GlideClient.Exec(Batch, bool)" path="/remarks/example" />
/// </remarks>
/// <param name="isAtomic">
/// <inheritdoc cref="BaseBatch{T}.BaseBatch(bool)" />
/// </param>
public sealed class Batch(bool isAtomic) : BaseBatch<Batch>(isAtomic), IBatchStandalone
{
    // Standalone commands: select, move, copy, scan

    /// <inheritdoc cref="IBatchStandalone.KeyCopy(ValkeyKey, ValkeyKey, int, bool)" />
    public Batch KeyCopy(ValkeyKey sourceKey, ValkeyKey destinationKey, int destinationDatabase, bool replace = false) => AddCmd(Request.KeyCopyAsync(sourceKey, destinationKey, destinationDatabase, replace));

    /// <inheritdoc cref="IBatchStandalone.KeyMove(ValkeyKey, int)" />
    public Batch KeyMove(ValkeyKey key, int database) => AddCmd(Request.KeyMoveAsync(key, database));

    // Explicit interface implementations for IBatchStandalone
    IBatchStandalone IBatchStandalone.KeyCopy(ValkeyKey sourceKey, ValkeyKey destinationKey, int destinationDatabase, bool replace) => KeyCopy(sourceKey, destinationKey, destinationDatabase, replace);
    IBatchStandalone IBatchStandalone.KeyMove(ValkeyKey key, int database) => KeyMove(key, database);
}
