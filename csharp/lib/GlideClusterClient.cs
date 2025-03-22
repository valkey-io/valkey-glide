// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Glide.Commands;

using static Glide.ConnectionConfiguration;

namespace Glide;

public sealed class GlideClusterClient : BaseClient, IGenericClusterCommands
{
    public GlideClusterClient(ClusterClientConfiguration config) : base(config) { }

    public async Task<object?> CustomCommand(GlideString[] args, Route? route = null)
        => await Command(RequestType.CustomCommand, args, resp => HandleServerResponse<object?>(resp, true), route);
}
