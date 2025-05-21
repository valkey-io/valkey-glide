/**
 * Types exported by the common package
 */

export interface DatabaseConnection {
    connect(): Promise<void>;
    disconnect(): Promise<void>;
}

export interface CacheOperations<T> {
    get(key: string): Promise<T | null>;
    set(key: string, value: T): Promise<void>;
}
