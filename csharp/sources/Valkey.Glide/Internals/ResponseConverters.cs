// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.Errors;
using static Valkey.Glide.Route;

namespace Valkey.Glide.Internals;

internal class ResponseConverters
{
    //public static string HandleOk(object? value)
    //    => HandleServerResponse<string>(value, false);

    //public static T HandleServerResponse<T>(object? value, bool isNullable) where T : class?
    //    => HandleServerResponse<T, T>(value, isNullable, o => o);

    public static ClusterValue<object?> HandleCustomCommandClusterValue(object? value, Route? route = null)
        => HandleServerValue<object, ClusterValue<object?>>(value, true, data
            => (data is string str && str == "OK") || route is SingleNodeRoute || data is not Dictionary<GlideString, object?>
                ? ClusterValue<object?>.OfSingleValue(data)
                : ClusterValue<object?>.OfMultiValue((Dictionary<GlideString, object?>)data));

    /*
    /// <summary>
    /// Process and convert a server response that may be a multi-node response.
    /// </summary>
    /// <typeparam name="R">GLIDE's return type per node.</typeparam>
    /// <typeparam name="T">Command's return type.</typeparam>
    /// <param name="value"></param>
    /// <param name="isNullable"></param>
    /// <param name="converter">Function to convert <typeparamref name="R"/> to <typeparamref name="T"/>.</param>
    public static ClusterValue<T> HandleClusterValue<R, T>(object? value, bool isNullable, Route route, Func<R, T> converter) where T : class?
        => HandleServerValue<object, ClusterValue<T>>(value, isNullable, data => route is SingleNodeRoute
            ? ClusterValue<T>.OfSingleValue(converter((R)data))
            : ClusterValue<T>.OfMultiValue(((Dictionary<GlideString, object>)data).ConvertValues(converter)));
    */

    /// <summary>
    /// Process and convert a cluster multi-node response.
    /// </summary>
    /// <typeparam name="R">GLIDE's return type per node.</typeparam>
    /// <typeparam name="T">Command's return type.</typeparam>
    /// <param name="value"></param>
    /// <param name="converter">Function to convert <typeparamref name="R"/> to <typeparamref name="T"/> (dictionary values).</param>
    public static Dictionary<string, T> HandleMultiNodeValue<R, T>(Dictionary<GlideString, object> dict, Func<R, T> converter) where T : class? where R : class?
        => dict.DownCastKeys().ConvertValues(converter);

    /// <summary>
    /// Process and convert a server response.
    /// </summary>
    /// <typeparam name="R">GLIDE's return type.</typeparam>
    /// <typeparam name="T">Command's return type.</typeparam>
    /// <param name="value"></param>
    /// <param name="isNullable"></param>
    /// <param name="converter">Optional function to convert <typeparamref name="R" /> to <typeparamref name="T" />.</param>
    /// <returns></returns>
    /// <exception cref="Exception"></exception>
    public static T HandleServerValue<R, T>(object? value, bool isNullable, Func<R, T> converter) where T : class? where R : class?
    {
        if (value is null)
        {
            if (isNullable)
            {
#pragma warning disable CS8603 // Possible null reference return.
                return null;
#pragma warning restore CS8603 // Possible null reference return.
            }
            throw new RequestException($"Unexpected return type from Glide: got null expected {typeof(T).GetRealTypeName()}");
        }
        return value is R
            ? converter((value as R)!)
            : throw new RequestException($"Unexpected return type from Glide: got {value?.GetType().GetRealTypeName()} expected {typeof(T).GetRealTypeName()}");
    }


    //public static Dictionary<K, T> CastDictValues<K, T, V>(Dictionary<K, V> dict) where T : class? where V : class? where K : class
    //{
    //    return dict.Select(pair => (Key: pair.Key, Value: pair.Value as T)).ToDictionary(pair => pair.Key, pair => pair.Value);
    //}
}
