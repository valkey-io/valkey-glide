// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;

namespace Valkey.Glide.Pipeline;

/// <summary>
/// Interface for standalone-specific batch operations that are not available in cluster mode.
/// </summary>
internal interface IBatchStandalone
{
    /// <inheritdoc cref="IGenericCommands.KeyCopyAsync(ValkeyKey, ValkeyKey, int, bool, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IGenericCommands.KeyCopyAsync(ValkeyKey, ValkeyKey, int, bool, CommandFlags)" /></returns>
    IBatchStandalone KeyCopy(ValkeyKey sourceKey, ValkeyKey destinationKey, int destinationDatabase, bool replace = false);

    /// <inheritdoc cref="IGenericCommands.KeyMoveAsync(ValkeyKey, int, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IGenericCommands.KeyMoveAsync(ValkeyKey, int, CommandFlags)" /></returns>
    IBatchStandalone KeyMove(ValkeyKey key, int database);
}
