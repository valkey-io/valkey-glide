/* eslint-disable no-var */
import { beforeAll } from "@jest/globals";
import minimist from "minimist";
import { Logger } from "../build-ts";

beforeAll(() => {
    Logger.init("error", "log.log");
    // Logger.setLoggerConfig("off");
});

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
