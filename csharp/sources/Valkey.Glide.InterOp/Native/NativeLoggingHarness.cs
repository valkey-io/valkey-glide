// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Diagnostics;
using System.Diagnostics.CodeAnalysis;
using System.Runtime.CompilerServices;
using System.Runtime.InteropServices;
using Valkey.Glide.InterOp.Native.Logging;

namespace Valkey.Glide.InterOp;

/// <summary>
/// Represents an abstraction for the handling of logging hooks within the Valkey Glide InterOp framework.
/// </summary>
/// <remarks>
/// This abstract class provides a mechanism to interact with the native logging system,
/// ensuring proper lifecycle management and enabling custom logging implementations.
/// It utilizes callbacks for various logging events, which must be implemented in derived classes.
/// The logging event hooks are set using native imports and managed through function pointers.
/// </remarks>
/// <seealso cref="Valkey.Glide.InterOp.Native.Imports"/>
[SuppressMessage("ReSharper", "PrivateFieldCanBeConvertedToLocalVariable",
    Justification = "We need to keep the references alive.")]
public abstract unsafe class NativeLoggingHarness
{
    public static NativeLoggingHarness? Instance => s_instance;

    private static readonly object Lock = new();
    private static bool s_wasCreated;

    // ReSharper disable once NotAccessedField.Local -- We need to keep the reference alive at GC-Root
    private static NativeLoggingHarness? s_instance;

    private readonly IsEnabledCallback _isEnabledCallback;
    private readonly NewSpawnCallback _newSpawnCallback;
    private readonly RecordCallback _recordCallback;
    private readonly EventCallback _eventCallback;
    private readonly EnterCallback _enterCallback;
    private readonly ExitCallback _exitCallback;

    private readonly nint _isEnabledCallbackFptr;
    private readonly nint _newSpawnCallbackFptr;
    private readonly nint _recordCallbackFptr;
    private readonly nint _eventCallbackFptr;
    private readonly nint _enterCallbackFptr;
    private readonly nint _exitCallbackFptr;

    /// <summary>
    /// Provides an abstract and unsafe logging harness for managing logging callbacks and hooks.
    /// </summary>
    /// <remarks>
    /// This class defines the structure for handling various logging-related callbacks, including
    /// enabling, spawning new spans, recording events, assigning dependencies, and monitoring entry and exit events.
    /// The class ensures thread-safety during initialization and disallows multiple instances being created.
    /// </remarks>
    /// <threadsafety>
    /// Thread-safe due to the use of locking mechanisms and the enforcement of single instance creation.
    /// </threadsafety>
    /// <note>
    /// <b>This class will always leak data!</b>
    /// Once created, it cannot be undone without closing the application.
    /// </note>
    /// <exception cref="InvalidOperationException">
    /// Thrown if an instance of <see cref="NativeLoggingHarness"/> was already created.
    /// No memory will be leaked on the exception.
    /// </exception>
    protected NativeLoggingHarness()
    {
        lock (Lock)
        {
            if (s_wasCreated)
                throw new InvalidOperationException("Cannot create multiple logging harnesses.");
            s_wasCreated = true;
            s_instance = this;
        }

        _isEnabledCallback = OnIsEnabledCallback;
        _newSpawnCallback = OnNewSpawnCallback;
        _recordCallback = OnRecordCallback;
        _eventCallback = OnEventCallback;
        _enterCallback = OnEnterCallback;
        _exitCallback = OnExitCallback;
        _isEnabledCallbackFptr = Marshal.GetFunctionPointerForDelegate(_isEnabledCallback);
        _newSpawnCallbackFptr = Marshal.GetFunctionPointerForDelegate(_newSpawnCallback);
        _recordCallbackFptr = Marshal.GetFunctionPointerForDelegate(_recordCallback);
        _eventCallbackFptr = Marshal.GetFunctionPointerForDelegate(_eventCallback);
        _enterCallbackFptr = Marshal.GetFunctionPointerForDelegate(_enterCallback);
        _exitCallbackFptr = Marshal.GetFunctionPointerForDelegate(_exitCallback);
        GCHandle dataHandle = GCHandle.Alloc(this, GCHandleType.Normal);
        Native.Imports.set_logging_hooks(
            GCHandle.ToIntPtr(dataHandle),
            _isEnabledCallbackFptr,
            _newSpawnCallbackFptr,
            _recordCallbackFptr,
            _eventCallbackFptr,
            _enterCallbackFptr,
            _exitCallbackFptr);
    }

