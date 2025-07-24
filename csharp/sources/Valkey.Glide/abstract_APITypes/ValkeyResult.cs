// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Diagnostics.CodeAnalysis;
using System.Runtime.CompilerServices;

namespace Valkey.Glide;

/// <summary>
/// Represents a general-purpose result from server, that may be cast into various anticipated types.
/// </summary>
public abstract class ValkeyResult
{
    /// <summary>
    /// Do not use.
    /// </summary>
    [Obsolete("Please specify a result type", true)] // retained purely for binary compat
    public ValkeyResult() : this(default) { }

    internal ValkeyResult(ResultType resultType)
    {
        Resp3Type = resultType;
    }

    /// <summary>
    /// Create a new ValkeyResult representing a single value.
    /// </summary>
    /// <param name="value">The <see cref="ValkeyValue"/> to create a result from.</param>
    /// <param name="resultType">The type of result being represented.</param>
    /// <returns> new <see cref="ValkeyResult"/>.</returns>
    [SuppressMessage("ApiDesign", "RS0027:Public API with optional parameter(s) should have the most parameters amongst its public overloads", Justification = "Legacy compat.")]
    public static ValkeyResult Create(ValkeyValue value, ResultType? resultType = null) => new SingleResult(value, resultType);

    /// <summary>
    /// Create a new ValkeyResult representing an array of values.
    /// </summary>
    /// <param name="values">The <see cref="ValkeyValue"/>s to create a result from.</param>
    /// <returns> new <see cref="ValkeyResult"/>.</returns>
    public static ValkeyResult Create(ValkeyValue[] values)
        => Create(values, ResultType.Array);

    /// <summary>
    /// Create a new ValkeyResult representing an array of values.
    /// </summary>
    /// <param name="values">The <see cref="ValkeyValue"/>s to create a result from.</param>
    /// <param name="resultType">The explicit data type.</param>
    /// <returns> new <see cref="ValkeyResult"/>.</returns>
    public static ValkeyResult Create(ValkeyValue[] values, ResultType resultType) =>
        values == null ? NullArray : values.Length == 0 ? EmptyArray(resultType) :
            new ArrayResult(Array.ConvertAll(values, value => new SingleResult(value, null)), resultType);

    /// <summary>
    /// Create a new ValkeyResult representing an array of values.
    /// </summary>
    /// <param name="values">The <see cref="ValkeyResult"/>s to create a result from.</param>
    /// <returns> new <see cref="ValkeyResult"/>.</returns>
    public static ValkeyResult Create(ValkeyResult[] values)
        => Create(values, ResultType.Array);

    /// <summary>
    /// Create a new ValkeyResult representing an array of values.
    /// </summary>
    /// <param name="values">The <see cref="ValkeyResult"/>s to create a result from.</param>
    /// <param name="resultType">The explicit data type.</param>
    /// <returns> new <see cref="ValkeyResult"/>.</returns>
    public static ValkeyResult Create(ValkeyResult[] values, ResultType resultType)
        => values == null ? NullArray : values.Length == 0 ? EmptyArray(resultType) : new ArrayResult(values, resultType);

    internal static ValkeyResult Create(object? obj) => obj switch
    {
        IEnumerable<object?> arr => Create(arr.Select(Create).ToArray()),
        IDictionary<GlideString, object?> dict => Create(dict.SelectMany(p => new ValkeyResult[] { Create(p.Key), Create(p.Value) }).ToArray()),
        _ => Create(ValkeyValue.Unbox(obj)),
    };

    /// <summary>
    /// An empty array result.
    /// </summary>
    internal static ValkeyResult EmptyArray(ResultType type) => type switch
    {
        ResultType.Array => s_EmptyArray ??= new ArrayResult(Array.Empty<ValkeyResult>(), type),
        ResultType.Set => s_EmptySet ??= new ArrayResult(Array.Empty<ValkeyResult>(), type),
        ResultType.Map => s_EmptyMap ??= new ArrayResult(Array.Empty<ValkeyResult>(), type),
        _ => new ArrayResult(Array.Empty<ValkeyResult>(), type),
    };

    private static ValkeyResult? s_EmptyArray, s_EmptySet, s_EmptyMap;

    /// <summary>
    /// A null array result.
    /// </summary>
    internal static ValkeyResult NullArray { get; } = new ArrayResult(null, ResultType.Null);

