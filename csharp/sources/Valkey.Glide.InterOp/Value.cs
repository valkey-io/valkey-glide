using System;
using System.Collections.Generic;
using System.Diagnostics.CodeAnalysis;
using System.Globalization;
using System.Linq;

namespace Valkey.Glide.InterOp;

/// <summary>
/// Represents a flexible data structure capable of handling various types of values,
/// including primitive values, arrays, key-value pairs, and formatted strings. The
/// <see cref="Value"/> struct provides rich functionalities to store and manage
/// heterogeneous data types through its versatile properties and constructors.
/// </summary>
/// <remarks>
/// This struct allows for the representation of multiple data types, enabling
/// the user to store and transfer data efficiently. The kind or type of the value
/// stored in the instance is indicated by the <see cref="EValueKind"/> enumeration.
/// Depending on its current <see cref="EValueKind"/>, only certain properties may
/// hold relevant information. Therefore, the other properties should be considered
/// uninitialized or unused.
/// </remarks>
/// <seealso cref="EValueKind"/>
[SuppressMessage("ReSharper", "UnusedAutoPropertyAccessor.Global")]
public struct Value : IComparable<Value>, IEquatable<Value>, IEqualityComparer<Value>
{
    /// <summary>
    /// Indicates the category or type of the value represented by this instance.
    /// </summary>
    public EValueKind Kind { get; set; }

    /// <remarks>
    /// <list type="bullet">
    /// <item>Set if <see cref="Kind"/> is <see cref="EValueKind.FormatString"/>.</item>
    /// </list>
    /// </remarks>
    public string? Format { get; set; }

    /// <remarks>
    /// <list type="bullet">
    /// <item>Set if <see cref="Kind"/> is <see cref="EValueKind.String"/>.</item>
    /// <item>Set if <see cref="Kind"/> is <see cref="EValueKind.FormatString"/>.</item>
    /// </list>
    /// </remarks>
    public string? Data { get; set; }

    /// <remarks>
    /// <list type="bullet">
    /// <item>Set if <see cref="Kind"/> is <see cref="EValueKind.Boolean"/>.</item>
    /// </list>
    /// </remarks>
    public bool Flag { get; set; }

    /// <summary>
    /// Gets or sets a 64-bit signed integer value.
    /// </summary>
    /// <remarks>
    /// <list type="bullet">
    /// <item>Set if <see cref="Kind"/> is <see cref="EValueKind.Integer"/>.</item>
    /// </list>
    /// </remarks>
    public long Integer { get; set; }

    /// <summary>
    /// Represents a double-precision floating-point number.
    /// </summary>
    /// <remarks>
    /// <list type="bullet">
    /// <item>Set if <see cref="Kind"/> is <see cref="EValueKind.Integer"/>.</item>
    /// </list>
    /// </remarks>
    public double FloatingPoint { get; set; }


    /// <remarks>
    /// <list type="bullet">
    /// <item>Set if <see cref="Kind"/> is <see cref="EValueKind.KeyValuePairs"/>.</item>
    /// </list>
    /// </remarks>
    public KeyValuePair<Value, Value>[]? Pairs { get; set; }


    /// <remarks>
    /// <list type="bullet">
    /// <item>Set if <see cref="Kind"/> is <see cref="EValueKind.Array"/>.</item>
    /// </list>
    /// </remarks>
    public Value[]? Array { get; set; }

    /// <summary>
    /// Creates a new <see cref="Value"/> instance representing a value with no specific content.
    /// </summary>
    /// <returns>A <see cref="Value"/> instance with its kind set to <see cref="EValueKind.None"/>.</returns>
    public static Value CreateNone() => new Value { Kind = EValueKind.None };

    /// <summary>
    /// Creates a new <see cref="Value"/> instance representing a value with an "Okay" state.
    /// </summary>
    /// <returns>A <see cref="Value"/> instance with its kind set to <see cref="EValueKind.Okay"/>.</returns>
    public static Value CreateOkay() => new Value { Kind = EValueKind.Okay };

    /// <summary>
    /// Creates a new <see cref="Value"/> representing a formatted string with the specified key and value.
    /// </summary>
    /// <param name="key">The key used in the format string.</param>
    /// <param name="value">The value associated with the format string key.</param>
    /// <returns>A <see cref="Value"/> instance configured as a formatted string with the provided key and value.</returns>
    public static Value CreateFormatString(string? key, string? value)
    {
        return new Value
        {
            Kind = EValueKind.FormatString, Format = key, Data = value
        };
    }

