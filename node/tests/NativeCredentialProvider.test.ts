/*
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { describe, expect, it } from "@jest/globals";
import {
    CreateDirectClient,
    createMonitorClient,
    registerCredentialProvider,
    removeCredentialProvider,
} from "../build-ts/native";
import { connection_request } from "../build-ts/ProtobufMessage";

const encodeRequest = (
    credentialProviderKey?: string,
    includeAddress = true,
    includeIam = true,
): Uint8Array => {
    const request = connection_request.ConnectionRequest.create({
        addresses: includeAddress ? [{ host: "127.0.0.1", port: 1 }] : [],
        lazyConnect: true,
        ...(includeIam
            ? {
                  // IAM requires TLS; set it so the request reaches the
                  // native handle logic under test instead of the TLS guard.
                  tlsMode: connection_request.TlsMode.SecureTls,
                  authenticationInfo: {
                      username: "iam-user",
                      iamCredentials: {
                          clusterName: "cluster",
                          region: "us-east-1",
                          serviceType:
                              connection_request.ServiceType.ELASTICACHE,
                      },
                  },
              }
            : {}),
        ...(credentialProviderKey === undefined
            ? {}
            : { credentialProviderKey }),
    });
    return connection_request.ConnectionRequest.encode(request).finish();
};

describe("Direct native credential provider key boundary", () => {
    it("allows an absent credential provider key", async () => {
        const handle = await CreateDirectClient(
            encodeRequest(undefined, true, false),
            () => undefined,
        );

        handle.close();
    });

    it.each(["", " \t\n"])(
        "rejects a present blank credential provider key %p before client creation",
        (credentialProviderKey) => {
            // No address is intentional: reaching Client::new would produce a
            // different error instead of this synchronous configuration throw.
            expect(() =>
                CreateDirectClient(
                    encodeRequest(credentialProviderKey, false),
                    () => undefined,
                ),
            ).toThrow(/must not be empty or whitespace/u);
        },
    );

    it("accepts and consumes a valid credential provider key", async () => {
        const providerKey = registerCredentialProvider(() => ({
            accessKeyId: "access-key",
            secretAccessKey: "secret-key",
        }));

        try {
            const request = encodeRequest(providerKey);
            const handle = await CreateDirectClient(request, () => undefined);
            handle.close();

            expect(() => CreateDirectClient(request, () => undefined)).toThrow(
                /was not found in the registry/u,
            );
        } finally {
            removeCredentialProvider(providerKey);
        }
    });

    it("cleans a provider-direct handle synchronously after injected post-conversion failure", async () => {
        const {
            getProviderDirectCleanupCount,
            getProviderDirectOpenHandleCount,
            injectProviderDirectResolutionFailureAfterConversion,
        } = await import("../build-ts/native");
        injectProviderDirectResolutionFailureAfterConversion();

        // The one-shot fault and cleanup state are provider-direct-only. An
        // ordinary direct handle converts and closes without consuming either.
        const ordinaryHandle = await CreateDirectClient(
            encodeRequest(undefined, true, false),
            () => undefined,
        );
        ordinaryHandle.close();
        expect(getProviderDirectCleanupCount()).toBe(0);
        expect(getProviderDirectOpenHandleCount()).toBe(0);

        const providerKey = registerCredentialProvider(() => ({
            accessKeyId: "access-key",
            secretAccessKey: "secret-key",
        }));
        const request = encodeRequest(providerKey);

        try {
            await expect(
                CreateDirectClient(request, () => undefined),
            ).rejects.toThrow(/failure after native handle conversion/u);

            // Conversion committed the claim, and the injected failure invoked
            // provider-direct cleanup before publishing the rejection.
            expect(() => CreateDirectClient(request, () => undefined)).toThrow(
                /was not found in the registry/u,
            );
            expect(getProviderDirectCleanupCount()).toBe(1);
            expect(getProviderDirectOpenHandleCount()).toBe(0);
        } finally {
            removeCredentialProvider(providerKey);
        }
    });
});

describe("Raw monitor credential boundary", () => {
    const callback = () => undefined;

    it.each(["", "custom-provider"])(
        "rejects present credential provider key %p before address conversion",
        (key) => {
            expect(() =>
                createMonitorClient(encodeRequest(key, false, true), callback),
            ).toThrow(/do not support IAM authentication/u);
        },
    );

    it("rejects default-chain IAM before address conversion", () => {
        expect(() =>
            createMonitorClient(
                encodeRequest(undefined, false, true),
                callback,
            ),
        ).toThrow(/do not support IAM authentication/u);
    });

    it("allows username/password through the credential guard", () => {
        const request = connection_request.ConnectionRequest.create({
            authenticationInfo: { username: "user", password: "password" },
        });
        const bytes =
            connection_request.ConnectionRequest.encode(request).finish();
        expect(() => createMonitorClient(bytes, callback)).toThrow(
            /No addresses provided/u,
        );
    });
});
