/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

/// Base class for errors.
export abstract class ValkeyError extends Error {
    public message: string;

    constructor(message?: string) {
        super();
        this.message = message ?? "No error message provided";
    }

    public get name(): string {
        return this.constructor.name;
    }
}

/// Errors that report that the client has closed and is no longer usable.
export class ClosingError extends ValkeyError {}

/// Errors that were reported during a request.
export class RequestError extends ValkeyError {}

/// Errors that are thrown when a request times out.
export class TimeoutError extends RequestError {}

export const TIMEOUT_ERROR = new TimeoutError("Operation timed out");

/// Errors that are thrown when a transaction is aborted.
export class ExecAbortError extends RequestError {}

/// Errors that are thrown when a connection disconnects. These errors can be temporary, as the client will attempt to reconnect.
export class ConnectionError extends RequestError {}

/// Errors that are thrown when a request cannot be completed in current configuration settings.
export class ConfigurationError extends RequestError {}

export class CircuitBreakerError extends RequestError {}

/**
 * @internal
 * Maps a native client or pool creation failure to a `ValkeyError` with the same message:
 * {@link ConfigurationError} for napi code `InvalidArg` (invalid connection request or pool
 * configuration), else {@link ClosingError}.
 */
export function toClientCreationError(error: unknown): ValkeyError {
    if (error instanceof ValkeyError) {
        return error;
    }

    // Duck-typed: native errors may come from another realm (e.g. Jest VM).
    const { message, code } =
        typeof error === "object" && error !== null
            ? (error as { message?: unknown; code?: unknown })
            : { message: error, code: undefined };

    return code === "InvalidArg"
        ? new ConfigurationError(String(message))
        : new ClosingError(String(message));
}
