/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */

using System.Diagnostics;
using System.Runtime.CompilerServices;
using System.Runtime.InteropServices;

using Glide;

/// Reusable source of ValueTask. This object can be allocated once and then reused
/// to create multiple asynchronous operations, as long as each call to CreateTask
/// is awaited to completion before the next call begins.
internal class Message<T> : INotifyCompletion
{
    /// This is the index of the message in an external array, that allows the user to
    /// know how to find the message and set its result.
    public int Index { get; }

    /// The array holding the pointers  to the unmanaged memory that contains the operation's arguments.
    public IntPtr[]? args { get; private set; }
    // We need to save the args count, because sometimes we get arrays that are larger than they need to be. We can't rely on `this.args.Length`, due to it coming from an array pool.
    private int argsCount;
    private readonly MessageContainer<T> container;

    public Message(int index, MessageContainer<T> container)
    {
        Index = index;
        continuation = () => { };
        this.container = container;
    }

    private Action? continuation;
    const int COMPLETION_STAGE_STARTED = 0;
    const int COMPLETION_STAGE_NEXT_SHOULD_EXECUTE_CONTINUATION = 1;
    const int COMPLETION_STAGE_CONTINUATION_EXECUTED = 2;
    private int completionState;
    private T? result;
    private Exception? exception;

    /// Triggers a succesful completion of the task returned from the latest call
    /// to CreateTask.
    public void SetResult(T? result)
    {
        this.result = result;
        FinishSet();
    }

    /// Triggers a failure completion of the task returned from the latest call to
    /// CreateTask.
    public void SetException(Exception exc)
    {
        this.exception = exc;
        FinishSet();
    }

    private void FinishSet()
    {
        FreePointers();

        CheckRaceAndCallContinuation();
    }

    private void CheckRaceAndCallContinuation()
    {
        if (Interlocked.CompareExchange(ref this.completionState, COMPLETION_STAGE_NEXT_SHOULD_EXECUTE_CONTINUATION, COMPLETION_STAGE_STARTED) == COMPLETION_STAGE_NEXT_SHOULD_EXECUTE_CONTINUATION)
        {
            Debug.Assert(this.continuation != null);
            this.completionState = COMPLETION_STAGE_CONTINUATION_EXECUTED;
            try
            {
                continuation();
            }
            finally
            {
                this.container.ReturnFreeMessage(this);
            }
        }
    }

    public Message<T> GetAwaiter() => this;

    /// This returns a task that will complete once SetException / SetResult are called,
    /// and ensures that the internal state of the message is set-up before the task is created,
    /// and cleaned once it is complete.
    public void SetupTask(IntPtr[] args, int argsCount, object client)
    {
        continuation = null;
        this.completionState = COMPLETION_STAGE_STARTED;
        this.result = default(T);
        this.exception = null;
        this.client = client;
        this.args = args;
        this.argsCount = argsCount;
    }

    // This function isn't thread-safe. Access to it should be from a single thread, and only once per operation.
    // For the sake of performance, this responsibility is on the caller, and the function doesn't contain any safety measures.
    private void FreePointers()
    {
        if (this.args is not null)
        {
            for (var i = 0; i < this.argsCount; i++)
            {
                Marshal.FreeHGlobal(this.args[i]);
            }
            this.args = null;
            this.argsCount = 0;
        }
        client = null;
    }

    // Holding the client prevents it from being CG'd until all operations complete.
    private object? client;


    public void OnCompleted(Action continuation)
    {
        this.continuation = continuation;
        CheckRaceAndCallContinuation();
    }

    public bool IsCompleted => completionState == COMPLETION_STAGE_CONTINUATION_EXECUTED;

    public T? GetResult() => this.exception is null ? this.result : throw this.exception;
}
