// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;

using static Valkey.Glide.ConnectionConfiguration;

namespace Valkey.Glide;

public sealed class GlideClient : BaseClient, IConnectionManagementCommands, IGenericCommands
{
    public GlideClient(BaseClientConfiguration config) : base(config) { }

    public async Task<object?> CustomCommand(GlideString[] args)
        => await Command(RequestType.CustomCommand, args, resp => HandleServerResponse<object?>(resp, true));
}