    [MethodImpl(MethodImplOptions.AggressiveInlining)]
    private static void WithSelf(nint data, Action<NativeLoggingHarness> action)
    {
        try
        {
            GCHandle dataHandle = GCHandle.FromIntPtr(data);
            NativeLoggingHarness? self = (NativeLoggingHarness)dataHandle.Target;
            action(self);
        }
        catch (Exception e)
        {
            // We write all exceptions to error to prevent program faults.
            Console.Error.WriteLine(e.Message);
            Console.Error.WriteLine(e.StackTrace);

            // Always break if debugger is attached.
            if (Debugger.IsAttached)
            {
                Debugger.Break();
            }
        }
    }

    [MethodImpl(MethodImplOptions.AggressiveInlining)]
    private static T WithSelf<T>(nint data, Func<NativeLoggingHarness, T> action, T defaultValue = default)
    {
        try
        {
            GCHandle dataHandle = GCHandle.FromIntPtr(data);
            NativeLoggingHarness? self = (NativeLoggingHarness)dataHandle.Target;
            return action(self);
        }
        catch (Exception e)
        {
            // We write all exceptions to error to prevent program faults.
            Console.Error.WriteLine(e.Message);
            Console.Error.WriteLine(e.StackTrace);

            // Always break if debugger is attached.
            if (Debugger.IsAttached)
            {
                Debugger.Break();
            }

            return defaultValue;
        }
    }

    [SuppressMessage("ReSharper", "InconsistentNaming")]
    private static void OnExitCallback(nint ref_data, ulong in_span_id)
        => WithSelf(ref_data, self =>
        {
            self.OnExit(in_span_id);
        });

    [SuppressMessage("ReSharper", "InconsistentNaming")]
    private static void OnEnterCallback(nint ref_data, ulong in_span_id)
        => WithSelf(ref_data, self => self.OnEnter(in_span_id));

    [SuppressMessage("ReSharper", "InconsistentNaming")]
    private static void OnEventCallback(nint ref_data, byte* in_message, int in_message_length, Fields in_fields,
        EventData* in_event_data, SpanContext in_span_context)
        => WithSelf(ref_data, self =>
        {
            var message = HelperMethods.HandleString(in_message, in_message_length, false);
            self.OnNativeEvent(message, ref in_fields, ref *in_event_data, in_span_context);
        });


    [SuppressMessage("ReSharper", "InconsistentNaming")]
    private static void OnRecordCallback(nint ref_data, byte* in_message, int in_message_length, Fields in_fields,
        ulong in_span_id)
        => WithSelf(ref_data, self =>
        {
            var message = HelperMethods.HandleString(in_message, in_message_length, false);
            self.OnNativeRecord(message, ref in_fields, in_span_id);
        });

    [SuppressMessage("ReSharper", "InconsistentNaming")]
    private static void OnNewSpawnCallback(nint ref_data, byte* in_message, int in_message_length, Fields in_fields,
        EventData* in_event_data, SpanContext in_span_context, ulong in_span_id)
        => WithSelf(ref_data, self =>
        {
            var message = HelperMethods.HandleString(in_message, in_message_length, false);
            self.OnNativeNewSpawn(message, ref in_fields, ref *in_event_data, in_span_context, in_span_id);
        });

    [SuppressMessage("ReSharper", "InconsistentNaming")]
    private static bool OnIsEnabledCallback(nint ref_data, EventData* in_event_data)
        => WithSelf(ref_data, self => self.OnNativeIsEnabled(ref *in_event_data));


    protected abstract void OnExit(ulong inSpanId);
    protected abstract void OnEnter(ulong inSpanId);

    [SuppressMessage("ReSharper", "MemberCanBeProtected.Global")]
    protected internal abstract void OnNativeEvent(
        string message,
        ref Fields inFields,
        ref EventData inEventData,
        SpanContext inSpanContext
    );

    [SuppressMessage("ReSharper", "MemberCanBeProtected.Global")]
    protected internal abstract void OnNativeRecord(
        string message,
        ref Fields inFields,
        ulong inSpanId
    );

    [SuppressMessage("ReSharper", "MemberCanBeProtected.Global")]
    protected internal abstract void OnNativeNewSpawn(
        string message,
        ref Fields inFields,
        ref EventData inEventData,
        SpanContext inSpanContext,
        ulong inSpanId
    );

    [SuppressMessage("ReSharper", "MemberCanBeProtected.Global")]
    protected internal abstract bool OnNativeIsEnabled(ref EventData inEventData);
}
