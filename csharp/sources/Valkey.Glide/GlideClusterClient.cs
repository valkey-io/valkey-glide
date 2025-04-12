// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Commands.Options;

using static Valkey.Glide.ConnectionConfiguration;

namespace Valkey.Glide;

public sealed class GlideClusterClient(ClusterClientConfiguration config) : BaseClient(config), IGenericClusterCommands, IServerManagementClusterCommands
{
    public async Task<ClusterValue<object?>> CustomCommand(GlideString[] args)
        => await Command(RequestType.CustomCommand, args, resp => HandleCustomCommandClusterResponse(resp));

    public async Task<ClusterValue<object?>> CustomCommand(GlideString[] args, Route route)
        => await Command(RequestType.CustomCommand, args, resp => HandleCustomCommandClusterResponse(resp, route), route);

    public async Task<Dictionary<string, string>> Info() => await Info([]);

    public async Task<Dictionary<string, string>> Info(InfoOptions.Section[] sections)
        => await Command(RequestType.Info, sections.ToGlideStrings(), resp => HandleMultiNodeResponse<GlideString, string>(resp, gs => gs.ToString()));

    public async Task<ClusterValue<string>> Info(Route route) => await Info([], route);

    public async Task<ClusterValue<string>> Info(InfoOptions.Section[] sections, Route route)
        => await Command(RequestType.Info, sections.ToGlideStrings(), resp => HandleClusterValueResponse<GlideString, string>(resp, false, route, gs => gs.ToString()), route);
}
