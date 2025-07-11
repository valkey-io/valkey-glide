// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.IntegrationTests;

/// <summary>
/// Base class for tests that automatically captures console output
/// </summary>
public abstract class ConsoleCapturingTestBase : IDisposable
{
    private readonly ConsoleOutputInterceptor _interceptor;

    protected ITestOutputHelper Output { get; }
    protected ConsoleCapturingTestBase(ITestOutputHelper output)
    {
        Output = output;
        _interceptor = new ConsoleOutputInterceptor(output);
    }

    public void Dispose()
    {
        _interceptor.Dispose();
    }
}
