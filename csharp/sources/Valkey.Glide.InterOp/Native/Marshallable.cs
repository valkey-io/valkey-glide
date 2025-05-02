namespace Valkey.Glide.InterOp.Native;

public abstract class Marshallable : IDisposable
{
    private IntPtr _ptr = IntPtr.Zero;

    public IntPtr ToPtr()
    {
        if (_ptr == IntPtr.Zero)
        {
            _ptr = AllocateAndCopy();
        }

        return _ptr;
    }

    public void Dispose()
    {
        if (_ptr == IntPtr.Zero)
        {
            return;
        }

        FreeMemory();
        InterOpHelpers.FreeStructPtr(_ptr);
    }

    // All unmanaged memory allocations should happen only on this call and never before.
    protected abstract IntPtr AllocateAndCopy();

    protected abstract void FreeMemory();
}
