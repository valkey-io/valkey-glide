using System.Runtime.InteropServices;

namespace Valkey.Glide.InterOp.Internals;

[StructLayout(LayoutKind.Sequential)]
internal struct GlideValue
{
    public ValueType Type;
    public nuint Value;
    public uint Size;
}