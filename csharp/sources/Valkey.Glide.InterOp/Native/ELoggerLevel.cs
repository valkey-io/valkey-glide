using System.Diagnostics.CodeAnalysis;

namespace Valkey.Glide.InterOp.Native;

[SuppressMessage("ReSharper", "InconsistentNaming")]
public enum ELoggerLevel
{
    None  = 0,
    Error = 1,
    Warn  = 2,
    Info  = 3,
    Debug = 4,
    Trace = 5,
    Off   = 6,
}