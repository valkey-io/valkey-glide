/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { afterAll, beforeAll, describe, expect, it } from "@jest/globals";
import * as fs from "fs";
import { ValkeyCluster } from "../../utils/TestUtils.js";
import {
    GlideClient,
    GlideClusterClient,
    Logger,
    ProtocolVersion,
} from "../build-ts";
import {
    getCaCertificateData,
    getClientConfigurationOption,
} from "./TestUtilities";

const TIMEOUT = 50000;
const CLUSTER_CREATION_TIMEOUT = 120000;
const TLS_REQUEST_TIMEOUT = 10000;

/**
 * Load a PEM file from utils/tls_crts, resolving GLIDE_HOME_DIR the same way
 * getCaCertificateData does. Kept local since it is only used by these tests.
 */
function readTlsFile(name: string): Buffer {
    const glideHomeDir = process.env.GLIDE_HOME_DIR || process.cwd() + "/..";
    return fs.readFileSync(`${glideHomeDir}/utils/tls_crts/${name}`);
}

/**
 * ValkeyCluster.createCluster calls a version-fetch callback right after the
 * cluster comes up. The default getServerVersion opens a client, and any such
 * client on an mTLS-required server has to present a client certificate or
 * the TLS handshake is dropped. Insecure TLS does not help since it only
 * skips server-certificate verification on the client side. This stub keeps
 * the callback signature but does not open a connection, matching the Java
 * and Python fixtures which do not fetch the version either.
 */
const skipVersionFetch = async (): Promise<string> => "";

describe("mTLS integration", () => {
    let mtlsCluster: ValkeyCluster;
    let caCertData: Buffer;
    let clientCert: Buffer;
    let clientKey: Buffer;

    beforeAll(async () => {
        mtlsCluster = await ValkeyCluster.createCluster(
            false,
            1,
            0,
            skipVersionFetch,
            true,
            undefined,
            undefined,
            true,
        );

        caCertData = getCaCertificateData();
        clientCert = readTlsFile("server.crt");
        clientKey = readTlsFile("server.key");
    }, CLUSTER_CREATION_TIMEOUT);

    afterAll(async () => {
        try {
            if (mtlsCluster) {
                await mtlsCluster.close();
            }
        } catch (error) {
            Logger.log(
                "warn",
                "MutualTLS",
                "Error closing mTLS cluster",
                error as Error,
            );
        }
    });

    it(
        "client with cert+key accepted by server requiring client cert",
        async () => {
            const client = await GlideClient.createClient({
                ...getClientConfigurationOption(
                    mtlsCluster.getAddresses(),
                    ProtocolVersion.RESP3,
                    { requestTimeout: TLS_REQUEST_TIMEOUT },
                ),
                useTLS: true,
                advancedConfiguration: {
                    tlsAdvancedConfiguration: {
                        rootCertificates: caCertData,
                        mutualTls: {
                            kind: "bytes",
                            clientCertificate: clientCert,
                            clientKey: clientKey,
                        },
                    },
                },
            });

            try {
                const result = await client.ping();
                expect(result).toBe("PONG");
            } finally {
                await client.close();
            }
        },
        TIMEOUT,
    );

    it(
        "client without cert+key rejected by server requiring client cert",
        async () => {
            await expect(
                GlideClient.createClient({
                    ...getClientConfigurationOption(
                        mtlsCluster.getAddresses(),
                        ProtocolVersion.RESP3,
                        { requestTimeout: TLS_REQUEST_TIMEOUT },
                    ),
                    useTLS: true,
                    advancedConfiguration: {
                        tlsAdvancedConfiguration: {
                            rootCertificates: caCertData,
                        },
                    },
                }),
            ).rejects.toThrow();
        },
        TIMEOUT,
    );
});

describe("mTLS integration (cluster)", () => {
    let mtlsCluster: ValkeyCluster;
    let caCertData: Buffer;
    let clientCert: Buffer;
    let clientKey: Buffer;

    beforeAll(async () => {
        mtlsCluster = await ValkeyCluster.createCluster(
            true,
            3,
            1,
            skipVersionFetch,
            true,
            undefined,
            undefined,
            true,
        );

        caCertData = getCaCertificateData();
        clientCert = readTlsFile("server.crt");
        clientKey = readTlsFile("server.key");
    }, CLUSTER_CREATION_TIMEOUT);

    afterAll(async () => {
        try {
            if (mtlsCluster) {
                await mtlsCluster.close();
            }
        } catch (error) {
            Logger.log(
                "warn",
                "MutualTLS",
                "Error closing mTLS cluster",
                error as Error,
            );
        }
    });

    it(
        "cluster client with cert+key accepted by server requiring client cert",
        async () => {
            const client = await GlideClusterClient.createClient({
                ...getClientConfigurationOption(
                    mtlsCluster.getAddresses(),
                    ProtocolVersion.RESP3,
                    { requestTimeout: TLS_REQUEST_TIMEOUT },
                ),
                useTLS: true,
                advancedConfiguration: {
                    tlsAdvancedConfiguration: {
                        rootCertificates: caCertData,
                        mutualTls: {
                            kind: "bytes",
                            clientCertificate: clientCert,
                            clientKey: clientKey,
                        },
                    },
                },
            });

            try {
                const result = await client.ping();
                expect(result).toBe("PONG");
            } finally {
                await client.close();
            }
        },
        TIMEOUT,
    );

    it(
        "cluster client without cert+key rejected by server requiring client cert",
        async () => {
            await expect(
                GlideClusterClient.createClient({
                    ...getClientConfigurationOption(
                        mtlsCluster.getAddresses(),
                        ProtocolVersion.RESP3,
                        { requestTimeout: TLS_REQUEST_TIMEOUT },
                    ),
                    useTLS: true,
                    advancedConfiguration: {
                        tlsAdvancedConfiguration: {
                            rootCertificates: caCertData,
                        },
                    },
                }),
            ).rejects.toThrow();
        },
        TIMEOUT,
    );
});