    /// <summary>
    /// A null single result, to use as a default for invalid returns.
    /// </summary>
    internal static ValkeyResult NullSingle { get; } = new SingleResult(ValkeyValue.Null, ResultType.Null);

    /// <summary>
    /// Gets the number of elements in this item if it is a valid array, or <c>-1</c> otherwise.
    /// </summary>
    public virtual int Length => -1;

    /// <inheritdoc/>
    public sealed override string ToString() => ToString(out _) ?? "";

    /// <summary>
    /// Gets the string content as per <see cref="ToString()"/>, but also obtains the declared type from verbatim strings (for example <c>LATENCY DOCTOR</c>).
    /// </summary>
    /// <param name="type">The type of the returned string.</param>
    /// <returns>The content.</returns>
    public abstract string? ToString(out string? type);

    /// <summary>
    /// Indicate the type of result that was received from server, in RESP2 terms.
    /// </summary>
    [Obsolete($"Please use either {nameof(Resp2Type)} (simplified) or {nameof(Resp3Type)} (full)")]
    [Browsable(false), EditorBrowsable(EditorBrowsableState.Never)]
    public ResultType Type => Resp2Type;

    /// <summary>
    /// Indicate the type of result that was received from server, in RESP3 terms.
    /// </summary>
    public ResultType Resp3Type { get; }

    /// <summary>
    /// Indicate the type of result that was received from server, in RESP2 terms.
    /// </summary>
    public ResultType Resp2Type => Resp3Type == ResultType.Null ? Resp2NullType : Resp3Type.ToResp2();

    internal virtual ResultType Resp2NullType => ResultType.BulkString;

    /// <summary>
    /// Indicates whether this result was a null result.
    /// </summary>
    public abstract bool IsNull { get; }

    /// <summary>
    /// Interprets the result as a <see cref="string"/>.
    /// </summary>
    /// <param name="result">The result to convert to a <see cref="string"/>.</param>
    public static explicit operator string?(ValkeyResult? result) => result?.AsString();

    /// <summary>
    /// Interprets the result as a <see cref="T:byte[]"/>.
    /// </summary>
    /// <param name="result">The result to convert to a <see cref="T:byte[]"/>.</param>
    public static explicit operator byte[]?(ValkeyResult? result) => result?.AsByteArray();

    /// <summary>
    /// Interprets the result as a <see cref="double"/>.
    /// </summary>
    /// <param name="result">The result to convert to a <see cref="double"/>.</param>
    public static explicit operator double(ValkeyResult result) => result.AsDouble();

    /// <summary>
    /// Interprets the result as an <see cref="long"/>.
    /// </summary>
    /// <param name="result">The result to convert to a <see cref="long"/>.</param>
    public static explicit operator long(ValkeyResult result) => result.AsInt64();

    /// <summary>
    /// Interprets the result as an <see cref="ulong"/>.
    /// </summary>
    /// <param name="result">The result to convert to a <see cref="ulong"/>.</param>
    public static explicit operator ulong(ValkeyResult result) => result.AsUInt64();

    /// <summary>
    /// Interprets the result as an <see cref="int"/>.
    /// </summary>
    /// <param name="result">The result to convert to a <see cref="int"/>.</param>
    public static explicit operator int(ValkeyResult result) => result.AsInt32();

    /// <summary>
    /// Interprets the result as a <see cref="bool"/>.
    /// </summary>
    /// <param name="result">The result to convert to a <see cref="bool"/>.</param>
    public static explicit operator bool(ValkeyResult result) => result.AsBoolean();

    /// <summary>
    /// Interprets the result as a <see cref="ValkeyValue"/>.
    /// </summary>
    /// <param name="result">The result to convert to a <see cref="ValkeyValue"/>.</param>
    public static explicit operator ValkeyValue(ValkeyResult? result) => result?.AsValkeyValue() ?? ValkeyValue.Null;

    /// <summary>
    /// Interprets the result as a <see cref="ValkeyKey"/>.
    /// </summary>
    /// <param name="result">The result to convert to a <see cref="ValkeyKey"/>.</param>
    public static explicit operator ValkeyKey(ValkeyResult? result) => result?.AsValkeyKey() ?? default;

