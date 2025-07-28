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

    /// <inheritdoc cref="IBatchListCommands.ListLeftPush(ValkeyKey, ValkeyValue)" />
    public T ListLeftPush(ValkeyKey key, ValkeyValue value) => AddCmd(ListLeftPushAsync(key, value, When.Always));

    /// <inheritdoc cref="IBatchListCommands.ListLeftPush(ValkeyKey, ValkeyValue[])" />
    public T ListLeftPush(ValkeyKey key, ValkeyValue[] values) => AddCmd(ListLeftPushAsync(key, values, When.Always));

    /// <inheritdoc cref="IBatchListCommands.ListLeftPush(ValkeyKey, ValkeyValue[], CommandFlags)" />
    public T ListLeftPush(ValkeyKey key, ValkeyValue[] values, CommandFlags _) => AddCmd(ListLeftPushAsync(key, values));

    /// <inheritdoc cref="IBatchListCommands.ListRightPop(ValkeyKey)" />
    public T ListRightPop(ValkeyKey key) => AddCmd(ListRightPopAsync(key));

    /// <inheritdoc cref="IBatchListCommands.ListRightPop(ValkeyKey, long)" />
    public T ListRightPop(ValkeyKey key, long count) => AddCmd(ListRightPopAsync(key, count));

    /// <inheritdoc cref="IBatchListCommands.ListRightPush(ValkeyKey, ValkeyValue)" />
    public T ListRightPush(ValkeyKey key, ValkeyValue value) => AddCmd(ListRightPushAsync(key, value, When.Always));

    /// <inheritdoc cref="IBatchListCommands.ListRightPush(ValkeyKey, ValkeyValue[])" />
    public T ListRightPush(ValkeyKey key, ValkeyValue[] values) => AddCmd(ListRightPushAsync(key, values, When.Always));

    /// <inheritdoc cref="IBatchListCommands.ListRightPush(ValkeyKey, ValkeyValue[], CommandFlags)" />
    public T ListRightPush(ValkeyKey key, ValkeyValue[] values, CommandFlags _) => AddCmd(ListRightPushAsync(key, values));

    /// <inheritdoc cref="IBatchListCommands.ListLength(ValkeyKey)" />
    public T ListLength(ValkeyKey key) => AddCmd(ListLengthAsync(key));

    /// <inheritdoc cref="IBatchListCommands.ListRemove(ValkeyKey, ValkeyValue, long)" />
    public T ListRemove(ValkeyKey key, ValkeyValue value, long count = 0) => AddCmd(ListRemoveAsync(key, value, count));

    /// <inheritdoc cref="IBatchListCommands.ListTrim(ValkeyKey, long, long)" />
    public T ListTrim(ValkeyKey key, long start, long stop) => AddCmd(ListTrimAsync(key, start, stop));

    /// <inheritdoc cref="IBatchListCommands.ListRange(ValkeyKey, long, long)" />
    public T ListRange(ValkeyKey key, long start = 0, long stop = -1) => AddCmd(ListRangeAsync(key, start, stop));

    // Explicit interface implementations for IBatchListCommands
    IBatch IBatchListCommands.ListLeftPop(ValkeyKey key) => ListLeftPop(key);
    IBatch IBatchListCommands.ListLeftPop(ValkeyKey key, long count) => ListLeftPop(key, count);
    IBatch IBatchListCommands.ListLeftPush(ValkeyKey key, ValkeyValue value) => ListLeftPush(key, value);
    IBatch IBatchListCommands.ListLeftPush(ValkeyKey key, ValkeyValue[] values) => ListLeftPush(key, values);
    IBatch IBatchListCommands.ListLeftPush(ValkeyKey key, ValkeyValue[] values, CommandFlags flags) => ListLeftPush(key, values, flags);
    IBatch IBatchListCommands.ListRightPop(ValkeyKey key) => ListRightPop(key);
    IBatch IBatchListCommands.ListRightPop(ValkeyKey key, long count) => ListRightPop(key, count);
    IBatch IBatchListCommands.ListRightPush(ValkeyKey key, ValkeyValue value) => ListRightPush(key, value);
    IBatch IBatchListCommands.ListRightPush(ValkeyKey key, ValkeyValue[] values) => ListRightPush(key, values);
    IBatch IBatchListCommands.ListRightPush(ValkeyKey key, ValkeyValue[] values, CommandFlags flags) => ListRightPush(key, values, flags);
    IBatch IBatchListCommands.ListLength(ValkeyKey key) => ListLength(key);
    IBatch IBatchListCommands.ListRemove(ValkeyKey key, ValkeyValue value, long count) => ListRemove(key, value, count);
    IBatch IBatchListCommands.ListTrim(ValkeyKey key, long start, long stop) => ListTrim(key, start, stop);
    IBatch IBatchListCommands.ListRange(ValkeyKey key, long start, long stop) => ListRange(key, start, stop);
}
