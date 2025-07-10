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
        return new(RequestType.Set, args, false, response => response == "OK");
    }

    public static Cmd<object[], GlideString?[]> StringGetMultiple(GlideString[] keys)
        => new(RequestType.MGet, keys, false, array =>
            [.. array.Select(item => item as GlideString)]);

    public static Cmd<string, string> StringSetMultiple(GlideString[] keyValuePairs)
        => OK(RequestType.MSet, keyValuePairs);

    public static Cmd<long, long> StringSetRange(GlideString key, long offset, GlideString value)
        => Simple<long>(RequestType.SetRange, [key, offset.ToGlideString(), value]);

    public static Cmd<GlideString, GlideString> StringGetRange(GlideString key, long start, long end)
        => Simple<GlideString>(RequestType.GetRange, [key, start.ToGlideString(), end.ToGlideString()], true);

    public static Cmd<long, long> StringLength(GlideString key)
        => Simple<long>(RequestType.Strlen, [key]);
}