    /// <summary>
    /// Interprets the result as a <see cref="T:Nullable{double}"/>.
    /// </summary>
    /// <param name="result">The result to convert to a <see cref="T:Nullable{double}"/>.</param>
    public static explicit operator double?(ValkeyResult? result) => result?.AsNullableDouble();

    /// <summary>
    /// Interprets the result as a <see cref="T:Nullable{long}"/>.
    /// </summary>
    /// <param name="result">The result to convert to a <see cref="T:Nullable{long}"/>.</param>
    public static explicit operator long?(ValkeyResult? result) => result?.AsNullableInt64();

    /// <summary>
    /// Interprets the result as a <see cref="T:Nullable{ulong}"/>.
    /// </summary>
    /// <param name="result">The result to convert to a <see cref="T:Nullable{ulong}"/>.</param>
    public static explicit operator ulong?(ValkeyResult? result) => result?.AsNullableUInt64();

    /// <summary>
    /// Interprets the result as a <see cref="T:Nullable{int}"/>.
    /// </summary>
    /// <param name="result">The result to convert to a <see cref="T:Nullable{int}"/>.</param>
    public static explicit operator int?(ValkeyResult? result) => result?.AsNullableInt32();

    /// <summary>
    /// Interprets the result as a <see cref="T:Nullable{bool}"/>.
    /// </summary>
    /// <param name="result">The result to convert to a <see cref="T:Nullable{bool}"/>.</param>
    public static explicit operator bool?(ValkeyResult? result) => result?.AsNullableBoolean();

    /// <summary>
    /// Interprets the result as a <see cref="T:string[]"/>.
    /// </summary>
    /// <param name="result">The result to convert to a <see cref="T:string[]"/>.</param>
    public static explicit operator string?[]?(ValkeyResult? result) => result?.AsStringArray();

    /// <summary>
    /// Interprets the result as a <see cref="T:byte[][]"/>.
    /// </summary>
    /// <param name="result">The result to convert to a <see cref="T:byte[][]"/>.</param>
    public static explicit operator byte[]?[]?(ValkeyResult? result) => result?.AsByteArrayArray();

    /// <summary>
    /// Interprets the result as a <see cref="T:double[]"/>.
    /// </summary>
    /// <param name="result">The result to convert to a <see cref="T:double[]"/>.</param>
    public static explicit operator double[]?(ValkeyResult? result) => result?.AsDoubleArray();

    /// <summary>
    /// Interprets the result as a <see cref="T:long[]"/>.
    /// </summary>
    /// <param name="result">The result to convert to a <see cref="T:long[]"/>.</param>
    public static explicit operator long[]?(ValkeyResult? result) => result?.AsInt64Array();

    /// <summary>
    /// Interprets the result as a <see cref="T:ulong[]"/>.
    /// </summary>
    /// <param name="result">The result to convert to a <see cref="T:ulong[]"/>.</param>
    public static explicit operator ulong[]?(ValkeyResult? result) => result?.AsUInt64Array();

    /// <summary>
    /// Interprets the result as a <see cref="T:int[]"/>.
    /// </summary>
    /// <param name="result">The result to convert to a <see cref="T:int[]"/>.</param>
    public static explicit operator int[]?(ValkeyResult? result) => result?.AsInt32Array();

    /// <summary>
    /// Interprets the result as a <see cref="T:bool[]"/>.
    /// </summary>
    /// <param name="result">The result to convert to a <see cref="T:bool[]"/>.</param>
    public static explicit operator bool[]?(ValkeyResult? result) => result?.AsBooleanArray();

    /// <summary>
    /// Interprets the result as a <see cref="T:ValkeyValue[]"/>.
    /// </summary>
    /// <param name="result">The result to convert to a <see cref="T:ValkeyValue[]"/>.</param>
    public static explicit operator ValkeyValue[]?(ValkeyResult? result) => result?.AsValkeyValueArray();

    /// <summary>
    /// Interprets the result as a <see cref="T:ValkeyKey[]"/>.
    /// </summary>
    /// <param name="result">The result to convert to a <see cref="T:ValkeyKey[]"/>.</param>
    public static explicit operator ValkeyKey[]?(ValkeyResult? result) => result?.AsValkeyKeyArray();

    /// <summary>
    /// Interprets the result as a <see cref="T:ValkeyResult[]"/>.
    /// </summary>
    /// <param name="result">The result to convert to a <see cref="T:ValkeyResult[]"/>.</param>
    public static explicit operator ValkeyResult[]?(ValkeyResult? result) => result?.AsValkeyResultArray();

