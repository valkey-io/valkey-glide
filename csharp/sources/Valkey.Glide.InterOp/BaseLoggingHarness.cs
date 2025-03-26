// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.InterOp.Logging;

namespace Valkey.Glide.InterOp;

/// <summary>
/// Provides an abstract and safe logging harness for managing logging callbacks of glide core.
/// </summary>
/// <note>
/// <b>This class will always leak data!</b>
/// Once created, it cannot be undone without closing the application.
/// </note>
/// <exception cref="InvalidOperationException">
/// Thrown if an instance of <see cref="NativeLoggingHarness"/> was already created.
/// No memory will be leaked on the exception.
/// </exception>
/// <seealso cref="NativeLoggingHarness"/>
public abstract class BaseLoggingHarness() : NativeLoggingHarness
{
    protected abstract void OnEvent(string message, IReadOnlyCollection<KeyValuePair<string, string>> fields,
        EventData eventData, SpanContext spanContext);

    protected internal override void OnNativeEvent(
        string message,
        ref Native.Logging.Fields inFields,
        ref Native.Logging.EventData inEventData,
        Native.Logging.SpanContext inSpanContext)
    {
        var fields = HelperMethods.FromNativeFieldsToKeyValuePairs(inFields);
        var eventData = EventData.FromNative(ref inEventData);
        var spanContext = SpanContext.FromNative(inSpanContext);
        OnEvent(message, fields, eventData, spanContext);
    }

    protected abstract void OnRecord(
        string message,
        IReadOnlyCollection<KeyValuePair<string, string>> fields,
        ulong spanId);

    protected internal override void OnNativeRecord(
        string message,
        ref Native.Logging.Fields inFields,
        ulong inSpanId)
    {
        var fields = HelperMethods.FromNativeFieldsToKeyValuePairs(inFields);
        OnRecord(message, fields, inSpanId);
    }

    protected abstract void OnNewSpawn(
        string message,
        IReadOnlyCollection<KeyValuePair<string, string>> fields,
        EventData eventData,
        SpanContext spanContext, ulong spanId);

    protected internal override void OnNativeNewSpawn(
        string message,
        ref Native.Logging.Fields inFields,
        ref Native.Logging.EventData inEventData,
        Native.Logging.SpanContext inSpanContext,
        ulong inSpanId)
    {
        var fields = HelperMethods.FromNativeFieldsToKeyValuePairs(inFields);
        var eventData = EventData.FromNative(ref inEventData);
        var spanContext = SpanContext.FromNative(inSpanContext);
        OnNewSpawn(message, fields, eventData, spanContext, inSpanId);
    }

    protected abstract bool OnIsEnabled(EventData eventData);

    protected internal override bool OnNativeIsEnabled(ref Native.Logging.EventData inEventData)
    {
        var eventData = EventData.FromNative(ref inEventData);
        return OnIsEnabled(eventData);
    }
}
