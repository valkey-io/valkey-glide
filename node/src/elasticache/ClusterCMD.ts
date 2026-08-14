// Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0
// Adapted from RedisClientsCompatibilityTesting/Node/wrapper/src/ClusterCMD.ts

import { CreateReplicationGroupCommand } from "@aws-sdk/client-elasticache";
import { ElastiCacheClusterBase } from "./ClusterBase";
import { ConfigCMD } from "./ConfigCMD";
import { makeElastiCacheApiCall } from "./Utils";

export class ElastiCacheClusterCMD extends ElastiCacheClusterBase {
    public constructor(name: string) {
        super(name);
    }

    public static async launch(
        config: ConfigCMD | null = null,
    ): Promise<ElastiCacheClusterCMD> {
        if (!config) {
            config = new ConfigCMD();
        }

        console.log(`[ElastiCache] Creating CMD cluster: ${config.toString()}`);
        const cluster = new ElastiCacheClusterCMD(config.getName());
        const command = new CreateReplicationGroupCommand(
            config.createLaunchRequest(),
        );
        await makeElastiCacheApiCall(cluster.elastiCacheClient, command);
        await cluster.waitForAvailable();
        return cluster;
    }

    public async getPrimaryEndpoint(): Promise<string | undefined> {
        return (await this.getFirstReplicationGroup())?.NodeGroups?.[0]
            ?.PrimaryEndpoint?.Address;
    }

    public async getReaderEndpoint(): Promise<string | undefined> {
        return (await this.getFirstReplicationGroup())?.NodeGroups?.[0]
            ?.ReaderEndpoint?.Address;
    }

    /**
     * Returns all individual node addresses [host, port] for this replication group.
     * For a standalone cluster with N replicas, returns N+1 entries (1 primary + N replicas).
     * Uses ReadEndpoint for replicas and PrimaryEndpoint for the primary node.
     */
    public async getAllNodeAddresses(): Promise<[string, number][]> {
        const members = (await this.getFirstReplicationGroup())?.NodeGroups?.[0]
            ?.NodeGroupMembers;

        if (!members) return [];

        const addresses: [string, number][] = [];

        for (const member of members) {
            const host = member.ReadEndpoint?.Address;
            const port = member.ReadEndpoint?.Port;

            if (host && port) {
                addresses.push([host, port]);
            }
        }

        return addresses;
    }
}
