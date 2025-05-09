// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Diagnostics;

using Valkey.Glide.Internals;

using static Valkey.Glide.Commands.Options.InfoOptions;
using static Valkey.Glide.Errors;

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
    private readonly List<Func<object?, object?>> _converters = [];

    internal bool IsAtomic { get; private set; } = isAtomic;

    internal FFI.Batch ToFFI() => new([.. _commands], IsAtomic);

    /// <summary>
    /// Convert a response reseived from the server.
    /// </summary>
    internal object?[]? ConvertResponse(object?[]? response)
    {
        if (response is null)
        {
            return null;
        }

        Debug.Assert(response?.Length == _converters.Count, "Converteds misaligned");

        for (int i = 0; i < response?.Length; i++)
        {
            response[i] = _converters[i](response[i]);
        }
        return response;
    }

    /// <summary>
    /// Create a type checker for the response value.
    /// </summary>
    /// <typeparam name="V">Expected value type</typeparam>
    protected Func<object?, object?> MakeTypeChecker<V>(bool isNullable = false) where V : class?
        => MakeTypeCheckerAndConverter<V, V>(r => r, isNullable);

    /// <summary>
    /// Create a function which checks response value type and converts it.
    /// </summary>
    /// <typeparam name="V">Expected value type in server response</typeparam>
    /// <typeparam name="R">Resulting value type after conversion</typeparam>
    protected Func<object?, object?> MakeTypeCheckerAndConverter<V, R>(Func<V, R> converter, bool isNullable = false) where V : class? where R : class?
        => value =>
        {
#pragma warning disable IDE0046 // Convert to conditional expression
            if (value is null)
            {
                return isNullable
                    ? null
                    : (object)new RequestException($"Unexpected return type from Glide: got null expected {typeof(V).GetRealTypeName()}");
            }
#pragma warning restore IDE0046 // Convert to conditional expression
            return value is V
                ? converter((value as V)!)
                : new RequestException($"Unexpected return type from Glide: got {value?.GetType().GetRealTypeName()} expected {typeof(V).GetRealTypeName()}");
        };

    internal T AddCmd(FFI.Cmd cmd, Func<object?, object?> converter)
    {
        _commands.Add(cmd);
        _converters.Add(converter);
        return (T)this;
    }

    /// <inheritdoc cref="IBatch.CustomCommand(GlideString[])" />
    public T CustomCommand(GlideString[] args)
        => AddCmd(new(RequestType.CustomCommand, args), MakeTypeChecker<object>(true));

    /// <inheritdoc cref="IBatch.Get(GlideString)" />
    public T Get(GlideString key)
        => AddCmd(new(RequestType.Get, [key]), MakeTypeChecker<GlideString>(true));

    /// <inheritdoc cref="IBatch.Set(GlideString, GlideString)" />
    public T Set(GlideString key, GlideString value)
        => AddCmd(new(RequestType.Set, [key, value]), MakeTypeChecker<string>());

    /// <inheritdoc cref="IBatch.Info()" />
    public T Info() => Info([]);

    /// <inheritdoc cref="IBatch.Info(Section[])" />
    public T Info(Section[] sections)
        => AddCmd(new(RequestType.Info, sections.ToGlideStrings()),
            MakeTypeCheckerAndConverter<GlideString, string>(gs => gs.ToString()));

    IBatch IBatch.CustomCommand(GlideString[] args) => CustomCommand(args);
    IBatch IBatch.Get(GlideString key) => Get(key);
    IBatch IBatch.Set(GlideString key, GlideString value) => Set(key, value);
    IBatch IBatch.Info() => Info();
    IBatch IBatch.Info(Section[] sections) => Info(sections);
}
