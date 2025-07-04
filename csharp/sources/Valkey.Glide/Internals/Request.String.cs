// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.Internals.FFI;

namespace Valkey.Glide.Internals;

internal partial class Request
{
    public static Cmd<GlideString, GlideString> Get(GlideString key)
        => Simple<GlideString>(RequestType.Get, [key], true);

    public static Cmd<string, string> Set(GlideString key, GlideString value)
        => OK(RequestType.Set, [key, value]);

    public static Cmd<object[], GlideString?[]> MGet(GlideString[] keys)
        => new(RequestType.MGet, keys, false, array =>
            [.. array.Select(item => item as GlideString)]);

    public static Cmd<string, string> MSet(GlideString[] keyValuePairs)
        => OK(RequestType.MSet, keyValuePairs);

    public static Cmd<long, long> SetRange(GlideString key, long offset, GlideString value)
        => Simple<long>(RequestType.SetRange, [key, offset.ToGlideString(), value]);

    public static Cmd<GlideString, GlideString> GetRange(GlideString key, long start, long end)
        => Simple<GlideString>(RequestType.GetRange, [key, start.ToGlideString(), end.ToGlideString()], true);

    public static Cmd<long, long> Strlen(GlideString key)
        => Simple<long>(RequestType.Strlen, [key]);
}
