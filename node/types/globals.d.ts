/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

declare global {
    var STAND_ALONE_ENDPOINT: string;
    var CLUSTER_ENDPOINTS: string;
    var TLS: boolean;
    var CLI_ARGS: Record<string, string | boolean | number>;
}

// This export makes this file a module rather than a script
export {};
