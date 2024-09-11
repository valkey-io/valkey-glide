/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.commands.ServerManagementCommands;

/**
 * Optional arguments to {@link ServerManagementCommands#info(InfoOptions)}
 *
 * @see <a href="https://valkey.io/commands/info/">valkey.io</a>
 */
public final class InfoOptions {

    public enum Section {
        /** SERVER: General information about the server */
        SERVER,
        /** CLIENTS: Client connections section */
        CLIENTS,
        /** MEMORY: Memory consumption related information */
        MEMORY,
        /** PERSISTENCE: RDB and AOF related information */
        PERSISTENCE,
        /** STATS: General statistics */
        STATS,
        /** REPLICATION: Master/replica replication information */
        REPLICATION,
        /** CPU: CPU consumption statistics */
        CPU,
        /** COMMANDSTATS: Valkey command statistics */
        COMMANDSTATS,
        /** LATENCYSTATS: Valkey command latency percentile distribution statistics */
        LATENCYSTATS,
        /** SENTINEL: Valkey Sentinel section (only applicable to Sentinel instances) */
        SENTINEL,
        /** CLUSTER: Valkey Cluster section */
        CLUSTER,
        /** MODULES: Modules section */
        MODULES,
        /** KEYSPACE: Database related statistics */
        KEYSPACE,
        /** ERRORSTATS: Valkey error statistics */
        ERRORSTATS,
        /** ALL: Return all sections (excluding module generated ones) */
        ALL,
        /** DEFAULT: Return only the default set of sections */
        DEFAULT,
        /** EVERYTHING: Includes all and modules */
        EVERYTHING,
    }
}