    /// <summary>
    /// Interprets a multi-bulk result with successive key/name values as a dictionary keyed by name.
    /// </summary>
    /// <param name="comparer">The key comparator to use, or <see cref="StringComparer.InvariantCultureIgnoreCase"/> by default.</param>
    public Dictionary<string, ValkeyResult> ToDictionary(IEqualityComparer<string>? comparer = null)
    {
        var arr = AsValkeyResultArray();
        if (arr is null)
        {
            return [];
        }
        int len = arr.Length / 2;
        var result = new Dictionary<string, ValkeyResult>(len, comparer ?? StringComparer.InvariantCultureIgnoreCase);
        for (int i = 0; i < arr.Length; i += 2)
        {
            result.Add(arr[i].AsString()!, arr[i + 1]);
        }
        return result;
    }

    /// <summary>
    /// Get a sub-item by index.
    /// </summary>
    public virtual ValkeyResult this[int index] => throw new InvalidOperationException("Indexers can only be used on array results");

    internal abstract bool AsBoolean();
    internal abstract bool[]? AsBooleanArray();
    internal abstract byte[]? AsByteArray();
    internal abstract byte[][]? AsByteArrayArray();
    internal abstract double AsDouble();
    internal abstract double[]? AsDoubleArray();
    internal abstract int AsInt32();
    internal abstract int[]? AsInt32Array();
    internal abstract long AsInt64();
    internal abstract ulong AsUInt64();
    internal abstract long[]? AsInt64Array();
    internal abstract ulong[]? AsUInt64Array();
    internal abstract bool? AsNullableBoolean();
    internal abstract double? AsNullableDouble();
    internal abstract int? AsNullableInt32();
    internal abstract long? AsNullableInt64();
    internal abstract ulong? AsNullableUInt64();
    internal abstract ValkeyKey AsValkeyKey();
    internal abstract ValkeyKey[]? AsValkeyKeyArray();
    internal abstract ValkeyResult[]? AsValkeyResultArray();
    internal abstract ValkeyValue AsValkeyValue();
    internal abstract ValkeyValue[]? AsValkeyValueArray();
    internal abstract string? AsString();
    internal abstract string?[]? AsStringArray();

    private sealed class ArrayResult : ValkeyResult
    {
        public override bool IsNull => _value is null;
        private readonly ValkeyResult[]? _value;

        internal override ResultType Resp2NullType => ResultType.Array;

        public ArrayResult(ValkeyResult[]? value, ResultType resultType) : base(value is null ? ResultType.Null : resultType)
        {
            _value = value;
        }

        public override int Length => _value is null ? -1 : _value.Length;

        public override string? ToString(out string? type)
        {
            type = null;
            return _value == null ? "(nil)" : (_value.Length + " element(s)");
        }

        internal override bool AsBoolean()
        {
            if (IsSingleton) return _value![0].AsBoolean();
            throw new InvalidCastException();
        }

        public override ValkeyResult this[int index] => _value![index];

        internal override bool[]? AsBooleanArray() => IsNull ? null : Array.ConvertAll(_value!, x => x.AsBoolean());

        internal override byte[]? AsByteArray()
        {
            if (IsSingleton) return _value![0].AsByteArray();
            throw new InvalidCastException();
        }

        internal override byte[][]? AsByteArrayArray()
            => IsNull ? null
            : _value!.Length == 0
                ? Array.Empty<byte[]>()
                : Array.ConvertAll(_value, x => x.AsByteArray()!);

        private bool IsSingleton => _value?.Length == 1;
        private bool IsEmpty => _value?.Length == 0;
        internal override double AsDouble()
        {
            if (IsSingleton) return _value![0].AsDouble();
            throw new InvalidCastException();
        }

        internal override double[]? AsDoubleArray()
            => IsNull ? null
            : IsEmpty ? Array.Empty<double>()
            : Array.ConvertAll(_value!, x => x.AsDouble());

        internal override int AsInt32()
        {
            if (IsSingleton) return _value![0].AsInt32();
            throw new InvalidCastException();
        }

        internal override int[]? AsInt32Array()
            => IsNull ? null
            : IsEmpty ? Array.Empty<int>()
            : Array.ConvertAll(_value!, x => x.AsInt32());

