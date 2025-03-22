// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Collections;
using System.ComponentModel;
using System.Text;

namespace Glide;

/// <summary>
/// Fancy extensions for <see langword="string" /> and <see langword="byte[]" />, which help working with <see cref="GlideString" />.
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
    /// Convert a <paramref name="bytes"/> to a <see cref="GlideString" />.
    /// </summary>
    /// <param name="bytes">A <see langword="byte[]" /> to convert.</param>
    /// <returns>A <see cref="GlideString" />.</returns>
    public static GlideString ToGlideString(this byte[] bytes) => new(bytes);

    /// <summary>
    /// Convert an <see langword="string[]" /> to an <see langword="GlideString[]" />.
    /// </summary>
    /// <param name="strings">An array of <see langword="string" />s to convert.</param>
    /// <returns>An array of <see cref="GlideString" />s.</returns>
    public static GlideString[] ToGlideStrings(this string[] strings) => strings.Select(s => new GlideString(s)).ToArray();

    /// <summary>
    /// Convert an array of <see langword="byte[]" />s to an <see langword="GlideString[]" />.
    /// </summary>
    /// <param name="strings">An array of <see langword="byte[]" />s to convert.</param>
    /// <returns>An array of <see cref="GlideString" />s.</returns>
    public static GlideString[] ToGlideStrings(this byte[][] strings) => strings.Select(s => new GlideString(s)).ToArray();

    /// <summary>
    /// Convert an <see langword="GlideString[]" /> to an <see langword="string[]" />.<br />
    /// <b>Note:</b> a resulting <see langword="string" /> may be incorrect if original <see cref="GlideString" />
    /// stores a non-UTF8 compatible sequence of bytes.
    /// </summary>
    /// <param name="strings">An array of <see cref="GlideString" />s to convert.</param>
    /// <returns>An array of <see langword="string" />s.</returns>
    public static string[] ToStrings(this GlideString[] strings) => strings.Select(s => s.ToString()).ToArray();

    /// <summary>
    /// Convert an <see langword="GlideString[]" /> to an array of <see langword="byte[]" />.<br />
    /// </summary>
    /// <param name="strings">An array of <see cref="GlideString" />s to convert.</param>
    /// <returns>An array of <see langword="byte[]" />.</returns>
    public static byte[][] ToByteArrays(this GlideString[] strings) => strings.Select(s => s.Bytes).ToArray();
}

/// <summary>
/// Represents a Valkey string type. Since Valkey stores strings as <see langword="byte[]" />,
/// such strings can contain non-UTF8 compatible symbols or even arbitrary binary data BLOBs.<br />
/// This class stores data as <see langword="byte[]" /> too, but provides API to represent data
/// as a <see langword="string" /> if conversion is possible.<br />
/// A <see cref="GlideString" /> could be implicitly instatiated from a <see langword="string" />.
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
/// </summary>
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
        Str = $"Value isn't convertible to string: [{string.Join(' ', bytes.Select(b => $"{b:X2}").ToArray())}]";
    }

    /// <summary>
    /// Create a <see cref="GlideString" /> initiated by a <see langword="string" />.<br />
    /// </summary>
    /// <param name="string">A <see langword="byte[]" /> to store.</param>
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
    /// <example>
    /// <code>
    /// GlideString gs = new byte[] { 0, 42, 255, 243, 0, 253, 15 };
    /// if (gs.CanConvertToString())
    /// {
    ///     string str = gs; // Conversion without data loss is impossible, so str is incorrect.
    /// }
    /// </code>
    /// </example>
    /// </summary>
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
        return new(left.Bytes.Concat(right.Bytes).ToArray());
    }

    public override int GetHashCode() => ((IStructuralEquatable)Bytes).GetHashCode(EqualityComparer<byte>.Default);

    internal byte[] Bytes { get; }

    internal string Str;

    private bool? _canConvertToString;

    private readonly object _lock = new();
}
