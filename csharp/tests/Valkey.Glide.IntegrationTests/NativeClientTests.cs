using Valkey.Glide.IntegrationTests.Fixtures;
using Valkey.Glide.InterOp;
using Valkey.Glide.InterOp.Native;
using Value = Valkey.Glide.InterOp.Value;

namespace Valkey.Glide.IntegrationTests;

public class NativeClientTests(ValkeyAspireFixture fixture) : IClassFixture<ValkeyAspireFixture>
{
    [Fact]
    public void CanCreateClient()
    {
        // Arrange
        // Act
        using NativeClient nativeClient = new(fixture.ConnectionRequest);
        // Assert
        Assert.NotNull(nativeClient);
    }

    [Fact]
    public async Task CanSendGetCommandAsync()
    {
        using NativeClient nativeClient = new(fixture.ConnectionRequest);
        Value result = await nativeClient.SendCommandAsync(ERequestType.Get, "test");
        Assert.Equivalent(InterOp.EValueKind.None, result.Kind);
    }

    [Theory]
    [InlineData(NativeClient.SmallStringOptimizationArgs - 3)]
    [InlineData(NativeClient.SmallStringOptimizationArgs - 2)]
    [InlineData(NativeClient.SmallStringOptimizationArgs - 1)]
    [InlineData(NativeClient.SmallStringOptimizationArgs)]
    [InlineData(NativeClient.SmallStringOptimizationArgs + 1)]
    [InlineData(NativeClient.SmallStringOptimizationArgs + 2)]
    [InlineData(NativeClient.SmallStringOptimizationArgs + 3)]
    [InlineData(NativeClient.SmallStringOptimizationArgs + NativeClient.SmallStringOptimizationArgs)]
    public async Task CanSendGetCommandWithSmallStringOptimization(int argsCount)
    {
        Assert.InRange(argsCount, 0, int.MaxValue);
        using NativeClient nativeClient = new(fixture.ConnectionRequest);
        Value result = await nativeClient.SendCommandAsync(ERequestType.Get, new string('0', argsCount));
        Assert.Equivalent(InterOp.EValueKind.None, result.Kind);
    }
    [Theory]
    [InlineData(NativeClient.SmallStringOptimizationArgs - 3)]
    [InlineData(NativeClient.SmallStringOptimizationArgs - 2)]
    [InlineData(NativeClient.SmallStringOptimizationArgs - 1)]
    [InlineData(NativeClient.SmallStringOptimizationArgs)]
    [InlineData(NativeClient.SmallStringOptimizationArgs + 1)]
    [InlineData(NativeClient.SmallStringOptimizationArgs + 2)]
    [InlineData(NativeClient.SmallStringOptimizationArgs + 3)]
    [InlineData(NativeClient.SmallStringOptimizationArgs + NativeClient.SmallStringOptimizationArgs)]
    public async Task CanSendSetCommandWithSmallStringOptimization(int argsCount)
    {
        Assert.InRange(argsCount, 2, int.MaxValue);
        using NativeClient nativeClient = new(fixture.ConnectionRequest);
        Value result = await nativeClient.SendCommandAsync(ERequestType.Set, new string('0', argsCount), string.Concat("\"", new string('0', argsCount - 2), "\""));
        Assert.Equivalent(InterOp.EValueKind.Okay, result.Kind);
    }
    [Fact]
    public async Task CanRunConcurrently()
    {
        using NativeClient nativeClient = new(fixture.ConnectionRequest);
        await Parallel.ForAsync(0, 1000, new ParallelOptions
        {
            MaxDegreeOfParallelism = 1000,
            TaskScheduler = TaskScheduler.Default,
        }, async (_, _) => await nativeClient.SendCommandAsync(ERequestType.Get, "test")
            .ConfigureAwait(false));
    }

    [Fact]
    public void CanDoubleDispose()
    {
        // Arrange
        NativeClient nativeClient = new(fixture.ConnectionRequest);
        // Act
        nativeClient.Dispose();
        nativeClient.Dispose();
        // Assert
    }

}
