import {
    ClusterResponse,
    GlideClusterClient,
    RequestError,
} from "@valkey/valkey-glide";

// Parameters<> and ReturnType<> read the last overload of `info`, so they
// must keep the type of the call without `allowPartialResults`.
export async function clusterInfoTypes(client: GlideClusterClient) {
    const info = (...args: Parameters<GlideClusterClient["info"]>) =>
        client.info(...args);
    const plain: ClusterResponse<string> = await info();
    const returned: ReturnType<GlideClusterClient["info"]> = client.info();
    const plainReturn: Promise<ClusterResponse<string>> = returned;
    const partial: string | Record<string, string | RequestError> =
        await client.info({ allowPartialResults: true });
    return [plain, plainReturn, partial];
}
