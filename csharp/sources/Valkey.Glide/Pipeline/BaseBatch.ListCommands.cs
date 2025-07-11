// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.Internals.Request;

namespace Valkey.Glide.Pipeline;

/// <summary>
/// List commands for BaseBatch.
/// </summary>
public abstract partial class BaseBatch<T>
{
    /// <inheritdoc cref="IBatchListCommands.ListLeftPop(ValkeyKey)" />
    public T ListLeftPop(ValkeyKey key) => AddCmd(ListLeftPopAsync(key));

    /// <inheritdoc cref="IBatchListCommands.ListLeftPop(ValkeyKey, long)" />
    public T ListLeftPop(ValkeyKey key, long count) => AddCmd(ListLeftPopAsync(key, count));

    /// <inheritdoc cref="IBatchListCommands.ListLeftPush(ValkeyKey, ValkeyValue[])" />
    public T ListLeftPush(ValkeyKey key, ValkeyValue[] values) => AddCmd(ListLeftPushAsync(key, values));

    // Explicit interface implementations for IBatchListCommands
    IBatch IBatchListCommands.ListLeftPop(ValkeyKey key) => ListLeftPop(key);
    IBatch IBatchListCommands.ListLeftPop(ValkeyKey key, long count) => ListLeftPop(key, count);
    IBatch IBatchListCommands.ListLeftPush(ValkeyKey key, ValkeyValue[] values) => ListLeftPush(key, values);
}
