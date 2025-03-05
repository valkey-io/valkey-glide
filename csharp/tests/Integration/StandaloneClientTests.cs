// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Tests.Integration;

public class StandaloneClientTests
{
    [Fact]
    public void CanConnectWithDifferentParameters()
    {
        _ = new GlideClient(TestConfiguration.DefaultClientConfig()
            .WithClientName("GLIDE").Build());

        _ = new GlideClient(TestConfiguration.DefaultClientConfig()
            .WithTls(false).Build());

        _ = new GlideClient(TestConfiguration.DefaultClientConfig()
            .WithConnectionTimeout(2000).Build());

        _ = new GlideClient(TestConfiguration.DefaultClientConfig()
            .WithRequestTimeout(2000).Build());

        _ = new GlideClient(TestConfiguration.DefaultClientConfig()
            .WithDataBaseId(4).Build());

        _ = new GlideClient(TestConfiguration.DefaultClientConfig()
            .WithConnectionRetryStrategy(1, 2, 3).Build());

        _ = new GlideClient(TestConfiguration.DefaultClientConfig()
            .WithAuthentication("default", "").Build());

        _ = new GlideClient(TestConfiguration.DefaultClientConfig()
            .WithProtocolVersion(ConnectionConfiguration.Protocol.RESP2).Build());

        _ = new GlideClient(TestConfiguration.DefaultClientConfig()
            .WithReadFrom(new ConnectionConfiguration.ReadFrom(ConnectionConfiguration.ReadFromStrategy.Primary)).Build());
    }
}
