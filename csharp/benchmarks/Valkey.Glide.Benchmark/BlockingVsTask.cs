using BenchmarkDotNet.Attributes;
using Valkey.Glide.InterOp;
using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide.Benchmark;

/// <summary>
/// A class designed to benchmark the performance difference between asynchronous and blocking operations
/// using a NativeClient for executing commands.
/// </summary>
/// <remarks>
/// The class contains methods to measure the performance of getting a non-existing key
/// through both asynchronous and blocking command execution. These methods are set up to be
/// benchmarked using BenchmarkDotNet.
/// </remarks>
public class BlockingVsTask
{
    #region Setup / Teardown

    private NativeClient        _nativeClient = null!;
    private ValkeyAspireFixture _fixture      = null!;

    [GlobalSetup]
    public async Task Setup()
    {
        _fixture = new ValkeyAspireFixture();
        await _fixture.InitializeAsync();
        _nativeClient = new NativeClient(_fixture.ConnectionRequest);
    }

    [GlobalCleanup]
    public async Task Cleanup()
    {
        _nativeClient.Dispose();
        await _fixture.DisposeAsync();
    }

    #endregion

    [Benchmark]
    public async Task AsyncGetNonExistingKey()
    {
        _ = await _nativeClient.SendCommandAsync(ERequestType.Get, "non-existing-key");
    }

    [Benchmark]
    public void BlockingGetNonExistingKey()
    {
        _ = _nativeClient.SendCommand(ERequestType.Get, "non-existing-key");
    }
}