        internal override long AsInt64()
        {
            if (IsSingleton) return _value![0].AsInt64();
            throw new InvalidCastException();
        }
        internal override ulong AsUInt64()
        {
            if (IsSingleton) return _value![0].AsUInt64();
            throw new InvalidCastException();
        }

        internal override long[]? AsInt64Array()
            => IsNull ? null
            : IsEmpty ? Array.Empty<long>()
            : Array.ConvertAll(_value!, x => x.AsInt64());

        internal override ulong[]? AsUInt64Array()
            => IsNull ? null
            : IsEmpty ? Array.Empty<ulong>()
            : Array.ConvertAll(_value!, x => x.AsUInt64());

        internal override bool? AsNullableBoolean()
        {
            if (IsSingleton) return _value![0].AsNullableBoolean();
            throw new InvalidCastException();
        }

        internal override double? AsNullableDouble()
        {
            if (IsSingleton) return _value![0].AsNullableDouble();
            throw new InvalidCastException();
        }

        internal override int? AsNullableInt32()
        {
            if (IsSingleton) return _value![0].AsNullableInt32();
            throw new InvalidCastException();
        }

        internal override long? AsNullableInt64()
        {
            if (IsSingleton) return _value![0].AsNullableInt64();
            throw new InvalidCastException();
        }
        internal override ulong? AsNullableUInt64()
        {
            if (IsSingleton) return _value![0].AsNullableUInt64();
            throw new InvalidCastException();
        }

        internal override ValkeyKey AsValkeyKey()
        {
            if (IsSingleton) return _value![0].AsValkeyKey();
            throw new InvalidCastException();
        }

        internal override ValkeyKey[]? AsValkeyKeyArray()
            => IsNull ? null
            : IsEmpty ? Array.Empty<ValkeyKey>()
            : Array.ConvertAll(_value!, x => x.AsValkeyKey());

        internal override ValkeyResult[]? AsValkeyResultArray() => _value;

        internal override ValkeyValue AsValkeyValue()
        {
            if (IsSingleton) return _value![0].AsValkeyValue();
            throw new InvalidCastException();
        }

        internal override ValkeyValue[]? AsValkeyValueArray()
            => IsNull ? null
            : IsEmpty ? Array.Empty<ValkeyValue>()
            : Array.ConvertAll(_value!, x => x.AsValkeyValue());

        internal override string? AsString()
        {
            if (IsSingleton) return _value![0].AsString();
            throw new InvalidCastException();
        }

        internal override string?[]? AsStringArray()
            => IsNull ? null
            : IsEmpty ? Array.Empty<string>()
            : Array.ConvertAll(_value!, x => x.AsString());
    }

    /// <summary>
    /// Create a <see cref="ValkeyResult"/> from a key.
    /// </summary>
    /// <param name="key">The <see cref="ValkeyKey"/> to create a <see cref="ValkeyResult"/> from.</param>
    public static ValkeyResult Create(ValkeyKey key) => Create(key.AsValkeyValue(), ResultType.BulkString);

    private sealed class SingleResult : ValkeyResult, IConvertible
    {
        private readonly ValkeyValue _value;

        public SingleResult(ValkeyValue value, ResultType? resultType) : base(value.IsNull ? ResultType.Null : resultType ?? (value.IsInteger ? ResultType.Integer : ResultType.BulkString))
        {
            _value = value;
        }

        public override bool IsNull => Resp3Type == ResultType.Null || _value.IsNull;

        public override string? ToString(out string? type)
        {
            type = null;
            string? s = _value;
            if (Resp3Type == ResultType.VerbatimString && s is not null && s.Length >= 4 && s[3] == ':')
            {
                // remove the prefix
                type = s.Substring(0, 3);
                s = s.Substring(4);
            }
            return s;
        }

