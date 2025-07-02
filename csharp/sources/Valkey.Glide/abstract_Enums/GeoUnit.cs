using System;

namespace Valkey.Glide;

/// <summary>
/// Units associated with Geo Commands.
/// </summary>
public enum GeoUnit
{
    /// <summary>
    /// Meters.
    /// </summary>
    Meters,

    /// <summary>
    /// Kilometers.
    /// </summary>
    Kilometers,

    /// <summary>
    /// Miles.
    /// </summary>
    Miles,

    /// <summary>
    /// Feet.
    /// </summary>
    Feet,
}

internal static class GeoUnitExtensions
{
    internal static ValkeyValue ToLiteral(this GeoUnit unit) => unit switch
    {
        GeoUnit.Feet => ValkeyLiterals.ft,
        GeoUnit.Kilometers => ValkeyLiterals.km,
        GeoUnit.Meters => ValkeyLiterals.m,
        GeoUnit.Miles => ValkeyLiterals.mi,
        _ => throw new ArgumentOutOfRangeException(nameof(unit)),
    };
}
