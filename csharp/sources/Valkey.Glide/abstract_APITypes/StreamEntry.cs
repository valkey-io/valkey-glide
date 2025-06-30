﻿using System;

namespace Valkey.Glide;

/// <summary>
/// Describes an entry contained in a Valkey Stream.
/// </summary>
public readonly struct StreamEntry
{
    /// <summary>
    /// Creates an stream entry.
    /// </summary>
    public StreamEntry(ValkeyValue id, NameValueEntry[] values)
    {
        Id = id;
        Values = values;
    }

    /// <summary>
    /// A null stream entry.
    /// </summary>
    public static StreamEntry Null { get; } = new StreamEntry(ValkeyValue.Null, Array.Empty<NameValueEntry>());

    /// <summary>
    /// The ID assigned to the message.
    /// </summary>
    public ValkeyValue Id { get; }

    /// <summary>
    /// The values contained within the message.
    /// </summary>
    public NameValueEntry[] Values { get; }

    /// <summary>
    /// Search for a specific field by name, returning the value.
    /// </summary>
    public ValkeyValue this[ValkeyValue fieldName]
    {
        get
        {
            var values = Values;
            if (values != null)
            {
                for (int i = 0; i < values.Length; i++)
                {
                    if (values[i].name == fieldName)
                        return values[i].value;
                }
            }
            return ValkeyValue.Null;
        }
    }

    /// <summary>
    /// Indicates that the Valkey Stream Entry is null.
    /// </summary>
    public bool IsNull => Id == ValkeyValue.Null && Values == Array.Empty<NameValueEntry>();
}