        internal override bool AsBoolean() => (bool)_value;
        internal override bool[] AsBooleanArray() => [AsBoolean()];
        internal override byte[]? AsByteArray() => (byte[]?)_value;
        internal override byte[][] AsByteArrayArray() => [AsByteArray()!];
        internal override double AsDouble() => (double)_value;
        internal override double[] AsDoubleArray() => [AsDouble()];
        internal override int AsInt32() => (int)_value;
        internal override int[] AsInt32Array() => [AsInt32()];
        internal override long AsInt64() => (long)_value;
        internal override ulong AsUInt64() => (ulong)_value;
        internal override long[] AsInt64Array() => [AsInt64()];
        internal override ulong[] AsUInt64Array() => [AsUInt64()];
        internal override bool? AsNullableBoolean() => (bool?)_value;
        internal override double? AsNullableDouble() => (double?)_value;
        internal override int? AsNullableInt32() => (int?)_value;
        internal override long? AsNullableInt64() => (long?)_value;
        internal override ulong? AsNullableUInt64() => (ulong?)_value;
        internal override ValkeyKey AsValkeyKey() => (byte[]?)_value;
        internal override ValkeyKey[] AsValkeyKeyArray() => [AsValkeyKey()];
        internal override ValkeyResult[] AsValkeyResultArray() => throw new InvalidCastException();
        internal override ValkeyValue AsValkeyValue() => _value;
        internal override ValkeyValue[] AsValkeyValueArray() => [AsValkeyValue()];
        internal override string? AsString() => (string?)_value;
        internal override string?[]? AsStringArray() => [AsString()];
        TypeCode IConvertible.GetTypeCode() => TypeCode.Object;
        bool IConvertible.ToBoolean(IFormatProvider? provider) => AsBoolean();
        char IConvertible.ToChar(IFormatProvider? provider)
        {
            checked { return (char)AsInt32(); }
        }
        sbyte IConvertible.ToSByte(IFormatProvider? provider)
        {
            checked { return (sbyte)AsInt32(); }
        }
        byte IConvertible.ToByte(IFormatProvider? provider)
        {
            checked { return (byte)AsInt32(); }
        }
        short IConvertible.ToInt16(IFormatProvider? provider)
        {
            checked { return (short)AsInt32(); }
        }
        ushort IConvertible.ToUInt16(IFormatProvider? provider)
        {
            checked { return (ushort)AsInt32(); }
        }
        int IConvertible.ToInt32(IFormatProvider? provider) => AsInt32();
        uint IConvertible.ToUInt32(IFormatProvider? provider)
        {
            checked { return (uint)AsInt64(); }
        }
        long IConvertible.ToInt64(IFormatProvider? provider) => AsInt64();
        ulong IConvertible.ToUInt64(IFormatProvider? provider)
        {
            checked { return (ulong)AsInt64(); }
        }
        float IConvertible.ToSingle(IFormatProvider? provider) => (float)AsDouble();
        double IConvertible.ToDouble(IFormatProvider? provider) => AsDouble();
        decimal IConvertible.ToDecimal(IFormatProvider? provider)
        {
            // we can do this safely *sometimes*
            if (Resp2Type == ResultType.Integer) return AsInt64();
            // but not always
            ThrowNotSupported();
            return default;
        }
        DateTime IConvertible.ToDateTime(IFormatProvider? provider)
        {
            ThrowNotSupported();
            return default;
        }
        string IConvertible.ToString(IFormatProvider? provider) => AsString()!;
        object IConvertible.ToType(Type conversionType, IFormatProvider? provider)
        {
            switch (System.Type.GetTypeCode(conversionType))
            {
                case TypeCode.Boolean: return AsBoolean();
                case TypeCode.Char: checked { return (char)AsInt32(); }
                case TypeCode.SByte: checked { return (sbyte)AsInt32(); }
                case TypeCode.Byte: checked { return (byte)AsInt32(); }
                case TypeCode.Int16: checked { return (short)AsInt32(); }
                case TypeCode.UInt16: checked { return (ushort)AsInt32(); }
                case TypeCode.Int32: return AsInt32();
                case TypeCode.UInt32: checked { return (uint)AsInt64(); }
                case TypeCode.Int64: return AsInt64();
                case TypeCode.UInt64: checked { return (ulong)AsInt64(); }
                case TypeCode.Single: return (float)AsDouble();
                case TypeCode.Double: return AsDouble();
                case TypeCode.Decimal when Resp2Type == ResultType.Integer: return AsInt64();
                case TypeCode.String: return AsString()!;
                default:
                    ThrowNotSupported();
                    return default;
            }
        }

        [DoesNotReturn]
        private void ThrowNotSupported([CallerMemberName] string? caller = null)
            => throw new NotSupportedException($"{typeof(SingleResult).FullName} does not support {nameof(IConvertible)}.{caller} with value '{AsString()}'");
    }
}
