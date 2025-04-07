// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Runtime.InteropServices;

namespace Valkey.Glide.Internals;

internal class ResponseHandler
{
    [StructLayout(LayoutKind.Sequential)]
    private struct GlideValue
    {
        public ValueType Type;
        public nuint Value;
        public uint Size;
    }

    public enum ValueType : uint
    {
        Null = 0,
        Int = 1,
        Float = 2,
        Bool = 3,
        String = 4,
        Array = 5,
        Map = 6,
        Set = 7,
        BulkString = 8,
        OK = 9,
    }

    public static object? HandleResponse(IntPtr valuePtr)
    {
        GlideValue value = Marshal.PtrToStructure<GlideValue>(valuePtr);
        return TraverseValue(value);
    }

    private static GlideString CreateString(GlideValue value)
    {
        byte[] bytes = new byte[value.Size];
        Marshal.Copy(new IntPtr((long)value.Value), bytes, 0, (int)value.Size);
        return new GlideString(bytes);
    }

    private static object?[] CreateArray(GlideValue value)
    {
        object?[] values = new object?[value.Size];
        IntPtr ptr = new((long)value.Value);
        for (int i = 0; i < values.Length; i++)
        {
            values[i] = HandleResponse(ptr);
            ptr += Marshal.SizeOf<GlideValue>();
        }

        return values;
    }

    private static Dictionary<GlideString, object?> CreateMap(GlideValue value)
    {
        object?[] values = CreateArray(value);
        Dictionary<GlideString, object?> res = [];
        for (int i = 0; i < values.Length; i += 2)
        {
            res[(GlideString)values[i]!] = values[i + 1];
        }
        return res;
    }

    private static object? TraverseValue(GlideValue value) => value.Type switch
    {
        ValueType.Null => null,
        ValueType.Int => (long)value.Value,
        ValueType.Float => (double)value.Value,
        ValueType.Bool => value.Value != 0,
        ValueType.BulkString or ValueType.String => CreateString(value),
        ValueType.Array => CreateArray(value),
        ValueType.Map => CreateMap(value),
        ValueType.Set => CreateArray(value).ToHashSet(),
        ValueType.OK => new GlideString("OK"),
        _ => throw new NotImplementedException(),
    };
}
