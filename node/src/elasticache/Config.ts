// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
// Adapted from RedisClientsCompatibilityTesting/Node/wrapper/src/Config.ts

import { CreateReplicationGroupCommandInput } from "@aws-sdk/client-elasticache";
import {
    CLUSTER_DESCRIPTION,
    DEFAULT_PARAMETER_GROUP,
    EC_PROD_SUBNET_GROUP,
    ENGINE_LOG_REQUEST,
    ENGINE_VERSION,
    INSTANCE_TYPE,
    SECURITY_GROUP,
    SLOW_LOG_REQUEST,
} from "./Constants";
import { generateRandomStr } from "./Utils";

export class Config {
    protected instanceType: INSTANCE_TYPE;
    protected version: ENGINE_VERSION;
    protected multiAz: boolean;
    public tls: boolean;
    protected clusterModeEnabled: boolean | null;
    private _name: string;

    public constructor(
        instanceType: INSTANCE_TYPE = INSTANCE_TYPE.T4G_SMALL,
        version: ENGINE_VERSION = ENGINE_VERSION.SIX,
        multiAz = true,
        tls = true,
        name: string | null = null,
    ) {
        this.instanceType = instanceType;
        this.version = version;
        this.multiAz = multiAz;
        this.tls = tls;
        this.clusterModeEnabled = null;
        // Match other repo: uses NAME env var as prefix, same format
        const prefix = process.env.NAME;

        if (!prefix && name === null) {
            throw new Error(
                "env var NAME not set. Set NAME to your cluster name prefix (e.g. your alias).",
            );
        }

        this._name = name ?? `${prefix}-test-${generateRandomStr(10)}`;
    }

    public getName(): string {
        return this._name;
    }

    public getDefaultParameterGroup(): DEFAULT_PARAMETER_GROUP {
        if (this.version === ENGINE_VERSION.EIGHT_0) {
            return this.clusterModeEnabled
                ? DEFAULT_PARAMETER_GROUP.VALKEY_8_CLUSTER_MODE_ENABLED
                : DEFAULT_PARAMETER_GROUP.VALKEY_8_CLUSTER_MODE_DISABLED;
        } else if (
            this.version === ENGINE_VERSION.SEVEN_2 ||
            this.version === ENGINE_VERSION.SEVEN
        ) {
            return this.clusterModeEnabled
                ? DEFAULT_PARAMETER_GROUP.VALKEY_7_CLUSTER_MODE_ENABLED
                : DEFAULT_PARAMETER_GROUP.VALKEY_7_CLUSTER_MODE_DISABLED;
        } else {
            // 6.x
            return this.clusterModeEnabled
                ? DEFAULT_PARAMETER_GROUP.REDIS_6_X_CLUSTER_MODE_ENABLED
                : DEFAULT_PARAMETER_GROUP.REDIS_6_X_CLUSTER_MODE_DISABLED;
        }
    }

    public createBaseLaunchRequest(): CreateReplicationGroupCommandInput {
        return {
            ReplicationGroupId: this._name,
            TransitEncryptionEnabled: this.tls,
            MultiAZEnabled: this.multiAz,
            CacheNodeType: this.instanceType,
            Engine: this.getEngine(),
            EngineVersion: this.version,
            CacheSubnetGroupName: EC_PROD_SUBNET_GROUP,
            LogDeliveryConfigurations: [ENGINE_LOG_REQUEST, SLOW_LOG_REQUEST],
            ReplicationGroupDescription: CLUSTER_DESCRIPTION,
            SecurityGroupIds: [SECURITY_GROUP],
        } as CreateReplicationGroupCommandInput;
    }

    private getEngine(): string {
        if (
            this.version === ENGINE_VERSION.SEVEN_2 ||
            this.version === ENGINE_VERSION.EIGHT_0
        ) {
            return "valkey";
        }

        return "redis";
    }

    public toString(): string {
        return `name: ${this._name}, instance: ${this.instanceType}, version: ${this.version}, tls: ${this.tls}, multiAz: ${this.multiAz}`;
    }
}
