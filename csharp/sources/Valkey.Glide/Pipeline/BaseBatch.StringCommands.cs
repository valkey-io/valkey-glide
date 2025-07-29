// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Internals;

namespace Valkey.Glide.Pipeline;

public abstract partial class BaseBatch<T> where T : BaseBatch<T>
{
    /// <inheritdoc cref="IBatchStringCommands.StringGet(ValkeyKey)" />
    public T StringGetAsync(ValkeyKey key) => AddCmd(Request.StringGet(key));

    /// <inheritdoc cref="IBatchStringCommands.StringGet(ValkeyKey[])" />
    public T StringGetAsync(ValkeyKey[] keys) => AddCmd(Request.StringGetMultiple(keys));

    /// <inheritdoc cref="IBatchStringCommands.StringSet(ValkeyKey, ValkeyValue)" />
    public T StringSetAsync(ValkeyKey key, ValkeyValue value) => AddCmd(Request.StringSet(key, value));

    /// <inheritdoc cref="IBatchStringCommands.StringSet(KeyValuePair{ValkeyKey, ValkeyValue}[])" />
    public T StringSetAsync(KeyValuePair<ValkeyKey, ValkeyValue>[] values) => AddCmd(Request.StringSetMultiple(values));

    /// <inheritdoc cref="IBatchStringCommands.StringGetRange(ValkeyKey, long, long)" />
    public T StringGetRangeAsync(ValkeyKey key, long start, long end) => AddCmd(Request.StringGetRange(key, start, end));

    /// <inheritdoc cref="IBatchStringCommands.StringSetRange(ValkeyKey, long, ValkeyValue)" />
    public T StringSetRangeAsync(ValkeyKey key, long offset, ValkeyValue value) => AddCmd(Request.StringSetRange(key, offset, value));

    /// <inheritdoc cref="IBatchStringCommands.StringLength(ValkeyKey)" />
    public T StringLengthAsync(ValkeyKey key) => AddCmd(Request.StringLength(key));

    /// <inheritdoc cref="IBatchStringCommands.StringAppend(ValkeyKey, ValkeyValue)" />
    public T StringAppendAsync(ValkeyKey key, ValkeyValue value) => AddCmd(Request.StringAppend(key, value));

    /// <inheritdoc cref="IBatchStringCommands.StringDecrement(ValkeyKey)" />
    public T StringDecrementAsync(ValkeyKey key) => AddCmd(Request.StringDecr(key));

    /// <inheritdoc cref="IBatchStringCommands.StringDecrement(ValkeyKey, long)" />
    public T StringDecrementAsync(ValkeyKey key, long decrement) => AddCmd(Request.StringDecrBy(key, decrement));

    /// <inheritdoc cref="IBatchStringCommands.StringIncrement(ValkeyKey)" />
    public T StringIncrementAsync(ValkeyKey key) => AddCmd(Request.StringIncr(key));

    /// <inheritdoc cref="IBatchStringCommands.StringIncrement(ValkeyKey, long)" />
    public T StringIncrementAsync(ValkeyKey key, long increment) => AddCmd(Request.StringIncrBy(key, increment));

    /// <inheritdoc cref="IBatchStringCommands.StringIncrement(ValkeyKey, double)" />
    public T StringIncrementAsync(ValkeyKey key, double increment) => AddCmd(Request.StringIncrByFloat(key, increment));

    IBatch IBatchStringCommands.StringGet(ValkeyKey key) => StringGetAsync(key);
    IBatch IBatchStringCommands.StringGet(ValkeyKey[] keys) => StringGetAsync(keys);
    IBatch IBatchStringCommands.StringSet(ValkeyKey key, ValkeyValue value) => StringSetAsync(key, value);
    IBatch IBatchStringCommands.StringSet(KeyValuePair<ValkeyKey, ValkeyValue>[] values) => StringSetAsync(values);
    IBatch IBatchStringCommands.StringGetRange(ValkeyKey key, long start, long end) => StringGetRangeAsync(key, start, end);
    IBatch IBatchStringCommands.StringSetRange(ValkeyKey key, long offset, ValkeyValue value) => StringSetRangeAsync(key, offset, value);
    IBatch IBatchStringCommands.StringLength(ValkeyKey key) => StringLengthAsync(key);
    IBatch IBatchStringCommands.StringAppend(ValkeyKey key, ValkeyValue value) => StringAppendAsync(key, value);
    IBatch IBatchStringCommands.StringDecrement(ValkeyKey key) => StringDecrementAsync(key);
    IBatch IBatchStringCommands.StringDecrement(ValkeyKey key, long decrement) => StringDecrementAsync(key, decrement);
    IBatch IBatchStringCommands.StringIncrement(ValkeyKey key) => StringIncrementAsync(key);
    IBatch IBatchStringCommands.StringIncrement(ValkeyKey key, long increment) => StringIncrementAsync(key, increment);
    IBatch IBatchStringCommands.StringIncrement(ValkeyKey key, double increment) => StringIncrementAsync(key, increment);
}
