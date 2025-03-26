// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Text;

namespace Valkey.Glide.InterOp;

/// <summary>
/// Logging harness that logs all glide logs to stdout, using <see cref="Console.WriteLine(string)"/>.
/// </summary>
public sealed class StdOutLoggingHarness : BaseLoggingHarness
{
    protected override void OnExit(ulong inSpanId)
    {
        // ReSharper disable once LocalizableElement
        // ReSharper disable once ArrangeMethodOrOperatorBody
        Console.WriteLine($"[Glide Core][OnExit]: {{ \"SpanId\": {inSpanId} }}");
    }

    protected override void OnEnter(ulong inSpanId)
    {
        // ReSharper disable once LocalizableElement
        // ReSharper disable once ArrangeMethodOrOperatorBody
        Console.WriteLine($"[Glide Core][OnEnter]: {{ \"SpanId\": {inSpanId} }}");
    }

    protected override void OnEvent(string message, IReadOnlyCollection<KeyValuePair<string, string>> fields, InterOp.Logging.EventData eventData, InterOp.Logging.SpanContext spanContext)
    {
        // START
        var builder = new StringBuilder();
        builder.Append("[Glide Core][OnNativeEvent]: { ");

        // Message
        builder.Append($"\"Message\": \"{message}\", ");

        // Fields
        builder.Append("\"Fields\": { ");
        foreach (KeyValuePair<string,string> keyValuePair in fields)
        {
            builder.Append($"\"{keyValuePair.Key}\": \"{keyValuePair.Value}\", ");
        }
        builder.Append("}, ");

        // EventData
        builder.Append("\"EventData\": { ");
        builder.Append($"\"Name\": {eventData.Name}, ");
        builder.Append($"\"Severity\": {eventData.Severity}, ");
        builder.Append($"\"Target\": {eventData.Target}, ");
        builder.Append($"\"File\": {eventData.File}, ");
        builder.Append($"\"Line\": {eventData.Line}, ");
        builder.Append($"\"ModulePath\": {eventData.ModulePath}, ");
        builder.Append($"\"Kind\": {eventData.Kind} ");
        builder.Append("}, ");

        // SpanContext
        builder.Append("\"SpanContext\": { ");
        builder.Append($"\"Kind\": {spanContext.Kind}, ");
        builder.Append($"\"ParentId\": {spanContext.ParentId} ");
        builder.Append("}");

        // END
        builder.Append("}");

        Console.WriteLine(builder.ToString());
    }


    protected override void OnRecord(string message, IReadOnlyCollection<KeyValuePair<string, string>> fields, ulong spanId)
    {
        var builder = new StringBuilder();
        builder.Append("[Glide Core][OnRecord]: { ");
        builder.Append($"\"Message\": \"{message}\", ");
        builder.Append("\"Fields\": { ");
        foreach (KeyValuePair<string,string> keyValuePair in fields)
        {
            builder.Append($"\"{keyValuePair.Key}\": \"{keyValuePair.Value}\", ");
        }
        builder.Append("}, ");
        builder.Append($"\"SpanId\": {spanId} ");
        builder.Append("}");
        Console.WriteLine(builder.ToString());
    }

    protected override void OnNewSpawn(
        string message,
        IReadOnlyCollection<KeyValuePair<string, string>> fields,
        InterOp.Logging.EventData eventData,
        InterOp.Logging.SpanContext spanContext,
        ulong spanId)
    {
        // START
        var builder = new StringBuilder();
        builder.Append("[Glide Core][OnNewSpawn]: { ");

        // Message
        builder.Append($"\"Message\": \"{message}\", ");

        // Fields
        builder.Append("\"Fields\": { ");
        foreach (KeyValuePair<string,string> keyValuePair in fields)
        {
            builder.Append($"\"{keyValuePair.Key}\": \"{keyValuePair.Value}\", ");
        }
        builder.Append("}, ");

        // EventData
        builder.Append("\"EventData\": { ");
        builder.Append($"\"Name\": {eventData.Name}, ");
        builder.Append($"\"Severity\": {eventData.Severity}, ");
        builder.Append($"\"Target\": {eventData.Target}, ");
        builder.Append($"\"File\": {eventData.File}, ");
        builder.Append($"\"Line\": {eventData.Line}, ");
        builder.Append($"\"ModulePath\": {eventData.ModulePath}, ");
        builder.Append($"\"Kind\": {eventData.Kind} ");
        builder.Append("}, ");

        // SpanContext
        builder.Append("\"SpanContext\": { ");
        builder.Append($"\"Kind\": {spanContext.Kind}, ");
        builder.Append($"\"ParentId\": {spanContext.ParentId} ");
        builder.Append("}, ");

        // SpanId
        builder.Append($"\"SpanId\": {spanId} ");

        // END
        builder.Append("}");
        Console.WriteLine(builder.ToString());
    }

    protected override bool OnIsEnabled(InterOp.Logging.EventData eventData) => true;
}
