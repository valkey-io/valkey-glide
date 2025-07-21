// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.Internals.FFI;

namespace Valkey.Glide.Internals;

internal partial class Request
{
    public static Cmd<object?, object?> CustomCommand(GlideString[] args)
        => new(RequestType.CustomCommand, args, true, o => o);

    public static Cmd<object?, T> CustomCommand<T>(GlideString[] args, Func<object?, T> converter) where T : class?
        => new(RequestType.CustomCommand, args, true, converter);

#pragma warning disable IDE0051 // Add missing cases TODO: REMOVE ONCE 4336 IS MERGED
    /// <summary>
    /// Create a Cmd which returns OK
    /// </summary>
    private static Cmd<string, string> OK(RequestType request, GlideString[] args)
        => Simple<string>(request, args);
#pragma warning restore IDE0051 // Add missing cases

    /// <summary>
    /// Create a Cmd which does not need type conversion
    /// </summary>
    private static Cmd<T, T> Simple<T>(RequestType request, GlideString[] args, bool isNullable = false)
        => new(request, args, isNullable, o => o);

    /// <summary>
    /// Create a Cmd which returns a Boolean value based on the response being 1 or not.
    /// </summary>
    /// <typeparam name="T">Any type that can be implicitly cast to a numeric value for comparison</typeparam>
    /// <param name="request">The request type</param>
    /// <param name="args">The command arguments</param>
    /// <returns>A command that converts the response to a boolean value (true if response equals 1)</returns>
    private static Cmd<T, bool> Boolean<T>(RequestType request, GlideString[] args)
        => new(request, args, false, response => Convert.ToInt64(response) == 1);

    /// <summary>
    /// Create a Cmd which returns a Boolean value based on the response being OK or not.
    /// </summary>
    /// <param name="request">The request type</param>
    /// <param name="args">The command arguments</param>
    /// <returns>A command that converts the response to a boolean value (true if response equals OK)</returns>
    private static Cmd<string, bool> OKToBool(RequestType request, GlideString[] args)
        => new(request, args, false, response => response == "OK");
    /// <summary>
    /// Create a Cmd which converts the response to a ValkeyValue.
    /// </summary>
    /// <param name="request">The request type</param>
    /// <param name="args">The command arguments</param>
    /// <param name="isNullable">Whether the response can be null</param>
    /// <returns>A command that converts the response to a ValkeyValue</returns>
    private static Cmd<GlideString, ValkeyValue> ToValkeyValue(RequestType request, GlideString[] args, bool isNullable = false)
        => new(request, args, isNullable, response => (ValkeyValue)response);

    /// <summary>
    /// Create a Cmd which converts a HashSet of objects to an array of ValkeyValues.
    /// </summary>
    /// <param name="request">The request type</param>
    /// <param name="args">The command arguments</param>
    /// <returns>A command that converts a HashSet to a ValkeyValue array</returns>
    private static Cmd<object[], ValkeyValue[]> ObjectArrayToValkeyValueArray(RequestType request, GlideString[] args)
        => new(request, args, false, set => [.. set.Select(obj => {
            GlideString glideString = obj is GlideString gs ? gs : new GlideString((string)obj);
            return (ValkeyValue)glideString;
        })]);

    private static Cmd<object[], HashEntry[]> ObjectArrayToHashEntries(RequestType request, GlideString[] args, bool isNullable = false)
        => new(request, args, isNullable, objects => [.. objects.Select(he => {
            object[] arr = (object[])he;
            GlideString key = arr[0] is GlideString gs0 ? gs0 : new GlideString((string)arr[0]);
            GlideString value = arr[1] is GlideString gs1 ? gs1 : new GlideString((string)arr[1]);
            return new HashEntry(key, value);
        })]);

    private static Cmd<Dictionary<GlideString, object>, HashEntry[]> DictionaryToHashEntries(RequestType request, GlideString[] args, bool isNullable = false)
        => new(request, args, isNullable, dict => [.. dict.Select(he => {
            GlideString value = he.Value is GlideString gs ? gs : new GlideString((string)he.Value);
            return new HashEntry(he.Key, value);
        })]);
}
