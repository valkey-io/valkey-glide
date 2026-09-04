/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 *
 * Internal, package-private plumbing for isolated scopes. This module is not
 * re-exported from the package entry point.
 *
 * The connection request captured when a client connects can carry a password
 * or a byte-based mTLS private key. It is held in a module-scope `WeakMap`
 * keyed by the client, not on the client instance or its prototype, so no
 * caller holding a client can reach the bytes through property or symbol
 * enumeration. Only this module reads them, and only to hand them straight to
 * the native scope layer.
 */

import { scopeTryAcquire } from "../build-ts/native";
import { connection_request } from "../build-ts/ProtobufMessage";
import type { BaseClient } from "./BaseClient";

/** client -> serialized ConnectionRequest captured at connect time. */
const connectionRequests = new WeakMap<BaseClient, Uint8Array>();

/** Store the connection request captured when the client connected. */
export function setScopeConnectionRequest(
    client: BaseClient,
    bytes: Uint8Array,
): void {
    connectionRequests.set(client, bytes);
}

/** Drop the stored request when the client closes. */
export function clearScopeConnectionRequest(client: BaseClient): void {
    connectionRequests.delete(client);
}

/** Whether a connection request is cached for this client. */
export function hasScopeConnectionRequest(client: BaseClient): boolean {
    return connectionRequests.has(client);
}

/**
 * Rewrite the password in the cached request so scopes opened after a password
 * update authenticate with the new credential. No-op if nothing is cached.
 */
export function refreshScopeConnectionPassword(
    client: BaseClient,
    password: string | null,
): void {
    const bytes = connectionRequests.get(client);
    if (!bytes) return;

    const request = connection_request.ConnectionRequest.decode(bytes);
    const authenticationInfo =
        request.authenticationInfo ??
        connection_request.AuthenticationInfo.create({});
    authenticationInfo.password = password ?? "";
    request.authenticationInfo = authenticationInfo;
    connectionRequests.set(
        client,
        connection_request.ConnectionRequest.encode(request).finish(),
    );
}

/**
 * Try to acquire a scope for a client, feeding the cached connection request
 * straight to the native layer. The bytes never leave this module, so a caller
 * holding the client or the returned scope cannot read the password or mTLS key
 * they carry. Pass `explicitBytes` to use a caller-supplied request instead.
 *
 * Returns the native scope id (>= 0), or a negative value when the pool is
 * exhausted or the request is invalid.
 */
export function tryAcquireScope(
    client: BaseClient,
    clientId: number,
    routingSlot: number,
    explicitBytes?: Uint8Array,
): number {
    const bytes = explicitBytes ?? connectionRequests.get(client);

    if (!bytes) {
        throw new Error(
            "Client has no connection request available for a scope. " +
                "Ensure it was created via GlideClient.createClient() (or GlideClusterClient.createClient()) and is connected.",
        );
    }

    return scopeTryAcquire(clientId, bytes, routingSlot);
}
