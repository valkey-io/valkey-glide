using System.Runtime.InteropServices;

namespace Valkey.Glide.InterOp.Native;

[StructLayout(LayoutKind.Sequential, CharSet = CharSet.Ansi)]
public struct NodeAddress
{
    [MarshalAs(UnmanagedType.LPStr)]
    public string Host;
    public ushort Port;
}
