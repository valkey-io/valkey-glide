// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Internals;

namespace Valkey.Glide;

public abstract partial class BaseClient : IConnectionManagementCommands
{
    public async Task<TimeSpan> PingAsync(CommandFlags ignored = CommandFlags.None)
        => await Command(Request.PingAsync(ignored));

    public async Task<TimeSpan> PingAsync(ValkeyValue message, CommandFlags ignored = CommandFlags.None)
        => await Command(Request.PingAsync(message, ignored));

    public async Task<ValkeyValue> EchoAsync(ValkeyValue message, CommandFlags ignored = CommandFlags.None)
        => await Command(Request.EchoAsync(message, ignored));
}
