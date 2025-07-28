// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Internals;

namespace Valkey.Glide;

public abstract partial class BaseClient : IListCommands
{
    public async Task<ValkeyValue> ListLeftPopAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.ListLeftPopAsync(key));
    }

    public async Task<ValkeyValue[]?> ListLeftPopAsync(ValkeyKey key, long count, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.ListLeftPopAsync(key, count));
    }

    public async Task<long> ListLeftPushAsync(ValkeyKey key, ValkeyValue value, When when = When.Always, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.ListLeftPushAsync(key, value, when));
    }

    public async Task<long> ListLeftPushAsync(ValkeyKey key, ValkeyValue[] values, When when = When.Always, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.ListLeftPushAsync(key, values, when));
    }

    public async Task<long> ListLeftPushAsync(ValkeyKey key, ValkeyValue[] values, CommandFlags flags)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.ListLeftPushAsync(key, values));
    }

    public async Task<ValkeyValue> ListRightPopAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.ListRightPopAsync(key));
    }

    public async Task<ValkeyValue[]?> ListRightPopAsync(ValkeyKey key, long count, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.ListRightPopAsync(key, count));
    }

    public async Task<long> ListRightPushAsync(ValkeyKey key, ValkeyValue value, When when = When.Always, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.ListRightPushAsync(key, value, when));
    }

    public async Task<long> ListRightPushAsync(ValkeyKey key, ValkeyValue[] values, When when = When.Always, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.ListRightPushAsync(key, values, when));
    }

    public async Task<long> ListRightPushAsync(ValkeyKey key, ValkeyValue[] values, CommandFlags flags)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.ListRightPushAsync(key, values));
    }

    public async Task<long> ListLengthAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.ListLengthAsync(key));
    }

    public async Task<long> ListRemoveAsync(ValkeyKey key, ValkeyValue value, long count = 0, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.ListRemoveAsync(key, value, count));
    }

    public async Task ListTrimAsync(ValkeyKey key, long start, long stop, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        _ = await Command(Request.ListTrimAsync(key, start, stop));
    }

    public async Task<ValkeyValue[]> ListRangeAsync(ValkeyKey key, long start = 0, long stop = -1, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.ListRangeAsync(key, start, stop));
    }
}
