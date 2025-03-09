using System.Diagnostics.CodeAnalysis;
using System.Runtime.InteropServices;

namespace Valkey.Glide.InterOp.Native;

[SuppressMessage("ReSharper", "InconsistentNaming")]
[StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
public struct BlockingCommandResult
{
    public        int   success;
    public unsafe byte* error_string;
    public        Value value;
}
