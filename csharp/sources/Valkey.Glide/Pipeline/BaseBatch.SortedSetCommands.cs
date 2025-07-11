// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.Internals.Request;

namespace Valkey.Glide.Pipeline;

/// <summary>
/// Sorted Set commands for BaseBatch.
/// </summary>
public abstract partial class BaseBatch<T>
{
    /// <inheritdoc cref="IBatchSortedSetCommands.SortedSetAdd(ValkeyKey, ValkeyValue, double)" />
    public T SortedSetAdd(ValkeyKey key, ValkeyValue member, double score) => AddCmd(SortedSetAddAsync(key, member, score));

    /// <inheritdoc cref="IBatchSortedSetCommands.SortedSetAdd(ValkeyKey, SortedSetEntry[])" />
    public T SortedSetAdd(ValkeyKey key, SortedSetEntry[] entries) => AddCmd(SortedSetAddAsync(key, entries));

    // Explicit interface implementations for IBatchSortedSetCommands
    IBatch IBatchSortedSetCommands.SortedSetAdd(ValkeyKey key, ValkeyValue member, double score) => SortedSetAdd(key, member, score);
    IBatch IBatchSortedSetCommands.SortedSetAdd(ValkeyKey key, SortedSetEntry[] entries) => SortedSetAdd(key, entries);
}
