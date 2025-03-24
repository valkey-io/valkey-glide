using BenchmarkDotNet.Attributes;
using Valkey.Glide.Commands.ExtensionMethods;

namespace Valkey.Glide.Benchmark.GlideClient;

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

    private Valkey.Glide.GlideClient        _client = null!;
    private ValkeyAspireFixture _fixture      = null!;

    [GlobalSetup]
    public async Task Setup()
    {
        _fixture = new ValkeyAspireFixture();
        await _fixture.InitializeAsync();
        _client = new Valkey.Glide.GlideClient(_fixture.ConnectionRequest);
        await _client.SetAsync("existing-key", "value");
    }

    [GlobalCleanup]
    public async Task Cleanup()
    {
        _client.Dispose();
        await _fixture.DisposeAsync();
    }

    #endregion

    [Benchmark]
    public async Task GetExistingKey() => _ = await _client.GetAsync("existing-key");

    [Benchmark]
    public async Task GetNonExistingKey() => _ = await _client.GetAsync("non-existing-key");

    [Benchmark]
    public async Task SetExistingKey() => await _client.SetAsync("existing-key", "value");
}
