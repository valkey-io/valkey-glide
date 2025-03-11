using System.ComponentModel;
using System.Diagnostics.CodeAnalysis;

namespace Valkey.Glide.InterOp.Native;

[EditorBrowsable(EditorBrowsableState.Advanced)]
[SuppressMessage("ReSharper", "InconsistentNaming")]
public enum EProtocolVersion
{
    None,

    /// <see href="https://github.com/redis/redis-specifications/blob/master/protocol/RESP2.md"/>
    RESP2,

    /// <see href="https://github.com/redis/redis-specifications/blob/master/protocol/RESP3.md"/>
    RESP3,
}
