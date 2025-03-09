using System.ComponentModel;

namespace Valkey.Glide.InterOp.Native;

[EditorBrowsable(EditorBrowsableState.Advanced)]
public enum ECreateClientHandleCode
{
    Success                   = 0,
    ParameterError            = 1,
    ThreadCreationError       = 2,
    ConnectionTimedOutError   = 3,
    ConnectionToFailedError   = 4,
    ConnectionToClusterFailed = 5,
    ConnectionIoError         = 6,
}
