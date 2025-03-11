using System.ComponentModel;
using System.Diagnostics.CodeAnalysis;

namespace Valkey.Glide.InterOp.Native;

[EditorBrowsable(EditorBrowsableState.Advanced)]
[SuppressMessage("ReSharper", "InconsistentNaming")]
public enum ETlsMode
{
    None        = 0,
    NoTls       = 1,
    InsecureTls = 2,
    SecureTls   = 3,
}
