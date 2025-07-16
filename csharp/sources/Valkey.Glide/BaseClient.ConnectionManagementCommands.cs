// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Internals;

namespace Valkey.Glide;

public abstract partial class BaseClient : IConnectionManagementCommands
{
    public async Task<TimeSpan> PingAsync(CommandFlags flags = CommandFlags.None)
        => await Command(Request.Ping(flags));

    public async Task<TimeSpan> PingAsync(ValkeyValue message, CommandFlags flags = CommandFlags.None)
        => await Command(Request.Ping(message, flags));

    public async Task<TimeSpan> PingAsync(Route route, CommandFlags flags = CommandFlags.None)
        => await Command(Request.Ping(flags), route);

    public async Task<TimeSpan> PingAsync(ValkeyValue message, Route route, CommandFlags flags = CommandFlags.None)
        => await Command(Request.Ping(message, flags), route);
}
