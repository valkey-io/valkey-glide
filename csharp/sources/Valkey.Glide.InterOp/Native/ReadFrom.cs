using System.ComponentModel;
using System.Diagnostics.CodeAnalysis;
using System.Runtime.InteropServices;

namespace Valkey.Glide.InterOp.Native;

[EditorBrowsable(EditorBrowsableState.Advanced)]
[SuppressMessage("ReSharper", "InconsistentNaming")]
[StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
public struct ReadFrom
{
    public        EReadFromKind kind;
    public unsafe byte*         value;
}
