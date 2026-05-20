/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { GlideClient, MonitorLine } from "../build-ts";

describe("Monitor", () => {
    let client: GlideClient;

    beforeAll(async () => {
        client = await GlideClient.createClient({
            addresses: [{ host: "localhost", port: 6379 }],
        });
    });

    afterAll(async () => {
        await client.close();
    });

    it("receives SET command", async () => {
        const lines: MonitorLine[] = [];
        const monitor = client.startMonitor((line) => lines.push(line));
        await client.set("monitor_node_key", "monitor_node_val");
        await new Promise((r) => setTimeout(r, 300));
        monitor.stop();
        const setLines = lines.filter(
            (l) => l.command.toUpperCase() === "SET",
        );
        expect(setLines.length).toBeGreaterThan(0);
        expect(setLines[0].args).toEqual([
            "monitor_node_key",
            "monitor_node_val",
        ]);
        expect(setLines[0].timestamp).toBeGreaterThan(0);
        expect(setLines[0].clientAddr).toBeTruthy();
    });

    it("no lines after stop", async () => {
        const lines: MonitorLine[] = [];
        const monitor = client.startMonitor((line) => lines.push(line));
        monitor.stop();
        const countAfterStop = lines.length;
        await client.set("monitor_node_key2", "val2");
        await new Promise((r) => setTimeout(r, 200));
        expect(lines.length).toBe(countAfterStop);
    });
});
