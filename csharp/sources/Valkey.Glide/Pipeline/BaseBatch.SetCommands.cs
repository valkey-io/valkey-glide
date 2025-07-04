// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.Internals.Request;

namespace Valkey.Glide.Pipeline;

/// <summary>
/// Set commands for BaseBatch.
/// </summary>
public abstract partial class BaseBatch<T>
{
    /// <inheritdoc cref="IBatchSetCommands.SetAdd(ValkeyKey, ValkeyValue)" />
    public T SetAdd(ValkeyKey key, ValkeyValue value) => AddCmd(SetAddAsync(key, value));

    /// <inheritdoc cref="IBatchSetCommands.SetAdd(ValkeyKey, ValkeyValue[])" />
    public T SetAdd(ValkeyKey key, ValkeyValue[] values) => AddCmd(SetAddAsync(key, values));

    /// <inheritdoc cref="IBatchSetCommands.SetRemove(ValkeyKey, ValkeyValue)" />
    public T SetRemove(ValkeyKey key, ValkeyValue value) => AddCmd(SetRemoveAsync(key, value));

    /// <inheritdoc cref="IBatchSetCommands.SetRemove(ValkeyKey, ValkeyValue[])" />
    public T SetRemove(ValkeyKey key, ValkeyValue[] values) => AddCmd(SetRemoveAsync(key, values));

    /// <inheritdoc cref="IBatchSetCommands.SetMembers(ValkeyKey)" />
    public T SetMembers(ValkeyKey key) => AddCmd(SetMembersAsync(key));

    /// <inheritdoc cref="IBatchSetCommands.SetLength(ValkeyKey)" />
    public T SetLength(ValkeyKey key) => AddCmd(SetLengthAsync(key));

    /// <inheritdoc cref="IBatchSetCommands.SetIntersectionLength(ValkeyKey[], long)" />
    public T SetIntersectionLength(ValkeyKey[] keys, long limit = 0) => AddCmd(SetIntersectionLengthAsync(keys, limit));

    /// <inheritdoc cref="IBatchSetCommands.SetPop(ValkeyKey)" />
    public T SetPop(ValkeyKey key) => AddCmd(SetPopAsync(key));

    /// <inheritdoc cref="IBatchSetCommands.SetPop(ValkeyKey, long)" />
    public T SetPop(ValkeyKey key, long count) => AddCmd(SetPopAsync(key, count));

    /// <inheritdoc cref="IBatchSetCommands.SetUnion(ValkeyKey, ValkeyKey)" />
    public T SetUnion(ValkeyKey first, ValkeyKey second) => AddCmd(SetUnionAsync([first, second]));

    /// <inheritdoc cref="IBatchSetCommands.SetUnion(ValkeyKey[])" />
    public T SetUnion(ValkeyKey[] keys) => AddCmd(SetUnionAsync(keys));

    /// <inheritdoc cref="IBatchSetCommands.SetIntersect(ValkeyKey, ValkeyKey)" />
    public T SetIntersect(ValkeyKey first, ValkeyKey second) => AddCmd(SetIntersectAsync([first, second]));

    /// <inheritdoc cref="IBatchSetCommands.SetIntersect(ValkeyKey[])" />
    public T SetIntersect(ValkeyKey[] keys) => AddCmd(SetIntersectAsync(keys));

    /// <inheritdoc cref="IBatchSetCommands.SetDifference(ValkeyKey, ValkeyKey)" />
    public T SetDifference(ValkeyKey first, ValkeyKey second) => AddCmd(SetDifferenceAsync([first, second]));

    /// <inheritdoc cref="IBatchSetCommands.SetDifference(ValkeyKey[])" />
    public T SetDifference(ValkeyKey[] keys) => AddCmd(SetDifferenceAsync(keys));

    /// <inheritdoc cref="IBatchSetCommands.SetUnionStore(ValkeyKey, ValkeyKey, ValkeyKey)" />
    public T SetUnionStore(ValkeyKey destination, ValkeyKey first, ValkeyKey second) => AddCmd(SetUnionStoreAsync(destination, [first, second]));

    /// <inheritdoc cref="IBatchSetCommands.SetUnionStore(ValkeyKey, ValkeyKey[])" />
    public T SetUnionStore(ValkeyKey destination, ValkeyKey[] keys) => AddCmd(SetUnionStoreAsync(destination, keys));

