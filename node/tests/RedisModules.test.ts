/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */

import {
    afterAll,
    afterEach,
    beforeAll,
    describe,
    expect,
    it,
} from "@jest/globals";
import {
    BaseClientConfiguration,
    InfoOptions,
    RedisClusterClient,
    parseInfoResponse,
} from "../";
import { runBaseTests } from "./SharedTests";
import { RedisCluster, flushallOnPort, getFirstResult } from "./TestUtilities";

type Context = {
    client: RedisClusterClient;
};

const TIMEOUT = 10000;

describe("RedisModules", () => {
    let testsFailed = 0;
    let cluster: RedisCluster;
    beforeAll(async () => {
        const args = process.argv.slice(2);
        const loadModuleArgs = args.filter((arg) =>
            arg.startsWith("--load-module=")
        );
        const loadModuleValues = loadModuleArgs.map((arg) => arg.split("=")[1]);
        cluster = await RedisCluster.createCluster(3, 0, loadModuleValues);
    }, 20000);

    afterEach(async () => {
        await Promise.all(cluster.ports().map((port) => flushallOnPort(port)));
    });

    afterAll(async () => {
        if (testsFailed === 0) {
            await cluster.close();
        }
    });

    const getOptions = (ports: number[]): BaseClientConfiguration => {
        return {
            addresses: ports.map((port) => ({
                host: "localhost",
                port,
            })),
        };
    };

    runBaseTests<Context>({
        init: async (protocol, clientName) => {
            const options = getOptions(cluster.ports());
            options.serverProtocol = protocol;
            options.clientName = clientName;
            testsFailed += 1;
            const client = await RedisClusterClient.createClient(options);
            return {
                context: {
                    client,
                },
                client,
            };
        },
        close: (context: Context, testSucceeded: boolean) => {
            if (testSucceeded) {
                testsFailed -= 1;
            }

            context.client.close();
        },
        timeout: TIMEOUT,
    });

    it("simple search test", async () => {
        const client = await RedisClusterClient.createClient(
            getOptions(cluster.ports())
        );
        const info = parseInfoResponse(
            getFirstResult(await client.info([InfoOptions.Modules])).toString()
        )["module"];
        expect(info).toEqual(expect.stringContaining("search"));
        client.close();
    });

    it("simple json test", async () => {
        const client = await RedisClusterClient.createClient(
            getOptions(cluster.ports())
        );
        const info = parseInfoResponse(
            getFirstResult(await client.info([InfoOptions.Modules])).toString()
        )["module"];
        expect(info).toEqual(expect.stringContaining("ReJSON"));
        client.close();
    });
});
