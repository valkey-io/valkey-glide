// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.Internals.Request;

namespace Valkey.Glide.Pipeline;

/// <summary>
/// Sorted Set commands for BaseBatch.
/// </summary>
public abstract partial class BaseBatch<T>
{
    /// <inheritdoc cref="IBatchSortedSetCommands.SortedSetAdd(ValkeyKey, ValkeyValue, double, SortedSetWhen)" />
    public T SortedSetAdd(ValkeyKey key, ValkeyValue member, double score, SortedSetWhen when = SortedSetWhen.Always) => AddCmd(SortedSetAddAsync(key, member, score, when));

    /// <inheritdoc cref="IBatchSortedSetCommands.SortedSetAdd(ValkeyKey, SortedSetEntry[], SortedSetWhen)" />
    public T SortedSetAdd(ValkeyKey key, SortedSetEntry[] values, SortedSetWhen when = SortedSetWhen.Always) => AddCmd(SortedSetAddAsync(key, values, when));

    /// <inheritdoc cref="IBatchSortedSetCommands.SortedSetRemove(ValkeyKey, ValkeyValue)" />
    public T SortedSetRemove(ValkeyKey key, ValkeyValue member) => AddCmd(SortedSetRemoveAsync(key, member));

    /// <inheritdoc cref="IBatchSortedSetCommands.SortedSetRemove(ValkeyKey, ValkeyValue[])" />
    public T SortedSetRemove(ValkeyKey key, ValkeyValue[] members) => AddCmd(SortedSetRemoveAsync(key, members));

    // Explicit interface implementations for IBatchSortedSetCommands
    IBatch IBatchSortedSetCommands.SortedSetAdd(ValkeyKey key, ValkeyValue member, double score, SortedSetWhen when) => SortedSetAdd(key, member, score, when);
    IBatch IBatchSortedSetCommands.SortedSetAdd(ValkeyKey key, SortedSetEntry[] values, SortedSetWhen when) => SortedSetAdd(key, values, when);
    IBatch IBatchSortedSetCommands.SortedSetRemove(ValkeyKey key, ValkeyValue member) => SortedSetRemove(key, member);
    IBatch IBatchSortedSetCommands.SortedSetRemove(ValkeyKey key, ValkeyValue[] members) => SortedSetRemove(key, members);
}