    /// <inheritdoc cref="IBatchSetCommands.SetIntersectStore(ValkeyKey, ValkeyKey, ValkeyKey)" />
    public T SetIntersectStore(ValkeyKey destination, ValkeyKey first, ValkeyKey second) => AddCmd(SetIntersectStoreAsync(destination, [first, second]));

    /// <inheritdoc cref="IBatchSetCommands.SetIntersectStore(ValkeyKey, ValkeyKey[])" />
    public T SetIntersectStore(ValkeyKey destination, ValkeyKey[] keys) => AddCmd(SetIntersectStoreAsync(destination, keys));

    /// <inheritdoc cref="IBatchSetCommands.SetDifferenceStore(ValkeyKey, ValkeyKey, ValkeyKey)" />
    public T SetDifferenceStore(ValkeyKey destination, ValkeyKey first, ValkeyKey second) => AddCmd(SetDifferenceStoreAsync(destination, [first, second]));

    /// <inheritdoc cref="IBatchSetCommands.SetDifferenceStore(ValkeyKey, ValkeyKey[])" />
    public T SetDifferenceStore(ValkeyKey destination, ValkeyKey[] keys) => AddCmd(SetDifferenceStoreAsync(destination, keys));

    // Explicit interface implementations for IBatchSetCommands
    IBatch IBatchSetCommands.SetAdd(ValkeyKey key, ValkeyValue value) => SetAdd(key, value);
    IBatch IBatchSetCommands.SetAdd(ValkeyKey key, ValkeyValue[] values) => SetAdd(key, values);
    IBatch IBatchSetCommands.SetRemove(ValkeyKey key, ValkeyValue value) => SetRemove(key, value);
    IBatch IBatchSetCommands.SetRemove(ValkeyKey key, ValkeyValue[] values) => SetRemove(key, values);
    IBatch IBatchSetCommands.SetMembers(ValkeyKey key) => SetMembers(key);
    IBatch IBatchSetCommands.SetLength(ValkeyKey key) => SetLength(key);
    IBatch IBatchSetCommands.SetIntersectionLength(ValkeyKey[] keys, long limit) => SetIntersectionLength(keys, limit);
    IBatch IBatchSetCommands.SetPop(ValkeyKey key) => SetPop(key);
    IBatch IBatchSetCommands.SetPop(ValkeyKey key, long count) => SetPop(key, count);
    IBatch IBatchSetCommands.SetUnion(ValkeyKey first, ValkeyKey second) => SetUnion(first, second);
    IBatch IBatchSetCommands.SetUnion(ValkeyKey[] keys) => SetUnion(keys);
    IBatch IBatchSetCommands.SetIntersect(ValkeyKey first, ValkeyKey second) => SetIntersect(first, second);
    IBatch IBatchSetCommands.SetIntersect(ValkeyKey[] keys) => SetIntersect(keys);
    IBatch IBatchSetCommands.SetDifference(ValkeyKey first, ValkeyKey second) => SetDifference(first, second);
    IBatch IBatchSetCommands.SetDifference(ValkeyKey[] keys) => SetDifference(keys);
    IBatch IBatchSetCommands.SetUnionStore(ValkeyKey destination, ValkeyKey first, ValkeyKey second) => SetUnionStore(destination, first, second);
    IBatch IBatchSetCommands.SetUnionStore(ValkeyKey destination, ValkeyKey[] keys) => SetUnionStore(destination, keys);
    IBatch IBatchSetCommands.SetIntersectStore(ValkeyKey destination, ValkeyKey first, ValkeyKey second) => SetIntersectStore(destination, first, second);
    IBatch IBatchSetCommands.SetIntersectStore(ValkeyKey destination, ValkeyKey[] keys) => SetIntersectStore(destination, keys);
    IBatch IBatchSetCommands.SetDifferenceStore(ValkeyKey destination, ValkeyKey first, ValkeyKey second) => SetDifferenceStore(destination, first, second);
    IBatch IBatchSetCommands.SetDifferenceStore(ValkeyKey destination, ValkeyKey[] keys) => SetDifferenceStore(destination, keys);
}
