/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import {
    afterAll,
    beforeAll,
    describe,
    expect,
    it,
} from "@jest/globals";
import * as fs from "fs";
import { TestTLSConfig, ValkeyCluster } from "../../utils/TestUtils.js";
import { GlideClient, Logger, ProtocolVersion } from "../build-ts";
import {
    getCaCertificateData,
    getClientConfigurationOption,
    getServerVersion,
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

describe("mTLS integration", () => {
    let mtlsCluster: ValkeyCluster;
    let caCertData: Buffer;
    let clientCert: Buffer;
    let clientKey: Buffer;

    beforeAll(async () => {
        // Start the cluster once with an insecure TLS bootstrap so
        // getServerVersion can talk to the mTLS-required server without a
        // client certificate. The real tests below use full cert material.
        const startupTlsConfig: TestTLSConfig = {
            useTLS: true,
            requestTimeout: TLS_REQUEST_TIMEOUT,
            advancedConfiguration: {
                tlsAdvancedConfiguration: {
                    insecure: true,
                },
            },
        };

        mtlsCluster = await ValkeyCluster.createCluster(
            false,
            1,
            0,
            getServerVersion,
            true,
            startupTlsConfig,
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
