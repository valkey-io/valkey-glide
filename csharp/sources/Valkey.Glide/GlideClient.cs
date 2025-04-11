// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Commands.Options;

using static Valkey.Glide.ConnectionConfiguration;

namespace Valkey.Glide;

public sealed class GlideClient(StandaloneClientConfiguration config) : BaseClient(config), IConnectionManagementCommands, IGenericCommands, IServerManagementCommands
{
    public async Task<object?> CustomCommand(GlideString[] args)
        => await Command(RequestType.CustomCommand, args, resp => HandleServerResponse<object?>(resp, true));

    public async Task<string> Info() => await Info([]);

    public async Task<string> Info(InfoOptions.Section[] sections)
        => await Command(RequestType.Info, sections.ToGlideStrings(), resp => HandleServerResponse<GlideString, string>(resp, false, gs => gs.ToString()));
}
