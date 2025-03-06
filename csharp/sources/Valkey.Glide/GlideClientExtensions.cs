using System.ComponentModel;
using System.Diagnostics.CodeAnalysis;
using System.Globalization;
using Valkey.Glide.InterOp.Native;
using Valkey.Glide.Properties;
using Valkey.Glide.Properties;

namespace Valkey.Glide;

/// <summary>
/// Hosts the different valkey commands in an implementation-friendly manner
/// </summary>
/// <remarks>
/// We do this to ease on the implementation of <see cref="IGlideClient"/>
/// </remarks>
public static class GlideClientExtensions
{
    /// <summary>
    /// <para>
    ///     Count the number of set bits (population counting) in a string.
    /// </para>
    /// <para>
    ///     By default all the bytes contained in the string are examined.
    ///     It is possible to specify the counting operation only in an interval
    ///     passing the additional arguments start and end.
    /// </para>
    /// <para>
    ///     Non-existent <paramref cref="key"/>s are treated as empty strings, so the command will return zero.
    /// </para>
    /// </summary>
    /// <example>
    /// <code>
    /// 127.0.0.1:6379> SET mykey "foobar"
    /// OK
    /// 127.0.0.1:6379> BITCOUNT mykey
    /// (integer) 26
    /// 127.0.0.1:6379> BITCOUNT mykey 0 0
    /// (integer) 4
    /// 127.0.0.1:6379> BITCOUNT mykey 1 1
    /// (integer) 6
    /// 127.0.0.1:6379> BITCOUNT mykey 1 1 BYTE
    /// (integer) 6
    /// 127.0.0.1:6379> BITCOUNT mykey 5 30 BIT
    /// (integer) 17
    /// </code>
    /// </example>
    /// <param name="client">
    /// The client instance used to execute the bit counting operation.
    /// </param>
    /// <param name="key">
    /// The key whose value's bits will be analyzed.
    /// </param>
    /// <param name="range">
    /// An optional range within the value to restrict the bit counting operation. If specified, both start and end must be provided.
    /// </param>
    /// <param name="bitCountType">
    /// An optional enumeration to specify whether the range is interpreted as bit or byte offsets.
    /// If specified, a <paramref name="range"/> must also be supplied.
    /// </param>
    /// <exception cref="ArgumentException">
    ///     Thrown if the arguments do not match the expected pattern.
    /// </exception>
    /// <exception cref="InvalidEnumArgumentException">
    ///     Thrown if the enum value  provided for <see cref="bitCountType"/> is out of range.
    /// </exception>
    /// <exception cref="FormatException">
    ///     Thrown if the value returned by the client could not be interpreted as an <see cref="int"/>.
    /// </exception>
    /// <returns>
    /// A <see cref="Task{TResult}"/> that represents the asynchronous operation.
    /// The result encapsulates the count of set bits in the specified key,
    /// or within the specified range, and indicates whether a result was successfully obtained.
    /// </returns>
    /// <seealso href="https://valkey.io/commands/bitcount/"/>
    public static async Task<Result<int>> BitCountAsync(
        this IGlideClient client,
        string key,
        Range? range = null,
        EBitCountType? bitCountType = null
    )
    {
        if (bitCountType is not null && range is null)
            throw new ArgumentException(Language.BitCount_RangeRequiredIfBitCountTypeSpecified, nameof(range));
        var command = ERequestType.BitCount;
        if (range is null)
            return await ParseResultAsync<int>(client.CommandAsync(command, key));

        var start = range.Value.Start.IsFromEnd ? -range.Value.Start.Value : range.Value.Start.Value;
        var end = range.Value.End.IsFromEnd ? -range.Value.End.Value : range.Value.End.Value;

        if (bitCountType is null)
            return await ParseResultAsync<int>(client.CommandAsync(command, key, start.ToString(), end.ToString()));
        return await ParseResultAsync<int>(
            client.CommandAsync(
                command,
                key,
                start.ToString(),
                end.ToString(),
                bitCountType.Value switch
                {
                    EBitCountType.Bit  => "BIT",
                    EBitCountType.Byte => "BYTE",
                    _ => throw new InvalidEnumArgumentException(
                        nameof(bitCountType),
                        (int) bitCountType.Value,
                        typeof(EBitCountType)
                    ),
                }
            )
        );
    }

