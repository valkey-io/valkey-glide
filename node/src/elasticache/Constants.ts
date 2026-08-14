// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
// Adapted from RedisClientsCompatibilityTesting/Node/wrapper/src/Constants.ts

// These match the other repo exactly. Override via env vars if needed.
export const SECURITY_GROUP =
    process.env.EC_SECURITY_GROUP ;
export const EC_PROD_SUBNET_GROUP: string =
    process.env.EC_SUBNET_GROUP ?? "client-testing";
export const CLUSTER_DESCRIPTION =
    "Valkey GLIDE Windows integration test cluster";

export const ENGINE_LOG_REQUEST = {
    LogType: "engine-log",
    DestinationType: "cloudwatch-logs",
    DestinationDetails: {
        CloudWatchLogsDetails: {
            LogGroup: "client-testing-engine-logs",
        },
    },
    LogFormat: "text",
};

export const SLOW_LOG_REQUEST = {
    LogType: "slow-log",
    DestinationType: "cloudwatch-logs",
    DestinationDetails: {
        CloudWatchLogsDetails: {
            LogGroup: "client-testing-slow-logs",
        },
    },
    LogFormat: "text",
};

export enum INSTANCE_TYPE {
    M6G_LARGE = "cache.m6g.large",
    M6G_XLARGE = "cache.m6g.xlarge",
    M6G_2XLARGE = "cache.m6g.2xlarge",
    M6G_4XLARGE = "cache.m6g.4xlarge",
    M6G_8XLARGE = "cache.m6g.8xlarge",
    M6G_12XLARGE = "cache.m6g.12xlarge",
    M6G_16XLARGE = "cache.m6g.16xlarge",
    M5_LARGE = "cache.m5.large",
    M5_XLARGE = "cache.m5.xlarge",
    M5_2XLARGE = "cache.m5.2xlarge",
    M5_4XLARGE = "cache.m5.4xlarge",
    M5_12XLARGE = "cache.m5.12xlarge",
    M5_24XLARGE = "cache.m5.24xlarge",
    T4G_MICRO = "cache.t4g.micro",
    T4G_SMALL = "cache.t4g.small",
    T4G_MEDIUM = "cache.t4g.medium",
    T3_MICRO = "cache.t3.micro",
    T3_SMALL = "cache.t3.small",
    T3_MEDIUM = "cache.t3.medium",
    R6G_LARGE = "cache.r6g.large",
    R6G_XLARGE = "cache.r6g.xlarge",
    R6G_2XLARGE = "cache.r6g.2xlarge",
    R6G_8XLARGE = "cache.r6g.8xlarge",
    R5_LARGE = "cache.r5.large",
    R5_XLARGE = "cache.r5.xlarge",
    R5_2XLARGE = "cache.r5.2xlarge",
    R5_4XLARGE = "cache.r5.4xlarge",
}

export enum ENGINE_VERSION {
    // Match other repo naming: ElastiCache API accepts these exact strings
    SIX = "6.x",
    SEVEN = "7.0",
    SEVEN_2 = "7.2",
    EIGHT_0 = "8.0",
}

export enum DEFAULT_PARAMETER_GROUP {
    REDIS_6_X_CLUSTER_MODE_DISABLED = "default.redis6.x",
    REDIS_6_X_CLUSTER_MODE_ENABLED = "default.redis6.x.cluster.on",
    VALKEY_7_CLUSTER_MODE_DISABLED = "default.valkey7",
    VALKEY_7_CLUSTER_MODE_ENABLED = "default.valkey7.cluster.on",
    VALKEY_8_CLUSTER_MODE_DISABLED = "default.valkey8",
    VALKEY_8_CLUSTER_MODE_ENABLED = "default.valkey8.cluster.on",
}
