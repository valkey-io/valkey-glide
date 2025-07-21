// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Diagnostics;

using static Valkey.Glide.Errors;
using static Valkey.Glide.Internals.FFI;

namespace Valkey.Glide.Internals;

internal interface ICmd
{
    /// <summary>
    /// Convert to an FFI-ready struct.
    /// </summary>
    Cmd ToFfi();
    /// <summary>
    /// Get untyped converted (used for batch).
    /// </summary>
    Func<object?, object?> GetConverter();
}

internal class Cmd<R, T> : ICmd
{
    public readonly bool IsNullable;
    public readonly Func<R, T> Converter;
    public readonly RequestType Request;
    public readonly ArgsArray ArgsArray;

#pragma warning disable IDE0046 // Convert to conditional expression
    public Func<object?, object?> GetConverter() => value =>
    {
        if (value is null)
        {
            if (IsNullable)
            {
                return null;
            }
            throw new RequestException($"Unexpected return type from Glide: got null expected {typeof(T).GetRealTypeName()}");
        }
        if (value is RequestException)
        {
            return value;
        }
        Debug.Assert(value!.GetType() == typeof(R) || typeof(R).IsAssignableFrom(value!.GetType()),
            $"Unexpected return type from Glide: got {value?.GetType().GetRealTypeName()} expected {typeof(R).GetRealTypeName()}");

        return Converter((R)value!);
    };
#pragma warning restore IDE0046 // Convert to conditional expression

    public Cmd ToFfi() => new(Request, ArgsArray.Args);

    public new string ToString() => $"{Request} [{string.Join(' ', ArgsArray.Args.ToStrings())}]";

    public Cmd(RequestType request, GlideString[] args, bool isNullable, Func<R, T> converter)
    {
        Request = request;
        ArgsArray = new() { Args = args };
        IsNullable = isNullable;
        Converter = converter;
    }

    /// <summary>
    /// Convert a command to one which handles a multi-node cluster value.
    /// </summary>
    public Cmd<Dictionary<GlideString, object>, Dictionary<string, T>> ToMultiNodeValue()
        => new(Request, ArgsArray.Args, IsNullable, map => ResponseConverters.HandleMultiNodeValue(map, Converter));

    /// <summary>
    /// Convert a command to one which handles a <see cref="ClusterValue{T}" />.
    /// </summary>
    /// <param name="isSingleValue">Whether current command call returns a single value.</param>
    public Cmd<object, ClusterValue<T>> ToClusterValue(bool isSingleValue)
        => new(Request, ArgsArray.Args, IsNullable, ResponseConverters.MakeClusterValueHandler(Converter, isSingleValue));

    /// <summary>
    /// Get full command line including command name.
    /// </summary>
    public string[] GetArgs() => Request == RequestType.CustomCommand
        ? ArgsArray.Args.ToStrings()
        : [.. ArgsArray.Args.ToStrings().Prepend(Request.ToString().ToUpper())];
}

internal record ArgsArray
{
    public GlideString[] Args = [];
}
