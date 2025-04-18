/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

// This file handles resolving and exporting all native bindings from the underlying 
// Rust implementation. It ensures all types and constants are properly exported
// regardless of whether in development or production environments.
/* eslint-disable */
/* prettier-ignore */
import { readFileSync } from 'fs';
import { arch, platform } from 'process';

// Function to detect musl libc on Linux (same as in rust-client/index.js)
function isMusl(): boolean {
    // For Node 10
    if (!process.report || typeof process.report.getReport !== 'function') {
        try {
            const lddPath = require('child_process').execSync('which ldd').toString().trim();
            return readFileSync(lddPath, 'utf8').includes('musl');
        } catch (e) {
            return true;
        }
    } else {
        const { glibcVersionRuntime } = (process.report.getReport() as any).header;
        return !glibcVersionRuntime;
    }
}

// Dynamic loading of the appropriate native binding
function loadNativeBinding() {
    let nativeBinding = null;
    
    // First try to load the local rust-client (for development)
    try {
        const rustClient = require('../rust-client');
        console.debug('Loaded local rust-client module');
        return rustClient;
    } catch (localError) {
        // Local client not available, try platform-specific packages
        // We'll use the same package naming convention as in the original code
        console.debug('Local rust-client module not available, trying platform-specific packages');
    }

    // If local client isn't available, try platform-specific packages
    // This follows the same pattern as the original rust-client/index.js
    try {
        const packagePrefix = '@valkey/valkey-glide';
        
        switch (platform) {
            case 'darwin':
                switch (arch) {
                    case 'x64':
                        nativeBinding = require(`${packagePrefix}-darwin-x64`);
                        break;
                    case 'arm64':
                        nativeBinding = require(`${packagePrefix}-darwin-arm64`);
                        break;
                    default:
                        throw new Error(`Unsupported architecture on macOS: ${arch}`);
                }

                break;
            case 'linux':
                switch (arch) {
                    case 'x64':
                        if (isMusl()) {
                            nativeBinding = require(`${packagePrefix}-linux-x64-musl`);
                        } else {
                            nativeBinding = require(`${packagePrefix}-linux-x64-gnu`);
                        }

                        break;
                    case 'arm64':
                        if (isMusl()) {
                            nativeBinding = require(`${packagePrefix}-linux-arm64-musl`);
                        } else {
                            nativeBinding = require(`${packagePrefix}-linux-arm64-gnu`);
                        }

                        break;
                    case 'arm':
                        if (isMusl()) {
                            nativeBinding = require(`${packagePrefix}-linux-arm-musleabihf`);
                        } else {
                            nativeBinding = require(`${packagePrefix}-linux-arm-gnueabihf`);
                        }

                        break;
                    default:
                        throw new Error(`Unsupported architecture on Linux: ${arch}`);
                }

                break;
            case 'win32':
                switch (arch) {
                    case 'x64':
                        nativeBinding = require(`${packagePrefix}-win32-x64-msvc`);
                        break;
                    default:
                        throw new Error(`Unsupported architecture on Windows: ${arch}`);
                }

                break;
            default:
                throw new Error(`Unsupported OS: ${platform}, architecture: ${arch}`);
        }
        
        console.debug(`Loaded platform-specific binding: ${platform}-${arch}`);
        return nativeBinding;
    } catch (platformError) {
        const errorMessage = platformError instanceof Error 
            ? platformError.message 
            : String(platformError);
        console.error(`Failed to load native binding: ${errorMessage}`);
        throw new Error(`Failed to load native binding: ${errorMessage}`);
    }
}

const nativeBinding = loadNativeBinding();


export const Level = nativeBinding.Level;
export const MAX_REQUEST_ARGS_LEN = nativeBinding.MAX_REQUEST_ARGS_LEN;
export const DEFAULT_REQUEST_TIMEOUT_IN_MILLISECONDS = nativeBinding.DEFAULT_REQUEST_TIMEOUT_IN_MILLISECONDS;
export const DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS = nativeBinding.DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS;
export const DEFAULT_INFLIGHT_REQUESTS_LIMIT = nativeBinding.DEFAULT_INFLIGHT_REQUESTS_LIMIT;
export const AsyncClient = nativeBinding.AsyncClient;
export const StartSocketConnection = nativeBinding.StartSocketConnection;
export const log = nativeBinding.log;
export const InitInternalLogger = nativeBinding.InitInternalLogger;
export const valueFromSplitPointer = nativeBinding.valueFromSplitPointer;
export const createLeakedString = nativeBinding.createLeakedString;
export const createLeakedStringVec = nativeBinding.createLeakedStringVec;
export const createLeakedMap = nativeBinding.createLeakedMap;
export const createLeakedArray = nativeBinding.createLeakedArray;
export const createLeakedAttribute = nativeBinding.createLeakedAttribute;
export const createLeakedBigint = nativeBinding.createLeakedBigint;
export const createLeakedDouble = nativeBinding.createLeakedDouble;
export const Script = nativeBinding.Script;
export const ClusterScanCursor = nativeBinding.ClusterScanCursor;
export const getStatistics = nativeBinding.getStatistics;

export default nativeBinding;
