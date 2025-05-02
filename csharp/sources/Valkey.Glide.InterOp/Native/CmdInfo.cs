using System.Runtime.InteropServices;

namespace Valkey.Glide.InterOp.Native;

[StructLayout(LayoutKind.Sequential)]
internal struct CmdInfo
{
    public RequestType RequestType;
    public IntPtr Args;
    public nuint ArgCount;
    public IntPtr ArgLengths;
}