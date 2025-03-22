// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Glide.Commands;

using static Glide.ConnectionConfiguration;

namespace Glide;

public sealed class GlideClusterClient : BaseClient, IGenericClusterCommands
{
    private GlideClusterClient() { }

    public static async Task<GlideClusterClient> CreateClient(ClusterClientConfiguration config)
        => await CreateClient(config, () => new GlideClusterClient());

    public async Task<object?> CustomCommand(GlideString[] args, Route? route = null)
        => await Command<object?>(RequestType.CustomCommand, args, resp => HandleServerResponse<object?>(resp, true), route);
}
