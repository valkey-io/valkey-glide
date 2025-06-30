﻿using System;

namespace Valkey.Glide;

/// <summary>
/// The direction in which to sequence elements.
/// </summary>
public enum Order
{
    /// <summary>
    /// Ordered from low values to high values.
    /// </summary>
    Ascending,

    /// <summary>
    /// Ordered from high values to low values.
    /// </summary>
    Descending,
}

internal static class OrderExtensions
{
    internal static ValkeyValue ToLiteral(this Order order) => order switch
    {
        Order.Ascending => ValkeyLiterals.ASC,
        Order.Descending => ValkeyLiterals.DESC,
        _ => throw new ArgumentOutOfRangeException(nameof(order)),
    };
}
