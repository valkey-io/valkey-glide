using System.ComponentModel;
using System.Globalization;
using Valkey.Glide.InterOp.Native;
using Valkey.Glide.Properties;

namespace Valkey.Glide.Commands;

/// <summary>
/// Hosts the <c>bitcount</c> valkey command in an implementation-friendly manner
/// </summary>
/// <remarks>
/// We do this to ease the implementation of <see cref="IGlideClient"/>
/// </remarks>
public static class BitCountCommand
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
            return await ResultHelpers.ParseResultAsync<int>(client.CommandAsync(command, key));

        var start = range.Value.Start.IsFromEnd ? -range.Value.Start.Value : range.Value.Start.Value;
        var end = range.Value.End.IsFromEnd ? -range.Value.End.Value : range.Value.End.Value;

        if (bitCountType is null)
            return await ResultHelpers.ParseResultAsync<int>(client.CommandAsync(command, key, start.ToString(), end.ToString()));
        return await ResultHelpers.ParseResultAsync<int>(
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
}
