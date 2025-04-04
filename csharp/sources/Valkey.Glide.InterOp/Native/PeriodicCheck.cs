using System.ComponentModel;
using System.Diagnostics.CodeAnalysis;

namespace Valkey.Glide.InterOp.Native;

[EditorBrowsable(EditorBrowsableState.Advanced)]
[SuppressMessage("ReSharper", "InconsistentNaming")]
public struct PeriodicCheck
{
    public EPeriodicCheckKind kind;
    public ulong              secs;
    public ulong              nanos;
}
