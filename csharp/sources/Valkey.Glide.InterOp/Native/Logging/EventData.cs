using System.ComponentModel;
using System.Diagnostics.CodeAnalysis;
using System.Runtime.InteropServices;

namespace Valkey.Glide.InterOp.Native.Logging;

[EditorBrowsable(EditorBrowsableState.Advanced)]
[SuppressMessage("ReSharper", "InconsistentNaming")]
[StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
public unsafe struct EventData
{
    /// The name of the span described by this metadata.
    public char* name;

    /// The length of `name`
    public int name_length;

    /// The part of the system that the span that this metadata describes
    /// occurred in.
    public char* target;

    /// The length of `target`
    public int target_length;

    /// The severity of the described span.
    public ESeverity severity;

    /// The name of the Rust module where the span occurred, or `nullptr` if this
    /// could not be determined.
    public char* module_path;

    /// The length of `module_path`
    public int module_path_length;

    /// The name of the source code file where the span occurred, or `nullptr` if
    /// this could not be determined.
    public char* file;

    /// The length of `file`
    public int file_length;

    /// The line number in the source code file where the span occurred, or
    /// -1 if this could not be determined.
    public int line;

    /// The kind of the call-site.
    public EEventDataKind kind;
}
