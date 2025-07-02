// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Internals;

namespace Valkey.Glide;

public abstract partial class BaseClient : IStringBaseCommands, ISetCommands
{
    public async Task<string> Set(GlideString key, GlideString value)
        => await Command(Request.Set(key, value));

    public async Task<GlideString?> Get(GlideString key)
        => await Command(Request.Get(key));

    /**
    =================================
    SET COMMANDS
    =================================
    */
    public async Task<bool> SetAdd(ValkeyKey key, ValkeyValue value, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SetAdd(key, value));

    public async Task<long> SetAdd(ValkeyKey key, ValkeyValue[] values, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SetAdd(key, values));

    public async Task<bool> SetRemove(ValkeyKey key, ValkeyValue value, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SetRemove(key, value));

    public async Task<long> SetRemove(ValkeyKey key, ValkeyValue[] values, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SetRemove(key, values));

    public async Task<ValkeyValue[]> SetMembers(ValkeyKey key, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SetMembers(key));

    public async Task<long> SetLength(ValkeyKey key, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SetLength(key));

    public async Task<long> SetIntersectionLength(ValkeyKey[] keys, long limit = 0, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SetIntersectionLength(keys, limit));

    public async Task<ValkeyValue> SetPop(ValkeyKey key, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SetPop(key));

    public async Task<ValkeyValue[]> SetPop(ValkeyKey key, long count, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SetPop(key, count));

    public async Task<ValkeyValue[]> SetCombine(SetOperation operation, ValkeyKey first, ValkeyKey second, CommandFlags flags = CommandFlags.None)
        => await SetCombine(operation, [first, second], flags);

    public async Task<ValkeyValue[]> SetCombine(SetOperation operation, ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SetCombine(operation, keys));

    public async Task<long> SetCombineAndStore(SetOperation operation, ValkeyKey destination, ValkeyKey first, ValkeyKey second, CommandFlags flags = CommandFlags.None)
        => await SetCombineAndStore(operation, destination, [first, second], flags);

    public async Task<long> SetCombineAndStore(SetOperation operation, ValkeyKey destination, ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SetCombineAndStore(operation, destination, keys));
}
