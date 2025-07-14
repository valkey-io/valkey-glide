// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.Internals.FFI;

namespace Valkey.Glide.Internals;

internal partial class Request
{
    public static Cmd<GlideString, GlideString> StringGet(GlideString key)
        => Simple<GlideString>(RequestType.Get, [key], true);

    public static Cmd<string, bool> StringSet(ValkeyKey key, ValkeyValue value)
    {
        GlideString[] args = [key.ToGlideString(), value.ToGlideString()];
        // TODO: for lambda function arguments of "response => response == "OK"", replace with OkToBool
        return new(RequestType.Set, args, false, response => response == "OK");
    }

    public static Cmd<object[], ValkeyValue[]> StringGetMultiple(ValkeyKey[] keys)
    {
        GlideString[] glideKeys = [.. keys.Select(k => k.ToGlideString())];
        return new(RequestType.MGet, glideKeys, false, array =>
            [.. array.Select(item => item is null ? ValkeyValue.Null : (ValkeyValue)(GlideString)item)]);
    }

    public static Cmd<string, bool> StringSetMultiple(KeyValuePair<ValkeyKey, ValkeyValue>[] values)
    {
        KeyValuePair<GlideString, GlideString>[] glideValues = [..
            values.Select(kvp => new KeyValuePair<GlideString, GlideString>(kvp.Key.ToGlideString(), kvp.Value.ToGlideString()))
        ];
        GlideString[] keyValuePairs = Helpers.ConvertKeyValuePairsToArray(glideValues);
        return new(RequestType.MSet, keyValuePairs, false, response => response == "OK");
    }

    public static Cmd<long, long> StringSetRange(GlideString key, long offset, GlideString value)
        => Simple<long>(RequestType.SetRange, [key, offset.ToGlideString(), value]);

    public static Cmd<GlideString, GlideString> StringGetRange(GlideString key, long start, long end)
        => Simple<GlideString>(RequestType.GetRange, [key, start.ToGlideString(), end.ToGlideString()], true);

    public static Cmd<long, long> StringLength(GlideString key)
        => Simple<long>(RequestType.Strlen, [key]);
}
