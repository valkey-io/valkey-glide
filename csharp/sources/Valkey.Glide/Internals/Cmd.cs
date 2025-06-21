// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Diagnostics;

using Valkey.Glide.Commands.Options;

using static Valkey.Glide.Errors;
using static Valkey.Glide.Internals.FFI;

namespace Valkey.Glide.Internals;

internal class Request // TODO naming
{
    internal interface ICmd
    {
        Cmd ToFfi();
        Func<object?, object?> GetConverter();
    }

    internal class Cmd<R, T> : ICmd where R : class? where T : class?
    {
        public readonly bool IsNullable;
        public readonly Func<R, T> Converter;
        public readonly RequestType Request;
        public readonly ArgsArray Args;

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
            return Convert((R)value!);
        };
#pragma warning restore IDE0046 // Convert to conditional expression

        public Cmd ToFfi() => new(Request, Args.Args);

        public new string ToString() => $"{Request} [{string.Join(' ', Args.Args.ToStrings())}]";

        public T Convert(R value)
        {
            Debug.Assert(value!.GetType() == typeof(R) || typeof(R).IsAssignableFrom(value!.GetType()),
                $"Unexpected return type from Glide: got {value?.GetType().GetRealTypeName()} expected {typeof(R).GetRealTypeName()}");

            return Converter(value);
        }

        public Cmd(RequestType request, GlideString[] args, bool isNullable, Func<R, T> converter)
        {
            Request = request;
            Args = new() { Args = args };
            IsNullable = isNullable;
            Converter = converter;
        }

        /// <summary>
        /// Convert a command to one which handles a multi-node cluster value.
        /// </summary>
        public Cmd<Dictionary<GlideString, object>, Dictionary<string, T>> ToMultiNodeValue()
            => new(Request, Args.Args, IsNullable, map => ResponseConverters.HandleMultiNodeValue(map, Converter));

        /// <summary>
        /// Convert a command to one which handles a <see cref="ClusterValue{T}" />.
        /// </summary>
        /// <param name="isSingleValue">Whether current command call returns a single value.</param>
        public Cmd<object, ClusterValue<T>> ToClusterValue(bool isSingleValue)
            => new(Request, Args.Args, IsNullable, ResponseConverters.MakeClusterValueHandler(Converter, isSingleValue));
    }

    internal record ArgsArray
    {
        public GlideString[] Args = [];
    }

    public static Cmd<object?, object?> CustomCommand(GlideString[] args)
        => new(RequestType.CustomCommand, args, true, o => o);

    public static Cmd<object?, T> CustomCommand<T>(GlideString[] args, Func<object?, T> converter) where T : class?
        => new(RequestType.CustomCommand, args, true, converter);

    public static Cmd<GlideString, string> Info(InfoOptions.Section[] sections)
        => new(RequestType.Info, sections.ToGlideStrings(), false, gs => gs.ToString());

    public static Cmd<GlideString, GlideString> Get(GlideString key)
        => Simple<GlideString>(RequestType.Get, [key], true);

    public static Cmd<string, string> Set(GlideString key, GlideString value)
        => OK(RequestType.Set, [key, value]);


    /// <summary>
    /// Create a Cmd which returns OK
    /// </summary>
    private static Cmd<string, string> OK(RequestType request, GlideString[] args)
        => Simple<string>(request, args);

    /// <summary>
    /// Create a Cmd which does not need type conversion
    /// </summary>
    private static Cmd<T, T> Simple<T>(RequestType request, GlideString[] args, bool isNullable = false) where T : class?
        => new(request, args, isNullable, o => o);
}
