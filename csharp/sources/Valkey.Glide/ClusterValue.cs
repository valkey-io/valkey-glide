// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Internals;

namespace Valkey.Glide;

public class ClusterValue<T>
{
    private Dictionary<string, T>? _multiValue = default;
    private T? _singleValue = default;

    private ClusterValue() { }

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
        => new() { _multiValue = obj.DonwCastKeys() };

    public Dictionary<string, T> MultiValue
        => _multiValue ?? throw new Exception("No multi value stored");

    public T SingleValue
        => _singleValue ?? throw new Exception("No single value stored");

    public bool HasMultiData => _multiValue != null;

    public bool HasSingleData => _singleValue != null;
}
