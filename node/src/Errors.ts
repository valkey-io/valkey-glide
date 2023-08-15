/// Base class for errors.
export abstract class BaseRedisError extends Error {
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
export class ClosingError extends BaseRedisError {}

/// Errors that were reported during a request.
export class RedisError extends BaseRedisError {}

/// Errors that are thrown when a request times out.
export class TimeoutError extends RedisError {}

/// Errors that are thrown when a transaction is aborted.
export class ExecAbortError extends RedisError {}

/// Errors that are thrown when a connection disconnects. These errors can be temporary, as the client will attempt to reconnect.
export class ConnectionError extends RedisError {}
