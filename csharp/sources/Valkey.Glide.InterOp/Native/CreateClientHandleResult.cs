using System.Diagnostics.CodeAnalysis;
using System.Runtime.InteropServices;

namespace Valkey.Glide.InterOp.Native;

[SuppressMessage("ReSharper", "InconsistentNaming")]
[StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
public struct CreateClientHandleResult
{
    public        ECreateClientHandleCode result;
    public unsafe nint                    client_handle;
    public unsafe byte*                   error_string;
}
