// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.Internals.FFI;

namespace Valkey.Glide.Internals;

internal partial class Request
{
    public static Cmd<GlideString, ValkeyValue> ListLeftPopAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return new(RequestType.LPop, [key], true, gs => gs is null ? ValkeyValue.Null : (ValkeyValue)gs);
    }

    public static Cmd<object[], ValkeyValue[]?> ListLeftPopAsync(ValkeyKey key, long count, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return new(RequestType.LPop, [key, count.ToString()], true, array =>
            array is null ? null : [.. array.Cast<GlideString>().Select(gs => (ValkeyValue)gs)]);
    }

    public static Cmd<long, long> ListLeftPushAsync(ValkeyKey key, ValkeyValue value, When when = When.Always, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        Utils.Requires<NotImplementedException>(when == When.Always, "LPUSHX has not been implemented yet.");
        return Simple<long>(RequestType.LPush, [key, value]);
    }

    public static Cmd<long, long> ListLeftPushAsync(ValkeyKey key, ValkeyValue[] values, When when = When.Always, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        Utils.Requires<NotImplementedException>(when == When.Always, "LPUSHX has not been implemented yet.");
        return Simple<long>(RequestType.LPush, [key.ToGlideString(), .. values.ToGlideStrings()]);
    }

    public static Cmd<GlideString, ValkeyValue> ListRightPopAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return new(RequestType.RPop, [key], true, gs => gs is null ? ValkeyValue.Null : (ValkeyValue)gs);
    }

    public static Cmd<object[], ValkeyValue[]?> ListRightPopAsync(ValkeyKey key, long count, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return new(RequestType.RPop, [key, count.ToString()], true, array =>
            array is null ? null : [.. array.Cast<GlideString>().Select(gs => (ValkeyValue)gs)]);
    }

    public static Cmd<long, long> ListRightPushAsync(ValkeyKey key, ValkeyValue value, When when = When.Always, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        Utils.Requires<NotImplementedException>(when == When.Always, "RPUSHX has not been implemented yet.");
        return Simple<long>(RequestType.RPush, [key, value]);
    }

    public static Cmd<long, long> ListRightPushAsync(ValkeyKey key, ValkeyValue[] values, When when = When.Always, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        Utils.Requires<NotImplementedException>(when == When.Always, "RPUSHX has not been implemented yet.");
        return Simple<long>(RequestType.RPush, [key.ToGlideString(), .. values.ToGlideStrings()]);
    }

    public static Cmd<long, long> ListLengthAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return Simple<long>(RequestType.LLen, [key]);
    }

    public static Cmd<long, long> ListRemoveAsync(ValkeyKey key, ValkeyValue value, long count = 0, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return Simple<long>(RequestType.LRem, [key, count.ToString(), value]);
    }

    public static Cmd<string, string> ListTrimAsync(ValkeyKey key, long start, long stop, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return Simple<string>(RequestType.LTrim, [key, start.ToString(), stop.ToString()]);
    }

    public static Cmd<object[], ValkeyValue[]> ListRangeAsync(ValkeyKey key, long start = 0, long stop = -1, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return new(RequestType.LRange, [key, start.ToString(), stop.ToString()], false, array =>
            [.. array.Cast<GlideString>().Select(gs => (ValkeyValue)gs)]);
    }
}
