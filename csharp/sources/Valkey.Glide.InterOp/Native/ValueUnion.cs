using System.ComponentModel;
using System.Diagnostics.CodeAnalysis;
using System.Runtime.InteropServices;

namespace Valkey.Glide.InterOp.Native;

[EditorBrowsable(EditorBrowsableState.Advanced)]
[SuppressMessage("ReSharper", "InconsistentNaming")]
[StructLayout(LayoutKind.Explicit, CharSet = CharSet.Unicode)]
public struct ValueUnion
{
    [FieldOffset(0)]
    public long i;

    [FieldOffset(0)]
    public double f;

    [FieldOffset(0)]
    public unsafe byte* ptr;
}
