// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Internals;

namespace Valkey.Glide;

public abstract partial class BaseClient : IListCommands
{
    public async Task<ValkeyValue> ListLeftPopAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
        => await Command(Request.ListLeftPopAsync(key, flags));

    public async Task<ValkeyValue[]?> ListLeftPopAsync(ValkeyKey key, long count, CommandFlags flags = CommandFlags.None)
        => await Command(Request.ListLeftPopAsync(key, count, flags));

    public async Task<long> ListLeftPushAsync(ValkeyKey key, ValkeyValue value, When when = When.Always, CommandFlags flags = CommandFlags.None)
        => await Command(Request.ListLeftPushAsync(key, value, when, flags));

    public async Task<long> ListLeftPushAsync(ValkeyKey key, ValkeyValue[] values, When when = When.Always, CommandFlags flags = CommandFlags.None)
        => await Command(Request.ListLeftPushAsync(key, values, when, flags));

    public async Task<long> ListLeftPushAsync(ValkeyKey key, ValkeyValue[] values, CommandFlags flags)
        => await Command(Request.ListLeftPushAsync(key, values, When.Always, flags));

    public async Task<ValkeyValue> ListRightPopAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
        => await Command(Request.ListRightPopAsync(key, flags));

    public async Task<ValkeyValue[]?> ListRightPopAsync(ValkeyKey key, long count, CommandFlags flags = CommandFlags.None)
        => await Command(Request.ListRightPopAsync(key, count, flags));

    public async Task<long> ListRightPushAsync(ValkeyKey key, ValkeyValue value, When when = When.Always, CommandFlags flags = CommandFlags.None)
        => await Command(Request.ListRightPushAsync(key, value, when, flags));

    public async Task<long> ListRightPushAsync(ValkeyKey key, ValkeyValue[] values, When when = When.Always, CommandFlags flags = CommandFlags.None)
        => await Command(Request.ListRightPushAsync(key, values, when, flags));

    public async Task<long> ListRightPushAsync(ValkeyKey key, ValkeyValue[] values, CommandFlags flags)
        => await Command(Request.ListRightPushAsync(key, values, When.Always, flags));

    public async Task<long> ListLengthAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
        => await Command(Request.ListLengthAsync(key, flags));

    public async Task<long> ListRemoveAsync(ValkeyKey key, ValkeyValue value, long count = 0, CommandFlags flags = CommandFlags.None)
        => await Command(Request.ListRemoveAsync(key, value, count, flags));

    public async Task ListTrimAsync(ValkeyKey key, long start, long stop, CommandFlags flags = CommandFlags.None)
        => await Command(Request.ListTrimAsync(key, start, stop, flags));

    public async Task<ValkeyValue[]> ListRangeAsync(ValkeyKey key, long start = 0, long stop = -1, CommandFlags flags = CommandFlags.None)
        => await Command(Request.ListRangeAsync(key, start, stop, flags));
}
