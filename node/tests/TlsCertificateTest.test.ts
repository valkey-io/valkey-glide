/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import {
    afterAll,
    afterEach,
    beforeAll,
    describe,
    expect,
    it,
} from "@jest/globals";
import * as fs from "fs";
import { TestTLSConfig, ValkeyCluster } from "../../utils/TestUtils.js";
import {
    GlideClient,
    GlideClusterClient,
    Logger,
    loadClientCertificateFromFile,
    loadClientPrivateKeyFromFile,
    loadRootCertificatesFromFile,
    ProtocolVersion,
} from "../build-ts";
import {
    getClientConfigurationOption,
    getServerVersion,
} from "./TestUtilities";

const TIMEOUT = 50000;
const CLUSTER_CREATION_TIMEOUT = 120000; // Increased timeout for TLS cluster creation
const TLS_REQUEST_TIMEOUT = 10000;

const getTlsClientConfigurationOption = (addresses: [string, number][]) =>
    getClientConfigurationOption(addresses, ProtocolVersion.RESP3, {
        requestTimeout: TLS_REQUEST_TIMEOUT,
    });

describe("TLS with custom certificates", () => {
    let standaloneCluster: ValkeyCluster;
    let clusterModeCluster: ValkeyCluster;
    let standaloneClient: GlideClient | undefined;
    let clusterClient: GlideClusterClient | undefined;
    let caCertData: Buffer;
    let caCertPath: string;
    let serverCertPath: string;
    let serverKeyPath: string;

    beforeAll(async () => {
        // Use insecure TLS only for getServerVersion during startup
        // This allows getting the version before certificates are read
        // The actual test connections will use proper certificate validation
        const startupTlsConfig: TestTLSConfig = {
            useTLS: true,
            requestTimeout: TLS_REQUEST_TIMEOUT,
            advancedConfiguration: {
                tlsAdvancedConfiguration: {
                    insecure: true,
                },
            },
        };

        // Start standalone TLS server (generates certificates)
        standaloneCluster = await ValkeyCluster.createCluster(
            false,
            1,
            1,
            getServerVersion,
            true,
            startupTlsConfig,
        );

        // Start cluster mode TLS server
        clusterModeCluster = await ValkeyCluster.createCluster(
            true,
            3,
            2,
            getServerVersion,
            true,
            startupTlsConfig,
        );

        // Read CA certificate after servers start (certificates now exist)
        const glideHomeDir =
            process.env.GLIDE_HOME_DIR || process.cwd() + "/..";
        caCertPath = `${glideHomeDir}/utils/tls_crts/ca.crt`;
        serverCertPath = `${glideHomeDir}/utils/tls_crts/server.crt`;
        serverKeyPath = `${glideHomeDir}/utils/tls_crts/server.key`;
        caCertData = fs.readFileSync(caCertPath);

        // Small delay to ensure cluster is fully ready after TLS setup
        await new Promise((resolve) => setTimeout(resolve, 1000));
    }, CLUSTER_CREATION_TIMEOUT);

    afterEach(async () => {
        if (standaloneClient) {
            await standaloneClient.close();
            standaloneClient = undefined;
        }

        if (clusterClient) {
            await clusterClient.close();
            clusterClient = undefined;
        }
    });

    afterAll(async () => {
        try {
            if (standaloneCluster) {
                await standaloneCluster.close();
            }

            if (clusterModeCluster) {
                await clusterModeCluster.close();
            }
        } catch (error) {
            Logger.log(
                "warn",
                "TlsCertificateTest",
                "Error closing clusters",
                error as Error,
            );
        }
    });

    describe("Standalone TLS with certificates", () => {
        it(
            "should fail to connect with TLS when no certificate provided",
            async () => {
                // Self-signed certificate will be rejected by platform verifier
                await expect(
                    GlideClient.createClient({
                        ...getTlsClientConfigurationOption(
                            standaloneCluster.getAddresses(),
                        ),
                        useTLS: true,
                    }),
                ).rejects.toThrow();
            },
            TIMEOUT,
        );

        it(
            "should connect when insecure TLS is enabled without certificate",
            async () => {
                standaloneClient = await GlideClient.createClient({
                    ...getTlsClientConfigurationOption(
                        standaloneCluster.getAddresses(),
                    ),
                    useTLS: true,
                    advancedConfiguration: {
                        tlsAdvancedConfiguration: {
                            insecure: true,
                        },
                    },
                });

                const result = await standaloneClient.ping();
                expect(result).toBe("PONG");
            },
            TIMEOUT,
        );

        it(
            "should connect with TLS using custom certificate as Buffer",
            async () => {
                standaloneClient = await GlideClient.createClient({
                    ...getTlsClientConfigurationOption(
                        standaloneCluster.getAddresses(),
                    ),
                    useTLS: true,
                    advancedConfiguration: {
                        tlsAdvancedConfiguration: {
                            rootCertificates: caCertData,
                        },
                    },
                });

                const result = await standaloneClient.set(
                    "tls-test-key",
                    "test-value",
                );
                expect(result).toBe("OK");
            },
            TIMEOUT,
        );

        it(
            "should connect with custom certificate as string",
            async () => {
                const certString = caCertData.toString("utf-8");

                standaloneClient = await GlideClient.createClient({
                    ...getTlsClientConfigurationOption(
                        standaloneCluster.getAddresses(),
                    ),
                    useTLS: true,
                    advancedConfiguration: {
                        tlsAdvancedConfiguration: {
                            rootCertificates: certString,
                        },
                    },
                });

                const result = await standaloneClient.ping();
                expect(result).toBe("PONG");
            },
            TIMEOUT,
        );

        it(
            "should connect with certificate loaded from file helper",
            async () => {
                const certData = loadRootCertificatesFromFile(caCertPath);

                standaloneClient = await GlideClient.createClient({
                    ...getTlsClientConfigurationOption(
                        standaloneCluster.getAddresses(),
                    ),
                    useTLS: true,
                    advancedConfiguration: {
                        tlsAdvancedConfiguration: {
                            rootCertificates: certData,
                        },
                    },
                });

                const result = await standaloneClient.ping();
                expect(result).toBe("PONG");
            },
            TIMEOUT,
        );

        it(
            "should connect with multiple certificates in bundle",
            async () => {
                // Simulate a cacerts.pem with multiple certificates
                const certString = caCertData.toString("utf-8");
                const multipleCerts = certString + "\n" + certString;
                const certBundle = Buffer.from(multipleCerts, "utf-8");

                standaloneClient = await GlideClient.createClient({
                    ...getTlsClientConfigurationOption(
                        standaloneCluster.getAddresses(),
                    ),
                    useTLS: true,
                    advancedConfiguration: {
                        tlsAdvancedConfiguration: {
                            rootCertificates: certBundle,
                        },
                    },
                });

                const result = await standaloneClient.ping();
                expect(result).toBe("PONG");
            },
            TIMEOUT,
        );

        it(
            "should fail with empty certificate",
            async () => {
                const emptyCert = Buffer.alloc(0);

                await expect(
                    GlideClient.createClient({
                        ...getTlsClientConfigurationOption(
                            standaloneCluster.getAddresses(),
                        ),
                        useTLS: true,
                        advancedConfiguration: {
                            tlsAdvancedConfiguration: {
                                rootCertificates: emptyCert,
                            },
                        },
                    }),
                ).rejects.toThrow();
            },
            TIMEOUT,
        );

        it(
            "should fail with invalid certificate",
            async () => {
                const invalidCert = Buffer.from(
                    "-----BEGIN CERTIFICATE-----\nINVALID\n-----END CERTIFICATE-----",
                    "utf-8",
                );

                await expect(
                    GlideClient.createClient({
                        ...getTlsClientConfigurationOption(
                            standaloneCluster.getAddresses(),
                        ),
                        useTLS: true,
                        advancedConfiguration: {
                            tlsAdvancedConfiguration: {
                                rootCertificates: invalidCert,
                            },
                        },
                    }),
                ).rejects.toThrow();
            },
            TIMEOUT,
        );

        it(
            "should fail when insecure TLS is enabled without useTLS",
            async () => {
                await expect(
                    GlideClient.createClient({
                        ...getTlsClientConfigurationOption(
                            standaloneCluster.getAddresses(),
                        ),
                        useTLS: false,
                        advancedConfiguration: {
                            tlsAdvancedConfiguration: {
                                insecure: true,
                            },
                        },
                    }),
                ).rejects.toThrow(
                    "TLS advanced configuration cannot be set when useTLS is disabled.",
                );
            },
            TIMEOUT,
        );
    });

    describe("Cluster TLS with certificates", () => {
        it(
            "should fail to connect with TLS when no certificate provided",
            async () => {
                // Self-signed certificate will be rejected by platform verifier
                await expect(
                    GlideClusterClient.createClient({
                        ...getTlsClientConfigurationOption(
                            clusterModeCluster.getAddresses(),
                        ),
                        useTLS: true,
                    }),
                ).rejects.toThrow();
            },
            TIMEOUT,
        );

        it(
            "should connect when insecure TLS is enabled without certificate",
            async () => {
                clusterClient = await GlideClusterClient.createClient({
                    ...getTlsClientConfigurationOption(
                        clusterModeCluster.getAddresses(),
                    ),
                    useTLS: true,
                    advancedConfiguration: {
                        tlsAdvancedConfiguration: {
                            insecure: true,
                        },
                    },
                });

                const result = await clusterClient.ping();
                expect(result).toBe("PONG");
            },
            TIMEOUT,
        );

        it(
            "should connect with all TLS materials loaded from file helpers",
            async () => {
                const rootCert = loadRootCertificatesFromFile(caCertPath);
                const clientCert =
                    loadClientCertificateFromFile(serverCertPath);
                const clientKey = loadClientPrivateKeyFromFile(serverKeyPath);

                clusterClient = await GlideClusterClient.createClient({
                    ...getTlsClientConfigurationOption(
                        clusterModeCluster.getAddresses(),
                    ),
                    useTLS: true,
                    advancedConfiguration: {
                        tlsAdvancedConfiguration: {
                            rootCertificates: rootCert,
                            clientCertificate: clientCert,
                            clientPrivateKey: clientKey,
                        },
                    },
                });

                const result = await clusterClient.ping();
                expect(result).toBe("PONG");
            },
            TIMEOUT,
        );

        it(
            "should connect with TLS using custom certificate as Buffer",
            async () => {
                clusterClient = await GlideClusterClient.createClient({
                    ...getTlsClientConfigurationOption(
                        clusterModeCluster.getAddresses(),
                    ),
                    useTLS: true,
                    advancedConfiguration: {
                        tlsAdvancedConfiguration: {
                            rootCertificates: caCertData,
                        },
                    },
                });

                const result = await clusterClient.set(
                    "tls-cluster-key",
                    "test-value",
                );
                expect(result).toBe("OK");
            },
            TIMEOUT,
        );

        it(
            "should connect with custom certificate as string",
            async () => {
                const certString = caCertData.toString("utf-8");

                clusterClient = await GlideClusterClient.createClient({
                    ...getTlsClientConfigurationOption(
                        clusterModeCluster.getAddresses(),
                    ),
                    useTLS: true,
                    advancedConfiguration: {
                        tlsAdvancedConfiguration: {
                            rootCertificates: certString,
                        },
                    },
                });

                const result = await clusterClient.ping();
                expect(result).toBe("PONG");
            },
            TIMEOUT,
        );

        it(
            "should connect with multiple certificates in bundle",
            async () => {
                // Simulate a cacerts.pem with multiple certificates
                const certString = caCertData.toString("utf-8");
                const multipleCerts = certString + "\n" + certString;
                const certBundle = Buffer.from(multipleCerts, "utf-8");

                clusterClient = await GlideClusterClient.createClient({
                    ...getTlsClientConfigurationOption(
                        clusterModeCluster.getAddresses(),
                    ),
                    useTLS: true,
                    advancedConfiguration: {
                        tlsAdvancedConfiguration: {
                            rootCertificates: certBundle,
                        },
                    },
                });

                const result = await clusterClient.ping();
                expect(result).toBe("PONG");
            },
            TIMEOUT,
        );

        it(
            "should fail with empty certificate",
            async () => {
                const emptyCert = Buffer.alloc(0);

                await expect(
                    GlideClusterClient.createClient({
                        ...getTlsClientConfigurationOption(
                            clusterModeCluster.getAddresses(),
                        ),
                        useTLS: true,
                        advancedConfiguration: {
                            tlsAdvancedConfiguration: {
                                rootCertificates: emptyCert,
                            },
                        },
                    }),
                ).rejects.toThrow();
            },
            TIMEOUT,
        );

        it(
            "should fail with invalid certificate",
            async () => {
                const invalidCert = Buffer.from(
                    "-----BEGIN CERTIFICATE-----\nINVALID\n-----END CERTIFICATE-----",
                    "utf-8",
                );

                await expect(
                    GlideClusterClient.createClient({
                        ...getTlsClientConfigurationOption(
                            clusterModeCluster.getAddresses(),
                        ),
                        useTLS: true,
                        advancedConfiguration: {
                            tlsAdvancedConfiguration: {
                                rootCertificates: invalidCert,
                            },
                        },
                    }),
                ).rejects.toThrow();
            },
            TIMEOUT,
        );

        it(
            "should fail when insecure TLS is enabled without useTLS",
            async () => {
                await expect(
                    GlideClusterClient.createClient({
                        ...getTlsClientConfigurationOption(
                            clusterModeCluster.getAddresses(),
                        ),
                        useTLS: false,
                        advancedConfiguration: {
                            tlsAdvancedConfiguration: {
                                insecure: true,
                            },
                        },
                    }),
                ).rejects.toThrow(
                    "TLS advanced configuration cannot be set when useTLS is disabled.",
                );
            },
            TIMEOUT,
        );
    });
});
