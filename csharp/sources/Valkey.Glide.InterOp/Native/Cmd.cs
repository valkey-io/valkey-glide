using System.Runtime.InteropServices;

namespace Valkey.Glide.InterOp.Native;

/// A wrapper for a command, resposible for marshalling (allocating and freeing) the required data
internal class Cmd : Marshallable
{
    private IntPtr[] _argPtrs = [];
    private GCHandle _pinnedArgs;
    private nuint[] _lengths = [];
    private GCHandle _pinnedLengths;
    private readonly GlideString[] _args;
    private CmdInfo _cmd;

    public Cmd(RequestType requestType, GlideString[] arguments)
    {
        _cmd = new() {RequestType = requestType, ArgCount = (nuint)arguments.Length};
        _args = arguments;
    }

    protected override void FreeMemory()
    {
        for (nuint i = 0; i < _cmd.ArgCount; i++)
        {
            Marshal.FreeHGlobal(_argPtrs[i]);
        }

        _pinnedArgs.Free();
        InterOpHelpers.PoolReturn(_argPtrs);
        _pinnedLengths.Free();
        InterOpHelpers.PoolReturn(_lengths);
    }

    protected override IntPtr AllocateAndCopy()
    {
        // 1. Allocate memory for arguments and for for arguments' lenghts
        _argPtrs = InterOpHelpers.PoolRent<IntPtr>(_args.Length);
        _lengths = InterOpHelpers.PoolRent<nuint>(_args.Length);

        // 2. Copy data into allocated array in unmanaged memory
        for (int i = 0; i < _args.Length; i++)
        {
            // 2.1 Copy an argument
            _argPtrs[i] = Marshal.AllocHGlobal(_args[i].Length);
            Marshal.Copy(_args[i].Bytes, 0, _argPtrs[i], _args[i].Length);
            // 2.2 Copy arg's len
            _lengths[i] = (nuint)_args[i].Length;
        }

        // 3. Pin it
        // We need to pin the array in place, in order to ensure that the GC doesn't move it while the operation is running.
        _pinnedArgs = GCHandle.Alloc(_argPtrs, GCHandleType.Pinned);
        _cmd.Args = _pinnedArgs.AddrOfPinnedObject();
        _pinnedLengths = GCHandle.Alloc(_lengths, GCHandleType.Pinned);
        _cmd.ArgLengths = _pinnedLengths.AddrOfPinnedObject();

        return InterOpHelpers.StructToPtr(_cmd);
    }
}
