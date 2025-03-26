// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Microsoft.Extensions.Logging;
using Valkey.Glide.InterOp;
using Valkey.Glide.InterOp.Logging;

namespace Valkey.Glide.Hosting;

/// <summary>
/// Logging harness for the native Glide Core to log against.
/// Will log all events received to the <paramref name="logger"/> passed.
/// </summary>
/// <remarks>
/// This class is a singleton. Only one logging harness may exist per application.
/// This limitation is enforced!
/// </remarks>
/// <param name="logger">The logger to use for logging.</param>
/// <note>
/// <b>This class will always leak data!</b>
/// Once created, it cannot be undone without closing the application.
/// </note>
/// <exception cref="InvalidOperationException">
/// Thrown if an instance of <see cref="NativeLoggingHarness"/> was already created.
/// No memory will be leaked on the exception.
/// </exception>
/// <seealso cref="BaseLoggingHarness"/>
/// <seealso cref="NativeLoggingHarness"/>
public class GlideCoreLogger(ILogger<GlideCoreLogger> logger) : BaseLoggingHarness
{
    protected override void OnExit(ulong inSpanId)
    {
        // Do nothing
    }

    protected override void OnEnter(ulong inSpanId)
    {
        // Do nothing
    }

    protected override void OnEvent(string message, IReadOnlyCollection<KeyValuePair<string, string>> fields, EventData eventData, SpanContext spanContext)
    {
        var logLevel = eventData.Severity switch
        {
            ESeverity.Trace => LogLevel.Trace,
            ESeverity.Debug => LogLevel.Debug,
            ESeverity.Info => LogLevel.Information,
            ESeverity.Warn => LogLevel.Warning,
            ESeverity.Error => LogLevel.Error,
            _ => throw new ArgumentOutOfRangeException()
        };
        logger.Log(logLevel, new EventId(1, eventData.Name), fields, null, (_, _) => message);
    }

    protected override void OnRecord(string message, IReadOnlyCollection<KeyValuePair<string, string>> fields, ulong spanId)
    {
        // Do nothing
    }

    protected override void OnNewSpawn(string message, IReadOnlyCollection<KeyValuePair<string, string>> fields, EventData eventData, SpanContext spanContext, ulong spanId)
    {
        // Do nothing
    }

    protected override bool OnIsEnabled(EventData eventData) => true;
}
