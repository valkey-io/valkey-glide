// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System;
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

    internal abstract class IICmd<R, T> : ICmd where R : class? where T : class?
    {
        public abstract T Convert(R value);
        public readonly bool IsNullable;
        public readonly Func<R, T> Converter;
        public readonly RequestType Request;
        public readonly ArgsArray Args;

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

        internal IICmd(RequestType request, GlideString[] args, bool isNullable, Func<R, T> converter)
        {
            Request = request;
            Args = new() { Args = args };
            IsNullable = isNullable;
            Converter = converter;
        }

        public Cmd ToFfi() => new(Request, Args.Args);

        public string ToString()
        {
            return $"{Request} [{string.Join(' ', Args.Args.ToStrings())}]";
        }
    }

    internal class Cmd<R, T> : IICmd<R, T> where R : class? where T : class?
    {
        public override T Convert(R value)
        {
            Debug.Assert(value.GetType() == typeof(R) || typeof(R).IsAssignableFrom(value.GetType()),
                $"Unexpected return type from Glide: got {value?.GetType().GetRealTypeName()} expected {typeof(R).GetRealTypeName()}");

            return Converter(value)!;
        }

        //public Dictionary<string, T> ConvertMultiNodeResponse(R value)
        //{

        //}
#pragma warning disable IDE0290 // Use primary constructor
        public Cmd(RequestType request, GlideString[] args, bool isNullable, Func<R, T> converter)
            : base(request, args, isNullable, converter) { }
#pragma warning restore IDE0290 // Use primary constructor
    }
    /*
    internal class ClusterValueCmd<R, T> : IICmd<object, ClusterValue<T>> where R : class? where T : class?
    {
        private readonly bool _isSingleValue;
        public new readonly Func<R, T> Converter;

        internal ClusterValueCmd(RequestType request, GlideString[] args, bool isNullable, Func<R, T> converter, bool isSingleValue)
            : base(request, args, isNullable, converter)
        {
            _isSingleValue = isSingleValue;
            Converter = converter;
        }

        // TODO below
        public override ClusterValue<T> Convert(object value)
            => _isSingleValue
                ? ClusterValue<T>.OfSingleValue(Converter((R)value))
                : ClusterValue<T>.OfMultiValue(((Dictionary<GlideString, R>)value).ConvertValues(Converter));

        //public override ClusterValue<T> Convert(R value) => throw new NotImplementedException();
    }

    /*
    internal class ClusterValueCmd2<R, T> : Cmd<R, ClusterValue<T>> where R : class? where T : class?
    {
        private readonly bool _isSingleValue;
        private readonly Cmd<R, T> _innerCmd;

        internal ClusterValueCmd2(RequestType request, GlideString[] args, bool isNullable, Func<R, T> converter, bool isSingleValue)
            : base(request, args, isNullable, o => throw new ArgumentException("This converter shouldn't be used")) // TODO avoid exception or null
        {
            _isSingleValue = isSingleValue;
            Converter = converter;
        }

        public ClusterValue<T> Convert(object value)
            => _isSingleValue
                ? ClusterValue<T>.OfSingleValue(Converter((R)value))
                : ClusterValue<T>.OfMultiValue(((Dictionary<GlideString, R>)value).ConvertValues(Converter));
    }*/

    internal record ArgsArray
    {
        public GlideString[] Args = [];
    }

    public static Cmd<object?, object?> CustomCommand(GlideString[] args)
        => new(RequestType.CustomCommand, args, true, o => o);

    public static Cmd<object?, T> CustomCommand<T>(GlideString[] args, Func<object?, T> converter) where T : class?
        => new(RequestType.CustomCommand, args, true, converter);

    public static Cmd<GlideString, string> InfoStandalone(InfoOptions.Section[] sections)
        => new(RequestType.Info, sections.ToGlideStrings(), false, gs => gs.ToString());

    public static Cmd<Dictionary<GlideString, object>, Dictionary<string, string>> InfoCluster(InfoOptions.Section[] sections)
        => new(RequestType.Info, sections.ToGlideStrings(), false, map =>
            ResponseConverters.HandleMultiNodeValue<GlideString, string>(map, gs => gs.ToString()));

    public static Cmd<object, ClusterValue<string>> InfoWithRoute(InfoOptions.Section[] sections, bool isSingleValue)
        => new(RequestType.Info, sections.ToGlideStrings(), false, MakeClusterValueHandler<GlideString, string>(gs => gs.ToString(), isSingleValue));
    //=> new ClusterValueCmd<GlideString, string>(RequestType.Info, sections.ToGlideStrings(), true, gs => gs.ToString(), isSingleValue);

    public static Cmd<GlideString, GlideString> Get(GlideString key)
        => Simple<GlideString>(RequestType.Get, [key], true);

    public static Cmd<string, string> Set(GlideString key, GlideString value)
        => OK(RequestType.Set, [key, value]);


    private static Func<object, ClusterValue<T>> MakeClusterValueHandler<R, T>(Func<R, T> converter, bool isSingleValue) where T : class? where R : class?
        => isSingleValue
            ? value => ClusterValue<T>.OfSingleValue(converter((R)value))
            : value => ClusterValue<T>.OfMultiValue(((Dictionary<GlideString, object?>)value).ConvertValues(converter));


    // Create a Cmd which returns OK
    private static Cmd<string, string> OK(RequestType request, GlideString[] args)
        => Simple<string>(request, args);

    // Create a Cmd which does not need type conversion
    private static Cmd<T, T> Simple<T>(RequestType request, GlideString[] args, bool isNullable = false) where T : class?
        => new(request, args, isNullable, o => o);
}
