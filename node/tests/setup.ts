/// <reference types="jest" />
import { afterEach, beforeAll } from "@jest/globals";
import * as fs from "fs";
import minimist from "minimist";
import { Logger } from "../build-ts";
import { ELASTICACHE_ENDPOINTS_FILE, EndpointsFile } from "./jest.globalSetup";

beforeAll(() => {
    Logger.init("error", "log.log");

    // Logger.setLoggerConfig("off");
    // Retry failing tests up to 2 times in CI to absorb transient failures
    if (process.env.CI) {
        jest.retryTimes(2, { logErrorsBeforeRetry: true });
    }

    // When not using real AWS credentials (e.g. local dev without an IAM role),
    // set fake credentials so IAM-related test code doesn't fail on missing env vars.
    // When USE_ELASTICACHE=true the real IAM role credentials must not be overridden.
    if (process.env.USE_ELASTICACHE !== "true") {
        process.env.AWS_ACCESS_KEY_ID =
            process.env.AWS_ACCESS_KEY_ID ?? "test_access_key";
        process.env.AWS_SECRET_ACCESS_KEY =
            process.env.AWS_SECRET_ACCESS_KEY ?? "test_secret_key";
        process.env.AWS_SESSION_TOKEN =
            process.env.AWS_SESSION_TOKEN ?? "test_session_token";
    }
});

// Clear all timers after each test to prevent hanging handles,
// Hanging handles are often caused by setTimeout, setInterval, or similar functions that are not cleared properly. Meaning we create a timer which something is waiting for it to finish, whether the test or some code piece, and not clearing it led to the test hanging. Causing memory leaks and other issues.
afterEach(() => {
    jest.clearAllTimers();
});

declare global {
    var CLI_ARGS: Record<string, string | boolean | number>;
    var CLUSTER_ENDPOINTS: string;
    var STAND_ALONE_ENDPOINT: string;
    var TLS: boolean;
    var TLS_CLUSTER_ENDPOINTS: string;
    var TLS_STAND_ALONE_ENDPOINT: string;
}

const args = minimist(process.argv.slice(2));
// Make the arguments available globally.
// When USE_ELASTICACHE=true and no CLI args provided, fall back to env vars
// set from the ElastiCache endpoints file.
global.CLI_ARGS = args;

// When USE_ELASTICACHE=true, synchronously read the endpoints file here at
// module eval time so globals are populated before any test code runs.
// (beforeAll runs after module evaluation, so reading there is too late.)
if (process.env.USE_ELASTICACHE === "true") {
    try {
        if (fs.existsSync(ELASTICACHE_ENDPOINTS_FILE)) {
            const data = JSON.parse(
                fs.readFileSync(ELASTICACHE_ENDPOINTS_FILE, "utf-8"),
            ) as EndpointsFile;

            if (!process.env.STANDALONE_ENDPOINT && data.standaloneEndpoint) {
                process.env.STANDALONE_ENDPOINT = data.standaloneEndpoint;
            }

            if (!process.env.CLUSTER_ENDPOINT && data.clusterEndpoint) {
                process.env.CLUSTER_ENDPOINT = data.clusterEndpoint;
            }
        }
    } catch (err) {
        console.warn(
            `[setup] Could not read ElastiCache endpoints file at module load: ${err}`,
        );
    }
}

global.CLUSTER_ENDPOINTS =
    (args["cluster-endpoints"] as string) ??
    (process.env.USE_ELASTICACHE === "true"
        ? process.env.CLUSTER_ENDPOINT
        : undefined);
global.STAND_ALONE_ENDPOINT =
    (args["standalone-endpoints"] as string) ??
    (process.env.USE_ELASTICACHE === "true"
        ? process.env.STANDALONE_ENDPOINT
        : undefined);
global.TLS = !!args.tls;
global.TLS_CLUSTER_ENDPOINTS = args["tls-cluster-endpoints"] as string;
global.TLS_STAND_ALONE_ENDPOINT = args["tls-standalone-endpoints"] as string;
