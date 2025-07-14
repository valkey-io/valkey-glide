// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Collections;
using System.ComponentModel;
using System.Text;

using static Valkey.Glide.Commands.Options.InfoOptions;

namespace Valkey.Glide;

/// <summary>
/// Fancy extensions for different types, which help working with <see cref="GlideString" />.
/// </summary>
public static class GlideStringExtensions
{
    /// <summary>
    /// Convert a <paramref name="string"/> to a <see cref="GlideString" />.
    /// </summary>
    /// <param name="string">A <see langword="string" /> to convert.</param>
    /// <returns>A <see cref="GlideString" />.</returns>
    public static GlideString ToGlideString(this string @string) => new(@string);

    /// <summary>
    /// Convert a <paramref name="int"/> to a <see cref="GlideString" />.
    /// </summary>
    /// <param name="int">A <see langword="int" /> to convert.</param>
    /// <returns>A <see cref="GlideString" />.</returns>
    public static GlideString ToGlideString(this int @int) => new(@int.ToString());

    /// <summary>
    /// Convert a <paramref name="long"/> to a <see cref="GlideString" />.
    /// </summary>
    /// <param name="long">A <see langword="long" /> to convert.</param>
    /// <returns>A <see cref="GlideString" />.</returns>
    public static GlideString ToGlideString(this long @long) => new(@long.ToString());

    /// <summary>
    /// Convert a <paramref name="double"/> to a <see cref="GlideString" />.
    /// </summary>
    /// <param name="double">A <see langword="double" /> to convert.</param>
    /// <returns>A <see cref="GlideString" />.</returns>
    public static GlideString ToGlideString(this double @double)
        => double.IsPositiveInfinity(@double) ? new("+inf")
            : double.IsNegativeInfinity(@double) ? new("-inf")
            : double.IsNaN(@double) ? new("nan")
            : new(@double.ToString("G17", System.Globalization.CultureInfo.InvariantCulture));

    /// <summary>
    /// Convert a <paramref name="bytes"/> to a <see cref="GlideString" />.
    /// </summary>
    /// <param name="bytes">A <see langword="byte[]" /> to convert.</param>
    /// <returns>A <see cref="GlideString" />.</returns>
    public static GlideString ToGlideString(this byte[] bytes) => new(bytes);

    /// <summary>
    /// Convert a <paramref name="key"/> to a <see cref="GlideString" />.
    /// </summary>
    /// <param name="key">A <see langword="ValkeyKey" /> to convert.</param>
    /// <returns>A <see cref="GlideString" />.</returns>
    public static GlideString ToGlideString(this ValkeyKey key) => (GlideString)key;

    /// <summary>
    /// Convert a <paramref name="value"/> to a <see cref="GlideString" />.
    /// </summary>
    /// <param name="value">A <see langword="ValkeyValue" /> to convert.</param>
    /// <returns>A <see cref="GlideString" />.</returns>
    public static GlideString ToGlideString(this ValkeyValue value) => (GlideString)value;

    /// <summary>
    /// Convert an <see langword="string[]" /> to an <see langword="GlideString[]" />.
    /// </summary>
    /// <param name="strings">An array of <see langword="string" />s to convert.</param>
    /// <returns>An array of <see cref="GlideString" />s.</returns>
    public static GlideString[] ToGlideStrings(this string[] strings) => [.. strings.Select(s => new GlideString(s))];

    /// <summary>
    /// Convert an array of <see langword="byte[]" />s to an <see langword="GlideString[]" />.
    /// </summary>
    /// <param name="strings">An array of <see langword="byte[]" />s to convert.</param>
    /// <returns>An array of <see cref="GlideString" />s.</returns>
    public static GlideString[] ToGlideStrings(this byte[][] strings) => [.. strings.Select(s => new GlideString(s))];

    /// <summary>
    /// Convert an <see langword="Section[]" /> to an <see langword="GlideString[]" />.
    /// </summary>
    /// <param name="strings">An array of <see langword="string" />s to convert.</param>
    /// <returns>An array of <see cref="GlideString" />s.</returns>
    public static GlideString[] ToGlideStrings(this Section[] strings) => [.. strings.Select(s => new GlideString(s.ToString()))];

    /// <summary>
    /// Convert an <see langword="ValkeyKey[]" /> to an <see langword="GlideString[]" />.
    /// </summary>
    /// <param name="keys">An array of <see langword="ValkeyKey" />s to convert.</param>
    /// <returns>An array of <see cref="GlideString" />s.</returns>
    public static GlideString[] ToGlideStrings(this ValkeyKey[] keys) => [.. keys.Select(k => (GlideString)k)];

