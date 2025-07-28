// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.Internals.FFI;

namespace Valkey.Glide.Internals;

internal partial class Request
{
    public static Cmd<GlideString, ValkeyValue> ListLeftPopAsync(ValkeyKey key)
        => new(RequestType.LPop, [key], true, gs => gs is null ? ValkeyValue.Null : (ValkeyValue)gs);

    public static Cmd<object[], ValkeyValue[]?> ListLeftPopAsync(ValkeyKey key, long count)
        => new(RequestType.LPop, [key, count.ToGlideString()], true, array =>
            array is null ? null : [.. array.Cast<GlideString>().Select(gs => (ValkeyValue)gs)]);

    public static Cmd<long, long> ListLeftPushAsync(ValkeyKey key, ValkeyValue value, When when = When.Always)
    {
        Utils.Requires<NotImplementedException>(when == When.Always, "LPUSHX has not been implemented yet.");
        return Simple<long>(RequestType.LPush, [key, value]);
    }

    public static Cmd<long, long> ListLeftPushAsync(ValkeyKey key, ValkeyValue[] values, When when = When.Always)
    {
        Utils.Requires<NotImplementedException>(when == When.Always, "LPUSHX has not been implemented yet.");
        return Simple<long>(RequestType.LPush, [key.ToGlideString(), .. values.ToGlideStrings()]);
    }

    public static Cmd<long, long> ListLeftPushAsync(ValkeyKey key, ValkeyValue[] values)
        => Simple<long>(RequestType.LPush, [key.ToGlideString(), .. values.ToGlideStrings()]);

    public static Cmd<GlideString, ValkeyValue> ListRightPopAsync(ValkeyKey key)
        => new(RequestType.RPop, [key], true, gs => gs is null ? ValkeyValue.Null : (ValkeyValue)gs);

    public static Cmd<object[], ValkeyValue[]?> ListRightPopAsync(ValkeyKey key, long count)
        => new(RequestType.RPop, [key, count.ToGlideString()], true, array =>
            array is null ? null : [.. array.Cast<GlideString>().Select(gs => (ValkeyValue)gs)]);

    public static Cmd<long, long> ListRightPushAsync(ValkeyKey key, ValkeyValue value, When when = When.Always)
    {
        Utils.Requires<NotImplementedException>(when == When.Always, "RPUSHX has not been implemented yet.");
        return Simple<long>(RequestType.RPush, [key, value]);
    }

    public static Cmd<long, long> ListRightPushAsync(ValkeyKey key, ValkeyValue[] values, When when = When.Always)
    {
        Utils.Requires<NotImplementedException>(when == When.Always, "RPUSHX has not been implemented yet.");
        return Simple<long>(RequestType.RPush, [key.ToGlideString(), .. values.ToGlideStrings()]);
    }

    public static Cmd<long, long> ListRightPushAsync(ValkeyKey key, ValkeyValue[] values)
        => Simple<long>(RequestType.RPush, [key.ToGlideString(), .. values.ToGlideStrings()]);

    public static Cmd<long, long> ListLengthAsync(ValkeyKey key)
        => Simple<long>(RequestType.LLen, [key]);

    public static Cmd<long, long> ListRemoveAsync(ValkeyKey key, ValkeyValue value, long count = 0)
        => Simple<long>(RequestType.LRem, [key, count.ToGlideString(), value]);

    public static Cmd<string, string> ListTrimAsync(ValkeyKey key, long start, long stop)
        => Simple<string>(RequestType.LTrim, [key, start.ToGlideString(), stop.ToGlideString()]);

    public static Cmd<object[], ValkeyValue[]> ListRangeAsync(ValkeyKey key, long start = 0, long stop = -1)
        => new(RequestType.LRange, [key, start.ToGlideString(), stop.ToGlideString()], false, array =>
            [.. array.Cast<GlideString>().Select(gs => (ValkeyValue)gs)]);
}
