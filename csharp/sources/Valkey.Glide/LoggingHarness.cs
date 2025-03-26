// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.InterOp;
using Valkey.Glide.InterOp.Native.Logging;

namespace Valkey.Glide;

public sealed class LoggingHarness : NativeLoggingHarness
{
    protected override void OnExit(ulong inSpanId)
    {
        Console.WriteLine($"OnExit: {inSpanId}");
    }

    protected override void OnEnter(ulong inSpanId)
    {
        Console.WriteLine($"OnEnter: {inSpanId}");
    }

    protected override void OnEvent(string message, Fields inFields, ref EventData inEventData,
        SpanContext inSpanContext)
    {
        Console.WriteLine($"OnEvent: {message}");
    }

    protected override void OnRecordFollowsFrom(ulong inSpanId, ulong inFollowsId)
    {
        Console.WriteLine($"OnRecordFollowsFrom: {inSpanId} -> {inFollowsId}");
    }

    protected override void OnRecord(string message, Fields inFields, ulong inSpanId) {
        Console.WriteLine($"OnRecord: {message}");
    }

    protected override ulong OnNewSpawn(string message, Fields inFields, ref EventData inEventData,
        SpanContext inSpanContext)
    {
        Console.WriteLine($"OnNewSpawn: {message}");
        return 0;
    }

    protected override bool OnIsEnabled(ref EventData inEventData)
    {
        Console.WriteLine("OnIsEnabled");
        return true;
    }
}
