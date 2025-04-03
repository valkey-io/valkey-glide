using System.ComponentModel;
using System.Diagnostics.CodeAnalysis;
using System.Runtime.InteropServices;

namespace Valkey.Glide.InterOp.Native;

[SuppressMessage("ReSharper", "InconsistentNaming")]
[StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
[EditorBrowsable(EditorBrowsableState.Advanced)]
public struct ConnectionRetryStrategy
{
    public int  ignore;
    public uint exponent_base;
    public uint factor;
    public uint number_of_retries;
}
