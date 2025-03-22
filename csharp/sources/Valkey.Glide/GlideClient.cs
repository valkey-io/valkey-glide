// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Glide.Commands;

using static Glide.ConnectionConfiguration;

namespace Glide;

public sealed class GlideClient(StandaloneClientConfiguration config) : BaseClient(config), IConnectionManagementCommands, IGenericCommands
{
    public async Task<object?> CustomCommand(GlideString[] args)
        => await Command(RequestType.CustomCommand, args, resp => HandleServerResponse<object?>(resp, true));
}
