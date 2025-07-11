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

    IBatch IBatchStringCommands.StringGet(ValkeyKey key) => StringGetAsync(key);
    IBatch IBatchStringCommands.StringGet(ValkeyKey[] keys) => StringGetAsync(keys);
    IBatch IBatchStringCommands.StringSet(ValkeyKey key, ValkeyValue value) => StringSetAsync(key, value);
    IBatch IBatchStringCommands.StringSet(KeyValuePair<ValkeyKey, ValkeyValue>[] values) => StringSetAsync(values);
    IBatch IBatchStringCommands.StringGetRange(ValkeyKey key, long start, long end) => StringGetRangeAsync(key, start, end);
    IBatch IBatchStringCommands.StringSetRange(ValkeyKey key, long offset, ValkeyValue value) => StringSetRangeAsync(key, offset, value);
    IBatch IBatchStringCommands.StringLength(ValkeyKey key) => StringLengthAsync(key);
}
