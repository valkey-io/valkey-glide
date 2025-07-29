// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Internals;

namespace Valkey.Glide;

public abstract partial class BaseClient : IHashCommands
{
    public async Task<ValkeyValue> HashGetAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.HashGetAsync(key, hashField));
    }

    public async Task<ValkeyValue[]> HashGetAsync(ValkeyKey key, ValkeyValue[] hashFields, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.HashGetAsync(key, hashFields));
    }

    public async Task<HashEntry[]> HashGetAllAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.HashGetAllAsync(key));
    }

    public async Task HashSetAsync(ValkeyKey key, HashEntry[] hashFields, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        _ = await Command(Request.HashSetAsync(key, hashFields));
    }

    public async Task<bool> HashSetAsync(ValkeyKey key, ValkeyValue hashField, ValkeyValue value, When when = When.Always, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.HashSetAsync(key, hashField, value, when));
    }

    public async Task<bool> HashDeleteAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.HashDeleteAsync(key, hashField));
    }

    public async Task<long> HashDeleteAsync(ValkeyKey key, ValkeyValue[] hashFields, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.HashDeleteAsync(key, hashFields));
    }

    public async Task<bool> HashExistsAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.HashExistsAsync(key, hashField));
    }

    public async Task<long> HashLengthAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.HashLengthAsync(key));
    }

    public async Task<long> HashStringLengthAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.HashStringLengthAsync(key, hashField));
    }

    public async Task<ValkeyValue[]> HashValuesAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.HashValuesAsync(key));
    }

    public async Task<ValkeyValue> HashRandomFieldAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.HashRandomFieldAsync(key));
    }

    public async Task<ValkeyValue[]> HashRandomFieldsAsync(ValkeyKey key, long count, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.HashRandomFieldsAsync(key, count));
    }

    public async Task<HashEntry[]> HashRandomFieldsWithValuesAsync(ValkeyKey key, long count, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.HashRandomFieldsWithValuesAsync(key, count));
    }
}
