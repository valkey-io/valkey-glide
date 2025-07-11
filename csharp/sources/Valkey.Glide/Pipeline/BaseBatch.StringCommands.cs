// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Internals;

namespace Valkey.Glide.Pipeline;

public abstract partial class BaseBatch<T> where T : BaseBatch<T>
{
    /// <inheritdoc cref="IBatchStringCommands.StringGetAsync(ValkeyKey)" />
    public T StringGetAsync(ValkeyKey key) => AddCmd(Request.StringGet(key));

    /// <inheritdoc cref="IBatchStringCommands.StringGetAsync(ValkeyKey[])" />
    public T StringGetAsync(ValkeyKey[] keys) => AddCmd(Request.StringGetMultiple(keys));

    /// <inheritdoc cref="IBatchStringCommands.StringSetAsync(ValkeyKey, ValkeyValue)" />
    public T StringSetAsync(ValkeyKey key, ValkeyValue value) => AddCmd(Request.StringSet(key, value));

    /// <inheritdoc cref="IBatchStringCommands.StringSetAsync(KeyValuePair{ValkeyKey, ValkeyValue}[])" />
    public T StringSetAsync(KeyValuePair<ValkeyKey, ValkeyValue>[] values) => AddCmd(Request.StringSetMultiple(values));

    /// <inheritdoc cref="IBatchStringCommands.StringGetRangeAsync(ValkeyKey, long, long)" />
    public T StringGetRangeAsync(ValkeyKey key, long start, long end) => AddCmd(Request.StringGetRange(key, start, end));

    /// <inheritdoc cref="IBatchStringCommands.StringSetRangeAsync(ValkeyKey, long, ValkeyValue)" />
    public T StringSetRangeAsync(ValkeyKey key, long offset, ValkeyValue value) => AddCmd(Request.StringSetRange(key, offset, value));

    /// <inheritdoc cref="IBatchStringCommands.StringLengthAsync(ValkeyKey)" />
    public T StringLengthAsync(ValkeyKey key) => AddCmd(Request.StringLength(key));

    IBatch IBatchStringCommands.StringGetAsync(ValkeyKey key) => StringGetAsync(key);
    IBatch IBatchStringCommands.StringGetAsync(ValkeyKey[] keys) => StringGetAsync(keys);
    IBatch IBatchStringCommands.StringSetAsync(ValkeyKey key, ValkeyValue value) => StringSetAsync(key, value);
    IBatch IBatchStringCommands.StringSetAsync(KeyValuePair<ValkeyKey, ValkeyValue>[] values) => StringSetAsync(values);
    IBatch IBatchStringCommands.StringGetRangeAsync(ValkeyKey key, long start, long end) => StringGetRangeAsync(key, start, end);
    IBatch IBatchStringCommands.StringSetRangeAsync(ValkeyKey key, long offset, ValkeyValue value) => StringSetRangeAsync(key, offset, value);
    IBatch IBatchStringCommands.StringLengthAsync(ValkeyKey key) => StringLengthAsync(key);
}
