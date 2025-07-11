// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Diagnostics;

using Valkey.Glide.Internals;

using static Valkey.Glide.Commands.Options.InfoOptions;

namespace Valkey.Glide.Pipeline;

/// <summary>
/// Base class encompassing shared commands for both standalone and cluster server installations.
/// Batches allow the execution of a group of commands in a single step.
/// <para />
/// Batch Response: An <c>array</c> of command responses is returned by the client <c>Exec</c> API,
/// in the order they were given. Each element in the array represents a command given to the <c>Batch</c>.
/// The response for each command depends on the executed Valkey command. Specific response types are
/// documented alongside each method.
/// </summary>
/// <typeparam name="T">Child typing for chaining method calls.</typeparam>
/// <param name="isAtomic">
/// Determines whether the batch is atomic or non-atomic. If <see langword="true" />, the batch will be executed as
/// an atomic transaction. If <see langword="false" />, the batch will be executed as a non-atomic pipeline.
/// </param>
public abstract partial class BaseBatch<T>(bool isAtomic) : IBatch where T : BaseBatch<T>
{
    private readonly List<ICmd> _commands = [];

    internal bool IsAtomic { get; private set; } = isAtomic;

    internal FFI.Batch ToFFI() => new([.. _commands.Select(c => c.ToFfi())], IsAtomic);

    /// <summary>
    /// Convert a response received from the server.
    /// </summary>
    internal object?[]? ConvertResponse(object?[]? response)
    {
        if (response is null)
        {
            return null;
        }

        Debug.Assert(response.Length == _commands.Count,
            $"Response misaligned: received {response.Length} responses but submitted {_commands.Count} commands");

        for (int i = 0; i < response?.Length; i++)
        {
            response[i] = _commands[i].GetConverter()(response[i]);
        }
        return response;
    }

    internal T AddCmd(ICmd cmd)
    {
        _commands.Add(cmd);
        return (T)this;
    }

    /// <inheritdoc cref="IBatch.CustomCommand(GlideString[])" />
    public T CustomCommand(GlideString[] args) => AddCmd(Request.CustomCommand(args));

    /// <inheritdoc cref="IBatch.Info()" />
    public T Info() => Info([]);

    /// <inheritdoc cref="IBatch.Info(Section[])" />
    public T Info(Section[] sections) => AddCmd(Request.Info(sections));

    IBatch IBatch.CustomCommand(GlideString[] args) => CustomCommand(args);
    IBatch IBatch.Info() => Info();
    IBatch IBatch.Info(Section[] sections) => Info(sections);
}
