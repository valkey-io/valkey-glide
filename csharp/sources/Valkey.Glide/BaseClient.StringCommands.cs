// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Internals;

namespace Valkey.Glide;

public abstract partial class BaseClient : IStringBaseCommands
{
    public async Task<bool> StringSetAsync(ValkeyKey key, ValkeyValue value, CommandFlags flags = CommandFlags.None)
    {
        _ = await Command(Request.StringSet(key, value));
        return true;
    }

    public async Task<ValkeyValue> StringGetAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
        => await Command(Request.StringGet(key));

    public async Task<ValkeyValue[]> StringGetAsync(ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
    {
        GlideString[] glideKeys = [.. keys.Select(k => (GlideString)k)];
        GlideString?[] result = await Command(Request.StringGetMultiple(glideKeys));
        return [.. result.Select(r => r is null ? ValkeyValue.Null : (ValkeyValue)r)];
    }

    public async Task<bool> StringSetAsync(KeyValuePair<ValkeyKey, ValkeyValue>[] values, CommandFlags flags = CommandFlags.None)
    {
        KeyValuePair<GlideString, GlideString>[] glideValues = [..
            values.Select(kvp => new KeyValuePair<GlideString, GlideString>(kvp.Key, kvp.Value))
        ];
        GlideString[] keyValuePairs = Helpers.ConvertKeyValuePairsToArray(glideValues);
        _ = await Command(Request.StringSetMultiple(keyValuePairs));
        return true;
    }

    public async Task<ValkeyValue> StringGetRangeAsync(ValkeyKey key, long start, long end, CommandFlags flags = CommandFlags.None)
        => await Command(Request.StringGetRange(key, start, end));

    public async Task<ValkeyValue> StringSetRangeAsync(ValkeyKey key, long offset, ValkeyValue value, CommandFlags flags = CommandFlags.None)
    {
        long result = await Command(Request.StringSetRange(key, offset, value));
        return result;
    }

    public async Task<long> StringLengthAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
        => await Command(Request.StringLength(key));
}
