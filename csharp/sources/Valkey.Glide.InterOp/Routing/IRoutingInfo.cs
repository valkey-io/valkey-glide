using System.ComponentModel;
using Valkey.Glide.InterOp.Native;
using Valkey.Glide.InterOp.Native.Routing;

namespace Valkey.Glide.InterOp.Routing;

/// <summary>
/// This is part of the internal API and subject to change without notice.
/// Do not derive from this interface.
/// </summary>
public interface IRoutingInfo
{
    /// <summary>
    /// This is part of the internal API and subject to change without notice.
    /// Do not use this method.
    /// </summary>
    [EditorBrowsable(EditorBrowsableState.Advanced)]
    unsafe RoutingInfo? ToNative(MarshalString marshalString, MarshalBytes marshalBytes);
}
