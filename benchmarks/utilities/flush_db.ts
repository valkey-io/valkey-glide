import { createRedisClient, receivedOptions } from "./utils";
import { RedisClusterType, RedisClientType } from "redis";

async function flush_database(
    host: string,
    isCluster: boolean,
    tls: boolean,
    port: number
) {
    if (isCluster) {
        const client = (await createRedisClient(
            host,
            isCluster,
            tls,
            port
        )) as RedisClusterType;
        await Promise.all(
            client.masters.map((master) => {
                return flush_database(master.host, false, tls, port);
        const client = (await createRedisClient(
            host,
            isCluster,
            tls,
            port
        )) as RedisClientType;
        await client.connect();
        await client.flushAll();
        await client.quit();
    }
}

Promise.resolve()
    .then(async () => {
        console.log("Flushing " + receivedOptions.host);
        await flush_database(
            receivedOptions.host,
            receivedOptions.clusterModeEnabled,
<<<<<<< HEAD
            receivedOptions.tls,
            receivedOptions.port
=======
>>>>>>> 8bb5f01f (removed duplicated logic to utils and changed js to ts)
        );
    });
