// Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0
// Adapted from RedisClientsCompatibilityTesting/Node/wrapper/src/ClusterBase.ts

import {
    DeleteReplicationGroupCommand,
    DescribeReplicationGroupsCommand,
    DescribeReplicationGroupsCommandOutput,
    ElastiCacheClient,
    ReplicationGroup,
} from "@aws-sdk/client-elasticache";
import {
    initElastiCacheClient,
    makeElastiCacheApiCall,
    waitBetweenApiCalls,
} from "./Utils";

export class ElastiCacheClusterBase {
    public name: string;
    public elastiCacheClient: ElastiCacheClient;

    public constructor(name: string) {
        this.name = name.toLowerCase();
        this.elastiCacheClient = initElastiCacheClient();
    }

    public async getFirstReplicationGroup(): Promise<
        ReplicationGroup | undefined
    > {
        const command = new DescribeReplicationGroupsCommand({
            ReplicationGroupId: this.name,
        });
        const response = (await makeElastiCacheApiCall(
            this.elastiCacheClient,
            command,
        )) as DescribeReplicationGroupsCommandOutput;
        return response?.ReplicationGroups?.[0];
    }

    public async getStatus(): Promise<string | undefined> {
        return (await this.getFirstReplicationGroup())?.Status;
    }

    public async isAvailable(): Promise<boolean> {
        return (await this.getStatus()) === "available";
    }

    public async waitForAvailable(): Promise<void> {
        console.log(
            `[ElastiCache] Waiting for ${this.name} to become available...`,
        );

        while (!(await this.isAvailable())) {
            await waitBetweenApiCalls();
        }

        console.log(`[ElastiCache] ${this.name} is available.`);
    }

    public getName(): string {
        return this.name;
    }

    public async deleteCluster(): Promise<boolean> {
        console.log(`[ElastiCache] Deleting cluster ${this.name}...`);
        const command = new DeleteReplicationGroupCommand({
            ReplicationGroupId: this.name,
            RetainPrimaryCluster: false,
        });

        try {
            await this.elastiCacheClient.send(command);
            console.log(`[ElastiCache] Delete request sent for ${this.name}.`);
            return true;
        } catch (error) {
            console.error(
                `[ElastiCache] Failed to delete ${this.name}: ${error}`,
            );
            return false;
        }
    }
}