    /// <summary>
    /// Creates a new <see cref="Value"/> instance representing a boolean value.
    /// </summary>
    /// <param name="b">The boolean value to be stored in the <see cref="Value"/> instance.</param>
    /// <returns>A <see cref="Value"/> configured with the specified boolean value.</returns>
    public static Value CreateBoolean(bool b)
    {
        return new Value
        {
            Kind = EValueKind.Boolean,
            Flag = b,
        };
    }

    /// <summary>
    /// Creates a new <see cref="Value"/> that contains the specified key-value pairs.
    /// </summary>
    /// <param name="pairs">An array of key-value pairs represented as <see cref="KeyValuePair{TKey, TValue}"/> where both key and value are of type <see cref="Value"/>.</param>
    /// <returns>A <see cref="Value"/> instance initialized with the provided key-value pairs.</returns>
    public static Value CreatePairs(KeyValuePair<Value, Value>[] pairs)
    {
        return new Value
        {
            Kind  = EValueKind.Boolean,
            Pairs = pairs,
        };
    }

    /// <summary>
    /// Creates a new <see cref="Value"/> representing an array of <see cref="Value"/> elements.
    /// </summary>
    /// <param name="array">The array of <see cref="Value"/> elements to be used.</param>
    /// <returns>A <see cref="Value"/> instance configured as an array containing the provided elements.</returns>
    public static Value CreateArray(Value[] array)
    {
        return new Value
        {
            Kind  = EValueKind.Array,
            Array = array,
        };
    }

    /// <summary>
    /// Creates a new <see cref="Value"/> representing an integer with the specified value.
    /// </summary>
    /// <param name="l">The integer value to be encapsulated in the <see cref="Value"/>.</param>
    /// <returns>A <see cref="Value"/> instance configured as an integer with the provided value.</returns>
    public static Value CreateInteger(long l)
    {
        return new Value
        {
            Kind    = EValueKind.Integer,
            Integer = l,
        };
    }

    /// <summary>
    /// Creates a new <see cref="Value"/> instance representing a string value using the provided input.
    /// </summary>
    /// <param name="s">The string value to be encapsulated within the <see cref="Value"/> instance. Can be null.</param>
    /// <returns>A <see cref="Value"/> instance configured with the provided string as its data.</returns>
    public static Value CreateString(string? s)
    {
        return new Value
        {
            Kind = EValueKind.String,
            Data = s,
        };
    }

    /// <summary>
    /// Creates a new <see cref="Value"/> representing a floating-point number with the specified value.
    /// </summary>
    /// <param name="f">The floating-point number to assign to the value.</param>
    /// <returns>A <see cref="Value"/> instance configured as a floating-point number with the provided value.</returns>
    public static Value CreateFloatingPoint(double f)
    {
        return new Value
        {
            Kind          = EValueKind.FloatingPoint,
            FloatingPoint = f,
        };
    }


    /// <inheritdoc />
    public int CompareTo(Value other)
    {
        if (Kind != other.Kind)
            return Kind.CompareTo(other.Kind);

        return Kind switch
        {
            EValueKind.None => 0,
            EValueKind.String => string.Compare(
                Data ?? string.Empty,
                other.Data ?? string.Empty,
                StringComparison.Ordinal
            ),
            EValueKind.FormatString => string.Compare(
                                           Format ?? string.Empty,
                                           other.Format ?? string.Empty,
                                           StringComparison.Ordinal
                                       )
                                       + string.Compare(
                                           Data ?? string.Empty,
                                           other.Data ?? string.Empty,
                                           StringComparison.Ordinal
                                       ),
            EValueKind.Boolean => Flag.CompareTo(other.Flag),
            EValueKind.KeyValuePairs => Pairs?.Zip(other.Pairs ?? [], (l, r) => (l, r))
                                            .Aggregate(
                                                0,
                                                (l, r) => l
                                                          + r.l.Key.CompareTo(r.r.Key)
                                                          + r.l.Value.CompareTo(r.r.Value) switch
                                                          {
                                                              > 0 => 1,
                                                              < 0 => -1,
                                                              0   => 0,
                                                          }
                                            )
                                        ?? 0,
            EValueKind.Integer       => Integer.CompareTo(other.Integer),
            EValueKind.FloatingPoint => FloatingPoint.CompareTo(other.FloatingPoint),
            EValueKind.Array => Array?.Zip(other.Array ?? [], (l, r) => (l, r))
                                    .Aggregate(0, (l, r) => l + r.l.CompareTo(r.r))
                                ?? 0,
            _ => throw new ArgumentOutOfRangeException(),
        };
    }

