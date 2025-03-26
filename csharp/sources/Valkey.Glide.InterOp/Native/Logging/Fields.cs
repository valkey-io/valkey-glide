using System.ComponentModel;
using System.Diagnostics.CodeAnalysis;
using System.Runtime.InteropServices;

namespace Valkey.Glide.InterOp.Native.Logging;

[EditorBrowsable(EditorBrowsableState.Advanced)]
[SuppressMessage("ReSharper", "InconsistentNaming")]
[StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
public unsafe struct Fields
{
    /// The names of the key-value fields attached to the described span or
    /// event.
    /// This may be `nullptr` if empty
    public KeyValuePair* fields;

    /// The length of fields
    public int fields_length;
}