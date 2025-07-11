// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.Internals.FFI;

namespace Valkey.Glide.Internals;

internal partial class Request
{
    public static Cmd<GlideString, ValkeyValue> ListLeftPopAsync(ValkeyKey key)
        => new(RequestType.LPop, [key], true, gs => (ValkeyValue)gs);

    public static Cmd<object[], ValkeyValue[]> ListLeftPopAsync(ValkeyKey key, long count)
        => new(RequestType.LPop, [key, count.ToString()], true, array =>
            [.. array.Cast<GlideString>().Select(gs => (ValkeyValue)gs)]);

    public static Cmd<long, long> ListLeftPushAsync(ValkeyKey key, ValkeyValue[] values)
        => Simple<long>(RequestType.LPush, [key.ToGlideString(), .. values.ToGlideStrings()]);
}
