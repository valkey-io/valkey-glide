// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Internals;

namespace Valkey.Glide;

public abstract partial class BaseClient : IListBaseCommands
{
    public async Task<ValkeyValue> ListLeftPopAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
        => await Command(Request.ListLeftPopAsync(key));

    public async Task<ValkeyValue[]> ListLeftPopAsync(ValkeyKey key, long count, CommandFlags flags = CommandFlags.None)
        => await Command(Request.ListLeftPopAsync(key, count));

    public async Task<long> ListLeftPushAsync(ValkeyKey key, ValkeyValue[] values, When when = When.Always, CommandFlags flags = CommandFlags.None)
        => await Command(Request.ListLeftPushAsync(key, values));
}
