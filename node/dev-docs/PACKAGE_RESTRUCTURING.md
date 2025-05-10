# Valkey Glide Node.js Package Restructuring

## Overview

This document outlines the restructuring of the Node.js client package for Valkey Glide. The goal is to separate the package into two distinct components:

1. **Core TypeScript Package** (`@valkey/valkey-glide`): Contains the TypeScript API without any native code
2. **Platform-Specific Packages** (`@valkey/valkey-glide-<platform>-<arch>`): Contain only the native code for specific platforms

This separation provides numerous benefits including smaller package sizes, better maintainability, and improved CI/CD workflows.

## Current Structure

Currently, the package structure is as follows:

- `@valkey/valkey-glide`: A single package containing both TypeScript code and platform-specific native bindings
- The native loader (`native.js`) attempts to load local `.node` files first, then falls back to external packages
- All code is built and published as a single unit

## New Structure

The new structure will consist of:

### 1. Core TypeScript Package (`@valkey/valkey-glide`)

- Contains only TypeScript code (compiled to JavaScript)
- Has peer dependencies on platform-specific packages
- Automatically loads the correct platform package at runtime
- Serves as the main entry point for users

### 2. Platform-Specific Packages (`@valkey/valkey-glide-<platform>-<arch>`)

- One package per supported platform/architecture (e.g., `linux-arm64-gnu`, `darwin-x64`)
- Contains only the native code (`.node` file) for its specific platform
- Has no dependencies on the main TypeScript package
- Includes minimal JavaScript code for loading the native module

## Implementation Details

### Package Structure and Files

#### Core Package

```
@valkey/valkey-glide/
├── build-ts/         # Compiled TypeScript code
├── native.js         # Native module loader (imports from platform packages)
├── native.d.ts       # Type definitions for native functions
├── package.json      # Main package file with peer dependencies
└── README.md
```

#### Platform-Specific Package

```
@valkey/valkey-glide-<platform>-<arch>/
├── native.js         # Simple loader for the .node file
├── native.d.ts       # Type definitions (same as main package)
├── *.node            # Native binary module
└── package.json      # Platform package definition
```

### Native Module Loading

The `native.js` file in the main package will:

1. Detect the current platform and architecture
2. Attempt to require the appropriate platform-specific package
3. Export the required native functions

```javascript
// Example native.js implementation in main package
const { platform, arch } = process;
let nativeBinding;

// Determine which package to load based on platform/architecture
const packageName = determinePackageName(platform, arch);
try {
  // Load the platform-specific package
  nativeBinding = require(packageName);
} catch (e) {
  throw new Error(`Failed to load native binding for ${platform}-${arch}: ${e.message}`);
}

// Export all native functions
module.exports = nativeBinding;
```

### Package.json Configuration

#### Main Package

```json
{
  "name": "@valkey/valkey-glide",
  "version": "1.0.0",
  "main": "build-ts/index.js",
  "types": "build-ts/index.d.ts",
  "peerDependencies": {
    "@valkey/valkey-glide-linux-x64-gnu": "^1.0.0",
    "@valkey/valkey-glide-linux-arm64-gnu": "^1.0.0",
    "@valkey/valkey-glide-darwin-x64": "^1.0.0",
    "@valkey/valkey-glide-darwin-arm64": "^1.0.0"
    // Additional platforms as needed
  },
  "peerDependenciesMeta": {
    "@valkey/valkey-glide-linux-x64-gnu": {
      "optional": true
    },
    // Mark all platform packages as optional
  },
  "dependencies": {
    "long": "5",
    "protobufjs-minimal": "6.11.5"
  }
}
```

#### Platform-Specific Package

```json
{
  "name": "@valkey/valkey-glide-linux-x64-gnu",
  "version": "1.0.0",
  "main": "native.js",
  "types": "native.d.ts",
  "os": ["linux"],
  "cpu": ["x64"],
  "libc": ["glibc"]
}
```

## Build and Release Process

### Building

1. Build the TypeScript code into the `build-ts` directory
2. Generate platform-specific `.node` files for each supported platform
3. Create platform-specific packages with the appropriate `.node` file
4. Create the main package with the TypeScript code

### Versioning

All packages (main and platform-specific) should share the same version number to ensure compatibility. When a new version is released, all packages should be updated together.

### CI/CD Pipeline

The CI/CD pipeline will:

1. Build the TypeScript code once
2. Build native code for each platform in parallel
3. Package and publish all packages together
4. Run verification tests to ensure the packages work together correctly

## Transition Plan

1. Create template packages for each platform
2. Update the native loader in the main package
3. Convert dependencies to peer dependencies
4. Update build scripts to generate all packages
5. Update CI/CD pipeline
6. Test the new structure thoroughly
7. Release the new packages

## Benefits

1. **Smaller Packages**: Users only download what they need for their platform
2. **Cleaner Dependencies**: Clear separation between TypeScript and native code
3. **Improved Maintainability**: Changes to TypeScript don't require rebuilding native code
4. **Enhanced CI**: Faster CI workflows for TypeScript changes
5. **Better Version Control**: Native code and TypeScript code can evolve separately