    /// <summary>
    /// Convert an <see langword="ValkeyValue[]" /> to an <see langword="GlideString[]" />.
    /// </summary>
    /// <param name="values">An array of <see langword="ValkeyValue" />s to convert.</param>
    /// <returns>An array of <see cref="GlideString" />s.</returns>
    public static GlideString[] ToGlideStrings(this ValkeyValue[] values) => [.. values.Select(v => (GlideString)v)];

    /// <summary>
    /// Convert an <see langword="GlideString[]" /> to an <see langword="string[]" />.<br />
    /// <b>Note:</b> a resulting <see langword="string" /> may be incorrect if original <see cref="GlideString" />
    /// stores a non-UTF8 compatible sequence of bytes.
    /// </summary>
    /// <param name="strings">An array of <see cref="GlideString" />s to convert.</param>
    /// <returns>An array of <see langword="string" />s.</returns>
    public static string[] ToStrings(this GlideString[] strings) => [.. strings.Select(s => s.ToString())];

    /// <summary>
    /// Convert an <see langword="GlideString[]" /> to an array of <see langword="byte[]" />.<br />
    /// </summary>
    /// <param name="strings">An array of <see cref="GlideString" />s to convert.</param>
    /// <returns>An array of <see langword="byte[]" />.</returns>
    public static byte[][] ToByteArrays(this GlideString[] strings) => [.. strings.Select(s => s.Bytes)];
}

/// <summary>
/// Represents a Valkey string type. Since Valkey stores strings as <see langword="byte[]" />,
/// such strings can contain non-UTF8 compatible symbols or even arbitrary binary data BLOBs.<br />
/// This class stores data as <see langword="byte[]" /> too, but provides API to represent data
/// as a <see langword="string" /> if conversion is possible.<br />
/// A <see cref="GlideString" /> could be implicitly instatiated from a <see langword="string" />.
/// </summary>
/// <remarks>
/// <example>
/// <code>
/// GlideString gs1 = "123";
/// gs1 += "abc";
/// string str1 = gs1; // GlideString converted to a string
///
/// GlideString gs2 = new byte[] { 0, 42, 255, 243, 0, 253, 15 };
/// if (gs2.CanConvertToString())
/// {
///     string str2 = gs2; // Conversion without data loss is impossible, so str2 is incorrect.
/// }
/// </code>
/// </example>
/// </remarks>
[ImmutableObject(true)]
public sealed class GlideString : IComparable<GlideString>
{
    /// <summary>
    /// Create a <see cref="GlideString" /> initiated by a <see langword="byte[]" />.<br />
    /// <paramref name="bytes"/> can store any arbirtrary binary data.
    /// </summary>
    /// <param name="bytes">A <see langword="byte[]" /> to store.</param>
    public GlideString(byte[] bytes)
    {
        _canConvertToString = null;
        Bytes = bytes;
        Str = $"Value isn't convertible to string: [{string.Join(' ', [.. bytes.Select(b => $"{b:X2}")])}]";
    }

    /// <summary>
    /// Create a <see cref="GlideString" /> initiated by a <see langword="string" />.<br />
    /// </summary>
    /// <param name="string">A <see langword="string" /> to store.</param>
    public GlideString(string @string)
    {
        _canConvertToString = true;
        Str = @string;
        Bytes = Encoding.UTF8.GetBytes(@string);
    }

    /// <inheritdoc cref="GlideString(byte[])" />
    public static GlideString Of(byte[] bytes) => new(bytes);
    /// <inheritdoc cref="GlideString(string)" />
    public static GlideString Of(string @string) => new(@string);

    /// <summary>
    /// Get a reference to a <see langword="byte[]" /> stored in this <see cref="GlideString" />.
    /// </summary>
    /// <returns>A <see langword="byte[]" /> of that <see cref="GlideString" />.</returns>
    public ReadOnlySpan<byte> GetBytes() => Bytes;
    /// <summary>
    /// Get a length of a <see langword="byte[]" /> stored in this <see cref="GlideString" />.
    /// </summary>
    public int Length => Bytes.Length;

    /// <summary>
    /// Convert this <see cref="GlideString" /> to a <see langword="string" /> if possible.<br />
    /// <b>Note:</b> a resulting <see langword="string" /> may be incorrect if original <see cref="GlideString" />
    /// stores a non-UTF8 compatible sequence of bytes. It is <b>highly recommended</b> to call to
    /// <see cref="CanConvertToString()" /> prior to do a conversion.
    /// </summary>
    /// <remarks>
    /// <example>
    /// <code>
    /// GlideString gs = new byte[] { 0, 42, 255, 243, 0, 253, 15 };
    /// if (gs.CanConvertToString())
    /// {
    ///     string str = gs; // Conversion without data loss is impossible, so str is incorrect.
    /// }
    /// </code>
    /// </example>
    /// </remarks>
    /// <returns>A <see langword="string" /> representation of this <see cref="GlideString" />.</returns>
    public string GetString()
    {
        if (_canConvertToString ?? false)
        {
            return Str;
        }
        _ = CanConvertToString();
        return Str;
    }

