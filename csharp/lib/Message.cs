using System.Runtime.InteropServices;
using System.Threading.Tasks.Sources;

/// Reusable source of ValueTask. This object can be allocated once and then reused
/// to create multiple asynchronous operations, as long as each call to CreateTask
/// is awaited to completion before the next call begins.
internal class Message<T> : IValueTaskSource<T>
{
    /// This is the index of the message in an external array, that allows the user to
    /// know how to find the message and set its result.
    public uint Index { get; }

    /// The pointer to the unmanaged memory that contains the operation's key.
    public IntPtr KeyPtr { get; private set; }

    /// The pointer to the unmanaged memory that contains the operation's key.
    public IntPtr ValuePtr { get; private set; }

    public Message(uint index)
    {
        Index = index;
    }

    /// Triggers a succesful completion of the task returned from the latest call 
    /// to CreateTask.
    public void SetResult(T result) => _source.SetResult(result);

    /// Triggers a failure completion of the task returned from the latest call to
    /// CreateTask.
    public void SetException(Exception exc) => _source.SetException(exc);

    /// This returns a task that will complete once SetException / SetResult are called,
    /// and ensures that the internal state of the message is set-up before the task is created,
    /// and cleaned once it is complete.
    public async ValueTask<T> CreateTask(string? key, string? value, object client)
    {
        this.client = client;
        this.KeyPtr = key is null ? IntPtr.Zero : Marshal.StringToHGlobalAnsi(key);
        this.ValuePtr = value is null ? IntPtr.Zero : Marshal.StringToHGlobalAnsi(value);
        var result = await new ValueTask<T>(this, _source.Version);
        FreePointers();
        _source.Reset();
        return result;
    }

    private void FreePointers()
    {
        if (ValuePtr != IntPtr.Zero)
        {
            Marshal.FreeHGlobal(KeyPtr);
        }
        if (ValuePtr != IntPtr.Zero)
        {
            Marshal.FreeHGlobal(ValuePtr);
        }
        client = null;
    }

    // Holding the client prevents it from being CG'd until all operations complete.
    private object? client;

    private ManualResetValueTaskSourceCore<T> _source = new ManualResetValueTaskSourceCore<T>()
    {
        RunContinuationsAsynchronously = false
    };

    ValueTaskSourceStatus IValueTaskSource<T>.GetStatus(short token)
        => _source.GetStatus(token);
    void IValueTaskSource<T>.OnCompleted(Action<object?> continuation,
        object? state, short token, ValueTaskSourceOnCompletedFlags flags)
            => _source.OnCompleted(continuation, state, token, flags);
    T IValueTaskSource<T>.GetResult(short token) => _source.GetResult(token);
}
