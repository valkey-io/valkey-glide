// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Runtime.InteropServices;

namespace Glide.Internals;

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

    private static object? TraverseValue(GlideValue value)
    {
#pragma warning disable IDE0022 // Use expression body for method
        switch (value.Type)
        {
            case ValueType.Null: return null;
            case ValueType.Int: return (long)value.Value;
            case ValueType.Float: return (double)value.Value;
            case ValueType.Bool: return value.Value != 0;
            case ValueType.BulkString:
            case ValueType.String:
                {
                    byte[] bytes = new byte[value.Size];
                    Marshal.Copy(new IntPtr((long)value.Value), bytes, 0, (int)value.Size);
                    return new GlideString(bytes);
                }
            case ValueType.Array:
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
            case ValueType.Map:
                {
                    object?[] values = new object?[value.Size];
                    IntPtr ptr = new((long)value.Value);
                    for (int i = 0; i < values.Length; i++)
                    {
                        values[i] = HandleResponse(ptr);
                        ptr += Marshal.SizeOf<GlideValue>();
                    }

                    Dictionary<GlideString, object?> res = [];
                    for (int i = 0; i < values.Length; i += 2)
                    {
                        res[(GlideString)values[i]!] = values[i + 1];
                    }
                    return res;
                }
            case ValueType.Set:
                {
                    object?[] values = new object?[value.Size];
                    IntPtr ptr = new((long)value.Value);
                    for (int i = 0; i < values.Length; i++)
                    {
                        values[i] = HandleResponse(ptr);
                        ptr += Marshal.SizeOf<GlideValue>();
                    }
                    return values.ToHashSet();
                }
            case ValueType.OK: return new GlideString("OK");
            default:
                throw new NotImplementedException();
        }
#pragma warning restore IDE0022 // Use expression body for method
    }
}