    /// <inheritdoc cref="GetString()" />
    public override string ToString() => GetString();

    /// <summary>
    /// Check whether <see cref="GlideString" /> could be converted to a <see langword="string" /> without data loss.<br />
    /// Use this method prior to converting a <see cref="GlideString" /> to a <see langword="string" />.
    /// </summary>
    /// <returns><see langword="true" /> if <see cref="GlideString" /> could be safely converted to a <see langword="string" />.</returns>
    public bool CanConvertToString()
    {
        if (_canConvertToString is not null)
        {
            return (bool)_canConvertToString;
        }

        // double-checked locking
        lock (_lock)
        {
            if (_canConvertToString is not null)
            {
                return (bool)_canConvertToString;
            }

            // TODO find a better way to check this
            // Detect whether `bytes` could be represented by a UTF-8 string without data corruption
            string tmpStr = Encoding.UTF8.GetString(Bytes);
            if (Encoding.UTF8.GetBytes(tmpStr).SequenceEqual(Bytes))
            {
                Str = tmpStr;
                _canConvertToString = true;
                return true;
            }
            _canConvertToString = false;
            return false;
        }
    }

    /// <inheritdoc cref="GetString()" />
    public static implicit operator string?(GlideString? gs) => gs?.ToString();
    /// <inheritdoc cref="GetBytes()" />
    public static implicit operator byte[]?(GlideString? gs) => gs?.Bytes;

    /// <inheritdoc cref="GlideString(string)" />
    public static implicit operator GlideString(string @string) => new(@string);
    /// <inheritdoc cref="GlideString(byte[])" />
    public static implicit operator GlideString(byte[] bytes) => new(bytes);

#pragma warning disable IDE0072 // Add missing cases
    public static implicit operator GlideString(ValkeyValue value) => value.Type switch
    {
        ValkeyValue.StorageType.Null => new GlideString([]),
        ValkeyValue.StorageType.Raw => new GlideString(((ReadOnlyMemory<byte>)value).ToArray()),
        _ => new GlideString((string)value!),
    };
#pragma warning restore IDE0072 // Add missing cases

    public static implicit operator GlideString(ValkeyKey key)
    {
        byte[]? keyBytes = key;
        return keyBytes == null ? new GlideString([]) : new GlideString(keyBytes);
    }

    public int CompareTo(GlideString? other)
    {
        if (other is null)
        {
            return -1;
        }
        if (other == this)
        {
            return 0;
        }

        if (Length != other.Length)
        {
            return Length - other.Length;
        }

        for (int i = 0; i < Length; i++)
        {
            if (Bytes[i] != other.Bytes[i])
            {
                return Bytes[i] - other.Bytes[i];
            }
        }
        return 0;
    }

    public override bool Equals(object? obj)
    {
        if (ReferenceEquals(this, obj))
        {
            return true;
        }

#pragma warning disable IDE0046 // Convert to conditional expression
        if (obj is null || obj.GetType() != typeof(GlideString))
        {
            return false;
        }
#pragma warning restore IDE0046 // Convert to conditional expression

        return this == (GlideString)obj;
    }

    public static bool operator ==(GlideString left, GlideString right) => left.Bytes.SequenceEqual(right.Bytes);

    public static bool operator !=(GlideString left, GlideString right) => !(left == right);

    public static bool operator <(GlideString left, GlideString right) => left.CompareTo(right) < 0;

    public static bool operator <=(GlideString left, GlideString right) => left.CompareTo(right) <= 0;

    public static bool operator >(GlideString left, GlideString right) => left.CompareTo(right) > 0;

    public static bool operator >=(GlideString left, GlideString right) => left.CompareTo(right) >= 0;

    public static GlideString operator +(GlideString left, GlideString right)
    {
        // Store strings if they're both defined
#pragma warning disable IDE0046 // Convert to conditional expression
        if (left._canConvertToString is not null && (bool)left._canConvertToString &&
            right._canConvertToString is not null && (bool)right._canConvertToString)
        {
            return new(left.Str + right.Str);
        }
#pragma warning restore IDE0046 // Convert to conditional expression
        return new([.. left.Bytes, .. right.Bytes]);
    }

    public override int GetHashCode() => ((IStructuralEquatable)Bytes).GetHashCode(EqualityComparer<byte>.Default);

    internal byte[] Bytes { get; }

    internal string Str;

    private bool? _canConvertToString;

    private readonly object _lock = new();
}