    /// <inheritdoc />
    public bool Equals(Value other)
    {
        if (Kind != other.Kind)
            return false;
        return Kind switch
        {
            EValueKind.None          => true,
            EValueKind.String        => Data == other.Data,
            EValueKind.FormatString  => Format == other.Format && Data == other.Data,
            EValueKind.Boolean       => Flag == other.Flag,
            EValueKind.KeyValuePairs => Pairs == other.Pairs,
            EValueKind.Integer       => Integer == other.Integer,
            // ReSharper disable once CompareOfFloatsByEqualityOperator -- We want explicit equality here
            EValueKind.FloatingPoint => FloatingPoint == other.FloatingPoint,
            EValueKind.Array         => Array == other.Array,
            _                        => throw new ArgumentOutOfRangeException()
        };
    }

    /// <inheritdoc />
    public override string ToString()
    {
        return Kind switch
        {
            EValueKind.None   => "none",
            EValueKind.String => string.Concat('"', Data?.Replace("\"", "\\\""), '"'),
            EValueKind.FormatString => string.Concat(
                "{ ",
                '"',
                Format?.Replace("\"", "\\\""),
                '"',
                ", ",
                '"',
                Data?.Replace("\"", "\\\""),
                '"',
                " }"
            ),
            EValueKind.Boolean       => Flag ? "true" : "false",
            EValueKind.KeyValuePairs => string.Concat("(Value, Value)[", Pairs?.Length.ToString() ?? "null", "]"),
            EValueKind.Integer       => Integer.ToString(),
            EValueKind.FloatingPoint => FloatingPoint.ToString(CultureInfo.InvariantCulture),
            EValueKind.Array         => string.Concat("Value[", Array?.Length.ToString() ?? "null", "]"),
            _                        => string.Empty,
        };
    }

    /// <inheritdoc />
    public bool Equals(Value x, Value y)
    {
        return x.Equals(y);
    }

    /// <inheritdoc />
    public int GetHashCode(Value obj)
    {
        unchecked
        {
            var hashCode = (int) obj.Kind;
            hashCode = (hashCode * 397) ^ (obj.Format != null ? obj.Format.GetHashCode() : 0);
            hashCode = (hashCode * 397) ^ (obj.Data != null ? obj.Data.GetHashCode() : 0);
            hashCode = (hashCode * 397) ^ obj.Flag.GetHashCode();
            hashCode = (hashCode * 397) ^ obj.Integer.GetHashCode();
            hashCode = (hashCode * 397) ^ obj.FloatingPoint.GetHashCode();
            hashCode = (hashCode * 397) ^ (obj.Pairs != null ? obj.Pairs.GetHashCode() : 0);
            hashCode = (hashCode * 397) ^ (obj.Array != null ? obj.Array.GetHashCode() : 0);
            return hashCode;
        }
    }

    /// <summary>
    /// Determines whether the current <see cref="Value"/> instance represents a value with no specific content.
    /// </summary>
    /// <returns>True if the <see cref="Kind"/> property is set to <see cref="EValueKind.None"/>; otherwise, false.</returns>
    public bool IsNone() => Kind == EValueKind.None;

    /// <summary>
    /// Determines whether the current instance represents a value with a kind set to <see cref="EValueKind.Okay"/>.
    /// </summary>
    /// <returns>A boolean value indicating whether the kind is <see cref="EValueKind.Okay"/>.</returns>
    public bool IsOk() => Kind == EValueKind.Okay;

    /// <summary>
    /// Determines if the current <see cref="Value"/> instance represents a string value and, if so, retrieves its content.
    /// </summary>
    /// <param name="oText">When this method returns, contains the string value if the <see cref="Value"/> instance represents a <see cref="string"/>; otherwise, <see langword="null"/>.</param>
    /// <returns>True if the current <see cref="Value"/> instance is of kind <see cref="EValueKind.String"/>; otherwise, false.</returns>
    public bool IsString(out string oText)
    {
        if (Kind is EValueKind.String)
        {
            oText = Data ?? throw new NullReferenceException("Data is null although Kind is String");
            return true;
        }

        oText = null!;
        return false;
    }
}
