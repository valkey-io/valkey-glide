#!/usr/bin/env node
const { platform, arch } = process;
let nativeBinding = null;
switch (platform) {
    case 'linux':
        switch (arch) {
            case 'x64':
                nativeBinding = await import("@scope/babushka-linux-x64");
                break;
            case 'arm64':
                nativeBinding = await import("@scope/babushka-linux-arm64");
                break;
            default:
                throw new Error(`Unsupported OS: ${platform}, architecture: ${arch}`);
        }
        break;
    case 'darwin':
        switch (arch) {
            case 'x64':
                nativeBinding = await import("@scope/babushka-darwin-x64");
                break;
            case 'arm64':
                nativeBinding = await import("@scope/babushka-darwin-arm64");
                break;
            default:
                throw new Error(`Unsupported OS: ${platform}, architecture: ${arch}`);
        }
        break;
    default:
        throw new Error(`Unsupported OS: ${platform}, architecture: ${arch}`);
}
if (!nativeBinding) {
    throw new Error(`Failed to load native binding`);
}
export const { RedisClient, RedisClusterClient, Logger, ExpireOptions, InfoOptions, ClosingError, ExecAbortError, RedisError, RequestError, TimeoutError, ClusterTransaction, Transaction } = nativeBinding;
export default Object.assign(global, nativeBinding);
