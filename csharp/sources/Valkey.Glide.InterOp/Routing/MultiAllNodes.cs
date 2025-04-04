using System.ComponentModel;
using Valkey.Glide.InterOp.Native;
using Valkey.Glide.InterOp.Native.Routing;

namespace Valkey.Glide.InterOp.Routing;

/// <summary>
/// Route to all nodes in the clusters
/// </summary>
/// <param name="responsePolicy"></param>
public sealed class MultiAllNodes(EResponsePolicy responsePolicy) : IRoutingInfo
{
    // ToDo: Add documentation for responsePolicy
    public unsafe RoutingInfo? ToNative(MarshalString marshalString, MarshalBytes marshalBytes) => new RoutingInfo
    {
        kind = ERoutingInfo.MultiAllNodes,
        value = new RoutingInfoUnion
        {
            multi = responsePolicy switch
            {
                EResponsePolicy.None => ERoutingInfoMultiResponsePolicy.None,
                EResponsePolicy.OneSucceeded => ERoutingInfoMultiResponsePolicy.OneSucceeded,
                EResponsePolicy.FirstSucceededNonEmptyOrAllEmpty => ERoutingInfoMultiResponsePolicy
                    .FirstSucceededNonEmptyOrAllEmpty,
                EResponsePolicy.AllSucceeded => ERoutingInfoMultiResponsePolicy.AllSucceeded,
                EResponsePolicy.CombineArrays => ERoutingInfoMultiResponsePolicy.CombineArrays,
                EResponsePolicy.Special => ERoutingInfoMultiResponsePolicy.Special,
                EResponsePolicy.CombineMaps => ERoutingInfoMultiResponsePolicy.CombineMaps,
                EResponsePolicy.AggregateLogicalWithAnd => ERoutingInfoMultiResponsePolicy.AggregateLogicalWithAnd,
                EResponsePolicy.AggregateWithMin => ERoutingInfoMultiResponsePolicy.AggregateWithMin,
                EResponsePolicy.AggregateWithSum => ERoutingInfoMultiResponsePolicy.AggregateWithSum,
                _ => throw new InvalidEnumArgumentException(nameof(responsePolicy), (int)responsePolicy,
                    typeof(EResponsePolicy))
            }
        }
    };
}