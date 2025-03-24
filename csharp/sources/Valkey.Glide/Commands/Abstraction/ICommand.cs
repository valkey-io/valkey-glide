// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.InterOp;

namespace Valkey.Glide.Commands.Abstraction;

public interface IGlideCommand
{
    Task<Value> ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default);
}
