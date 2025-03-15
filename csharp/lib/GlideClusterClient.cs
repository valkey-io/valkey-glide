// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Glide.Commands;

using static Glide.ConnectionConfiguration;

namespace Glide;

public sealed class GlideClusterClient(ClusterClientConfiguration config) : BaseClient(config), IGenericClusterCommands
{
    public async Task<object?> CustomCommand(string[] args, Route? route = null)
        => await Command<object?>(args, RequestType.CustomCommand, route);
}
