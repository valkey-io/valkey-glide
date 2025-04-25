// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Internals;

namespace Valkey.Glide;

/// <summary>
/// Represents a returned value object from a server with cluster-mode enabled. The response type
/// may depend on the submitted <see cref="Route"/>.<br />
/// See also <seealso href="https://valkey.io/docs/topics/cluster-spec/">Valkey cluster specification</seealso>.
/// </summary>
/// <remarks>
/// <see langword="ClusterValue" /> stores values in a union-like object. It contains a single-value or
/// multi-value response from the server. If the command's routing is to a single node use
/// <see cref="ClusterValue{T}.SingleValue" /> to return a response of type <typeparamref name="T" />.
/// Otherwise, use <see cref="ClusterValue{T}.MultiValue" /> to return a <see langword="Dictionary" />
/// of <c>address: nodeResponse</c> where <c>address</c> is of type <see langword="string" /> and
/// <c>nodeResponse</c> is of type <typeparamref name="T" />.
/// </remarks>
/// <typeparam name="T">The wrapped response type</typeparam>
public class ClusterValue<T>
{
    private Dictionary<string, T>? _multiValue = default;
    private T? _singleValue = default;

    private ClusterValue() { }

    /// <summary>
    /// A constructor for the value with type auto-detection.
    /// </summary>
    internal static ClusterValue<T> Of(object obj)
    {
        if (obj is Dictionary<string, T> dict)
        {
            return OfMultiValue(dict);
        }
        else if (obj is Dictionary<GlideString, T> dictGs)
        {
            return OfMultiValue(dictGs);
        }
        return OfSingleValue((T)obj);
    }

    internal static ClusterValue<T> OfSingleValue(T obj)
        => new() { _singleValue = obj };

    internal static ClusterValue<T> OfMultiValue(Dictionary<string, T> obj)
        => new() { _multiValue = obj };

    internal static ClusterValue<T> OfMultiValue(Dictionary<GlideString, T> obj)
        => new() { _multiValue = obj.DownCastKeys() };

    /// <summary>
    /// Get per-node value.<br />
    /// Asserts if <see cref="HasMultiData" /> is <see langword="false" />.
    /// </summary>
    public Dictionary<string, T> MultiValue
        => _multiValue ?? throw new Exception("No multi value stored");

    /// <summary>
    /// Get the single value.<br />
    /// Asserts if <see cref="HasSingleData" /> is <see langword="false" />.
    /// </summary>
    public T SingleValue
        => _singleValue ?? throw new Exception("No single value stored");

    /// <summary>
    /// Check that multi-value is stored in this object. Should be called prior to <see cref="MultiValue" />.
    /// </summary>
    public bool HasMultiData => _multiValue != null;

    /// <summary>
    /// Check that single-value is stored in this object. Should be called prior to <see cref="SingleValue" />.
    /// </summary>
    public bool HasSingleData => _singleValue != null;
}
