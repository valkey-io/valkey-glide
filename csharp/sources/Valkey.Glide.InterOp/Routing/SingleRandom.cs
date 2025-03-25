// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.InterOp.Native;
using Valkey.Glide.InterOp.Native.Routing;

namespace Valkey.Glide.InterOp.Routing;

/// <summary>
/// Route to any node at random
/// </summary>
public sealed class SingleRandom() : IRoutingInfo
{
    public RoutingInfo? ToNative(MarshalString marshalString, MarshalBytes marshalBytes) =>
        new RoutingInfo {kind = ERoutingInfo.SingleRandom, value = new RoutingInfoUnion(),};
}
