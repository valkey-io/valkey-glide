// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Internals;

using static Valkey.Glide.Commands.Options.InfoOptions;

using RequestType = Valkey.Glide.Internals.FFI.RequestType;

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
public abstract class BaseBatch<T>(bool isAtomic) : IBatch where T : BaseBatch<T>
{
    private readonly List<FFI.Cmd> _commands = [];

    internal bool IsAtomic { get; private set; } = isAtomic;

    internal FFI.Batch ToFFI() => new([.. _commands], IsAtomic);

    /// <inheritdoc cref="IBatch.CustomCommand(GlideString[])" />
    public T CustomCommand(GlideString[] args)
    {
        _commands.Add(new(RequestType.CustomCommand, args));
        return (T)this;
    }

    /// <inheritdoc cref="IBatch.Get(GlideString)" />
    public T Get(GlideString key)
    {
        _commands.Add(new(RequestType.Get, [key]));
        return (T)this;
    }

    /// <inheritdoc cref="IBatch.Set(GlideString, GlideString)" />
    public T Set(GlideString key, GlideString value)
    {
        _commands.Add(new(RequestType.Set, [key, value]));
        return (T)this;
    }

    /// <inheritdoc cref="IBatch.Info()" />
    public T Info()
    {
        _commands.Add(new(RequestType.Info, []));
        return (T)this;
    }

    /// <inheritdoc cref="IBatch.Info(Section[])" />
    public T Info(Section[] sections)
    {
        _commands.Add(new(RequestType.Info, sections.ToGlideStrings()));
        return (T)this;
    }

    IBatch IBatch.CustomCommand(GlideString[] args) => CustomCommand(args);
    IBatch IBatch.Get(GlideString key) => Get(key);
    IBatch IBatch.Set(GlideString key, GlideString value) => Set(key, value);
    IBatch IBatch.Info() => Info();
    IBatch IBatch.Info(Section[] sections) => Info(sections);
}
