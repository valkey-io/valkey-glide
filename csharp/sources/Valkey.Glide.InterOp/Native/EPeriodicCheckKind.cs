using System.ComponentModel;
using System.Diagnostics.CodeAnalysis;

namespace Valkey.Glide.InterOp.Native;

[EditorBrowsable(EditorBrowsableState.Advanced)]
[SuppressMessage("ReSharper", "InconsistentNaming")]
public enum EPeriodicCheckKind
{
    None     = 0,
    Enabled  = 1,
    Disabled = 2,

    /// secs and nanos on PeriodicCheck must be set
    ManualInterval = 3,
}
