// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.ComponentModel;
using Valkey.Glide.InterOp.Native.Routing;

namespace Valkey.Glide.InterOp.Routing;

/// <summary>
/// Route to the node that matches the [Route]
/// </summary>
/// <param name="slot"></param>
/// <param name="slotAddress"></param>
public sealed unsafe class SingleSpecificKeyedNode(string slot, ESlotAddress slotAddress) : IRoutingInfo
{
    // ToDo: Fill in documentation for slot and slotAddress
    public RoutingInfo? ToNative(MarshalString marshalString, MarshalBytes marshalBytes) => new RoutingInfo
    {
        kind = ERoutingInfo.SingleSpecificKeyedNode,
        value = new RoutingInfoUnion
        {
            keyed_specific_node = new KeyedSlot
            {
                slot = marshalString(slot),
                slot_length = (uint)slot.Length,
                slot_addr = slotAddress switch
                {
                    ESlotAddress.Master => RouteSlotAddress.Master,
                    ESlotAddress.ReplicaOptional => RouteSlotAddress.ReplicaOptional,
                    ESlotAddress.ReplicaRequired => RouteSlotAddress.ReplicaRequired,
                    _ => throw new InvalidEnumArgumentException(nameof(slotAddress), (int)slotAddress,
                        typeof(ESlotAddress))
                },
            }
        },
    };
}
