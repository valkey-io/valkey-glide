// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
// Adapted from RedisClientsCompatibilityTesting/Node/wrapper/src/ConfigCME.ts
// Defaults match the other repo exactly: TLS=true, multiAz=true, numShards=2, numReplicasPerShard=1

import { CreateReplicationGroupCommandInput } from "@aws-sdk/client-elasticache";
import { Config } from "./Config";
import { ENGINE_VERSION, INSTANCE_TYPE } from "./Constants";

export class ConfigCME extends Config {
    public numShards: number;
    public numReplicasPerShard: number;

    public constructor(
        instanceType: INSTANCE_TYPE = INSTANCE_TYPE.T4G_SMALL,
        tls = false,
        version: ENGINE_VERSION = ENGINE_VERSION.SEVEN_2,
        multiAz = false,
        name: string | null = null,
        numShards = 2,
        numReplicasPerShard = 1,
    ) {
        super(instanceType, version, multiAz, tls, name);
        this.numShards = numShards;
        this.numReplicasPerShard = numReplicasPerShard;
        this.clusterModeEnabled = true;
    }

    public createLaunchRequest(): CreateReplicationGroupCommandInput {
        const req = this.createBaseLaunchRequest();

        if (this.numReplicasPerShard === 0 && this.multiAz) {
            throw new Error(
                "Using multiple availability zones requires more than 0 replicas per shard",
            );
        }

        req.NumNodeGroups = this.numShards;
        req.ReplicasPerNodeGroup = this.numReplicasPerShard;
        return req;
    }

    public toString(): string {
        return `${super.toString()}, numShards: ${this.numShards}, numReplicasPerShard: ${this.numReplicasPerShard}, clusterMode: enabled`;
    }
}
