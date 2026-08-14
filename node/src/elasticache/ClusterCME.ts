// Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0
// Adapted from RedisClientsCompatibilityTesting/Node/wrapper/src/ClusterCME.ts

import {
    CreateReplicationGroupCommand,
    CreateReplicationGroupCommandInput,
} from "@aws-sdk/client-elasticache";
import { ElastiCacheClusterBase } from "./ClusterBase";
import { ConfigCME } from "./ConfigCME";
import { makeElastiCacheApiCall } from "./Utils";

export class ElastiCacheClusterCME extends ElastiCacheClusterBase {
    public constructor(name: string) {
        super(name);
    }

    public static async launch(
        config: ConfigCME | null = null,
    ): Promise<ElastiCacheClusterCME> {
        if (!config) {
            config = new ConfigCME();
        }

        console.log(`[ElastiCache] Creating CME cluster: ${config.toString()}`);
        const cluster = new ElastiCacheClusterCME(config.getName());
        const command = new CreateReplicationGroupCommand(
            config.createLaunchRequest() as CreateReplicationGroupCommandInput,
        );
        await makeElastiCacheApiCall(cluster.elastiCacheClient, command);
        await cluster.waitForAvailable();
        return cluster;
    }

    public async getConfigurationEndpoint(): Promise<string | undefined> {
        return (await this.getFirstReplicationGroup())?.ConfigurationEndpoint
            ?.Address;
    }

    public async getNumShards(): Promise<number> {
        return (await this.getFirstReplicationGroup())?.NodeGroups?.length ?? 0;
    }
}
