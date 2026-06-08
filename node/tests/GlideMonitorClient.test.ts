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
        const received: MonitorLine[] = [];
        const config = getClientConfigurationOption(
            cluster.getAddresses(),
            ProtocolVersion.RESP2,
        );
        const monitor = await GlideMonitorClient.create(config, (line) =>
            received.push(line),
        );

        try {
            const client = await GlideClient.createClient(config);

            try {
                await client.set(`monitor_key_${Date.now()}`, "monitor_val");
                await new Promise((r) => setTimeout(r, 500));
            } finally {
                client.close();
            }
        } finally {
            await monitor.close();
        }

        expect(received.map((m) => m.command.toLowerCase())).toContain("set");
    });

    it("monitor line has correct field types", async () => {
        const config = getClientConfigurationOption(
            cluster.getAddresses(),
            ProtocolVersion.RESP2,
        );
        let capturedLine: MonitorLine | null = null;
        const monitor = await GlideMonitorClient.create(config, (line) => {
            if (capturedLine === null) capturedLine = line;
        });

        try {
            const client = await GlideClient.createClient(config);

            try {
                await client.ping();
                await new Promise((r) => setTimeout(r, 500));
            } finally {
                client.close();
            }
        } finally {
            await monitor.close();
        }

        expect(capturedLine).not.toBeNull();
        const line = capturedLine!;
        expect(typeof line.timestamp).toBe("number");
        expect(line.timestamp).toBeGreaterThan(0);
        expect(typeof line.db).toBe("number");
        expect(line.db).toBeGreaterThanOrEqual(0);
        expect(typeof line.clientAddr).toBe("string");
        expect(line.clientAddr.length).toBeGreaterThan(0);
        expect(typeof line.command).toBe("string");
        expect(line.command.length).toBeGreaterThan(0);
        expect(Array.isArray(line.args)).toBe(true);
    });

    it("monitor close is idempotent", async () => {
        const config = getClientConfigurationOption(
            cluster.getAddresses(),
            ProtocolVersion.RESP2,
        );
        const monitor = await GlideMonitorClient.create(config, () => {
            // no-op
        });
        await monitor.close();
        await expect(monitor.close()).resolves.not.toThrow();
    });
});
