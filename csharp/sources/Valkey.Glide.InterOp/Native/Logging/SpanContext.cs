using System.ComponentModel;
using System.Diagnostics.CodeAnalysis;
using System.Runtime.InteropServices;

namespace Valkey.Glide.InterOp.Native.Logging;

[EditorBrowsable(EditorBrowsableState.Advanced)]
[SuppressMessage("ReSharper", "InconsistentNaming")]
[StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
public struct SpanContext
{
    public ESpanContextKind kind;

    /// Contains the parent_id if [ESpanContextKind::Explicit] is used
    public ulong parent_id;
}
