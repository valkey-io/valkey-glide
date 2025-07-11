// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.Errors;
using static Valkey.Glide.Route;

namespace Valkey.Glide.Internals;

internal class ResponseConverters
{
    public static ClusterValue<object?> HandleCustomCommandClusterValue(object? value, Route? route = null)
        => HandleServerValue<object, ClusterValue<object?>>(value, true, data
            => (data is string str && str == "OK") || route is SingleNodeRoute || data is not Dictionary<GlideString, object?>
                ? ClusterValue<object?>.OfSingleValue(data)
                : ClusterValue<object?>.OfMultiValue((Dictionary<GlideString, object?>)data));

    /// <summary>
    /// Process and convert a server response that may be a multi-node response.
    /// </summary>
    /// <typeparam name="R">GLIDE's return type per node.</typeparam>
    /// <typeparam name="T">Command's return type.</typeparam>
    /// <param name="converter">Function to convert <typeparamref name="R"/> to <typeparamref name="T"/>.</param>
    /// <param name="isSingleValue">Whether current command call returns a single value.</param>
    public static Func<object, ClusterValue<T>> MakeClusterValueHandler<R, T>(Func<R, T> converter, bool isSingleValue)
        => isSingleValue
            ? value => ClusterValue<T>.OfSingleValue(converter((R)value))
            : value => ClusterValue<T>.OfMultiValue(((Dictionary<GlideString, object>)value).ConvertValues(converter));

    /// <summary>
    /// Process and convert a cluster multi-node response.
    /// </summary>
    /// <typeparam name="R">GLIDE's return type per node.</typeparam>
    /// <typeparam name="T">Command's return type.</typeparam>
    /// <param name="dict">Value from GLIDE core to handle.</param>
    /// <param name="converter">Function to convert <typeparamref name="R"/> to <typeparamref name="T"/> (dictionary values).</param>
    /// <returns>A converted value.</returns>
    public static Dictionary<string, T> HandleMultiNodeValue<R, T>(Dictionary<GlideString, object> dict, Func<R, T> converter)
        => dict.DownCastKeys().ConvertValues(converter);

    /// <summary>
    /// Process and convert a server response.
    /// </summary>
    /// <typeparam name="R">GLIDE's return type.</typeparam>
    /// <typeparam name="T">Command's return type.</typeparam>
    /// <param name="value">Value from GLIDE core to handle.</param>
    /// <param name="isNullable">Whether it could be nullable.</param>
    /// <param name="converter">Optional function to convert <typeparamref name="R" /> to <typeparamref name="T" />.</param>
    /// <returns>A converted value.</returns>
    /// <exception cref="Exception">When <paramref name="value"/> has incorrect type or value.</exception>
    public static T HandleServerValue<R, T>(object? value, bool isNullable, Func<R, T> converter)
    {
        Console.WriteLine($"Handling value: {value} ({value?.GetType().GetRealTypeName()})");

        if (value is null)
        {
            if (isNullable)
            {
#pragma warning disable CS8603 // Possible null reference return.
                return default; // will return a null
#pragma warning restore CS8603 // Possible null reference return.
            }
            throw new RequestException($"Unexpected return type from Glide: got null expected {typeof(T).GetRealTypeName()}");
        }
        return value is R val
            ? converter(val)
            : throw new RequestException($"Unexpected return type from Glide: got {value?.GetType().GetRealTypeName()} expected {typeof(R).GetRealTypeName()}");
    }
}
