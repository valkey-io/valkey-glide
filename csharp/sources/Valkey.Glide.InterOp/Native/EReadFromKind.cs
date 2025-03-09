using System.ComponentModel;
using System.Diagnostics.CodeAnalysis;

namespace Valkey.Glide.InterOp.Native;

[EditorBrowsable(EditorBrowsableState.Advanced)]
[SuppressMessage("ReSharper", "InconsistentNaming")]
public enum EReadFromKind
{
    None          = 0,
    Primary       = 1,
    PreferReplica = 2,

    // Define using ReadFrom.value
    AZAffinity = 3,

    // Define using ReadFrom.value
    AZAffinityReplicasAndPrimary = 4,
}
