// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.IntegrationTests;

/// <summary>
/// Base class for tests that automatically captures console output
/// </summary>
public abstract class ConsoleCapturingTestBase : IDisposable
{
    private readonly ConsoleOutputInterceptor _interceptor;

    protected ITestOutputHelper Output { get; }

#pragma warning disable IDE0290
    protected ConsoleCapturingTestBase(ITestOutputHelper output)
    {
        Output = output;
        _interceptor = new ConsoleOutputInterceptor(output);
    }
#pragma warning restore IDE0290

    public void Dispose()
    {
        _interceptor.Dispose();
    }
}
