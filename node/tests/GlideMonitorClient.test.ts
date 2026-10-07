// Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0

import { afterAll, beforeAll, describe, expect, it, jest } from "@jest/globals";
import { ValkeyCluster } from "../../utils/TestUtils.js";
import {
    BaseClientConfiguration,
    GlideClient,
    GlideMonitorClient,
    MonitorLine,
    ProtocolVersion,
    ServiceType,
} from "../build-ts";
import * as native from "../build-ts/native";
import {
    getClientConfigurationOption,
    getServerVersion,
    parseEndpoints,
} from "./TestUtilities";

function getClientInfoField(
    clientInfo: string,
    fieldName: string,
): string | undefined {
    const prefix = `${fieldName}=`;
    return clientInfo
        .trim()
        .split(/\s+/u)
        .find((field) => field.startsWith(prefix))
        ?.slice(prefix.length);
}

function isMonitorClient(clientInfo: string): boolean {
    const command = getClientInfoField(clientInfo, "cmd");
    const flags = getClientInfoField(clientInfo, "flags");
    return (
        command?.toLowerCase() === "monitor" || Boolean(flags?.includes("O"))
    );
}

function getMonitorClientIds(clientList: string): Set<string> {
    return new Set(
        clientList
            .split(/\r?\n/u)
            .filter(isMonitorClient)
            .map((clientInfo) => getClientInfoField(clientInfo, "id"))
            .filter((id): id is string => id !== undefined),
    );
}

function findNewMonitorClient(
    clientList: string,
    baselineMonitorIds: Set<string>,
): string | undefined {
    return clientList.split(/\r?\n/u).find((clientInfo) => {
        if (!isMonitorClient(clientInfo)) return false;
        const id = getClientInfoField(clientInfo, "id");
        return id !== undefined && !baselineMonitorIds.has(id);
    });
}

