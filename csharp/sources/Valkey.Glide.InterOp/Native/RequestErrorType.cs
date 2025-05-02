namespace Valkey.Glide.InterOp.Native;

public enum RequestErrorType : uint
{
    Unspecified = 0,
    ExecAbort = 1,
    Timeout = 2,
    Disconnect = 3,
}
