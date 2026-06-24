/**
 * Timing instrumentation for OpenTelemetry test operations.
 * Measures every significant operation to identify Windows/WSL vs macOS gaps.
 */
import { afterAll, beforeAll, describe, it } from "@jest/globals";
import * as fs from "fs";
import ValkeyCluster from "../../utils/TestUtils";
import {
    ClusterBatch,
    GlideClusterClient,
    GlideOpenTelemetryConfig,
    OpenTelemetry,
    ProtocolVersion,
} from "../build-ts";
import {
    getClientConfigurationOption,
    getServerVersion,
    parseEndpoints,
} from "./TestUtilities";

const TRACES_PATH = "/tmp/spans-timing.json";
const METRICS_URL = "https://valid-endpoint/v1/metrics";

function log(label: string, startMs: number): number {
    console.log(`[timing] ${label}: ${Date.now() - startMs}ms`);
    return Date.now();
}

describe("OpenTelemetry Timing", () => {
    let cluster: ValkeyCluster;

    beforeAll(async () => {
        let ts = Date.now();
        const clusterAddresses = global.CLUSTER_ENDPOINTS;
        cluster = clusterAddresses
            ? await ValkeyCluster.initFromExistingCluster(
                  true,
                  parseEndpoints(clusterAddresses),
                  getServerVersion,
              )
            : await ValkeyCluster.createCluster(true, 3, 1, getServerVersion);
        ts = log("createCluster", ts);

        OpenTelemetry.init({
            traces: { endpoint: "file://" + TRACES_PATH, samplePercentage: 100 },
            metrics: { endpoint: METRICS_URL },
            flushIntervalMs: 100,
        } as GlideOpenTelemetryConfig);
        log("OpenTelemetry.init", ts);
    }, 60000);

    afterAll(async () => {
        if (fs.existsSync(TRACES_PATH)) fs.unlinkSync(TRACES_PATH);
        await cluster?.close();
    });

    it("measure all operations", async () => {
        if (fs.existsSync(TRACES_PATH)) fs.unlinkSync(TRACES_PATH);

        let ts = Date.now();
        const client = await GlideClusterClient.createClient(
            getClientConfigurationOption(cluster.getAddresses(), ProtocolVersion.RESP3),
        );
        ts = log("createClient", ts);

        await client.set("timing_key", "value");
        ts = log("SET", ts);

        await client.get("timing_key");
        ts = log("GET", ts);

        // 100 sequential SETs
        for (let i = 0; i < 100; i++) await client.set(`k${i}`, `v${i}`);
        ts = log("100x SET sequential", ts);

        // batch 100 SETs (use hash tags to ensure same slot)
        const batch = new ClusterBatch(true);
        for (let i = 0; i < 100; i++) batch.set(`{batch}k${i}`, `bv${i}`);
        await client.exec(batch, true);
        ts = log("batch 100x SET", ts);

        client.close();
    }, 60000);
});
