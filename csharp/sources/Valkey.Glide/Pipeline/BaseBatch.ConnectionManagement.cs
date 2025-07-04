// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Internals;

namespace Valkey.Glide.Pipeline;

public abstract partial class BaseBatch<T> : IBatchConnectionManagementCommands where T : BaseBatch<T>
{
    /// <inheritdoc cref="IBatchConnectionManagementCommands.Ping()" />
    public T Ping() => AddCmd(Request.PingAsync());

    /// <inheritdoc cref="IBatchConnectionManagementCommands.Ping(ValkeyValue)" />
    public T Ping(ValkeyValue message) => AddCmd(Request.PingAsync(message));

    /// <inheritdoc cref="IBatchConnectionManagementCommands.Echo(ValkeyValue)" />
    public T Echo(ValkeyValue message) => AddCmd(Request.EchoAsync(message));

    IBatch IBatchConnectionManagementCommands.Ping() => Ping();
    IBatch IBatchConnectionManagementCommands.Ping(ValkeyValue message) => Ping(message);
    IBatch IBatchConnectionManagementCommands.Echo(ValkeyValue message) => Echo(message);
}
