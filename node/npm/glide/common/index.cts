import { arch, platform } from "process";

export const ERR_MESSAGE_LOAD_FAILED = `Failed to load native binding`;

const getPackageDistro = () => {
    switch (platform) {
        case "linux":
            switch (arch) {
                case "x64":
                    return "glide-for-redis-linux-x64";
                case "arm64":
                    return "glide-for-redis-linux-arm64";
                default:
                    throw new Error(
                        `Unsupported OS: ${platform}, architecture: ${arch}`,
                    );
            }
        case "darwin":
            switch (arch) {
                case "x64":
                    return "glide-for-redis-darwin-x64";
                case "arm64":
                    return "glide-for-redis-darwin-arm64";
                default:
                    throw new Error(
                        `Unsupported OS: ${platform}, architecture: ${arch}`,
                    );
            }
        default:
            throw new Error(
                `Unsupported OS: ${platform}, architecture: ${arch}`,
            );
    }
};
export const getPackagePath = () => {
    const distro = getPackageDistro();
    return `@scope/${distro}`;
};

export const getExports = ({
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
    Transaction
}: any) => ({
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
});
