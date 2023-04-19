import { AsyncClient } from "..";
import RedisServer from "redis-server";
/* eslint-disable @typescript-eslint/no-var-requires */
const FreePort = require("find-free-port");
import { describe } from "@jest/globals";
import { runCommonTests } from "./TestUtilities";

const PORT_NUMBER = 4000;

describe("AsyncClient", () => {
    runCommonTests<RedisServer>({
        init: async () => {
            const port = await FreePort(PORT_NUMBER).then(
                ([free_port]: number[]) => free_port
            );
            return new Promise((resolve, reject) => {
                const server = new RedisServer(port);
                server.open(async (err: Error | null) => {
                    if (err) {
                        reject(err);
                    }

                    const client = await AsyncClient.CreateConnection(
                        "redis://localhost:" + port
                    );

                    resolve({ client, context: server });
                });
            });
        },
        close: async (context: RedisServer) => {
            context.close();
        },
    });
});
