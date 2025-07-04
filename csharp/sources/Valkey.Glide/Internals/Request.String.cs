// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.Internals.FFI;

namespace Valkey.Glide.Internals;

internal partial class Request
{
    public static Cmd<GlideString, GlideString> Get(GlideString key)
        => Simple<GlideString>(RequestType.Get, [key], true);

    public static Cmd<string, string> Set(GlideString key, GlideString value)
        => OK(RequestType.Set, [key, value]);

    public static Cmd<long, long> Strlen(GlideString key)
        => Simple<long>(RequestType.Strlen, [key]);
}
