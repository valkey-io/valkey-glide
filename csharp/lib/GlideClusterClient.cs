// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Glide.ConnectionConfiguration;

namespace Glide;

public sealed class GlideClusterClient(ClusterClientConfiguration config) : BaseClient(config)
{
}
