/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { afterAll, beforeAll, describe, expect, it } from "@jest/globals";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import {
    BaseClient,
    BaseClientConfiguration,
    ConfigurationError,
    GlideClusterClientConfiguration,
    Logger,
    MAX_REQUEST_ARGS_LEN,
    applyTlsAdvancedConfiguration,
    loadClientCertificateAndKeyFromFile,
    loadRootCertificatesFromFile,
    MutualTls,
} from "../build-ts";
import {
    createLeakedStringVec,
    freeLeakedStringVec,
    valueFromSplitPointer,
} from "../build-ts/native";
import {
    command_request,
    connection_request,
} from "../build-ts/ProtobufMessage";
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

// TODO #6669: assert on the created request, not the config
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

    it("should set recoveryRequestsQueueSize", () => {
        const config: GlideClusterClientConfiguration = {
            addresses: [{ host: "localhost", port: 6379 }],
            recoveryRequestsQueueSize: 500,
        };

        expect(config.recoveryRequestsQueueSize).toBe(500);
    });

    it("should default recoveryRequestsQueueSize to undefined when not specified", () => {
        const config: GlideClusterClientConfiguration = {
            addresses: [{ host: "localhost", port: 6379 }],
        };

        expect(config.recoveryRequestsQueueSize).toBeUndefined();
    });
});

