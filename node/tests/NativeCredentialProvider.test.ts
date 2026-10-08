/*
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { describe, expect, it } from "@jest/globals";
import {
    CreateDirectClient,
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
});
