#!/usr/bin/env node
"use strict";
// import * as Bar from "@barshaul/babushka-linux-x64";
const { platform, arch } = process;
let nativeBinding = null;
let localFileExisted = false;
let loadError = null;
switch (platform) {
    case 'linux':
        switch (arch) {
            case 'x64':
                console.log("Installing linux library");
                nativeBinding = await import("@barshaul/babushka-linux-x64");
                break;
            default:
                throw new Error(`Unsupported OS: ${platform}, architecture: ${arch}`);
        }
        break;
    case 'darwin':
        switch (arch) {
            case 'x64':
                console.log("Installing MacOS library");
                nativeBinding = await import("@barshaul/babuahks-macos");
                break;
            default:
                throw new Error(`Unsupported OS: ${platform}, architecture: ${arch}`);
        }
        break;
    default:
        throw new Error(`Unsupported OS: ${platform}, architecture: ${arch}`);
}
if (!nativeBinding) {
    if (loadError) {
        throw loadError;
    }
    throw new Error(`Failed to load native binding`);
}
export default Object.assign(global, nativeBinding);
export const __esModule = true
