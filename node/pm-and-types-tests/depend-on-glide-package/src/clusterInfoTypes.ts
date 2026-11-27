import {
    ClusterResponse,
    GlideClusterClient,
    PartialResultsOption,
    RequestError,
    RouteOption,
} from "@valkey/valkey-glide";

// Parameters<> and ReturnType<> read the last overload of `info`, so they
// must keep the type of the call without `allowPartialResults`.
export async function clusterInfoTypes(
    client: GlideClusterClient,
    allowPartialResults?: boolean,
) {
    const info = (...args: Parameters<GlideClusterClient["info"]>) =>
        client.info(...args);
    const plain: ClusterResponse<string> = await info();
    const returned: ReturnType<GlideClusterClient["info"]> = client.info();
    const plainReturn: Promise<ClusterResponse<string>> = returned;
    const partial: string | Record<string, string | RequestError> =
        await client.info({ allowPartialResults: true });
    // An optional boolean also gets the type of the call with the option.
    const optional: string | Record<string, string | RequestError> =
        await client.info({ allowPartialResults });
    // Options typed `any` keep the type of the call without the option.
    const untyped: ClusterResponse<string> = await client.info(
        JSON.parse("{}"),
    );
    // A variable whose type has the option gets the type of the call with it.
    const options: RouteOption & PartialResultsOption = { allowPartialResults };
    // @ts-expect-error A node's error can be in the reply.
    const variable: ClusterResponse<string> = await client.info(options);
    return [plain, plainReturn, partial, optional, untyped, variable];
}
