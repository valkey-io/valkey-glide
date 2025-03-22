// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Diagnostics;
using System.Runtime.CompilerServices;

using Glide.Internals;

/// Reusable source of ValueTask. This object can be allocated once and then reused
/// to create multiple asynchronous operations, as long as each call to CreateTask
/// is awaited to completion before the next call begins.
internal class Message : INotifyCompletion
{
    /// This is the index of the message in an external array, that allows the user to
    /// know how to find the message and set its result.
    public int Index { get; }
    private MessageContainer Container { get; }
    private Action? _continuation = () => { };
    private const int COMPLETION_STAGE_STARTED = 0;
    private const int COMPLETION_STAGE_NEXT_SHOULD_EXECUTE_CONTINUATION = 1;
    private const int COMPLETION_STAGE_CONTINUATION_EXECUTED = 2;
    private int _completionState;
    private object? _result = default;
    private Exception? _exception;
    // Holding the client prevents it from being GC'd until all operations complete.
#pragma warning disable IDE0052 // Remove unread private members
    private object? _client;
#pragma warning restore IDE0052 // Remove unread private members

    internal Message(int index, MessageContainer container)
    {
        Index = index;
        Container = container;
    }

    /// Triggers a succesful completion of the task returned from the latest call
    /// to CreateTask.
    public void SetResult(object? result)
    {
        _result = result;
        FinishSet();
    }

    /// Triggers a failure completion of the task returned from the latest call to
    /// CreateTask.
    public void SetException(Exception exc)
    {
        _exception = exc;
        FinishSet();
    }

    private void FinishSet()
    {
        CleanUp();

        CheckRaceAndCallContinuation();
    }

    private void CheckRaceAndCallContinuation()
    {
        if (Interlocked.CompareExchange(ref _completionState, COMPLETION_STAGE_NEXT_SHOULD_EXECUTE_CONTINUATION, COMPLETION_STAGE_STARTED) == COMPLETION_STAGE_NEXT_SHOULD_EXECUTE_CONTINUATION)
        {
            Debug.Assert(_continuation != null);
            _completionState = COMPLETION_STAGE_CONTINUATION_EXECUTED;
            try
            {
                _continuation();
            }
            finally
            {
                Container.ReturnFreeMessage(this);
            }
        }
    }

    public Message GetAwaiter() => this;

    /// This returns a task that will complete once SetException / SetResult are called,
    /// and ensures that the internal state of the message is set-up before the task is created,
    /// and cleaned once it is complete.
    public void SetupTask(object client)
    {
        _continuation = null;
        _completionState = COMPLETION_STAGE_STARTED;
        _result = default;
        _exception = null;
        _client = client;
    }

    private void CleanUp() => _client = null;

    public void OnCompleted(Action continuation)
    {
        _continuation = continuation;
        CheckRaceAndCallContinuation();
    }

    public bool IsCompleted => _completionState == COMPLETION_STAGE_CONTINUATION_EXECUTED;

    public object? GetResult() => _exception is null ? _result : throw _exception;
}
