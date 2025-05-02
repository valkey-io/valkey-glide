using Valkey.Glide.Commands.Options;
using Valkey.Glide.InterOp;
using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide;

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
    public static GlideString[] ToGlideStrings(this InfoOptions.Section[] strings) => [.. strings.Select(s => new GlideString(s.ToString()))];

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