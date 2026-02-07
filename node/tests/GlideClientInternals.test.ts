/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { describe, expect, it } from "@jest/globals";
import {
    GlideClusterClientConfiguration,
    MAX_REQUEST_ARGS_LEN,
} from "../build-ts";
import {
    createLeakedStringVec,
    valueFromSplitPointer,
} from "../build-ts/native";

describe("NAPI createLeakedStringVec", () => {
    it("should create and return pointer pair", () => {
        const args = [
            new TextEncoder().encode("arg1"),
            new TextEncoder().encode("arg2"),
        ];
        const [low, high] = createLeakedStringVec(args);
        // Pointer should be non-zero (at least one of the halves)
        expect(low !== 0 || high !== 0).toBe(true);
    });

    it("should handle empty vector", () => {
        const [low, high] = createLeakedStringVec([]);
        expect(low !== 0 || high !== 0).toBe(true);
    });

    it("should handle large arguments", () => {
        const largeArg = new Uint8Array(MAX_REQUEST_ARGS_LEN + 100).fill(65);
        const [low, high] = createLeakedStringVec([largeArg]);
        expect(low !== 0 || high !== 0).toBe(true);
    });

    it("should handle binary data with null bytes", () => {
        const binaryData = new Uint8Array([0x00, 0x01, 0xff, 0x00, 0xfe]);
        const [low, high] = createLeakedStringVec([binaryData]);
        expect(low !== 0 || high !== 0).toBe(true);
    });

    it("should handle multiple large arguments", () => {
        const args = [];

        for (let i = 0; i < 10; i++) {
            args.push(new Uint8Array(10000).fill(i));
        }

        const [low, high] = createLeakedStringVec(args);
        expect(low !== 0 || high !== 0).toBe(true);
    });
});

describe("NAPI valueFromSplitPointer", () => {
    it("valueFromSplitPointer function is exported", () => {
        expect(typeof valueFromSplitPointer).toBe("function");
    });
});

describe("GlideClusterClientConfiguration", () => {
    it("should set refreshTopologyFromInitialNodes to true", () => {
        const config: GlideClusterClientConfiguration = {
            addresses: [{ host: "localhost", port: 6379 }],
            advancedConfiguration: {
                refreshTopologyFromInitialNodes: true,
            },
        };

        expect(
            config.advancedConfiguration?.refreshTopologyFromInitialNodes,
        ).toBe(true);
    });

    it("should set refreshTopologyFromInitialNodes to false", () => {
        const config: GlideClusterClientConfiguration = {
            addresses: [{ host: "localhost", port: 6379 }],
            advancedConfiguration: {
                refreshTopologyFromInitialNodes: false,
            },
        };

        expect(
            config.advancedConfiguration?.refreshTopologyFromInitialNodes,
        ).toBe(false);
    });

    it("should default refreshTopologyFromInitialNodes to undefined when not specified", () => {
        const config: GlideClusterClientConfiguration = {
            addresses: [{ host: "localhost", port: 6379 }],
            advancedConfiguration: {},
        };

        expect(
            config.advancedConfiguration?.refreshTopologyFromInitialNodes,
        ).toBeUndefined();
    });
});

describe("Circular Dependency Fix", () => {
    /* eslint-disable @typescript-eslint/no-require-imports */
    it("should import GlideClient without circular dependency errors", () => {
        expect(() => {
            const { GlideClient } = require("../build-ts");
            expect(GlideClient).toBeDefined();
            expect(typeof GlideClient).toBe("function");
        }).not.toThrow();
    });

    it("should import GlideClusterClient without circular dependency errors", () => {
        expect(() => {
            const { GlideClusterClient } = require("../build-ts");
            expect(GlideClusterClient).toBeDefined();
            expect(typeof GlideClusterClient).toBe("function");
        }).not.toThrow();
    });

    it("should support Jest requireActual pattern without circular dependency errors", () => {
        expect(() => {
            const actualModule = require("../build-ts");

            const mockModule = {
                ...actualModule,
                GlideClusterClient: {
                    createClient: jest.fn(),
                },
            };

            expect(mockModule.GlideClusterClient).toBeDefined();
            expect(actualModule.GlideClient).toBeDefined();
            expect(actualModule.BaseClient).toBeDefined();
        }).not.toThrow();
    });

    it("should import TimeoutError without circular dependency errors", () => {
        expect(() => {
            const { TimeoutError } = require("../build-ts");
            expect(TimeoutError).toBeDefined();
            expect(typeof TimeoutError).toBe("function");
        }).not.toThrow();
    });

    it("should handle the Jest mock pattern without throwing TypeError", () => {
        expect(() => {
            const actualModule = jest.requireActual("@valkey/valkey-glide");
            const mockDefinition = {
                ...actualModule,
                GlideClusterClient: {
                    createClient: jest.fn(),
                },
            };

            expect(mockDefinition).toBeDefined();
            expect(mockDefinition.GlideClusterClient).toBeDefined();
            expect(
                mockDefinition.GlideClusterClient.createClient,
            ).toBeDefined();
            expect(typeof mockDefinition.GlideClusterClient.createClient).toBe(
                "function",
            );
            expect(mockDefinition.GlideClient).toBeDefined();
            expect(mockDefinition.BaseClient).toBeDefined();
            expect(mockDefinition.TimeoutError).toBeDefined();
            expect(typeof actualModule.GlideClusterClient).toBe("function");
            expect(typeof actualModule.BaseClient).toBe("function");
        }).not.toThrow();
    });

    it("should handle import destructuring without circular dependency errors", () => {
        expect(() => {
            const {
                GlideClusterClient,
                TimeoutError,
            } = require("@valkey/valkey-glide");

            expect(GlideClusterClient).toBeDefined();
            expect(TimeoutError).toBeDefined();
            expect(typeof GlideClusterClient).toBe("function");
            expect(typeof TimeoutError).toBe("function");
        }).not.toThrow();
    });
    /* eslint-enable @typescript-eslint/no-require-imports */
});
