using System;
using System.Collections.Generic;

namespace Valkey.Glide;

/// <summary>
/// GeoRadius command options.
/// </summary>
[Flags]
public enum GeoRadiusOptions
{
    /// <summary>
    /// No Options.
    /// </summary>
    None = 0,

    /// <summary>
    /// Valkey will return the coordinates of any results.
    /// </summary>
    WithCoordinates = 1,

    /// <summary>
    /// Valkey will return the distance from center for all results.
    /// </summary>
    WithDistance = 2,

    /// <summary>
    /// Valkey will return the geo hash value as an integer. (This is the score in the sorted set).
    /// </summary>
    WithGeoHash = 4,

    /// <summary>
    /// Populates the commonly used values from the entry (the integer hash is not returned as it is not commonly useful).
    /// </summary>
    Default = WithCoordinates | WithDistance,
}

internal static class GeoRadiusOptionsExtensions
{
    internal static void AddArgs(this GeoRadiusOptions options, List<ValkeyValue> values)
    {
        if ((options & GeoRadiusOptions.WithCoordinates) != 0)
        {
            values.Add(ValkeyLiterals.WITHCOORD);
        }
        if ((options & GeoRadiusOptions.WithDistance) != 0)
        {
            values.Add(ValkeyLiterals.WITHDIST);
        }
        if ((options & GeoRadiusOptions.WithGeoHash) != 0)
        {
            values.Add(ValkeyLiterals.WITHHASH);
        }
    }
}
