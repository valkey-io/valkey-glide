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
        /** ALL: Return all sections (excluding module generated ones) */
        ALL,
        /** CLIENTS: Client connections section */
        CLIENTS,
        /** CLUSTER: Valkey Cluster section */
        CLUSTER,
        /**
         * CLUSTER_INFO: Valkey Cluster information.
         *
         * <p>Since Valkey 9.1.0.
         */
        CLUSTER_INFO,
        /** COMMANDSTATS: Valkey command statistics */
        COMMANDSTATS,
        /** CPU: CPU consumption statistics */
        CPU,
        /** DEFAULT: Return only the default set of sections */
        DEFAULT,
        /** ERRORSTATS: Valkey error statistics */
        ERRORSTATS,
        /** EVERYTHING: Includes all and modules */
        EVERYTHING,
        /** KEYSPACE: Database related statistics */
        KEYSPACE,
        /** LATENCYSTATS: Valkey command latency percentile distribution statistics */
        LATENCYSTATS,
        /** MEMORY: Memory consumption related information */
        MEMORY,
        /** MODULES: Modules section */
        MODULES,
        /** PERSISTENCE: RDB and AOF related information */
        PERSISTENCE,
        /** REPLICATION: Master/replica replication information */
        REPLICATION,
        /** SENTINEL: Valkey Sentinel section (only applicable to Sentinel instances) */
        SENTINEL,
        /** SERVER: General information about the server */
        SERVER,
        /** STATS: General statistics */
        STATS,
    }
}
