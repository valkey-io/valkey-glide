// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Internals;

namespace Valkey.Glide;

public abstract partial class BaseClient : IStringBaseCommands
{
    public async Task<string> StringSet(GlideString key, GlideString value)
        => await Command(Request.StringSet(key, value));

    public async Task<GlideString?> StringGet(GlideString key)
        => await Command(Request.StringGet(key));

    public async Task<GlideString?[]> StringGet(GlideString[] keys)
        => await Command(Request.StringGetAsync(keys));

    public async Task<string> StringSet(KeyValuePair<GlideString, GlideString>[] values)
    {
        GlideString[] keyValuePairs = Helpers.ConvertKeyValuePairsToArray(values);
        return await Command(Request.StringSetAsync(keyValuePairs));
    }

    public async Task<GlideString> StringGetRange(GlideString key, long start, long end)
        => await Command(Request.StringGetRange(key, start, end));

    public async Task<long> StringSetRange(GlideString key, long offset, GlideString value)
        => await Command(Request.StringSetRange(key, offset, value));

    public async Task<long> StringLength(GlideString key)
        => await Command(Request.StringLength(key));
}
