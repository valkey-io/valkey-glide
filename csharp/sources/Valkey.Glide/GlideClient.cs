// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide;

public sealed class GlideClient(ConnectionConfigBuilder config) : BaseClient(config.WithClusterMode(false)), IConnectionManagementCommands, IGenericCommands
{
    public async Task<object?> CustomCommand(GlideString[] args)
        => await Command(ERequestType.CustomCommand, args, resp => HandleServerResponse<object?>(resp, true));
}
