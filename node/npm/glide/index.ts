#!/usr/bin/env node

/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */

import { arch, platform } from "process";

let globalObject = global as unknown;

async function loadNativeBinding() {
    let nativeBinding = null;
    switch (platform) {
        case "linux":
            switch (arch) {
                case "x64":
                    nativeBinding = await import(
                        "@scope/glide-for-redis-linux-x64"
                    );
                    break;
                case "arm64":
                    nativeBinding = await import(
                        "@scope/glide-for-redis-linux-arm64"
                    );
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
                    nativeBinding = await import(
                        "@scope/glide-for-redis-darwin-x64"
                    );
                    break;
                case "arm64":
                    nativeBinding = await import(
                        "@scope/glide-for-redis-darwin-arm64"
                    );
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

async function initialize() {
    const nativeBinding = await loadNativeBinding();
    const {
        RedisClient,
        RedisClusterClient,
        Logger,
        ExpireOptions,
        InfoOptions,
        ClosingError,
        ExecAbortError,
        RedisError,
        RequestError,
        TimeoutError,
        ClusterTransaction,
        Transaction,
    } = nativeBinding;

    module.exports = {
        RedisClient,
        RedisClusterClient,
        Logger,
        ExpireOptions,
        InfoOptions,
        ClosingError,
        ExecAbortError,
        RedisError,
        RequestError,
        TimeoutError,
        ClusterTransaction,
        Transaction,
    };

    globalObject = Object.assign(global, nativeBinding);
}

initialize().catch((error) => {
    console.error("Failed to initialize:", error);
    process.exit(1);
});

export default globalObject;
