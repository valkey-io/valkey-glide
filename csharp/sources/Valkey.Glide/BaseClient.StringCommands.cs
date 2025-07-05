// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Internals;

namespace Valkey.Glide;

public abstract partial class BaseClient : IStringBaseCommands
{
    public async Task<string> StringSet(GlideString key, GlideString value)
        => await Command(Request.Set(key, value));

    public async Task<GlideString?> StringGet(GlideString key)
        => await Command(Request.Get(key));

    public async Task<GlideString?[]> StringGet(GlideString[] keys)
        => keys.Length == 0
            ? throw new ArgumentException("Keys array cannot be empty", nameof(keys))
            : await Command(Request.MGet(keys));

    public async Task<string> StringSet(KeyValuePair<GlideString, GlideString>[] values)
    {
        if (values.Length == 0)
        {
            throw new ArgumentException("Values array cannot be empty", nameof(values));
        }

        GlideString[] keyValuePairs = Helpers.ConvertKeyValuePairsToArray(values);
        return await Command(Request.MSet(keyValuePairs));
    }

    public async Task<GlideString> StringGetRange(GlideString key, long start, long end)
        => await Command(Request.GetRange(key, start, end));

    public async Task<long> StringSetRange(GlideString key, long offset, GlideString value)
        => await Command(Request.SetRange(key, offset, value));

    public async Task<long> StringLength(GlideString key)
        => await Command(Request.Strlen(key));
}