describe("GlideMonitorClient", () => {
    let cluster: ValkeyCluster;

    beforeAll(async () => {
        const standaloneAddresses: string =
            global.STAND_ALONE_ENDPOINT as string;
        cluster = standaloneAddresses
            ? await ValkeyCluster.initFromExistingCluster(
                  false,
                  parseEndpoints(standaloneAddresses),
                  getServerVersion,
              )
            : await ValkeyCluster.createCluster(false, 1, 1, getServerVersion);
    }, 40000);

    it("rejects IAM custom providers before calling native createMonitorClient", async () => {
        const monitorClass = GlideMonitorClient as unknown as {
            nativeCreateMonitorClient: typeof native.createMonitorClient;
        };
        const originalCreateMonitorClient =
            monitorClass.nativeCreateMonitorClient;
        const createMonitorMock = jest.fn<typeof native.createMonitorClient>();
        monitorClass.nativeCreateMonitorClient = createMonitorMock;
        const expectedError =
            "Monitor clients do not support IAM authentication or custom credential providers.";
        const config: BaseClientConfiguration = {
            addresses: [{ host: "invalid.example", port: 1 }],
            credentials: {
                username: "iam-user",
                iamConfig: {
                    clusterName: "my-cluster",
                    service: ServiceType.Elasticache,
                    region: "us-east-1",
                    credentialProvider: () => ({
                        accessKeyId: "AKID",
                        secretAccessKey: "SECRET",
                    }),
                },
            },
        };

        try {
            let thrown: unknown;

            try {
                await GlideMonitorClient.create(config);
            } catch (error) {
                thrown = error;
            }

            expect(thrown).toBeInstanceOf(Error);
            expect((thrown as Error).message).toBe(expectedError);
            expect(createMonitorMock).not.toHaveBeenCalled();
        } finally {
            monitorClass.nativeCreateMonitorClient =
                originalCreateMonitorClient;
        }
    });

    afterAll(async () => {
        await cluster.close();
    }, 20000);

    it("monitor receives commands", async () => {
        const config = getClientConfigurationOption(
            cluster.getAddresses(),
            ProtocolVersion.RESP2,
        );
        const monitor = await GlideMonitorClient.create(config);

        try {
            const client = await GlideClient.createClient(config);

            try {
                const key = `monitor_key_${Date.now()}`;
                await client.set(key, "monitor_val");
                let line: MonitorLine | undefined;
                const deadline = Date.now() + 5000;

                while (Date.now() < deadline) {
                    const next = await Promise.race([
                        monitor.getNextMessage(),
                        new Promise<undefined>((r) =>
                            setTimeout(r, 100, undefined),
                        ),
                    ]);

                    if (next && next.command.toLowerCase() === "set") {
                        line = next;
                        break;
                    }
                }

                expect(line).toBeDefined();
                expect(line!.command.toLowerCase()).toBe("set");
            } finally {
                client.close();
            }
        } finally {
            await monitor.close();
        }
    });

    it("monitor line has correct field types", async () => {
        const config = getClientConfigurationOption(
            cluster.getAddresses(),
            ProtocolVersion.RESP2,
        );
        const monitor = await GlideMonitorClient.create(config);

        try {
            const client = await GlideClient.createClient(config);

            try {
                await client.ping();
                let line: MonitorLine | undefined;
                const deadline = Date.now() + 5000;

                while (Date.now() < deadline) {
                    const next = await Promise.race([
                        monitor.getNextMessage(),
                        new Promise<undefined>((r) =>
                            setTimeout(r, 100, undefined),
                        ),
                    ]);

                    if (next && next.command.toLowerCase() === "ping") {
                        line = next;
                        break;
                    }
                }

                expect(line).toBeDefined();
                const l = line!;
                expect(typeof l.timestamp).toBe("number");
                expect(l.timestamp).toBeGreaterThan(0);
                expect(typeof l.db).toBe("number");
                expect(l.db).toBeGreaterThanOrEqual(0);
                expect(typeof l.clientAddr).toBe("string");
                expect(l.clientAddr.length).toBeGreaterThan(0);
                expect(typeof l.command).toBe("string");
                expect(l.command.length).toBeGreaterThan(0);
                expect(Array.isArray(l.args)).toBe(true);
            } finally {
                client.close();
            }
        } finally {
            await monitor.close();
        }
    });

    it.each([
        ["default", {}, "GlideJS"],
        ["override", { libName: "custom-client" }, "custom-client"],
        ["tag", { clientInfoTag: "framework:1.2" }, "GlideJS(framework:1.2)"],
        [
            "combined",
            { libName: "custom-client", clientInfoTag: "framework:1.2" },
            "custom-client(framework:1.2)",
        ],
    ] as [string, Partial<BaseClientConfiguration>, string][])(
        "monitor reports %s library identification",
        async (_caseName, overrides, expectedLibName) => {
            if (cluster.checkIfServerVersionLessThan("7.2.0")) return;

            const observerConfig = getClientConfigurationOption(
                cluster.getAddresses(),
                ProtocolVersion.RESP2,
            );
            const monitorConfig = getClientConfigurationOption(
                cluster.getAddresses(),
                ProtocolVersion.RESP2,
                overrides,
            );
            const observer = await GlideClient.createClient(observerConfig);

            try {
                const baselineClientList = String(
                    await observer.customCommand(["CLIENT", "LIST"]),
                );
                const baselineMonitorIds =
                    getMonitorClientIds(baselineClientList);
                const monitor = await GlideMonitorClient.create(
                    monitorConfig,
                    () => undefined,
                );

                try {
                    let monitorInfo: string | undefined;
                    let latestClientList = "";
                    const deadline = Date.now() + 5000;

                    while (!monitorInfo && Date.now() < deadline) {
                        latestClientList = String(
                            await observer.customCommand(["CLIENT", "LIST"]),
                        );
                        monitorInfo = findNewMonitorClient(
                            latestClientList,
                            baselineMonitorIds,
                        );

                        if (!monitorInfo) {
                            await new Promise((resolve) =>
                                setTimeout(resolve, 50),
                            );
                        }
                    }

                    if (!monitorInfo) {
                        throw new Error(
                            `Expected a new dedicated monitor connection, but CLIENT LIST returned: ${latestClientList}`,
                        );
                    }

                    expect(getClientInfoField(monitorInfo, "lib-name")).toBe(
                        expectedLibName,
                    );
                } finally {
                    await monitor.close();
                }
            } finally {
                observer.close();
            }
        },
        10000,
    );

    it("monitor close is idempotent", async () => {
        const config = getClientConfigurationOption(
            cluster.getAddresses(),
            ProtocolVersion.RESP2,
        );
        const monitor = await GlideMonitorClient.create(config);
        await monitor.close();
        await expect(monitor.close()).resolves.not.toThrow();
    });

    it("getNextMessage works without callback", async () => {
        const config = getClientConfigurationOption(
            cluster.getAddresses(),
            ProtocolVersion.RESP2,
        );
        const monitor = await GlideMonitorClient.create(config);

        try {
            const client = await GlideClient.createClient(config);

            try {
                await client.set("poll_test", "val");
                const line = await monitor.getNextMessage();
                expect(line).toBeDefined();
                expect(typeof line.command).toBe("string");
            } finally {
                client.close();
            }
        } finally {
            await monitor.close();
        }
    });
});
