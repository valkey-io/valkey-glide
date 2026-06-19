// Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0

import { afterAll, beforeAll, describe, expect, it } from "@jest/globals";
import { ValkeyCluster } from "../../utils/TestUtils.js";
import {
    GlideClient,
    GlideMonitorClient,
    MonitorLine,
    ProtocolVersion,
} from "../build-ts";
import {
    getClientConfigurationOption,
    getServerVersion,
    parseEndpoints,
} from "./TestUtilities";

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
