// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;

namespace Valkey.Glide.Pipeline;

internal interface IBatchConnectionManagementCommands
{
    /// <inheritdoc cref="IServerManagementCommands.PingAsync(CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IServerManagementCommands.PingAsync(CommandFlags)" /></returns>
    IBatch Ping();

    /// <inheritdoc cref="IServerManagementCommands.PingAsync(ValkeyValue, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IServerManagementCommands.PingAsync(ValkeyValue, CommandFlags)" /></returns>
    IBatch Ping(ValkeyValue message);

    /// <inheritdoc cref="IServerManagementCommands.EchoAsync(ValkeyValue, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IServerManagementCommands.EchoAsync(ValkeyValue, CommandFlags)" /></returns>
    IBatch Echo(ValkeyValue message);
}
