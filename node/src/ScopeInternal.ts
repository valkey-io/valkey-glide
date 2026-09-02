/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 *
 * Internal, package-private plumbing for isolated scopes. This module is not
 * re-exported from the package entry point, so the symbol below stays off the
 * public surface: only code within this package can reach the cached
 * connection-request bytes.
 */

/**
 * Keys the client accessor that hands a scope its cached connection-request
 * bytes. The request can hold a password or byte-based mTLS key, so it is
 * reached through a symbol rather than a public method.
 */
export const CONNECTION_REQUEST_BYTES: unique symbol = Symbol(
    "connectionRequestBytes",
);
