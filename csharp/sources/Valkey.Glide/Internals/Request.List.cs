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

    public static Cmd<GlideString, ValkeyValue> ListRightPopAsync(ValkeyKey key)
        => new(RequestType.RPop, [key], true, gs => (ValkeyValue)gs);

    public static Cmd<object[], ValkeyValue[]> ListRightPopAsync(ValkeyKey key, long count)
        => new(RequestType.RPop, [key, count.ToString()], true, array =>
            [.. array.Cast<GlideString>().Select(gs => (ValkeyValue)gs)]);

    public static Cmd<long, long> ListRightPushAsync(ValkeyKey key, ValkeyValue[] values)
        => Simple<long>(RequestType.RPush, [key.ToGlideString(), .. values.ToGlideStrings()]);

    public static Cmd<long, long> ListLengthAsync(ValkeyKey key)
        => Simple<long>(RequestType.LLen, [key]);

    public static Cmd<long, long> ListRemoveAsync(ValkeyKey key, ValkeyValue value, long count)
        => Simple<long>(RequestType.LRem, [key, count.ToString(), value]);

    public static Cmd<string, string> ListTrimAsync(ValkeyKey key, long start, long stop)
        => Simple<string>(RequestType.LTrim, [key, start.ToString(), stop.ToString()]);

    public static Cmd<object[], ValkeyValue[]> ListRangeAsync(ValkeyKey key, long start, long stop)
        => new(RequestType.LRange, [key, start.ToString(), stop.ToString()], false, array =>
            [.. array.Cast<GlideString>().Select(gs => (ValkeyValue)gs)]);
}
