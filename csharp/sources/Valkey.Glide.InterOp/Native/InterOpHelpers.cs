// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Buffers;
using System.Runtime.InteropServices;

namespace Valkey.Glide.InterOp.Native;

/// FFI-ready structs, helper methods and wrappers
internal class InterOpHelpers
{
    internal static IntPtr StructToPtr<T>(T @struct) where T : struct
    {
        IntPtr result = Marshal.AllocHGlobal(Marshal.SizeOf(typeof(T)));
        Marshal.StructureToPtr(@struct, result, false);
        return result;
    }

    internal static void FreeStructPtr(IntPtr ptr) => Marshal.FreeHGlobal(ptr);

    internal static T[] PoolRent<T>(int len) => ArrayPool<T>.Shared.Rent(len);

    internal static void PoolReturn<T>(T[] arr) => ArrayPool<T>.Shared.Return(arr);
}
