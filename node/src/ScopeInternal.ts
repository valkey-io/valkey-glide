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

import { scopeAcquire } from "../build-ts/native";
import type { BaseClient } from "./BaseClient";

/** client -> serialized ConnectionRequest captured at connect time. */
const connectionRequests = new WeakMap<BaseClient, Uint8Array>();

/** Store the connection request a client connected with (or the pool's, for a borrowed client). */
export function setScopeConnectionRequest(
    client: BaseClient,
    bytes: Uint8Array,
): void {
    connectionRequests.set(client, bytes);
}

/** Drop the stored request when the client closes or is released to its pool. */
export function clearScopeConnectionRequest(client: BaseClient): void {
    connectionRequests.delete(client);
}

/**
 * Acquire a scope for a client, feeding the cached connection request
 * straight to the native layer. The bytes never leave this module, so a caller
 * holding the client or the returned scope cannot read the password or mTLS key
 * they carry. Pass `explicitBytes` to use a caller-supplied request instead.
 *
 * Resolves to the native scope id; rejects with the core's prefixed message
 * when the wait times out, the client closes, or the request is invalid.
 */
export function acquireScope(
    client: BaseClient,
    clientId: number,
    hasRoutingSlot: boolean,
    routingSlot: number,
    timeoutMs: number,
    explicitBytes?: Uint8Array,
): Promise<number> {
    const bytes = explicitBytes ?? connectionRequests.get(client);

    if (!bytes) {
        throw new Error(
            "Client has no connection request available for a scope. " +
                "Ensure it was created via createClient() or borrowed from a ClientPool, and is still open.",
        );
    }

    return scopeAcquire(
        clientId,
        bytes,
        hasRoutingSlot,
        routingSlot,
        timeoutMs,
    );
}
