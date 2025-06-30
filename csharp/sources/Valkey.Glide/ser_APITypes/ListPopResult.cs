using System;

namespace Valkey.Glide;

/// <summary>
/// A contiguous portion of a redis list.
/// </summary>
public readonly struct ListPopResult
{
    /// <summary>
    /// A null ListPopResult, indicating no results.
    /// </summary>
    public static ListPopResult Null { get; } = new ListPopResult(ValkeyKey.Null, Array.Empty<ValkeyValue>());

    /// <summary>
    /// Whether this object is null/empty.
    /// </summary>
    public bool IsNull => Key.IsNull && Values == Array.Empty<ValkeyValue>();

    /// <summary>
    /// The key of the list that this set of entries came form.
    /// </summary>
    public ValkeyKey Key { get; }

    /// <summary>
    /// The values from the list.
    /// </summary>
    public ValkeyValue[] Values { get; }

    internal ListPopResult(ValkeyKey key, ValkeyValue[] values)
    {
        Key = key;
        Values = values;
    }
}
