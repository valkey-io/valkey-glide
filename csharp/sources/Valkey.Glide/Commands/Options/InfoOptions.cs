// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Commands.Options;

public class InfoOptions
{
    /// <summary>
    /// Specific section of information which could be requested by <c>INFO</c> command.
    /// </summary>
    public enum Section
    {
        /// <summary>
        /// SERVER: General information about the server.
        /// </summary>
        SERVER,
        /// <summary>
        /// CLIENTS: Client connections section.
        /// </summary>
        CLIENTS,
        /// <summary>
        /// MEMORY: Memory consumption related information.
        /// </summary>
        MEMORY,
        /// <summary>
        /// PERSISTENCE: RDB and AOF related information.
        /// </summary>
        PERSISTENCE,
        /// <summary>
        /// STATS: General statistics.
        /// </summary>
        STATS,
        /// <summary>
        /// REPLICATION: Master/replica replication information.
        /// </summary>
        REPLICATION,
        /// <summary>
        /// CPU: CPU consumption statistics.
        /// </summary>
        CPU,
        /// <summary>
        /// COMMANDSTATS: Valkey command statistics.
        /// </summary>
        COMMANDSTATS,
        /// <summary>
        /// LATENCYSTATS: Valkey command latency percentile distribution statistics.
        /// </summary>
        LATENCYSTATS,
        /// <summary>
        /// SENTINEL: Valkey Sentinel section (only applicable to Sentinel instances).
        /// </summary>
        SENTINEL,
        /// <summary>
        /// CLUSTER: Valkey Cluster section.
        /// </summary>
        CLUSTER,
        /// <summary>
        /// MODULES: Modules section.
        /// </summary>
        MODULES,
        /// <summary>
        /// KEYSPACE: Database related statistics.
        /// </summary>
        KEYSPACE,
        /// <summary>
        /// ERRORSTATS: Valkey error statistics.
        /// </summary>
        ERRORSTATS,
        /// <summary>
        /// ALL: Return all sections (excluding module generated ones).
        /// </summary>
        ALL,
        /// <summary>
        /// DEFAULT: Return only the default set of sections.
        /// </summary>
        DEFAULT,
        /// <summary>
        /// EVERYTHING: Includes all and modules.
        /// </summary>
        EVERYTHING,
    }
}
