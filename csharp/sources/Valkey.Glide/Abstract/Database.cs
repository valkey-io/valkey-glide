// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands.Options;
using Valkey.Glide.Internals;

using static Valkey.Glide.ConnectionConfiguration;

namespace Valkey.Glide;

internal class DatabaseImpl : GlideClient, IDatabase
{
    public new async Task<string> Info() => await Info([]);

    public new async Task<string> Info(InfoOptions.Section[] sections)
        => IsCluster
            ? await Command(Request.Info(sections), Route.Random)
            : await base.Info(sections);

    public IBatch CreateBatch(object? asyncState = null)
    {
        Utils.Requires<ArgumentException>(asyncState is null, "Async state is not supported by GLIDE");
        return new ValkeyBatch(this);
    }

    public ITransaction CreateTransaction(object? asyncState = null)
    {
        Utils.Requires<ArgumentException>(asyncState is null, "Async state is not supported by GLIDE");
        return new ValkeyTransaction(this);
    }

    internal readonly bool IsCluster;

    protected DatabaseImpl(bool isCluster) { IsCluster = isCluster; }

    internal static async Task<DatabaseImpl> Create(string host, ushort port, bool isCluster)
    {
        BaseClientConfiguration config = isCluster
            ? new ClusterClientConfigurationBuilder().WithAddress(host, port).WithRequestTimeout(1000).Build()
            : new StandaloneClientConfigurationBuilder().WithAddress(host, port).WithRequestTimeout(1000).Build();
        return await CreateClient(config, () => new DatabaseImpl(isCluster));
    }
}
