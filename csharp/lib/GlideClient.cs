// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Glide.Commands;

namespace Glide;

public class GlideClient(string host, uint port, bool useTLS) : BaseClient(host, port, useTLS), IConnectionManagementCommands, IGenericCommands
{
    public async Task<object?> CustomCommand(GlideString[] args)
        => await Command(args, RequestType.CustomCommand, HandleServerResponse<object?>);
}
