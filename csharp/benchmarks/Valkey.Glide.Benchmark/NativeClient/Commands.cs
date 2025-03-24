using BenchmarkDotNet.Attributes;
using Valkey.Glide.InterOp.Native;
using Valkey.Glide.InterOp.Routing;

namespace Valkey.Glide.Benchmark.NativeClient;

/// <summary>
/// A class designed to benchmark the performance difference between asynchronous and blocking operations
/// using a NativeClient for executing commands.
/// </summary>
/// <remarks>
/// The class contains methods to measure the performance of getting a non-existing key
/// through both asynchronous and blocking command execution. These methods are set up to be
/// benchmarked using BenchmarkDotNet.
/// </remarks>
public class Commands
{
    #region Setup / Teardown

    private InterOp.NativeClient _nativeClient = null!;
    private ValkeyAspireFixture _fixture = null!;

    [GlobalSetup]
    public async Task Setup()
    {
        _fixture = new ValkeyAspireFixture();
        await _fixture.InitializeAsync();
        _nativeClient = new InterOp.NativeClient(_fixture.ConnectionRequest);
        await _nativeClient.SendCommandAsync(ERequestType.Set, new NoRouting(), "existing-key", "\"value\"");
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
        _ = await _nativeClient.SendCommandAsync(ERequestType.Get, new NoRouting(), "non-existing-key");
    }
}
