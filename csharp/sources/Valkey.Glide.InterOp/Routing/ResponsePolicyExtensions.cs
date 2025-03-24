using System.ComponentModel;
using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide.InterOp.Routing;

internal static class ResponsePolicyExtensions
{
    public static ERoutingInfoMultiResponsePolicy ToNative(this EResponsePolicy responsePolicy) =>
        responsePolicy switch
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
        };
}