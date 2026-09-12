/// <reference types="jest" />
import { afterEach, beforeAll } from "@jest/globals";
import minimist from "minimist";
import { Logger } from "../build-ts";

// Retry failing tests up to 2 times in CI to absorb transient failures.
// Retried tests are still reported individually so flakes remain visible;
// retries only prevent re-triggering full CI runs for isolated flaky tests.
// Consider moving this to a dedicated flaky-test quarantine mechanism in a follow-up PR.
if (process.env.CI) {
    jest.retryTimes(2, { logErrorsBeforeRetry: true });
}

beforeAll(() => {
    Logger.init("error", "log.log");

    // When not using real AWS credentials (e.g. local dev without an IAM role),
    // set fake credentials so IAM-related test code doesn't fail on missing env vars.
    process.env.AWS_ACCESS_KEY_ID =
        process.env.AWS_ACCESS_KEY_ID ?? "test_access_key";
    process.env.AWS_SECRET_ACCESS_KEY =
        process.env.AWS_SECRET_ACCESS_KEY ?? "test_secret_key";
    process.env.AWS_SESSION_TOKEN =
        process.env.AWS_SESSION_TOKEN ?? "test_session_token";
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
global.CLI_ARGS = args;

global.CLUSTER_ENDPOINTS = args["cluster-endpoints"] as string;
global.STAND_ALONE_ENDPOINT = args["standalone-endpoints"] as string;
global.TLS = !!args.tls;
global.TLS_CLUSTER_ENDPOINTS = args["tls-cluster-endpoints"] as string;
global.TLS_STAND_ALONE_ENDPOINT = args["tls-standalone-endpoints"] as string;
