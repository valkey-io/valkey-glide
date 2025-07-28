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

    /// <inheritdoc cref="IBatchStringCommands.StringGetDelete(ValkeyKey)" />
    public T StringGetDeleteAsync(ValkeyKey key) => AddCmd(Request.StringGetDelete(key));

    /// <inheritdoc cref="IBatchStringCommands.StringGetSetExpiry(ValkeyKey, TimeSpan?)" />
    public T StringGetSetExpiryAsync(ValkeyKey key, TimeSpan? expiry) => AddCmd(Request.StringGetSetExpiry(key, expiry));

    /// <inheritdoc cref="IBatchStringCommands.StringGetSetExpiry(ValkeyKey, DateTime)" />
    public T StringGetSetExpiryAsync(ValkeyKey key, DateTime expiry) => AddCmd(Request.StringGetSetExpiry(key, expiry));

    /// <inheritdoc cref="IBatchStringCommands.StringLongestCommonSubsequence(ValkeyKey, ValkeyKey)" />
    public T StringLongestCommonSubsequenceAsync(ValkeyKey first, ValkeyKey second) => AddCmd(Request.StringLongestCommonSubsequence(first, second));

    /// <inheritdoc cref="IBatchStringCommands.StringLongestCommonSubsequenceLength(ValkeyKey, ValkeyKey)" />
    public T StringLongestCommonSubsequenceLengthAsync(ValkeyKey first, ValkeyKey second) => AddCmd(Request.StringLongestCommonSubsequenceLength(first, second));

    /// <inheritdoc cref="IBatchStringCommands.StringLongestCommonSubsequenceWithMatches(ValkeyKey, ValkeyKey, long)" />
    public T StringLongestCommonSubsequenceWithMatchesAsync(ValkeyKey first, ValkeyKey second, long minLength = 0) => AddCmd(Request.StringLongestCommonSubsequenceWithMatches(first, second, minLength));

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
    IBatch IBatchStringCommands.StringGetDelete(ValkeyKey key) => StringGetDeleteAsync(key);
    IBatch IBatchStringCommands.StringGetSetExpiry(ValkeyKey key, TimeSpan? expiry) => StringGetSetExpiryAsync(key, expiry);
    IBatch IBatchStringCommands.StringGetSetExpiry(ValkeyKey key, DateTime expiry) => StringGetSetExpiryAsync(key, expiry);
    IBatch IBatchStringCommands.StringLongestCommonSubsequence(ValkeyKey first, ValkeyKey second) => StringLongestCommonSubsequenceAsync(first, second);
    IBatch IBatchStringCommands.StringLongestCommonSubsequenceLength(ValkeyKey first, ValkeyKey second) => StringLongestCommonSubsequenceLengthAsync(first, second);
    IBatch IBatchStringCommands.StringLongestCommonSubsequenceWithMatches(ValkeyKey first, ValkeyKey second, long minLength) => StringLongestCommonSubsequenceWithMatchesAsync(first, second, minLength);
}
