using System.ComponentModel;
using System.Diagnostics.CodeAnalysis;

namespace Valkey.Glide.InterOp.Native.Logging;

[EditorBrowsable(EditorBrowsableState.Advanced)]
[SuppressMessage("ReSharper", "InconsistentNaming")]
public enum EEventDataKind
{
    Unknown,
    IsSpan,
    IsEvent,
}