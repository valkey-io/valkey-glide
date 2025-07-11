// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.Internals.Request;

namespace Valkey.Glide.Pipeline;

/// <summary>
/// Sorted Set commands for BaseBatch.
/// </summary>
public abstract partial class BaseBatch<T>
{
// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using static Valkey.Glide.Internals.Request;

namespace Valkey.Glide.Pipeline;

/// <summary>
/// Sorted Set commands for BaseBatch.
/// </summary>
public abstract partial class BaseBatch<T>
{
    /// <inheritdoc cref="IBatchSortedSetCommands.SortedSetAdd(ValkeyKey, ValkeyValue, double)" />
    public T SortedSetAdd(ValkeyKey key, ValkeyValue member, double score) => AddCmd(SortedSetAddAsync(key, member, score));

    /// <inheritdoc cref="IBatchSortedSetCommands.SortedSetAdd(ValkeyKey, ValkeyValue, double, When)" />
    public T SortedSetAdd(ValkeyKey key, ValkeyValue member, double score, When when) => AddCmd(SortedSetAddAsync(key, member, score, SortedSetWhenExtensions.Parse(when)));

    /// <inheritdoc cref="IBatchSortedSetCommands.SortedSetAdd(ValkeyKey, ValkeyValue, double, SortedSetWhen)" />
    public T SortedSetAdd(ValkeyKey key, ValkeyValue member, double score, SortedSetWhen when) => AddCmd(SortedSetAddAsync(key, member, score, when));

    /// <inheritdoc cref="IBatchSortedSetCommands.SortedSetAdd(ValkeyKey, SortedSetEntry[])" />
    public T SortedSetAdd(ValkeyKey key, SortedSetEntry[] values) => AddCmd(SortedSetAddAsync(key, values));

    /// <inheritdoc cref="IBatchSortedSetCommands.SortedSetAdd(ValkeyKey, SortedSetEntry[], When)" />
    public T SortedSetAdd(ValkeyKey key, SortedSetEntry[] values, When when) => AddCmd(SortedSetAddAsync(key, values, SortedSetWhenExtensions.Parse(when)));

    /// <inheritdoc cref="IBatchSortedSetCommands.SortedSetAdd(ValkeyKey, SortedSetEntry[], SortedSetWhen)" />
    public T SortedSetAdd(ValkeyKey key, SortedSetEntry[] values, SortedSetWhen when) => AddCmd(SortedSetAddAsync(key, values, when));

    // Explicit interface implementations for IBatchSortedSetCommands
    IBatch IBatchSortedSetCommands.SortedSetAdd(ValkeyKey key, ValkeyValue member, double score) => SortedSetAdd(key, member, score);
    IBatch IBatchSortedSetCommands.SortedSetAdd(ValkeyKey key, ValkeyValue member, double score, When when) => SortedSetAdd(key, member, score, when);
    IBatch IBatchSortedSetCommands.SortedSetAdd(ValkeyKey key, ValkeyValue member, double score, SortedSetWhen when) => SortedSetAdd(key, member, score, when);
    IBatch IBatchSortedSetCommands.SortedSetAdd(ValkeyKey key, SortedSetEntry[] values) => SortedSetAdd(key, values);
    IBatch IBatchSortedSetCommands.SortedSetAdd(ValkeyKey key, SortedSetEntry[] values, When when) => SortedSetAdd(key, values, when);
    IBatch IBatchSortedSetCommands.SortedSetAdd(ValkeyKey key, SortedSetEntry[] values, SortedSetWhen when) => SortedSetAdd(key, values, when);
}
}
