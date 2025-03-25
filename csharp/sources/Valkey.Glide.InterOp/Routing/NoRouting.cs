// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.InterOp.Native;
using Valkey.Glide.InterOp.Native.Routing;

namespace Valkey.Glide.InterOp.Routing;

public class NoRouting : IRoutingInfo
{
    public unsafe RoutingInfo? ToNative(MarshalString marshalString, MarshalBytes marshalBytes) => null;
}
