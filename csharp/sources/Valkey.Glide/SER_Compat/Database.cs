// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Commands.Options;

using static Valkey.Glide.ConnectionConfiguration;
using static Valkey.Glide.Internals.FFI;

namespace Valkey.Glide.SER_Compat;

/// <summary>
/// Describes functionality that is common to both standalone and cluster servers.<br />
/// This API is obsolete and no longer supported.<br />
/// Please use <see cref="GlideClient" /> or <see cref="GlideClusterClient" /> instead.
/// </summary>
[Obsolete("This API is obsolete and no longer supported. Please use `GlideClient` and `GlideClusterClient` instead.", false)]
public interface IDatabase : IStringBaseCommands, IGenericCommands, IServerManagementCommands
{ }

[Obsolete]
internal class DatabaseImpl : GlideClient, IDatabase
{
    public new async Task<string> Info() => await Info([]);

    public new async Task<string> Info(InfoOptions.Section[] sections)
        => _isCluster
            ? await Command(RequestType.Info, sections.ToGlideStrings(),
                resp => HandleServerResponse<GlideString, string>(resp, false, gs => gs.ToString()), Route.Random)
            : await base.Info(sections);

    private readonly bool _isCluster;

    private DatabaseImpl(bool isCluster) { _isCluster = isCluster; }

    internal static async Task<DatabaseImpl> Create(string host, ushort port, bool isCluster)
    {
        BaseClientConfiguration config = isCluster
            ? new ClusterClientConfigurationBuilder().WithAddress(host, port).Build()
            : new StandaloneClientConfigurationBuilder().WithAddress(host, port).Build();
        return await CreateClient(config, () => new DatabaseImpl(isCluster));
    }
}
