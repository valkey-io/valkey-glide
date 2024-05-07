#!/usr/bin/env node

/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */

import { arch, platform } from "process";

let globalObject = global as unknown;

function loadNativeBinding() {
    let nativeBinding = null;
    switch (platform) {
        case "linux":
            switch (arch) {
                case "x64":
                    nativeBinding = require("@scope/glide-for-redis-linux-x64");
                    break;
                case "arm64":
                    nativeBinding = require("@scope/glide-for-redis-linux-arm64");
                    break;
                default:
                    throw new Error(
                        `Unsupported OS: ${platform}, architecture: ${arch}`,
                    );
            }
            break;
        case "darwin":
            switch (arch) {
                case "x64":
                    nativeBinding = require("@scope/glide-for-redis-darwin-x64");
                    break;
                case "arm64":
                    nativeBinding = require("@scope/glide-for-redis-darwin-arm64");
                    break;
                default:
                    throw new Error(
                        `Unsupported OS: ${platform}, architecture: ${arch}`,
                    );
            }
            break;
        default:
            throw new Error(
                `Unsupported OS: ${platform}, architecture: ${arch}`,
            );
    }
    if (!nativeBinding) {
        throw new Error(`Failed to load native binding`);
    }
    return nativeBinding;
}

function initialize() {
    const nativeBinding = loadNativeBinding();
    const {
        RedisClient,
        RedisClusterClient,
        RedisClientConfiguration,
        SlotIdTypes,
        SlotKeyTypes,
        RouteByAddress,
        Routes,
        SingleNodeRoute,
        PeriodicChecksManualInterval,
        PeriodicChecks,
        Logger,
        ExpireOptions,
        InfoOptions,
        SetOptions,
        ZaddOptions,
        ScoreBoundry,
        RangeByIndex,
        RangeByScore,
        RangeByLex,
        SortedSetRange,
        StreamTrimOptions,
        StreamAddOptions,
        StreamReadOptions,
        ScriptOptions,
        ClosingError,
        ExecAbortError,
        RedisError,
        RequestError,
        TimeoutError,
        ConnectionError,
        ClusterTransaction,
        Transaction,
    } = nativeBinding;

    module.exports = {
        RedisClient,
        RedisClusterClient,
        RedisClientConfiguration,
        SlotIdTypes,
        SlotKeyTypes,
        RouteByAddress,
        Routes,
        SingleNodeRoute,
        PeriodicChecksManualInterval,
        PeriodicChecks,
        Logger,
        ExpireOptions,
        InfoOptions,
        SetOptions,
        ZaddOptions,
        ScoreBoundry,
        RangeByIndex,
        RangeByScore,
        RangeByLex,
        SortedSetRange,
        StreamTrimOptions,
        StreamAddOptions,
        StreamReadOptions,
        ScriptOptions,
        ClosingError,
        ExecAbortError,
        RedisError,
        RequestError,
        TimeoutError,
        ConnectionError,
        ClusterTransaction,
        Transaction,
    };

    globalObject = Object.assign(global, nativeBinding);
}

initialize();

export default globalObject;