describe("Client library identification requests", () => {
    class TestBaseClient extends BaseClient {
        public constructor() {
            super();
        }

        public buildRequest(
            options: BaseClientConfiguration,
        ): connection_request.IConnectionRequest {
            return this.createClientRequest(options);
        }
    }

    it.each([
        [undefined, undefined, "GlideJS"],
        ["custom-client", undefined, "custom-client"],
        [undefined, "framework:1.2", "GlideJS(framework:1.2)"],
        ["custom-client", "framework:1.2", "custom-client(framework:1.2)"],
        ["", "", "GlideJS"],
        [
            "custom/client+v2",
            "framework:@1.2!",
            "custom/client+v2(framework:@1.2!)",
        ],
    ])(
        "populates ordinary request for libName=%p and clientInfoTag=%p",
        (libName, clientInfoTag, expected) => {
            const config: BaseClientConfiguration = {
                addresses: [{ host: "localhost", port: 6379 }],
                libName,
                clientInfoTag,
            };

            expect(new TestBaseClient().buildRequest(config).libName).toBe(
                expected,
            );
        },
    );
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

const { TlsMode } = connection_request;

// Runs the TLS advanced-configuration block against a bare request object
// pre-seeded with `tlsMode`. Mirrors how `configureAdvancedConfigurationBase`
// invokes it on a real client, minus the connection setup.
//
// TODO #6669: derive tlsMode from useTLS via a full request build
const buildTlsRequest = (
    tls: Parameters<typeof applyTlsAdvancedConfiguration>[0],
    tlsMode: connection_request.TlsMode = TlsMode.SecureTls,
): connection_request.IConnectionRequest => {
    const request: connection_request.IConnectionRequest = { tlsMode };
    applyTlsAdvancedConfiguration(tls, request);
    return request;
};

const CLIENT_CERT_PEM =
    "-----BEGIN CERTIFICATE-----\nMIICLIENTCERT\n-----END CERTIFICATE-----";
const CLIENT_KEY_PEM =
    "-----BEGIN PRIVATE KEY-----\nMIICLIENTKEY\n-----END PRIVATE KEY-----";
const ROOT_CERT_PEM =
    "-----BEGIN CERTIFICATE-----\nMIICROOTCERT\n-----END CERTIFICATE-----";

const expectConfigurationError = (
    build: () => unknown,
    messageSubstring: string,
): void => {
    let err: unknown;

    try {
        build();
    } catch (e) {
        err = e;
    }

    expect(err).toBeInstanceOf(ConfigurationError);
    expect((err as Error).message).toContain(messageSubstring);
};

describe('mutualTls kind: "bytes"', () => {
    it("populates clientCert and clientKey from string PEM inputs", () => {
        const request = buildTlsRequest({
            mutualTls: {
                kind: "bytes",
                clientCertificate: CLIENT_CERT_PEM,
                clientKey: CLIENT_KEY_PEM,
            },
        });

        expect(request.clientCert).toEqual(
            new Uint8Array(Buffer.from(CLIENT_CERT_PEM, "utf-8")),
        );
        expect(request.clientKey).toEqual(
            new Uint8Array(Buffer.from(CLIENT_KEY_PEM, "utf-8")),
        );
        expect(request.certReload).toBeFalsy();
        expect(request.clientCertPath).toBeFalsy();
        expect(request.clientKeyPath).toBeFalsy();
    });

    it("populates clientCert and clientKey from Buffer PEM inputs", () => {
        const certBuffer = Buffer.from(CLIENT_CERT_PEM, "utf-8");
        const keyBuffer = Buffer.from(CLIENT_KEY_PEM, "utf-8");

        const request = buildTlsRequest({
            mutualTls: {
                kind: "bytes",
                clientCertificate: certBuffer,
                clientKey: keyBuffer,
            },
        });

        expect(request.clientCert).toEqual(new Uint8Array(certBuffer));
        expect(request.clientKey).toEqual(new Uint8Array(keyBuffer));
    });

    it("rejects mutualTls when TLS is disabled on the base connection", () => {
        expectConfigurationError(
            () =>
                buildTlsRequest(
                    {
                        mutualTls: {
                            kind: "bytes",
                            clientCertificate: CLIENT_CERT_PEM,
                            clientKey: CLIENT_KEY_PEM,
                        },
                    },
                    TlsMode.NoTls,
                ),
            "TLS advanced configuration cannot be set",
        );
    });

    // Regression guard. proto3 `bytes` treats an empty value as unset, so if
    // both cert and key were sent empty the core would see no mTLS material
    // and quietly fall back to server-auth-only TLS instead of erroring.
    it("rejects both clientCertificate and clientKey empty", () => {
        expectConfigurationError(
            () =>
                buildTlsRequest({
                    mutualTls: {
                        kind: "bytes",
                        clientCertificate: "",
                        clientKey: Buffer.alloc(0),
                    },
                }),
            "mutualTls.clientCertificate must not be empty",
        );
    });

    it("rejects an empty clientCertificate", () => {
        expectConfigurationError(
            () =>
                buildTlsRequest({
                    mutualTls: {
                        kind: "bytes",
                        clientCertificate: "",
                        clientKey: CLIENT_KEY_PEM,
                    },
                }),
            "mutualTls.clientCertificate must not be empty",
        );
    });

    it("rejects an empty clientKey", () => {
        expectConfigurationError(
            () =>
                buildTlsRequest({
                    mutualTls: {
                        kind: "bytes",
                        clientCertificate: CLIENT_CERT_PEM,
                        clientKey: Buffer.alloc(0),
                    },
                }),
            "mutualTls.clientKey must not be empty",
        );
    });
});

describe('mutualTls kind: "path" (implicit reload)', () => {
    let tmpDir: string;
    let clientCertPath: string;
    let clientKeyPath: string;

    beforeAll(() => {
        tmpDir = mkdtempSync(join(tmpdir(), "glide-mtls-path-"));
        clientCertPath = join(tmpDir, "client.crt");
        clientKeyPath = join(tmpDir, "client.key");
        writeFileSync(clientCertPath, CLIENT_CERT_PEM);
        writeFileSync(clientKeyPath, CLIENT_KEY_PEM);
    });

    afterAll(() => {
        rmSync(tmpDir, { recursive: true, force: true });
    });

    it("sets the path fields and enables reload with default cadence", () => {
        const request = buildTlsRequest({
            mutualTls: {
                kind: "path",
                clientCertPath,
                clientKeyPath,
            },
        });

        expect(request.clientCertPath).toBe(clientCertPath);
        expect(request.clientKeyPath).toBe(clientKeyPath);
        expect(request.certReload?.enabled).toBe(true);
        expect(request.certReload?.intervalSeconds).toBeUndefined();
        expect(request.clientCert).toBeFalsy();
        expect(request.clientKey).toBeFalsy();
    });

    it("propagates a custom reloadIntervalSeconds", () => {
        const request = buildTlsRequest({
            mutualTls: {
                kind: "path",
                clientCertPath,
                clientKeyPath,
                reloadIntervalSeconds: 120,
            },
        });

        expect(request.certReload).toEqual({
            enabled: true,
            intervalSeconds: 120,
        });
    });

    // 2**32 - 1 is the largest value the protobuf uint32 field holds.
    // Accepting it (and rejecting 2**32 below) proves the bound is inclusive.
    it("accepts reloadIntervalSeconds at the uint32 maximum", () => {
        const maxUint32 = 2 ** 32 - 1;
        const request = buildTlsRequest({
            mutualTls: {
                kind: "path",
                clientCertPath,
                clientKeyPath,
                reloadIntervalSeconds: maxUint32,
            },
        });

        expect(request.certReload).toEqual({
            enabled: true,
            intervalSeconds: maxUint32,
        });
    });

    const invalidIntervals: [string, number][] = [
        ["zero", 0],
        ["negative", -10],
        ["non-integer", 12.5],
        ["NaN", Number.NaN],
        ["Infinity", Number.POSITIVE_INFINITY],
        ["exceeds uint32", 2 ** 32],
    ];

    it.each(invalidIntervals)(
        "rejects reloadIntervalSeconds when %s",
        (_label, value) => {
            expectConfigurationError(
                () =>
                    buildTlsRequest({
                        mutualTls: {
                            kind: "path",
                            clientCertPath,
                            clientKeyPath,
                            reloadIntervalSeconds: value,
                        },
                    }),
                "mutualTls.reloadIntervalSeconds must be a positive integer",
            );
        },
    );

    it("names the uint32 maximum when reloadIntervalSeconds is too large", () => {
        expectConfigurationError(
            () =>
                buildTlsRequest({
                    mutualTls: {
                        kind: "path",
                        clientCertPath,
                        clientKeyPath,
                        reloadIntervalSeconds: 2 ** 32,
                    },
                }),
            "no greater than 4294967295",
        );
    });
});

describe("mutualTls unsupported variant fallthrough", () => {
    it("rejects an unknown kind and reports the discriminant only", () => {
        const bogus = {
            kind: "future" as const,
            clientCertificate: "SENSITIVE-PEM-BYTES",
            clientKey: "SENSITIVE-KEY-BYTES",
        } as unknown as MutualTls;

        let thrown: unknown;

        try {
            buildTlsRequest({ mutualTls: bogus });
        } catch (e) {
            thrown = e;
        }

        expect(thrown).toBeInstanceOf(ConfigurationError);
        const message = (thrown as Error).message;
        expect(message).toBe("Unsupported mutualTls variant: kind=future");
        // The error surface never mentions the caller's PEM material.
        expect(message).not.toContain("SENSITIVE-PEM-BYTES");
        expect(message).not.toContain("SENSITIVE-KEY-BYTES");
    });
});

describe("mutualTls interaction with existing TLS knobs", () => {
    it("leaves rootCertificates handling unchanged when mutualTls is absent", () => {
        const request = buildTlsRequest({
            rootCertificates: ROOT_CERT_PEM,
        });

        expect(request.rootCerts).toEqual([
            new Uint8Array(Buffer.from(ROOT_CERT_PEM, "utf-8")),
        ]);
        expect(request.clientCert).toBeFalsy();
        expect(request.clientKey).toBeFalsy();
        expect(request.certReload).toBeFalsy();
    });

    it("flips to InsecureTls when insecure is true, without touching mTLS fields", () => {
        const request = buildTlsRequest({
            insecure: true,
            mutualTls: {
                kind: "bytes",
                clientCertificate: CLIENT_CERT_PEM,
                clientKey: CLIENT_KEY_PEM,
            },
        });

        expect(request.tlsMode).toBe(TlsMode.InsecureTls);
        expect(request.clientCert).toBeTruthy();
    });
});

describe("TLS PEM file loaders", () => {
    let tmpDir: string;

    beforeAll(() => {
        tmpDir = mkdtempSync(join(tmpdir(), "glide-tls-loader-"));
    });

    afterAll(() => {
        rmSync(tmpDir, { recursive: true, force: true });
    });

    const writeFixture = (name: string, contents: string): string => {
        const filePath = join(tmpDir, name);
        writeFileSync(filePath, contents);
        return filePath;
    };

    describe("loadRootCertificatesFromFile", () => {
        it("loads PEM bytes from a file", async () => {
            const filePath = writeFixture("root-ok.pem", ROOT_CERT_PEM);
            const data = await loadRootCertificatesFromFile(filePath);
            expect(Buffer.isBuffer(data)).toBe(true);
            expect(data).toEqual(Buffer.from(ROOT_CERT_PEM));
        });

        it("rejects with a ConfigurationError when the file is missing", async () => {
            const missingPath = join(tmpDir, "root-missing.pem");
            await expect(
                loadRootCertificatesFromFile(missingPath),
            ).rejects.toBeInstanceOf(ConfigurationError);
            await expect(
                loadRootCertificatesFromFile(missingPath),
            ).rejects.toThrow(
                `Root certificate file not found: ${missingPath}`,
            );
            await expect(
                loadRootCertificatesFromFile(missingPath),
            ).rejects.toMatchObject({
                cause: expect.objectContaining({ code: "ENOENT" }),
            });
        });

        it("rejects with a ConfigurationError when the file is empty", async () => {
            const emptyPath = writeFixture("root-empty.pem", "");
            await expect(
                loadRootCertificatesFromFile(emptyPath),
            ).rejects.toBeInstanceOf(ConfigurationError);
            await expect(
                loadRootCertificatesFromFile(emptyPath),
            ).rejects.toThrow(`Root certificate file is empty: ${emptyPath}`);
        });
    });

    describe("loadClientCertificateAndKeyFromFile", () => {
        it("returns both cert and key buffers", async () => {
            const certPath = writeFixture("client-cert.pem", CLIENT_CERT_PEM);
            const keyPath = writeFixture("client-key.pem", CLIENT_KEY_PEM);
            const { cert, key } = await loadClientCertificateAndKeyFromFile(
                certPath,
                keyPath,
            );
            expect(cert).toEqual(Buffer.from(CLIENT_CERT_PEM));
            expect(key).toEqual(Buffer.from(CLIENT_KEY_PEM));
        });

        it("labels a missing cert as 'Client certificate'", async () => {
            const keyPath = writeFixture("k1.pem", CLIENT_KEY_PEM);
            const missingCert = join(tmpDir, "missing-cert.pem");
            await expect(
                loadClientCertificateAndKeyFromFile(missingCert, keyPath),
            ).rejects.toThrow(
                `Client certificate file not found: ${missingCert}`,
            );
        });

        it("labels a missing key as 'Client key'", async () => {
            const certPath = writeFixture("c2.pem", CLIENT_CERT_PEM);
            const missingKey = join(tmpDir, "missing-key.pem");
            await expect(
                loadClientCertificateAndKeyFromFile(certPath, missingKey),
            ).rejects.toThrow(`Client key file not found: ${missingKey}`);
        });

        it("rejects an empty cert file with the cert-specific label", async () => {
            const emptyCert = writeFixture("empty-cert.pem", "");
            const keyPath = writeFixture("k3.pem", CLIENT_KEY_PEM);
            await expect(
                loadClientCertificateAndKeyFromFile(emptyCert, keyPath),
            ).rejects.toThrow(`Client certificate file is empty: ${emptyCert}`);
        });

        it("rejects an empty key file with the key-specific label", async () => {
            const certPath = writeFixture("c4.pem", CLIENT_CERT_PEM);
            const emptyKey = writeFixture("empty-key.pem", "");
            await expect(
                loadClientCertificateAndKeyFromFile(certPath, emptyKey),
            ).rejects.toThrow(`Client key file is empty: ${emptyKey}`);
        });
    });

    it("MutualTls compile-time exclusivity guard exists", () => {
        // If someone flattens MutualTls into a shape without a `kind`
        // discriminator, `Extract<..., { kind: "path" }>` collapses to
        // `never` and this line stops type-checking.
        const _pathVariant: Extract<MutualTls, { kind: "path" }> = {
            kind: "path",
            clientCertPath: "/tmp/c",
            clientKeyPath: "/tmp/k",
        };
        expect(_pathVariant.kind).toBe("path");
    });
});
