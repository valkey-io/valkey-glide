// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Internals;

using static Valkey.Glide.ConnectionConfiguration;

namespace Valkey.Glide;

public sealed class GlideClusterClient(ClusterClientConfiguration config) : BaseClient(config), IGenericClusterCommands
{
    public async Task<object?> CustomCommand(GlideString[] args, Route? route = null)
        => await Command(FFI.RequestType.CustomCommand, args, resp => HandleServerResponse<object?>(resp, true), route);
}
