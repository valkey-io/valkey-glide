namespace Valkey.Glide.InterOp.Errors;

/// <summary>
/// An error on Valkey service-side that is thrown when a transaction is aborted
/// </summary>
public sealed class ExecAbortException : GlideException
{
    public ExecAbortException() : base() { }

    public ExecAbortException(string message) : base(message) { }

    public ExecAbortException(string message, Exception innerException) : base(message, innerException) { }
}