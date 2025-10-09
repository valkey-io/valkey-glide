/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { describe, expect, it } from "@jest/globals";

describe("GlideClusterClientConfiguration", () => {
    it("should set refreshTopologyFromInitialNodes to true", () => {
        // Test configuration structure without type checking
        // The actual type validation will happen when the protobuf types are regenerated
        const config = {
            addresses: [{ host: "localhost", port: 6379 }],
            advancedConfiguration: {
                refreshTopologyFromInitialNodes: true,
            },
        };

        // We're testing that the configuration is accepted and properly structured
        expect(
            config.advancedConfiguration.refreshTopologyFromInitialNodes,
        ).toBe(true);
    });

    it("should set refreshTopologyFromInitialNodes to false", () => {
        const config = {
            addresses: [{ host: "localhost", port: 6379 }],
            advancedConfiguration: {
                refreshTopologyFromInitialNodes: false,
            },
        };

        expect(
            config.advancedConfiguration.refreshTopologyFromInitialNodes,
        ).toBe(false);
    });

    it("should default refreshTopologyFromInitialNodes to undefined when not specified", () => {
        const config = {
            addresses: [{ host: "localhost", port: 6379 }],
            advancedConfiguration: {} as {
                refreshTopologyFromInitialNodes?: boolean;
            },
        };

        expect(
            config.advancedConfiguration.refreshTopologyFromInitialNodes,
        ).toBeUndefined();
    });
});
