/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { describe, expect, it } from "@jest/globals";
import {
    BaseClient,
    GlideClusterClientConfiguration,
    Logger,
    MAX_REQUEST_ARGS_LEN,
} from "../build-ts";
import {
    createLeakedStringVec,
    freeLeakedStringVec,
    valueFromSplitPointer,
} from "../build-ts/native";
import { command_request } from "../build-ts/ProtobufMessage";
import { createMigrate } from "../build-ts/Commands";
import { convertStringArrayToBuffer } from "./TestUtilities";
const { RequestType } = command_request;

describe("NAPI createLeakedStringVec", () => {
    it("should create and return pointer pair", () => {
        const args = [
            new TextEncoder().encode("arg1"),
            new TextEncoder().encode("arg2"),
        ];
        const [low, high] = createLeakedStringVec(args);
        // Pointer should be non-zero (at least one of the halves)
        expect(low !== 0 || high !== 0).toBe(true);
        freeLeakedStringVec(high, low);
    });

    it("should handle empty vector", () => {
        const [low, high] = createLeakedStringVec([]);
        expect(low !== 0 || high !== 0).toBe(true);
        freeLeakedStringVec(high, low);
    });

    it("should handle large arguments", () => {
        const largeArg = new Uint8Array(MAX_REQUEST_ARGS_LEN + 100).fill(65);
        const [low, high] = createLeakedStringVec([largeArg]);
        expect(low !== 0 || high !== 0).toBe(true);
        freeLeakedStringVec(high, low);
    });

    it("should handle binary data with null bytes", () => {
        const binaryData = new Uint8Array([0x00, 0x01, 0xff, 0x00, 0xfe]);
        const [low, high] = createLeakedStringVec([binaryData]);
        expect(low !== 0 || high !== 0).toBe(true);
        freeLeakedStringVec(high, low);
    });

    it("should handle multiple large arguments", () => {
        const args = [];

        for (let i = 0; i < 10; i++) {
            args.push(new Uint8Array(10000).fill(i));
        }

        const [low, high] = createLeakedStringVec(args);
        expect(low !== 0 || high !== 0).toBe(true);
        freeLeakedStringVec(high, low);
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

describe("BaseClient response handling", () => {
    class TestBaseClient extends BaseClient {
        public constructor() {
            super();
        }
    }

    it("continues draining responses after a handler exception", () => {
        const responses = [{ callbackIdx: 1 }, { callbackIdx: 2 }];
        const client = new TestBaseClient() as unknown as {
            clientHandle: { drainResponses: () => unknown[] };
            handleResponse: ReturnType<typeof jest.fn>;
            handleResponsesAvailable: () => void;
        };
        const logSpy = jest
            .spyOn(Logger, "log")
            .mockImplementation(() => undefined);

        client.clientHandle = {
            drainResponses: () => responses,
        };
        client.handleResponse = jest
            .fn()
            .mockImplementationOnce(() => {
                throw new Error("handler failed");
            })
            .mockImplementationOnce(() => undefined);

        expect(() => client.handleResponsesAvailable()).not.toThrow();
        expect(client.handleResponse).toHaveBeenCalledTimes(2);
        expect(client.handleResponse).toHaveBeenNthCalledWith(2, responses[1]);
        expect(logSpy).toHaveBeenCalledWith(
            "error",
            "Response handling",
            expect.stringContaining("handler failed"),
        );

        logSpy.mockRestore();
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

describe("createMigrate (multi-key) validation", () => {
    it("builds multi-key KEYS command", () => {
        const cmd = createMigrate("host", 6379, ["k1", "k2"], 0, 1000);
        expect(cmd.requestType).toEqual(RequestType.Migrate);
        expect(cmd.argsArray?.args).toEqual(
            convertStringArrayToBuffer([
                "host",
                "6379",
                "",
                "0",
                "1000",
                "KEYS",
                "k1",
                "k2",
            ]),
        );
    });

    it("throws when keys array is empty", () => {
        expect(() => createMigrate("host", 6379, [], 0, 1000)).toThrow(
            "key must not be an empty array",
        );
    });

    it("throws when username is set without password", () => {
        expect(() =>
            createMigrate("host", 6379, ["k"], 0, 1000, {
                username: "user",
            }),
        ).toThrow("MigrateOptions: 'username' requires 'password' to be set");
    });

    it("builds command with COPY, REPLACE and AUTH options", () => {
        const cmd = createMigrate("host", 6379, ["k"], 0, 1000, {
            copy: true,
            replace: true,
            password: "pass",
        });
        expect(cmd.argsArray?.args).toEqual(
            convertStringArrayToBuffer([
                "host",
                "6379",
                "",
                "0",
                "1000",
                "COPY",
                "REPLACE",
                "AUTH",
                "pass",
                "KEYS",
                "k",
            ]),
        );
    });

    it("builds command with AUTH2 (username + password)", () => {
        const cmd = createMigrate("host", 6379, ["k"], 0, 1000, {
            username: "user",
            password: "pass",
        });
        expect(cmd.argsArray?.args).toEqual(
            convertStringArrayToBuffer([
                "host",
                "6379",
                "",
                "0",
                "1000",
                "AUTH2",
                "user",
                "pass",
                "KEYS",
                "k",
            ]),
        );
    });
});
