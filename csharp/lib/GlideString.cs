// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Text;

namespace Glide;

// TODO docs for the god of docs ©
// and examples

public static class GlideStringExtensions
{
    public static GlideString ToGlideString(this string @string) => new(@string);
    public static GlideString ToGlideString(this byte[] bytes) => new(bytes);

    public static GlideString[] ToGlideStrings(this string[] strings) => [.. strings.Select(s => new GlideString(s))];
    public static GlideString[] ToGlideStrings(this byte[][] strings) => [.. strings.Select(s => new GlideString(s))];

    public static string[] ToStrings(this GlideString[] strings) => [.. strings.Select(s => s.ToString())];
    public static byte[][] ToByteArrays(this GlideString[] strings) => [.. strings.Select(s => s.Bytes)];
}

public sealed class GlideString : IComparable<GlideString>
{
    internal GlideString(byte[] bytes)
    {
        ConversionChecked = false;
        Bytes = bytes;
    }

    internal GlideString(string @string)
    {
        ConversionChecked = true;
        Str = @string;
        Bytes = Encoding.UTF8.GetBytes(@string);
    }

    public static GlideString Of(byte[] bytes) => new(bytes);
    public static GlideString Of(string @string) => new(@string);

    public ReadOnlySpan<byte> GetBytes() => Bytes;
    public int Length => Bytes.Length;

    public string GetString()
    {
        if (Str is not null)
        {
            return Str;
        }
        _ = CanConvertToString();
        return Str ?? "Value isn't convertible to string";
    }

    public override string ToString() => GetString();

    public bool CanConvertToString()
    {
        if (Str is not null)
        {
            return true;
        }

        if (ConversionChecked)
        {
            return false;
        }

        // double-checked locking
        lock (this)
        {
            if (ConversionChecked)
            {
                return Str is not null;
            }

            // TODO find a better way to check this
            // Detect whether `bytes` could be represented by a UTF-8 string without data corruption
            string tmpStr = Encoding.UTF8.GetString(Bytes);
            if (Encoding.UTF8.GetBytes(tmpStr).SequenceEqual(Bytes))
            {
                Str = tmpStr;
            }
            ConversionChecked = true;
            return Str is not null;
        }
    }

    public static implicit operator string(GlideString gs) => gs.ToString();
    public static implicit operator byte[](GlideString gs) => gs.Bytes;

    public static implicit operator GlideString(string @string) => new(@string);
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

        // TODO
        return Bytes.SequenceEqual(other.Bytes) ? 0 : 1;
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

    public override int GetHashCode() => Bytes.GetHashCode();

    internal byte[] Bytes { get; }

    internal string? Str = null;

    internal bool ConversionChecked;
}
