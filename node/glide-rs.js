#!/usr/bin/env node

/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

/* eslint-disable */
/* prettier-ignore */

const { existsSync, readFileSync } = require('fs')
const { join } = require('path')

const { platform, arch } = process

let nativeBinding = null
let localFileExisted = false
let loadError = null

function isMusl() {
    if (!process.report || typeof process.report.getReport !== 'function') {
        try {
            const lddPath = require('child_process').execSync('which ldd').toString().trim()
            return readFileSync(lddPath, 'utf8').includes('musl')
        } catch (e) {
            return true
        }
    } else {
        const { glibcVersionRuntime } = process.report.getReport().header
        return !glibcVersionRuntime
    }
}

switch (platform) {
    case 'darwin':
        switch (arch) {
            case 'x64':
                localFileExisted = existsSync(join(__dirname, 'rust-client/glide-rs.darwin-x64.node'));
                try {
                    if (localFileExisted) {
                        nativeBinding = require('./rust-client/glide-rs.darwin-x64.node');
                    } else {
                        nativeBinding = require('@valkey/valkey-glide-darwin-x64');
                    }
                } catch (e) {
                    loadError = e;
                }
                break;
            case 'arm64':
                localFileExisted = existsSync(join(__dirname, 'rust-client/glide-rs.darwin-arm64.node'));
                try {
                    if (localFileExisted) {
                        nativeBinding = require('./rust-client/glide-rs.darwin-arm64.node');
                    } else {
                        nativeBinding = require('@valkey/valkey-glide-darwin-arm64');
                    }
                } catch (e) {
                    loadError = e;
                }
                break;
            default:
                throw new Error(`Unsupported architecture on macOS: ${arch}`);
        }
        break;
    case 'linux':
        switch (arch) {
            case 'x64':
                if (isMusl()) {
                    localFileExisted = existsSync(join(__dirname, 'rust-client/glide-rs.linux-x64-musl.node'));
                    try {
                        if (localFileExisted) {
                            nativeBinding = require('./rust-client/glide-rs.linux-x64-musl.node');
                        } else {
                            nativeBinding = require('@valkey/valkey-glide-linux-musl-x64');
                        }
                    } catch (e) {
                        loadError = e;
                    }
                } else {
                    localFileExisted = existsSync(join(__dirname, 'rust-client/glide-rs.linux-x64-gnu.node'));
                    try {
                        if (localFileExisted) {
                            nativeBinding = require('./rust-client/glide-rs.linux-x64-gnu.node');
                        } else {
                            nativeBinding = require('@valkey/valkey-glide-linux-x64');
                        }
                    } catch (e) {
                        loadError = e;
                    }
                }
                break;
            case 'arm64':
                if (isMusl()) {
                    localFileExisted = existsSync(join(__dirname, 'rust-client/glide-rs.linux-arm64-musl.node'));
                    try {
                        if (localFileExisted) {
                            nativeBinding = require('./rust-client/glide-rs.linux-arm64-musl.node');
                        } else {
                            nativeBinding = require('@valkey/valkey-glide-linux-musl-arm64');
                        }
                    } catch (e) {
                        loadError = e;
                    }
                } else {
                    localFileExisted = existsSync(join(__dirname, 'rust-client/glide-rs.linux-arm64-gnu.node'));
                    try {
                        if (localFileExisted) {
                            nativeBinding = require('./rust-client/glide-rs.linux-arm64-gnu.node');
                        } else {
                            nativeBinding = require('@valkey/valkey-glide-linux-arm64');
                        }
                    } catch (e) {
                        loadError = e;
                    }
                }
                break;
            default:
                throw new Error(`Unsupported OS: ${platform}, architecture: ${arch}`);
        }
}
if (!nativeBinding) {
    if (loadError) {
        throw loadError
    }
    throw new Error(`Failed to load native binding`)
}

export default nativeBinding
