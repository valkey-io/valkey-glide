// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Internals;
using Valkey.Glide.Pipeline;

using static Valkey.Glide.ConnectionConfiguration;
using static Valkey.Glide.Pipeline.Options;

namespace Valkey.Glide;

public sealed class GlideClient(StandaloneClientConfiguration config) : BaseClient(config), IConnectionManagementCommands, IGenericCommands
{
    public async Task<object?> CustomCommand(GlideString[] args)
        => await Command(FFI.RequestType.CustomCommand, args, resp => HandleServerResponse<object?>(resp, true));

    // TODO to interface?
    public async Task<object?[]?> Exec(Batch batch)
        => await Batch(batch);

    public async Task<object?[]?> Exec(Batch batch, BatchOptions options)
        => await Batch(batch, options);
}
