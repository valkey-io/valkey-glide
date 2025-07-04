// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;

namespace Valkey.Glide.Pipeline;

internal interface IBatchConnectionManagementCommands
{
    /// <inheritdoc cref="IConnectionManagementCommands.PingAsync(CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IConnectionManagementCommands.PingAsync(CommandFlags)" /></returns>
    IBatch Ping();

    /// <inheritdoc cref="IConnectionManagementCommands.PingAsync(ValkeyValue, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IConnectionManagementCommands.PingAsync(ValkeyValue, CommandFlags)" /></returns>
    IBatch Ping(ValkeyValue message);

    /// <inheritdoc cref="IConnectionManagementCommands.EchoAsync(ValkeyValue, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IConnectionManagementCommands.EchoAsync(ValkeyValue, CommandFlags)" /></returns>
    IBatch Echo(ValkeyValue message);
}
