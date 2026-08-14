// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
// Adapted from RedisClientsCompatibilityTesting/Node/wrapper/src/ConfigCMD.ts
// Defaults match the other repo exactly: TLS=true, multiAz=true, numReplicas=1

import { CreateReplicationGroupCommandInput } from "@aws-sdk/client-elasticache";
import { Config } from "./Config";
import { ENGINE_VERSION, INSTANCE_TYPE } from "./Constants";

export class ConfigCMD extends Config {
    public numReplicas: number;

    public constructor(
        instanceType: INSTANCE_TYPE = INSTANCE_TYPE.T4G_SMALL,
        tls = false,
        version: ENGINE_VERSION = ENGINE_VERSION.SEVEN_2,
        multiAz = false,
        name: string | null = null,
        numReplicas = 1,
    ) {
        super(instanceType, version, multiAz, tls, name);
        this.numReplicas = numReplicas;
        this.clusterModeEnabled = false;
    }

    public createLaunchRequest(): CreateReplicationGroupCommandInput {
        const req = this.createBaseLaunchRequest();

        if (this.numReplicas === 0 && this.multiAz) {
            throw new Error(
                "Using multiple availability zones requires more than 0 replicas",
            );
        }

        req.NumCacheClusters = this.numReplicas + 1;
        return req;
    }

    public toString(): string {
        return `${super.toString()}, numReplicas: ${this.numReplicas}, clusterMode: disabled`;
    }
}
