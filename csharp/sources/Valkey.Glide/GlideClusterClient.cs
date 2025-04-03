// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.InterOp.Native;
using Valkey.Glide.InterOp.Routing;

namespace Valkey.Glide;

public sealed class GlideClusterClient(ConnectionConfigBuilder config) : BaseClient(config.WithClusterMode(true)), IGenericClusterCommands
{
    public async Task<object?> CustomCommand(GlideString[] args, IRoutingInfo? route = null)
        => await Command<object?>(ERequestType.CustomCommand, args, resp => HandleServerResponse<object?>(resp, true), route);
}
