// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Internals;

using static Valkey.Glide.Pipeline.Options;
using static Valkey.Glide.ConnectionConfiguration;
using Valkey.Glide.Pipeline;

namespace Valkey.Glide;

public sealed class GlideClusterClient(ClusterClientConfiguration config) : BaseClient(config), IGenericClusterCommands
{
    public async Task<object?> CustomCommand(GlideString[] args, Route? route = null)
        => await Command(FFI.RequestType.CustomCommand, args, resp => HandleServerResponse<object?>(resp, true), route);

    // TODO to interface?
    public async Task<object?[]?> Exec(Batch batch)
        => await Batch(batch);

    public async Task<object?[]?> Exec(Batch batch, ClusterBatchOptions options)
        => await Batch(batch, options);
}
