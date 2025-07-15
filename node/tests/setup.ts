/// <reference types="jest" />
import { afterEach, beforeAll } from "@jest/globals";
import minimist from "minimist";
import { Logger } from "../build-ts";
import { CleanupSocketFiles } from "../build-ts/native";

beforeAll(() => {
    Logger.init("error", "log.log");
    // Logger.setLoggerConfig("off");

    // Clean up any leftover socket files from previous test runs
    // This helps prevent "Can't assign requested address" errors on MacOS
    CleanupSocketFiles();
});

// Clear all timers after each test to prevent hanging handles,
// Hanging handles are often caused by setTimeout, setInterval, or similar functions that are not cleared properly. Meaning we create a timer which something is waiting for it to finish, whether the test or some code piece, and not clearing it led to the test hanging. Causing memory leaks and other issues.
afterEach(() => {
    jest.clearAllTimers();
});

// Add process exit handlers to clean up socket files
// This helps prevent socket file accumulation when tests are interrupted
const cleanupOnExit = () => {
    try {
        CleanupSocketFiles();
    } catch {
        // Ignore errors during cleanup
    }
};

process.on("exit", cleanupOnExit);
process.on("SIGINT", cleanupOnExit);
process.on("SIGTERM", cleanupOnExit);
process.on("SIGQUIT", cleanupOnExit);

declare global {
    var CLI_ARGS: Record<string, string | boolean | number>;
    var CLUSTER_ENDPOINTS: string;
    var STAND_ALONE_ENDPOINT: string;
    var TLS: boolean;
}

const args = minimist(process.argv.slice(2));
// Make the arguments available globally
global.CLI_ARGS = args;
global.CLUSTER_ENDPOINTS = args["cluster-endpoints"] as string;
global.STAND_ALONE_ENDPOINT = args["standalone-endpoints"] as string;
global.TLS = !!args.tls;
