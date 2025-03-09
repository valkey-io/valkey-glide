using System.Collections.Generic;
using System.Diagnostics.CodeAnalysis;

namespace Valkey.Glide.InterOp;

public enum EValueKind
{
    None,
    String,
    FormatString,
    Boolean,
    KeyValuePairs,
    Long,
    Array,
}

[SuppressMessage("ReSharper", "UnusedAutoPropertyAccessor.Global")]
public struct Value
{
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

    /// <remarks>
    /// <list type="bullet">
    /// <item>Set if <see cref="Kind"/> is <see cref="EValueKind.Long"/>.</item>
    /// </list>
    /// </remarks>
    public long Long { get; set; }


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

    public Value(string? key, string? value)
    {
        Format = key;
        Data   = value;
    }

    public Value(bool b)
    {
        Flag = b;
    }

    public Value(KeyValuePair<Value, Value>[] array)
    {
        Pairs = array;
    }

    public Value(Value[] array)
    {
        Array = array;
    }

    public Value(long l)
    {
        Long = l;
    }

    public Value(string? s)
    {
        Data = s;
    }
}