    /// <summary>
    /// Parses the result of a command asynchronously and attempts to convert it to a specified type.
    /// </summary>
    /// <typeparam name="T">
    /// The type to which the result is parsed. This type must implement <see cref="ISpanParsable{T}"/>.
    /// </typeparam>
    /// <param name="commandAsync">
    /// A task representing the asynchronous execution of a command that returns a string. The result of this task
    /// represents the raw command output to be parsed.
    /// </param>
    /// <returns>
    /// A <see cref="Result{T}"/> object containing the parsed value and a flag indicating whether the result was empty.
    /// </returns>
    /// <exception cref="FormatException">
    /// Thrown when the command result cannot be parsed into the specified type.
    /// </exception>
    private static async Task<Result<T>> ParseResultAsync<T>(Task<string?> commandAsync)
        where T : ISpanParsable<T>
    {
        var result = await commandAsync.ConfigureAwait(false);
        if (result is null)
            return new Result<T>(default, true);
        if (T.TryParse(result, CultureInfo.InvariantCulture, out var value))
            return new Result<T>(value, false);
        throw new FormatException($"The result '{result}' could not be parsed as an integer.");
    }
}

/// <summary>
/// Represents the result of an operation that produces an <typeparamref name="TValue"/> value,
/// along with a flag indicating if the result is empty.
/// </summary>
/// <remarks>
/// This structure is used to encapsulate the outcome of specific commands or calculations,
/// while also providing additional metadata about whether the result is valid or empty.
/// </remarks>
/// <param name="Value">The value returned or <see langword="default"/> if <paramref name="Empty"/> is <see langword="true"/></param>
public readonly record struct Result<T>(T? Value, bool Empty)
{
    /// <summary>
    /// Defines an implicit conversion from a value of type <typeparamref name="T"/> to a <see cref="Result{T}"/>.
    /// </summary>
    /// <param name="value">
    /// A value of type <typeparamref name="T"/>. If the value is <see langword="null"/>, the result will be marked as <see cref="Empty"/>.
    /// </param>
    /// <returns>
    /// A <see cref="Result{T}"/> instance encapsulating the specified <paramref name="value"/>
    /// and indicating whether the result is <see cref="Empty"/>.
    /// </returns>
    public static implicit operator Result<T>(T? value) => new(value ?? default, value is null);

    /// <summary>
    /// Defines an implicit conversion from a <see cref="Result{T}"/> to its underlying value of type <typeparamref name="T"/>.
    /// </summary>
    /// <param name="result">
    /// A <see cref="Result{T}"/> instance encapsulating the value to retrieve.
    /// If the result is marked as <see cref="Empty"/>, the returned value will be <see langword="null"/>.
    /// </param>
    /// <returns>
    /// The underlying value of type <typeparamref name="T"/> contained in the <paramref name="result"/>,
    /// or <see langword="null"/> if the result is  <see cref="Empty"/>.
    /// </returns>
    public static implicit operator T?(Result<T> result) => result.Value;

    /// <summary>
    /// Returns the <see cref="Value"/> if it is not <see cref="Empty"/>;
    /// otherwise, returns a <see langword="default"/> value.
    /// </summary>
    /// <param name="defaultValueFactory">
    /// A <see cref="Func{TResult}"/> to produce a default value if the result is empty.
    /// If <see langword="null"/>, the <see langword="default"/> value of <typeparamref name="T"/> will be returned.
    /// </param>
    /// <returns>
    /// The encapsulated <see cref="Value"/> if it is not <see cref="Empty"/>;
    /// otherwise, the result of the  <paramref name="defaultValueFactory"/>
    /// or the <see langword="default"/> value of <typeparamref name="T"/>.
    /// </returns>
    [return: NotNullIfNotNull(nameof(defaultValueFactory))]
    public T? ValueOrDefault(Func<T>? defaultValueFactory = null)
    {
        if (!Empty)
            return Value!;
        if (defaultValueFactory is null)
            return default;
        return defaultValueFactory()!;
    }
}

public enum EBitCountType
{
    Bit,
    Byte,
}
