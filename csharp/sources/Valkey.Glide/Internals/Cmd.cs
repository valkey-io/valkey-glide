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

    /**
    =================================
    SET COMMANDS
    =================================
    */
    public static Cmd<long, bool> SetAdd(ValkeyKey key, ValkeyValue value, CommandFlags flags = CommandFlags.None)
    {
        GlideString[] args = [key.ToString(), value.ToString()];
        return new(RequestType.SAdd, args, false, response => response == 1);
    }

    public static Cmd<long, long> SetAdd(ValkeyKey key, ValkeyValue[] values, CommandFlags flags = CommandFlags.None)
    {
        GlideString[] args = [key.ToString(), .. values.Select((v) => v.ToString())];
        return Simple<long>(RequestType.SAdd, args);
    }

    public static Cmd<long, bool> SetRemove(ValkeyKey key, ValkeyValue value, CommandFlags flags = CommandFlags.None)
    {
        GlideString[] args = [key.ToString(), value.ToString()];
        return new(RequestType.SRem, args, false, response => response == 1);
    }

    public static Cmd<long, long> SetRemove(ValkeyKey key, ValkeyValue[] values, CommandFlags flags = CommandFlags.None)
    {
        GlideString[] args = [key.ToString(), .. values.Select((v) => v.ToString())];
        return Simple<long>(RequestType.SRem, args);
    }

    public static Cmd<HashSet<object>, ValkeyValue[]> SetMembers(ValkeyKey key, CommandFlags flags = CommandFlags.None)
    {
        GlideString[] args = [key.ToString()];
        return new(RequestType.SMembers, args, false, set => set.Select(obj => (ValkeyValue)obj.ToString()).ToArray());
    }

    public static Cmd<long, long> SetLength(ValkeyKey key, CommandFlags flags = CommandFlags.None)
    {
        GlideString[] args = [key.ToString()];
        return Simple<long>(RequestType.SCard, args);
    }

    public static Cmd<long, long> SetIntersectionLength(ValkeyKey[] keys, long limit = 0, CommandFlags flags = CommandFlags.None)
    {
        List<GlideString> args = [keys.Length.ToString(), .. keys.Select(k => k.ToString())];
        if (limit > 0)
        {
            args.Add("LIMIT");
            args.Add(limit.ToString());
        }
        return Simple<long>(RequestType.SInterCard, args.ToArray());
    }

    public static Cmd<GlideString, ValkeyValue> SetPop(ValkeyKey key, CommandFlags flags = CommandFlags.None)
    {
        GlideString[] args = [key.ToString()];
        return new(RequestType.SPop, args, true, result => result is not null ? (ValkeyValue)result.ToString() : ValkeyValue.Null);
    }

    public static Cmd<HashSet<object>, ValkeyValue[]> SetPop(ValkeyKey key, long count, CommandFlags flags = CommandFlags.None)
    {
        GlideString[] args = [key.ToString(), count.ToString()];
        return new(RequestType.SPop, args, false, set => set.Select(obj => (ValkeyValue)obj.ToString()).ToArray());
    }

    public static Cmd<HashSet<object>, ValkeyValue[]> SetCombine(SetOperation operation, ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
    {
        RequestType requestType = operation switch
        {
            SetOperation.Union => RequestType.SUnion,
            SetOperation.Intersect => RequestType.SInter,
            SetOperation.Difference => RequestType.SDiff,
            _ => throw new ArgumentOutOfRangeException(nameof(operation))
        };

        GlideString[] args = keys.Select(k => (GlideString)k.ToString()).ToArray();
        return new(requestType, args, false, set => set.Select(obj => (ValkeyValue)obj.ToString()).ToArray());
    }

    public static Cmd<long, long> SetCombineAndStore(SetOperation operation, ValkeyKey destination, ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
    {
        RequestType requestType = operation switch
        {
            SetOperation.Union => RequestType.SUnionStore,
            SetOperation.Intersect => RequestType.SInterStore,
            SetOperation.Difference => RequestType.SDiffStore,
            _ => throw new ArgumentOutOfRangeException(nameof(operation))
        };

        List<GlideString> args = [destination.ToString(), .. keys.Select(k => (GlideString)k.ToString())];
        return Simple<long>(requestType, args.ToArray());
    }

    /// <summary>
    /// Create a Cmd which returns OK
    /// </summary>
    private static Cmd<string, string> OK(RequestType request, GlideString[] args)
        => Simple<string>(request, args);

    /// <summary>
    /// Create a Cmd which does not need type conversion
    /// </summary>
    private static Cmd<T, T> Simple<T>(RequestType request, GlideString[] args, bool isNullable = false)
        => new(request, args, isNullable, o => o);
}
