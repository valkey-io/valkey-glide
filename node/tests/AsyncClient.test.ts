import { afterAll, afterEach, beforeAll, describe } from "@jest/globals";
import RedisServer from "redis-server";
import { AsyncClient } from "..";
import { runCommonTests } from "./SharedTests";
import { flushallOnPort } from "./TestUtilities";
/* eslint-disable @typescript-eslint/no-var-requires */
const FreePort = require("find-free-port");

const PORT_NUMBER = 4000;

type EmptyObject = Record<string, never>;

describe("AsyncClient", () => {
    let server: RedisServer;
    let port: number;
    beforeAll(async () => {
        port = await FreePort(PORT_NUMBER).then(
            ([free_port]: number[]) => free_port
        );
        server = await new Promise((resolve, reject) => {
            const server = new RedisServer(port);
            server.open(async (err: Error | null) => {
                if (err) {
                    reject(err);
                }

                resolve(server);
            });
        });
    });

    afterEach(async () => {
        await flushallOnPort(port);
    });

    afterAll(() => {
        server.close();
    });

    runCommonTests<EmptyObject>({
        init: async () => {
            const client = await AsyncClient.CreateConnection(
                "redis://localhost:" + port
            );

            return { client, context: {} };
        },
        close: () => {
            // GC takes care of dropping the object
        },
    });
});
