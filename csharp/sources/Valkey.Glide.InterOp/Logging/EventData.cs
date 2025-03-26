// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.InterOp.Logging;

public readonly struct EventData(string name, string target, ESeverity severity, string? modulePath, string? file, int line, EEventDataKind kind)
{
    /// The name of the span described by this metadata.
    public readonly string Name = name;

    /// The part of the system that the span that this metadata describes
    /// occurred in.
    public readonly string Target = target;

    /// The severity of the described span.
    public readonly ESeverity Severity = severity;

    /// The name of the Rust module where the span occurred, or `nullptr` if this
    /// could not be determined.
    public readonly string? ModulePath = modulePath;

    /// The name of the source code file where the span occurred, or `nullptr` if
    /// this could not be determined.
    public readonly string? File = file;

    /// The line number in the source code file where the span occurred, or
    /// -1 if this could not be determined.
    public readonly int Line = line;

    /// The kind of the call-site.
    public readonly EEventDataKind Kind = kind;

    internal static unsafe EventData FromNative(ref Native.Logging.EventData eventData)
    {
        var name = HelperMethods.HandleString((byte*)eventData.name, eventData.name_length, false) ?? string.Empty;
        var target = HelperMethods.HandleString((byte*)eventData.target, eventData.target_length, false) ?? string.Empty;
        var severity = (ESeverity)eventData.severity;
        var modulePath = HelperMethods.HandleString((byte*)eventData.module_path, eventData.module_path_length, false);
        var file = HelperMethods.HandleString((byte*)eventData.file, eventData.file_length, false);
        var line = eventData.line;
        var kind = (EEventDataKind)eventData.kind;
        return new EventData(name, target, severity, modulePath, file, line, kind);
    }
}

